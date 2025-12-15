package com.example.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Утилита для фильтрации PlantUML моделей, чтобы оставить только релевантные части
 * для конкретного Use Case. Это помогает уменьшить размер контекста для LLM.
 */
@Slf4j
@Component
public class PlantUmlFilter {
    
    /**
     * Фильтрует Use Case модель, оставляя только указанный Use Case и связанные с ним элементы.
     * 
     * @param useCaseModel Полная Use Case модель
     * @param useCaseAlias Алиас Use Case для фильтрации (например, "loginToPersonalCabinet")
     * @param useCaseName Название Use Case (для дополнительной проверки)
     * @return Отфильтрованная модель
     */
    public String filterUseCaseModel(String useCaseModel, String useCaseAlias, String useCaseName) {
        if (useCaseModel == null || useCaseModel.isBlank()) {
            return useCaseModel;
        }
        
        if (useCaseAlias == null || useCaseAlias.isBlank()) {
            log.warn("Use Case alias is empty, returning full model");
            return useCaseModel;
        }
        
        try {
            // Ищем определение Use Case по алиасу
            Pattern useCasePattern = Pattern.compile(
                String.format("usecase\\s+\"([^\"]+)\"\\s+as\\s+%s", Pattern.quote(useCaseAlias)),
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
            );
            
            Matcher useCaseMatcher = useCasePattern.matcher(useCaseModel);
            if (!useCaseMatcher.find()) {
                log.warn("Use Case with alias '{}' not found, returning full model", useCaseAlias);
                return useCaseModel;
            }
            
            // Строим отфильтрованную модель
            StringBuilder filtered = new StringBuilder();
            String[] lines = useCaseModel.split("\n");
            
            // Добавляем заголовок
            boolean foundUseCase = false;
            List<String> relatedActors = new ArrayList<>();
            List<String> relatedUseCases = new ArrayList<>();
            
            for (String line : lines) {
                String trimmed = line.trim();
                
                // Сохраняем начало и конец
                if (trimmed.startsWith("@startuml") || trimmed.startsWith("@start")) {
                    filtered.append(line).append("\n");
                    continue;
                }
                
                if (trimmed.startsWith("@enduml") || trimmed.startsWith("@end")) {
                    // Добавляем найденные связи перед концом
                    for (String actor : relatedActors) {
                        filtered.append(actor).append("\n");
                    }
                    for (String uc : relatedUseCases) {
                        filtered.append(uc).append("\n");
                    }
                    filtered.append(line).append("\n");
                    break;
                }
                
                // Ищем наш Use Case
                if (trimmed.contains("as " + useCaseAlias) || 
                    (trimmed.contains("usecase") && trimmed.contains(useCaseName))) {
                    filtered.append(line).append("\n");
                    foundUseCase = true;
                    continue;
                }
                
                // Ищем связи с нашим Use Case (actor -> usecase, usecase -> usecase)
                if (foundUseCase) {
                    // Связи акторов с Use Case
                    Pattern actorLinkPattern = Pattern.compile(
                        String.format("(\\w+)\\s*[-.>]+\\s*%s", Pattern.quote(useCaseAlias)),
                        Pattern.CASE_INSENSITIVE
                    );
                    Matcher actorLinkMatcher = actorLinkPattern.matcher(trimmed);
                    if (actorLinkMatcher.find()) {
                        String actor = actorLinkMatcher.group(1);
                        // Добавляем определение актора, если его еще нет
                        if (!relatedActors.contains(actor) && !filtered.toString().contains(actor + " as")) {
                            // Ищем определение актора
                            Pattern actorDefPattern = Pattern.compile(
                                String.format("(actor|participant)\\s+\"([^\"]+)\"\\s+as\\s+%s", Pattern.quote(actor)),
                                Pattern.CASE_INSENSITIVE
                            );
                            for (String prevLine : lines) {
                                Matcher actorDefMatcher = actorDefPattern.matcher(prevLine);
                                if (actorDefMatcher.find()) {
                                    relatedActors.add(prevLine);
                                    break;
                                }
                            }
                        }
                        filtered.append(line).append("\n");
                        continue;
                    }
                    
                    // Связи Use Case с другими Use Case
                    Pattern ucLinkPattern = Pattern.compile(
                        String.format("%s\\s*[-.>]+\\s*(\\w+)", Pattern.quote(useCaseAlias)),
                        Pattern.CASE_INSENSITIVE
                    );
                    Matcher ucLinkMatcher = ucLinkPattern.matcher(trimmed);
                    if (ucLinkMatcher.find()) {
                        filtered.append(line).append("\n");
                        continue;
                    }
                }
                
                // Сохраняем общие элементы (легенда, заметки и т.д.)
                if (trimmed.startsWith("legend") || trimmed.startsWith("note") || 
                    trimmed.startsWith("skinparam") || trimmed.startsWith("!") ||
                    trimmed.isEmpty()) {
                    filtered.append(line).append("\n");
                }
            }
            
            String result = filtered.toString();
            log.info("Filtered Use Case model: {} -> {} chars (alias: {})", 
                    useCaseModel.length(), result.length(), useCaseAlias);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error filtering Use Case model, returning full model", e);
            return useCaseModel;
        }
    }
    
    /**
     * Фильтрует MVC модель, оставляя только части, связанные с указанным Use Case.
     * 
     * @param mvcModel Полная MVC модель
     * @param useCaseAlias Алиас Use Case для фильтрации
     * @return Отфильтрованная модель
     */
    public String filterMvcModel(String mvcModel, String useCaseAlias) {
        if (mvcModel == null || mvcModel.isBlank()) {
            return mvcModel;
        }
        
        if (useCaseAlias == null || useCaseAlias.isBlank()) {
            log.warn("Use Case alias is empty, returning full MVC model");
            return mvcModel;
        }
        
        try {
            // Ищем все упоминания Use Case в MVC модели
            Pattern ucPattern = Pattern.compile(
                String.format("\\b%s\\b", Pattern.quote(useCaseAlias)),
                Pattern.CASE_INSENSITIVE
            );
            
            StringBuilder filtered = new StringBuilder();
            String[] lines = mvcModel.split("\n");
            boolean inRelevantBlock = false;
            int braceDepth = 0;
            
            for (String line : lines) {
                String trimmed = line.trim();
                
                // Сохраняем начало и конец
                if (trimmed.startsWith("@startuml") || trimmed.startsWith("@start")) {
                    filtered.append(line).append("\n");
                    continue;
                }
                
                if (trimmed.startsWith("@enduml") || trimmed.startsWith("@end")) {
                    filtered.append(line).append("\n");
                    break;
                }
                
                // Проверяем, есть ли упоминание нашего Use Case
                Matcher ucMatcher = ucPattern.matcher(line);
                if (ucMatcher.find()) {
                    inRelevantBlock = true;
                    filtered.append(line).append("\n");
                    continue;
                }
                
                // Если мы в релевантном блоке, сохраняем его полностью
                if (inRelevantBlock) {
                    // Подсчитываем вложенность скобок для определения конца блока
                    braceDepth += countChar(line, '{') - countChar(line, '}');
                    filtered.append(line).append("\n");
                    
                    if (braceDepth <= 0 && trimmed.endsWith("}")) {
                        inRelevantBlock = false;
                        braceDepth = 0;
                    }
                } else {
                    // Сохраняем общие элементы
                    if (trimmed.startsWith("legend") || trimmed.startsWith("note") || 
                        trimmed.startsWith("skinparam") || trimmed.startsWith("!") ||
                        trimmed.startsWith("package") || trimmed.startsWith("namespace") ||
                        trimmed.isEmpty()) {
                        filtered.append(line).append("\n");
                    }
                }
            }
            
            String result = filtered.toString();
            log.info("Filtered MVC model: {} -> {} chars (alias: {})", 
                    mvcModel.length(), result.length(), useCaseAlias);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error filtering MVC model, returning full model", e);
            return mvcModel;
        }
    }
    
    /**
     * Сокращает narrative, оставляя только релевантные части, связанные с Use Case.
     * 
     * @param narrative Полный narrative
     * @param useCaseName Название Use Case
     * @param maxLength Максимальная длина (по умолчанию 2000 символов)
     * @return Сокращенный narrative
     */
    public String shortenNarrative(String narrative, String useCaseName, int maxLength) {
        if (narrative == null || narrative.isBlank()) {
            return narrative;
        }
        
        if (narrative.length() <= maxLength) {
            return narrative;
        }
        
        // Ищем упоминания Use Case в narrative
        if (useCaseName != null && !useCaseName.isBlank()) {
            int useCaseIndex = narrative.toLowerCase().indexOf(useCaseName.toLowerCase());
            if (useCaseIndex >= 0) {
                // Берем контекст вокруг упоминания Use Case
                int start = Math.max(0, useCaseIndex - maxLength / 2);
                int end = Math.min(narrative.length(), start + maxLength);
                String relevantPart = narrative.substring(start, end);
                
                if (start > 0) {
                    relevantPart = "..." + relevantPart;
                }
                if (end < narrative.length()) {
                    relevantPart = relevantPart + "...";
                }
                
                log.info("Shortened narrative: {} -> {} chars (found Use Case at position {})", 
                        narrative.length(), relevantPart.length(), useCaseIndex);
                
                return relevantPart;
            }
        }
        
        // Если Use Case не найден, просто обрезаем до maxLength
        String shortened = narrative.substring(0, maxLength) + "...";
        log.info("Shortened narrative: {} -> {} chars (truncated)", 
                narrative.length(), shortened.length());
        
        return shortened;
    }
    
    /**
     * Обрезает PlantUML модель до максимального размера, сохраняя начало и конец.
     * Используется как fallback, если фильтрация не помогла.
     * 
     * @param model PlantUML модель
     * @param maxLength Максимальная длина
     * @return Обрезанная модель
     */
    public String truncatePlantUml(String model, int maxLength) {
        if (model == null || model.isBlank() || model.length() <= maxLength) {
            return model;
        }
        
        // Ищем начало и конец
        int startIndex = model.indexOf("@start");
        int endIndex = model.lastIndexOf("@end");
        
        if (startIndex < 0 || endIndex < 0 || endIndex <= startIndex) {
            // Если не нашли маркеры, просто обрезаем
            return model.substring(0, maxLength) + "\n... (truncated) ...\n";
        }
        
        String header = model.substring(0, startIndex);
        String startTag = model.substring(startIndex, model.indexOf("\n", startIndex) + 1);
        String body = model.substring(model.indexOf("\n", startIndex) + 1, endIndex);
        String endTag = model.substring(endIndex);
        
        // Если тело слишком большое, обрезаем его
        if (body.length() > maxLength - header.length() - startTag.length() - endTag.length() - 100) {
            int bodyMaxLength = maxLength - header.length() - startTag.length() - endTag.length() - 100;
            body = body.substring(0, bodyMaxLength) + "\n... (truncated) ...\n";
        }
        
        String result = header + startTag + body + endTag;
        log.info("Truncated PlantUML model: {} -> {} chars", model.length(), result.length());
        
        return result;
    }
    
    private int countChar(String str, char c) {
        int count = 0;
        for (char ch : str.toCharArray()) {
            if (ch == c) count++;
        }
        return count;
    }
}

