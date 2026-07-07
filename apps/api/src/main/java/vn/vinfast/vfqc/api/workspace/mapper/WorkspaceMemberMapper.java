package vn.vinfast.vfqc.api.workspace.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.vinfast.vfqc.api.workspace.dto.WorkspaceMemberResponse;
import vn.vinfast.vfqc.api.workspace.entity.WorkspaceMember;

@Mapper(componentModel = "spring")
public interface WorkspaceMemberMapper {

  @Mapping(target = "user", ignore = true)
  @Mapping(target = "invitedByUser", ignore = true)
  WorkspaceMemberResponse toResponse(WorkspaceMember member);
}
