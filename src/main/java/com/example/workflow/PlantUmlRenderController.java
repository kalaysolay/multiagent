package com.example.workflow;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/render")
@RequiredArgsConstructor
public class PlantUmlRenderController {
    
    private final PlantUmlRenderService renderService;
    
    @PostMapping("/png")
    public ResponseEntity<?> renderToPng(@RequestBody Map<String, String> request) {
        String plantUmlCode = request.get("plantUml");
        
        if (plantUmlCode == null || plantUmlCode.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "PlantUML код не может быть пустым"));
        }
        
        try {
            String base64Image = renderService.renderToPngBase64(plantUmlCode);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("image", "data:image/png;base64," + base64Image));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Ошибка при рендеринге: " + e.getMessage()));
        }
    }
    
    @PostMapping("/svg")
    public ResponseEntity<?> renderToSvg(@RequestBody Map<String, String> request) {
        String plantUmlCode = request.get("plantUml");
        
        if (plantUmlCode == null || plantUmlCode.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "PlantUML код не может быть пустым"));
        }
        
        try {
            String svg = renderService.renderToSvg(plantUmlCode);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("svg", svg));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Ошибка при рендеринге: " + e.getMessage()));
        }
    }
    
    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestBody Map<String, String> request) {
        String plantUmlCode = request.get("plantUml");
        
        if (plantUmlCode == null || plantUmlCode.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("valid", false, "error", "PlantUML код не может быть пустым"));
        }
        
        try {
            boolean isValid = renderService.validatePlantUml(plantUmlCode);
            if (isValid) {
                return ResponseEntity.ok()
                        .body(Map.of("valid", true));
            } else {
                return ResponseEntity.ok()
                        .body(Map.of("valid", false, "error", "Обнаружены ошибки в синтаксисе PlantUML"));
            }
        } catch (Exception e) {
            return ResponseEntity.ok()
                    .body(Map.of("valid", false, "error", "Ошибка валидации: " + e.getMessage()));
        }
    }
}

