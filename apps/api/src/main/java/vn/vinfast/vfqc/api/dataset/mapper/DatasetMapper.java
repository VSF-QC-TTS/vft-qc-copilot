package vn.vinfast.vfqc.api.dataset.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.vinfast.vfqc.api.dataset.dto.DatasetResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetRowResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetSchemaColumnResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetSchemaResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetVersionResponse;
import vn.vinfast.vfqc.api.dataset.entity.Dataset;
import vn.vinfast.vfqc.api.dataset.entity.DatasetRow;
import vn.vinfast.vfqc.api.dataset.entity.DatasetSchema;
import vn.vinfast.vfqc.api.dataset.entity.DatasetSchemaColumn;
import vn.vinfast.vfqc.api.dataset.entity.DatasetVersion;

@Mapper(componentModel = "spring")
public interface DatasetMapper {

  @Mapping(target = "createdByUser", ignore = true)
  DatasetResponse toResponse(Dataset dataset);

  @Mapping(target = "createdByUser", ignore = true)
  DatasetVersionResponse toResponse(DatasetVersion version);

  @Mapping(target = "cells", source = "cellsJson")
  DatasetRowResponse toResponse(DatasetRow row);

  @Mapping(target = "createdByUser", ignore = true)
  @Mapping(target = "columns", source = "columns")
  DatasetSchemaResponse toResponse(DatasetSchema schema, java.util.List<DatasetSchemaColumnResponse> columns);

  @Mapping(target = "variable", expression = "java(\"$dataset.\" + column.getName())")
  DatasetSchemaColumnResponse toResponse(DatasetSchemaColumn column);
}
