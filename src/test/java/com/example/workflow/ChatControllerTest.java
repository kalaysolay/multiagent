package com.example.workflow;

import com.example.portal.chat.entity.ChatMessage;
import com.example.portal.chat.repository.ChatMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты для ChatController.
 * 
 * Тестируемые сценарии:
 * 1. Обработка сообщения без tool calls
 * 2. Обработка сообщения с tool calls
 * 3. Получение истории чата
 * 4. Получение списка разговоров
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerTest {
    
    @Mock
    private ToolCallingChatService toolCallingChatService;
    
    @Mock
    private ChatMessageRepository chatMessageRepository;
    
    @InjectMocks
    private ChatController chatController;
    
    private static final String TEST_MESSAGE = "Какой статус последней сессии?";
    private static final String TEST_RESPONSE = "Последняя сессия имеет статус COMPLETED";
    private static final String TEST_CONVERSATION_ID = "conv-123";
    
    @BeforeEach
    void setUp() {
        // Initialization if needed
    }
    
    // ====================================================================
    // Тесты для POST /api/chat
    // ====================================================================
    
    @Test
    @DisplayName("chat - возвращает ответ без tool calls")
    void chat_returnsResponseWithoutToolCalls() {
        // GIVEN
        ToolCallingChatService.ChatResult chatResult = new ToolCallingChatService.ChatResult(
                TEST_RESPONSE,
                List.of(),
                List.of(),
                TEST_CONVERSATION_ID
        );
        when(toolCallingChatService.processMessage(any(), any(), any(), any()))
                .thenReturn(chatResult);
        
        ChatController.ChatRequest request = new ChatController.ChatRequest(
                TEST_MESSAGE, null, null, null);
        
        // WHEN
        ChatController.ChatResponse response = chatController.chat(request);
        
        // THEN
        assertThat(response.response()).isEqualTo(TEST_RESPONSE);
        assertThat(response.toolCalls()).isEmpty();
        assertThat(response.diagrams()).isEmpty();
        assertThat(response.conversationId()).isEqualTo(TEST_CONVERSATION_ID);
    }
    
    @Test
    @DisplayName("chat - возвращает ответ с tool calls и диаграммами")
    void chat_returnsResponseWithToolCallsAndDiagrams() {
        // GIVEN
        List<ToolCallingChatService.ToolCallInfo> toolCalls = List.of(
                new ToolCallingChatService.ToolCallInfo(
                        "call-1", "getWorkflowSessions", "{}", "session data")
        );
        List<ToolCallingChatService.DiagramInfo> diagrams = List.of(
                new ToolCallingChatService.DiagramInfo(
                        "Domain Model", "@startuml\nclass Order\n@enduml", "plantuml")
        );
        
        ToolCallingChatService.ChatResult chatResult = new ToolCallingChatService.ChatResult(
                TEST_RESPONSE, toolCalls, diagrams, TEST_CONVERSATION_ID
        );
        when(toolCallingChatService.processMessage(any(), any(), any(), any()))
                .thenReturn(chatResult);
        
        ChatController.ChatRequest request = new ChatController.ChatRequest(
                TEST_MESSAGE, null, null, null);
        
        // WHEN
        ChatController.ChatResponse response = chatController.chat(request);
        
        // THEN
        assertThat(response.response()).isEqualTo(TEST_RESPONSE);
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().get(0).name()).isEqualTo("getWorkflowSessions");
        assertThat(response.diagrams()).hasSize(1);
        assertThat(response.diagrams().get(0).title()).isEqualTo("Domain Model");
    }
    
    @Test
    @DisplayName("chat - передает workflowSessionId в сервис")
    void chat_passesWorkflowSessionId() {
        // GIVEN
        String sessionId = "workflow-session-123";
        ToolCallingChatService.ChatResult chatResult = new ToolCallingChatService.ChatResult(
                TEST_RESPONSE, List.of(), List.of(), TEST_CONVERSATION_ID);
        when(toolCallingChatService.processMessage(any(), any(), eq(sessionId), any()))
                .thenReturn(chatResult);
        
        ChatController.ChatRequest request = new ChatController.ChatRequest(
                TEST_MESSAGE, null, sessionId, null);
        
        // WHEN
        chatController.chat(request);
        
        // THEN
        verify(toolCallingChatService).processMessage(eq(TEST_MESSAGE), any(), eq(sessionId), any());
    }
    
    @Test
    @DisplayName("chat - обрабатывает ошибку сервиса")
    void chat_handlesServiceError() {
        // GIVEN
        when(toolCallingChatService.processMessage(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Test error"));
        
        ChatController.ChatRequest request = new ChatController.ChatRequest(
                TEST_MESSAGE, null, null, null);
        
        // WHEN
        ChatController.ChatResponse response = chatController.chat(request);
        
        // THEN
        assertThat(response.response()).contains("ошибка");
        assertThat(response.response()).contains("Test error");
    }
    
    // ====================================================================
    // Тесты для GET /api/chat/history
    // ====================================================================
    
    @Test
    @DisplayName("getHistory - возвращает историю по conversationId")
    void getHistory_returnsHistoryByConversationId() {
        // GIVEN
        ChatMessage userMsg = ChatMessage.builder()
                .id(UUID.randomUUID())
                .role(ChatMessage.MessageRole.USER)
                .content(TEST_MESSAGE)
                .conversationId(TEST_CONVERSATION_ID)
                .createdAt(Instant.now())
                .build();
        ChatMessage assistantMsg = ChatMessage.builder()
                .id(UUID.randomUUID())
                .role(ChatMessage.MessageRole.ASSISTANT)
                .content(TEST_RESPONSE)
                .conversationId(TEST_CONVERSATION_ID)
                .createdAt(Instant.now())
                .build();
        
        when(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(TEST_CONVERSATION_ID))
                .thenReturn(List.of(userMsg, assistantMsg));
        
        // WHEN
        ChatController.ChatHistoryResponse response = chatController.getHistory(TEST_CONVERSATION_ID, 20);
        
        // THEN
        assertThat(response.messages()).hasSize(2);
        assertThat(response.messages().get(0).role()).isEqualTo("user");
        assertThat(response.messages().get(0).content()).isEqualTo(TEST_MESSAGE);
        assertThat(response.messages().get(1).role()).isEqualTo("assistant");
    }
    
    @Test
    @DisplayName("getHistory - возвращает последние сообщения без conversationId")
    void getHistory_returnsRecentMessagesWithoutConversationId() {
        // GIVEN
        ChatMessage msg1 = ChatMessage.builder()
                .id(UUID.randomUUID())
                .role(ChatMessage.MessageRole.USER)
                .content("Message 1")
                .createdAt(Instant.now().minusSeconds(100))
                .build();
        ChatMessage msg2 = ChatMessage.builder()
                .id(UUID.randomUUID())
                .role(ChatMessage.MessageRole.ASSISTANT)
                .content("Response 1")
                .createdAt(Instant.now())
                .build();
        
        when(chatMessageRepository.findTopNByOrderByCreatedAtDesc(20))
                .thenReturn(List.of(msg2, msg1)); // reversed order from DB
        
        // WHEN
        ChatController.ChatHistoryResponse response = chatController.getHistory(null, 20);
        
        // THEN
        assertThat(response.messages()).hasSize(2);
        // Should be in chronological order after sorting
        assertThat(response.messages().get(0).content()).isEqualTo("Message 1");
    }
    
    @Test
    @DisplayName("getHistory - включает информацию о tool calls")
    void getHistory_includesToolCallsInfo() {
        // GIVEN
        String toolCallsJson = "[{\"id\":\"call-1\",\"name\":\"getWorkflowSessions\",\"arguments\":\"{}\"}]";
        ChatMessage assistantMsg = ChatMessage.builder()
                .id(UUID.randomUUID())
                .role(ChatMessage.MessageRole.ASSISTANT)
                .content(TEST_RESPONSE)
                .toolCallsJson(toolCallsJson)
                .conversationId(TEST_CONVERSATION_ID)
                .createdAt(Instant.now())
                .build();
        
        when(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(TEST_CONVERSATION_ID))
                .thenReturn(List.of(assistantMsg));
        
        // WHEN
        ChatController.ChatHistoryResponse response = chatController.getHistory(TEST_CONVERSATION_ID, 20);
        
        // THEN
        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().get(0).toolCalls()).isNotNull();
        assertThat(response.messages().get(0).toolCalls()).hasSize(1);
        assertThat(response.messages().get(0).toolCalls().get(0).name()).isEqualTo("getWorkflowSessions");
    }
    
    // ====================================================================
    // Тесты для GET /api/chat/conversations
    // ====================================================================
    
    @Test
    @DisplayName("getConversations - возвращает список разговоров")
    void getConversations_returnsList() {
        // GIVEN
        when(chatMessageRepository.findDistinctConversationIds(10))
                .thenReturn(List.of(TEST_CONVERSATION_ID));
        
        ChatMessage msg = ChatMessage.builder()
                .id(UUID.randomUUID())
                .role(ChatMessage.MessageRole.USER)
                .content(TEST_MESSAGE)
                .conversationId(TEST_CONVERSATION_ID)
                .createdAt(Instant.now())
                .build();
        when(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(TEST_CONVERSATION_ID))
                .thenReturn(List.of(msg));
        
        // WHEN
        ChatController.ConversationsResponse response = chatController.getConversations(10);
        
        // THEN
        assertThat(response.conversations()).hasSize(1);
        assertThat(response.conversations().get(0).conversationId()).isEqualTo(TEST_CONVERSATION_ID);
        assertThat(response.conversations().get(0).messageCount()).isEqualTo(1);
    }
}
