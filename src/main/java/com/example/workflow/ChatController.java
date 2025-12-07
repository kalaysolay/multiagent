package com.example.workflow;

import com.example.portal.shared.service.LlmService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    
    private final LlmService llmService;
    
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String response = llmService.generate(request.message());
        return new ChatResponse(response);
    }
    
    public record ChatRequest(String message, List<Map<String, String>> history) {}
    public record ChatResponse(String response) {}
}

