package vn.vinfast.vfqc.api.workspace.mapper;

import org.mapstruct.Mapper;
import vn.vinfast.vfqc.api.workspace.dto.WorkspaceMemberResponse;
import vn.vinfast.vfqc.api.workspace.entity.WorkspaceMember;

@Mapper(componentModel = "spring")
public interface WorkspaceMemberMapper {

  WorkspaceMemberResponse toResponse(WorkspaceMember member);
}
