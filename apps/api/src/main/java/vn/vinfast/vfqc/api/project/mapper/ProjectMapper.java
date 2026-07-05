package vn.vinfast.vfqc.api.project.mapper;

import org.mapstruct.Mapper;
import vn.vinfast.vfqc.api.project.dto.ProjectResponse;
import vn.vinfast.vfqc.api.project.entity.Project;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

  ProjectResponse toResponse(Project project);
}
