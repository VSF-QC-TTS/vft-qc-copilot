package vn.vinfast.vfqc.api.target.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.vinfast.vfqc.api.target.enums.SecretBindingLocation;

public record DatasetMappingDto(
    @Schema(example = "BODY_JSON") @NotNull(message = "Dataset mapping location is required")
        SecretBindingLocation location,
    @Schema(example = "question") @NotBlank(message = "Dataset mapping key/path is required")
        String keyOrPath,
    @Schema(example = "question") @NotBlank(message = "Dataset column name is required")
        String columnName) {}
