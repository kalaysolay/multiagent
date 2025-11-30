package com.example.workflow;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class UserReviewWorker implements Worker {
    
    @Override
    public String name() {
        return "userReview";
    }
    
    @Override
    public void execute(Context ctx, Map<String, Object> args) throws Exception {
        // Собираем данные для пользовательского ревью
        Map<String, Object> reviewData = new HashMap<>();
        
        // Добавляем текущие артефакты
        reviewData.put("narrative", ctx.narrativeEffective());
        if (ctx.state.containsKey("plantuml")) {
            reviewData.put("domainModel", ctx.state.get("plantuml"));
        }
        if (ctx.state.containsKey("issues")) {
            reviewData.put("issues", ctx.state.get("issues"));
        }
        if (ctx.state.containsKey("narrativeIssues")) {
            reviewData.put("narrativeIssues", ctx.state.get("narrativeIssues"));
        }
        
        ctx.log("userReview: paused for user review");
        
        // Сохраняем сессию с статусом PAUSED_FOR_REVIEW
        // План и currentStepIndex будут сохранены в OrchestratorService
        
        // Бросаем исключение для паузы workflow
        String reviewDataJson = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(reviewData);
        
        throw new PauseForUserReviewException(ctx.requestId, reviewDataJson, 
                "Workflow paused for user review. Please review and provide feedback.");
    }
}

