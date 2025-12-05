package com.example.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.FileFormat;

@Slf4j
@Service
public class PlantUmlRenderService {
    
    private static final String RENDER_LOG_FILE = "render.log";
    
    /**
     * Рендерит PlantUML диаграмму в PNG формат (base64)
     */
    public String renderToPngBase64(String plantUmlCode) {
        try {
            SourceStringReader reader = new SourceStringReader(plantUmlCode);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            
            // generateImage возвращает String (описание диаграммы), изображение пишется в OutputStream
            String description = reader.generateImage(os);
            byte[] imageBytes = os.toByteArray();
            
            if (imageBytes.length == 0) {
                String error = "Рендеринг вернул пустой результат";
                logError(error);
                throw new RuntimeException(error);
            }
            
            String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
            logInfo("Диаграмма успешно отрендерена. Размер: " + imageBytes.length + " байт. Описание: " + description);
            
            return base64;
            
        } catch (Exception e) {
            String error = "Ошибка при рендеринге PlantUML: " + e.getMessage();
            logError(error, e);
            throw new RuntimeException(error, e);
        }
    }
    
    /**
     * Рендерит PlantUML диаграмму в SVG формат (строка)
     */
    public String renderToSvg(String plantUmlCode) {
        try {
            SourceStringReader reader = new SourceStringReader(plantUmlCode);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            
            // Для SVG используем FileFormatOption
            FileFormatOption option = new FileFormatOption(FileFormat.SVG);
            String description = reader.generateImage(os, option);
            String svg = os.toString(StandardCharsets.UTF_8);
            
            if (svg == null || svg.isEmpty()) {
                String error = "Рендеринг SVG вернул пустой результат";
                logError(error);
                throw new RuntimeException(error);
            }
            
            logInfo("SVG диаграмма успешно отрендерена. Размер: " + svg.length() + " символов. Описание: " + description);
            
            return svg;
            
        } catch (Exception e) {
            String error = "Ошибка при рендеринге PlantUML в SVG: " + e.getMessage();
            logError(error, e);
            throw new RuntimeException(error, e);
        }
    }
    
    /**
     * Валидирует PlantUML код без рендеринга
     */
    public boolean validatePlantUml(String plantUmlCode) {
        try {
            SourceStringReader reader = new SourceStringReader(plantUmlCode);
            // Пытаемся сгенерировать изображение в пустой поток для валидации
            String description = reader.generateImage(new java.io.ByteArrayOutputStream());
            return description != null && !description.isEmpty();
        } catch (Exception e) {
            logError("Валидация PlantUML не прошла: " + e.getMessage(), e);
            return false;
        }
    }
    
    private void logInfo(String message) {
        log.info("[PlantUML Render] {}", message);
        writeToLogFile("INFO", message, null);
    }
    
    private void logError(String message) {
        log.error("[PlantUML Render] {}", message);
        writeToLogFile("ERROR", message, null);
    }
    
    private void logError(String message, Throwable throwable) {
        log.error("[PlantUML Render] {}", message, throwable);
        writeToLogFile("ERROR", message, throwable);
    }
    
    private void writeToLogFile(String level, String message, Throwable throwable) {
        try {
            Path logPath = Paths.get(RENDER_LOG_FILE);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            String logEntry = String.format("[%s] [%s] %s%n", timestamp, level, message);
            
            if (throwable != null) {
                java.io.StringWriter sw = new java.io.StringWriter();
                java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                throwable.printStackTrace(pw);
                logEntry += sw.toString() + "\n";
            }
            
            Files.write(logPath, logEntry.getBytes(StandardCharsets.UTF_8), 
                       java.nio.file.StandardOpenOption.CREATE, 
                       java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Не удалось записать в файл логов: {}", RENDER_LOG_FILE, e);
        }
    }
}

