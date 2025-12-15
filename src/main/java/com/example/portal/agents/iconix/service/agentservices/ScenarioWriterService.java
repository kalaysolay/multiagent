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
public class ScenarioWriterService {

    private final ChatClient chat;

    @Value("classpath:prompts/scenario_writer.st")
    private Resource scenarioPromptTemplate;

    @Autowired
    public ScenarioWriterService(ChatClient.Builder builder) {
        this.chat = builder.build();
    }

    public String generateScenario(String narrative, String domainModelPlantUml, 
                                   String useCaseModelPlantUml, String mvcModelPlantUml, 
                                   String ragContext) {
        String promptTemplate = readResource(scenarioPromptTemplate);
        
        // Экранируем все % в шаблоне, кроме %s (спецификаторов формата)
        // Сначала заменяем все %s на временный маркер, затем экранируем все %, затем восстанавливаем %s
        String escapedTemplate = promptTemplate
                .replace("%s", "___PLACEHOLDER_S___")  // Временный маркер для %s
                .replace("%", "%%")                    // Экранируем все %
                .replace("___PLACEHOLDER_S___", "%s"); // Восстанавливаем %s
        
        // Экранируем все параметры перед вставкой
        String safeNarrative = PromptUtils.fullEscape(narrative);
        String safeDomainModel = PromptUtils.fullEscape(domainModelPlantUml);
        String safeUseCaseModel = PromptUtils.fullEscape(useCaseModelPlantUml);
        String safeMvcModel = PromptUtils.fullEscape(mvcModelPlantUml);
        String safeRagContext = PromptUtils.fullEscape(ragContext.isBlank() ? "нет" : ragContext);
        
        String userPrompt = String.format(escapedTemplate, 
                safeNarrative, 
                safeDomainModel, 
                safeUseCaseModel, 
                safeMvcModel, 
                safeRagContext);

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
            throw new RuntimeException("Failed to read prompt template", e);
        }
    }
}

