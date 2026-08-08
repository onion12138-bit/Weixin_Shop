package com.diybracelet.mall.recommendation.api;

import com.diybracelet.mall.recommendation.api.dto.AiApiKeyView;
import com.diybracelet.mall.recommendation.api.dto.AiConfigurationView;
import com.diybracelet.mall.recommendation.api.dto.SaveAiConfigurationRequest;
import com.diybracelet.mall.recommendation.application.AiConfigurationService;
import com.diybracelet.mall.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only HTTP contract for the singleton DeepSeek configuration. */
@RestController
@RequestMapping("/api/admin/ai/config")
public class AdminAiConfigurationController {

    private final AiConfigurationService configurationService;

    public AdminAiConfigurationController(AiConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping
    public ApiResponse<AiConfigurationView> getConfiguration() {
        return success(configurationService.getConfiguration());
    }

    @PutMapping
    public ApiResponse<AiConfigurationView> saveConfiguration(
            @Valid @RequestBody SaveAiConfigurationRequest request
    ) {
        return success(configurationService.save(request));
    }

    @PostMapping("/test")
    public ApiResponse<Void> testConnection() {
        configurationService.testConnection();
        return success(null);
    }

    @GetMapping("/api-key")
    public ResponseEntity<ApiResponse<AiApiKeyView>> revealApiKey() {
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(success(configurationService.revealApiKey()));
    }

    @DeleteMapping("/api-key")
    public ApiResponse<AiConfigurationView> clearApiKey(
            @RequestParam @Min(0) long version
    ) {
        return success(configurationService.clearApiKey(version));
    }

    private <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, UUID.randomUUID().toString());
    }
}
