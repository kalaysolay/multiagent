package com.example.workflow;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    
    private final ChatClient chatClient;
    
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String response = chatClient.prompt()
            .user(request.message())
            .call()
            .content();
        return new ChatResponse(response);
    }
    
    public record ChatRequest(String message, List<Map<String, String>> history) {}
    public record ChatResponse(String response) {}
}

