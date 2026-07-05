package vn.vinfast.vfqc.api.dataset.dto;

import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateDatasetRequest(
    @Schema(example = "Customer support smoke dataset")
    @Size(max = 160, message = "Dataset name must be at most 160 characters") String name,
    @Schema(example = "Manual prompts for daily regression")
    @Size(max = 1000, message = "Dataset description must be at most 1000 characters") String description) {}
