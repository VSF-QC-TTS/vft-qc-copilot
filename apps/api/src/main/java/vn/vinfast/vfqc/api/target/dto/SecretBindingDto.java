package vn.vinfast.vfqc.api.target.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import vn.vinfast.vfqc.api.target.enums.SecretBindingLocation;

public record SecretBindingDto(
    @Schema(example = "HEADER") @NotNull(message = "Secret binding location is required")
        SecretBindingLocation location,
    @Schema(example = "Authorization") @NotBlank(message = "Secret binding key/path is required")
        String keyOrPath,
    @Schema(description = "Secret id resolved only during runtime execution.")
        @NotNull(message = "Secret id is required")
        UUID secretId) {}
