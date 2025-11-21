package com.example.workflow;

public final class PromptUtils {

    private PromptUtils() {}

    public static String stEscape(String s) {
        if (s == null) return null;
        return s.replace("{", "{{").replace("}", "}}");
    }
}

