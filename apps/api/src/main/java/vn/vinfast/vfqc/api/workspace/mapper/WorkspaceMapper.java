package vn.vinfast.vfqc.api.workspace.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.vinfast.vfqc.api.workspace.dto.WorkspaceResponse;
import vn.vinfast.vfqc.api.workspace.entity.Workspace;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;
import vn.vinfast.vfqc.api.workspace.repository.WorkspaceSummaryView;

@Mapper(componentModel = "spring")
public interface WorkspaceMapper {

  @Mapping(target = "currentUserRole", source = "currentUserRole")
  WorkspaceResponse toResponse(Workspace workspace, WorkspaceRole currentUserRole);

  WorkspaceResponse toResponse(WorkspaceSummaryView view);
}
