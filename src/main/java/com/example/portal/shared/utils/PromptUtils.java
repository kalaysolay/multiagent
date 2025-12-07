package com.example.portal.shared.utils;

public final class PromptUtils {

    private PromptUtils() {}

    /**
     * Экранирует фигурные скобки для StringTemplate (ST4).
     */
    public static String stEscape(String s) {
        if (s == null) return null;
        return s.replace("{", "{{").replace("}", "}}");
    }

    /**
     * Экранирует символы % для String.format, чтобы они не интерпретировались как формат-спецификаторы.
     */
    public static String formatEscape(String s) {
        if (s == null) return null;
        return s.replace("%", "%%");
    }

    /**
     * Экранирует и для StringTemplate, и для String.format.
     * Используй для параметров, которые вставляются в промпт через String.format.
     */
    public static String fullEscape(String s) {
        if (s == null) return null;
        return formatEscape(stEscape(s));
    }
}

