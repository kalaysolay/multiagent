package com.example.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * Реализация минимального MCP stdio-сервера (JSON-RPC 2.0).
 * Запускается только если в аргументах приложения присутствует флаг --mcp-stdio.
 *
 * Поддерживаемые методы:
 * - initialize          — базовая инициализация (возвращает capabilities и версии)
 * - tools/list          — список доступных агентов
 * - tools/call          — вызов конкретного агента с аргументами
 *
 * Протокол: одна JSON-RPC строка на stdin -> одна JSON-RPC строка на stdout.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpStdioRunner implements CommandLineRunner {

    private final IconixMCPServer mcpServer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void run(String... args) throws Exception {
        boolean enabled = false;
        for (String arg : args) {
            if ("--mcp-stdio".equalsIgnoreCase(arg)) {
                enabled = true;
                break;
            }
        }
        if (!enabled) {
            return; // обычный режим работы приложения
        }

        log.info("Starting MCP stdio server (JSON-RPC 2.0)...");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                processRequestLine(line);
            }
        } catch (IOException e) {
            log.error("MCP stdio server stopped with IO error", e);
        }
        log.info("MCP stdio server stopped (stdin closed).");
    }

    private void processRequestLine(String line) {
        try {
            JsonNode request = objectMapper.readTree(line);
            String method = request.path("method").asText(null);
            JsonNode idNode = request.get("id");

            if (method == null) {
                writeError(idNode, -32600, "Invalid Request: no method");
                return;
            }

            Map<String, Object> responseBody;
            switch (method) {
                case "initialize" -> responseBody = handleInitialize();
                case "tools/list" -> responseBody = mcpServer.listTools();
                case "tools/call" -> responseBody = handleToolsCall(request.path("params"));
                default -> {
                    writeError(idNode, -32601, "Method not found: " + method);
                    return;
                }
            }

            writeResponse(idNode, responseBody);
        } catch (Exception e) {
            log.error("Failed to process MCP request line: {}", line, e);
        }
    }

    private Map<String, Object> handleInitialize() {
        return Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(
                        "tools", true
                )
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolsCall(JsonNode params) {
        if (params == null || params.isMissingNode()) {
            return Map.of("isError", true,
                    "content", java.util.List.of(Map.of("type", "text", "text", "Missing params")));
        }
        String toolName = params.path("name").asText(null);
        if (toolName == null || toolName.isBlank()) {
            return Map.of("isError", true,
                    "content", java.util.List.of(Map.of("type", "text", "text", "Tool name is required")));
        }
        Map<String, Object> arguments = objectMapper.convertValue(params.path("arguments"), Map.class);
        if (arguments == null) arguments = Map.of();
        return mcpServer.callTool(toolName, arguments);
    }

    private void writeResponse(JsonNode idNode, Map<String, Object> result) throws IOException {
        var resp = new java.util.LinkedHashMap<String, Object>();
        resp.put("jsonrpc", "2.0");
        if (idNode != null) resp.put("id", idNode);
        resp.put("result", result);
        System.out.println(objectMapper.writeValueAsString(resp));
        System.out.flush();
    }

    private void writeError(JsonNode idNode, int code, String message) throws IOException {
        var resp = new java.util.LinkedHashMap<String, Object>();
        resp.put("jsonrpc", "2.0");
        if (idNode != null) resp.put("id", idNode);
        resp.put("error", Map.of(
                "code", code,
                "message", message
        ));
        System.out.println(objectMapper.writeValueAsString(resp));
        System.out.flush();
    }
}

