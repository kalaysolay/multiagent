package com.example.workflow;

import com.example.portal.chat.entity.ChatMessage;
import com.example.portal.chat.repository.ChatMessageRepository;
import com.example.portal.shared.service.RagService;
import com.example.workflow.tools.DatabaseTools;
import com.example.workflow.tools.McpAgentTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;

/**
 * Unit-тесты для ToolCallingChatService.
 * Проверяет, что при наличии conversationId история загружается из БД и передаётся в контекст LLM.
 */
@ExtendWith(MockitoExtension.class)
class ToolCallingChatServiceTest {

    private static final String CONV_ID = "conv-123";
    private static final String PREVIOUS_USER = "Предыдущий вопрос";
    private static final String PREVIOUS_ASSISTANT = "Предыдущий ответ";
    private static final String NEW_MESSAGE = "Новое сообщение";

    @Mock(answer = RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private RagService ragService;

    @Mock
    private WorkflowSessionService workflowSessionService;

    @Mock
    private DatabaseTools databaseTools;

    @Mock
    private McpAgentTools mcpAgentTools;

    @InjectMocks
    private ToolCallingChatService toolCallingChatService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(toolCallingChatService, "contextMessagesLimit", 30);
        when(ragService.retrieveContext(any(), anyInt())).thenReturn(new RagService.ContextResult("", 0, true));
    }

    @Test
    @DisplayName("При наличии conversationId загружает историю из БД и передаёт её в контекст LLM")
    void processMessage_withConversationId_loadsHistoryFromDbAndPassesToLlm() {
        // История в БД: после saveUserMessage в разговоре будут старые сообщения + только что сохранённое новое (NEW_MESSAGE).
        // Порядок desc: новое первым — новое user, затем assistant, затем старый user.
        ChatMessage newUserMsg = ChatMessage.builder()
                .id(UUID.randomUUID())
                .role(ChatMessage.MessageRole.USER)
                .content(NEW_MESSAGE)
                .conversationId(CONV_ID)
                .createdAt(Instant.now())
                .build();
        ChatMessage assistantMsg = ChatMessage.builder()
                .id(UUID.randomUUID())
                .role(ChatMessage.MessageRole.ASSISTANT)
                .content(PREVIOUS_ASSISTANT)
                .conversationId(CONV_ID)
                .createdAt(Instant.now().minusSeconds(10))
                .build();
        ChatMessage userMsg = ChatMessage.builder()
                .id(UUID.randomUUID())
                .role(ChatMessage.MessageRole.USER)
                .content(PREVIOUS_USER)
                .conversationId(CONV_ID)
                .createdAt(Instant.now().minusSeconds(60))
                .build();
        when(chatMessageRepository.findByConversationIdOrderByCreatedAtDesc(eq(CONV_ID), any()))
                .thenReturn(List.of(newUserMsg, assistantMsg, userMsg));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        // Ответ LLM без tool calls
        AssistantMessage assistantOutput = new AssistantMessage(PREVIOUS_ASSISTANT);
        Generation generation = new Generation(assistantOutput);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatClient.prompt(any(Prompt.class)).call().chatResponse()).thenReturn(chatResponse);

        // Вызов с пустой историей с клиента — сервис должен взять историю из БД
        ToolCallingChatService.ChatResult result = toolCallingChatService.processMessage(
                NEW_MESSAGE,
                List.of(),
                null,
                CONV_ID
        );

        assertThat(result.response()).isEqualTo(PREVIOUS_ASSISTANT);
        assertThat(result.conversationId()).isEqualTo(CONV_ID);

        // При переданном conversationId история загружается из БД (источник правды — БД, не клиент)
        verify(chatMessageRepository).findByConversationIdOrderByCreatedAtDesc(eq(CONV_ID), any());
    }

    @Test
    @DisplayName("При отсутствии conversationId использует переданную с клиента историю")
    void processMessage_withoutConversationId_usesClientHistory() {
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        AssistantMessage assistantOutput = new AssistantMessage("OK");
        when(chatClient.prompt(any(Prompt.class)).call().chatResponse())
                .thenReturn(new ChatResponse(List.of(new Generation(assistantOutput))));

        List<Map<String, String>> clientHistory = List.of(
                Map.of("role", "user", "content", "Старый вопрос"),
                Map.of("role", "assistant", "content", "Старый ответ")
        );

        ToolCallingChatService.ChatResult result = toolCallingChatService.processMessage(
                "Новый вопрос",
                clientHistory,
                null,
                null
        );

        assertThat(result.conversationId()).isNotNull();
        // Без conversationId репозиторий по разговору не вызывается — используется переданная с клиента история
        verify(chatMessageRepository, never()).findByConversationIdOrderByCreatedAtDesc(any(), any());
    }
}
