package com.example.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * HTTP контроллер для публичного доступа к ICONIX агентам
 * Предоставляет REST API для независимого вызова агентов
 * 
 * Примечание: Это REST API для вызова агентов, а не полноценный MCP протокол.
 * Настоящий MCP работает через stdio (JSON-RPC 2.0), но этот API предоставляет
 * аналогичную функциональность через HTTP для удобства интеграции.
 * 
 * Для настоящего MCP протокола потребуется отдельная реализация через stdio transport.
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@Slf4j
public class MCPController {
    
    private final IconixMCPServer mcpServer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Получить список всех доступных агентов
     * 
     * GET /api/agents
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listTools() {
        try {
            Map<String, Object> result = mcpServer.listTools();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error listing MCP tools", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Вызвать агента
     * 
     * POST /api/agents/{agentName}
     * Body: {
     *   "narrative": "...",
     *   "domainModel": "@startuml\n..."
     * }
     */
    @PostMapping("/{agentName}")
    public ResponseEntity<Map<String, Object>> callAgent(
            @PathVariable String agentName,
            @RequestBody Map<String, Object> arguments) {
        try {
            if (agentName == null || agentName.isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "agent name is required"));
            }
            
            if (arguments == null) {
                arguments = Map.of();
            }
            
            Map<String, Object> result = mcpServer.callTool(agentName, arguments);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error calling MCP tool", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "error", e.getMessage(),
                    "isError", true
                ));
        }
    }
    
    /**
     * JSON-RPC 2.0 endpoint (для совместимости, если нужен JSON-RPC формат)
     * 
     * POST /api/agents/jsonrpc
     */
    @PostMapping("/jsonrpc")
    public ResponseEntity<Map<String, Object>> handleJsonRpc(@RequestBody JsonNode request) {
        try {
            String method = request.get("method").asText();
            JsonNode id = request.get("id");
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("jsonrpc", "2.0");
            if (id != null) {
                response.put("id", id);
            }
            
            switch (method) {
                case "tools/list":
                    response.put("result", mcpServer.listTools());
                    break;
                    
                case "tools/call":
                    JsonNode params = request.get("params");
                    String toolName = params.get("name").asText();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> arguments = objectMapper.convertValue(
                        params.get("arguments"),
                        Map.class
                    );
                    response.put("result", mcpServer.callTool(toolName, arguments));
                    break;
                    
                default:
                    response.put("error", Map.of(
                        "code", -32601,
                        "message", "Method not found: " + method
                    ));
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error handling JSON-RPC request", e);
            return ResponseEntity.ok(Map.of(
                "jsonrpc", "2.0",
                "id", request.get("id"),
                "error", Map.of(
                    "code", -32603,
                    "message", "Internal error: " + e.getMessage()
                )
            ));
        }
    }
}

