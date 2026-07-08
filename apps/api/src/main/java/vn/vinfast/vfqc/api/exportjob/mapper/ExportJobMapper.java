package vn.vinfast.vfqc.api.exportjob.mapper;

import org.mapstruct.Mapper;
import vn.vinfast.vfqc.api.exportjob.dto.ExportJobResponse;
import vn.vinfast.vfqc.api.exportjob.entity.ExportJob;

@Mapper(componentModel = "spring")
public interface ExportJobMapper {

  ExportJobResponse toResponse(ExportJob job);
}
