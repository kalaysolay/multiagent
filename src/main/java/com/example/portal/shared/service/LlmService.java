package com.example.portal.shared.service;

/**
 * Интерфейс для работы с LLM (Large Language Models).
 * Абстракция для работы с различными провайдерами LLM (OpenAI, корпоративные и т.д.).
 */
public interface LlmService {
    
    /**
     * Выполнить запрос к LLM с указанным промптом.
     * 
     * @param prompt текстовый промпт для LLM
     * @return ответ от LLM
     */
    String generate(String prompt);
    
    /**
     * Выполнить запрос к LLM с промптом и опциями.
     * 
     * @param prompt текстовый промпт для LLM
     * @param temperature температура генерации (0.0 - 2.0)
     * @return ответ от LLM
     */
    String generate(String prompt, Double temperature);
}

