package vn.vinfast.vfqc.api.target.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import vn.vinfast.vfqc.api.target.enums.TargetBodyType;

public record CreateChatbotTargetRequest(
    @Schema(example = "VF chatbot") @NotBlank(message = "Target name is required")
        @Size(max = 160, message = "Target name must be at most 160 characters")
        String name,
    @Schema(example = "Main chatbot API")
        @Size(max = 1000, message = "Target description must be at most 1000 characters")
        String description,
    @Size(max = 20000, message = "Raw cURL must be at most 20000 characters") String rawCurl,
    String method,
    @Size(max = 2000, message = "Target URL must be at most 2000 characters") String url,
    Map<String, String> headers,
    Map<String, String> queryParams,
    TargetBodyType bodyType,
    @Size(max = 20000, message = "Target body must be at most 20000 characters") String body,
    List<@Valid SecretBindingDto> secretBindings,
    List<@Valid DatasetMappingDto> datasetMappings,
    @Valid ResponseExtractionConfigDto responseExtractionConfig) {}
