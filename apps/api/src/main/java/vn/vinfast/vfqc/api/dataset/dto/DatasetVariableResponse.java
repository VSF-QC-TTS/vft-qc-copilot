package vn.vinfast.vfqc.api.dataset.dto;

import vn.vinfast.vfqc.api.dataset.enums.DatasetColumnDataType;

public record DatasetVariableResponse(String name, String variable, DatasetColumnDataType dataType, boolean required) {}
