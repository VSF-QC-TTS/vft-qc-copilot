package vn.vinfast.vfqc.api.runner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record RunnerRuntimeSecretsRequest(
    @Schema(description = "Secret ids referenced by the locked run snapshots.", example = "[\"7e1e7b32-6875-4308-88d8-aa56b18ff7e8\"]")
        @NotEmpty(message = "At least one secret id is required")
        List<UUID> secretIds) {}
