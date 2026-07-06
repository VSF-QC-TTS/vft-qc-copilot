package vn.vinfast.vfqc.api.target.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.vinfast.vfqc.api.target.enums.ResponseExtractionSource;

public record ResponseExtractionFieldDto(
    @Schema(example = "answer") @NotBlank(message = "Extraction field name is required")
        String fieldName,
    @Schema(example = "JSON_PATH") @NotNull(message = "Extraction source is required")
        ResponseExtractionSource source,
    @Schema(example = "$.answer") String path,
    @Schema(example = "true") boolean required) {}
