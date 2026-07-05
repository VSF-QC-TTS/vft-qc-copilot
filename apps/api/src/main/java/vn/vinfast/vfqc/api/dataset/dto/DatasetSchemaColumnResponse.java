package vn.vinfast.vfqc.api.dataset.dto;

import vn.vinfast.vfqc.api.dataset.enums.DatasetColumnDataType;

public record DatasetSchemaColumnResponse(
    Long id,
    String name,
    String displayName,
    DatasetColumnDataType dataType,
    boolean required,
    String description,
    int orderIndex,
    String variable) {}
