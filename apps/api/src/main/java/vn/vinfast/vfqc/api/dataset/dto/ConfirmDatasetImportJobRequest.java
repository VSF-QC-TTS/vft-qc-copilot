package vn.vinfast.vfqc.api.dataset.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import vn.vinfast.vfqc.api.dataset.enums.DatasetImportTargetSchemaMode;

public record ConfirmDatasetImportJobRequest(
    @NotBlank String datasetName,
    String datasetDescription,
    DatasetImportTargetSchemaMode targetSchemaMode,
    @NotEmpty List<@Valid DatasetImportMappingDto> mapping) {}
