package vn.vinfast.vfqc.api.dataset.dto;

import jakarta.validation.constraints.NotBlank;
import vn.vinfast.vfqc.api.dataset.enums.DatasetColumnDataType;
import vn.vinfast.vfqc.api.dataset.enums.DatasetImportMappingAction;

public record DatasetImportMappingDto(
    @NotBlank String sourceColumn,
    DatasetImportMappingAction action,
    String targetColumn,
    String displayName,
    DatasetColumnDataType dataType,
    Boolean required,
    String description) {}
