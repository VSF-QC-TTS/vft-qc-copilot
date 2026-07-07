package vn.vinfast.vfqc.api.project.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.vinfast.vfqc.api.project.dto.ProjectResponse;
import vn.vinfast.vfqc.api.project.entity.Project;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

  @Mapping(target = "createdByUser", ignore = true)
  ProjectResponse toResponse(Project project);
}
