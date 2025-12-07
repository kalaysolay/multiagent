package com.example.portal.agents.iconix.service.agentservices;

import com.example.portal.shared.utils.PromptUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Slf4j
@Service
public class UseCaseModellerService {

    private final ChatClient chat;

    @Value("classpath:prompts/usecase_modeller.st")
    private Resource useCasePromptTemplate;

    @Autowired
    public UseCaseModellerService(ChatClient.Builder builder) {
        this.chat = builder.build();
    }

    public String generateUseCasePlantUml(String narrative, String domainModelPlantUml, String ragContext) {
        String promptTemplate = readResource(useCasePromptTemplate);
        
        // Экранируем все параметры перед вставкой
        String safeNarrative = PromptUtils.fullEscape(narrative);
        String safeDomainModel = PromptUtils.fullEscape(domainModelPlantUml);
        String safeRagContext = PromptUtils.fullEscape(ragContext.isBlank() ? "нет" : ragContext);
        
        String userPrompt = String.format(promptTemplate, safeNarrative, safeDomainModel, safeRagContext);

        return chat.prompt()
                .user(PromptUtils.stEscape(userPrompt)) // Дополнительное экранирование для ST4
                .options(OpenAiChatOptions.builder()
                        .temperature(1.0)
                        .build())
                .call()
                .content();
    }

    private static String readResource(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read usecase prompt template", e);
        }
    }
}

