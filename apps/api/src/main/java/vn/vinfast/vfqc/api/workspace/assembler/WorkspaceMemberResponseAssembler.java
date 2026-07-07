package vn.vinfast.vfqc.api.workspace.assembler;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;
import vn.vinfast.vfqc.api.user.service.UserSummaryService;
import vn.vinfast.vfqc.api.workspace.dto.WorkspaceMemberResponse;
import vn.vinfast.vfqc.api.workspace.entity.WorkspaceMember;
import vn.vinfast.vfqc.api.workspace.mapper.WorkspaceMemberMapper;

@Component
@RequiredArgsConstructor
public class WorkspaceMemberResponseAssembler {

  private final WorkspaceMemberMapper memberMapper;
  private final UserSummaryService userSummaryService;

  public WorkspaceMemberResponse toResponse(WorkspaceMember member) {
    var usersById =
        userSummaryService.summariesById(
            Stream.of(member.getUserId(), member.getInvitedBy()).toList());
    return withUsers(memberMapper.toResponse(member), usersById);
  }

  public List<WorkspaceMemberResponse> toResponses(List<WorkspaceMember> members) {
    var userIds =
        members.stream()
            .flatMap(member -> Stream.of(member.getUserId(), member.getInvitedBy()))
            .toList();
    var usersById = userSummaryService.summariesById(userIds);
    return members.stream()
        .map(member -> withUsers(memberMapper.toResponse(member), usersById))
        .toList();
  }

  private WorkspaceMemberResponse withUsers(
      WorkspaceMemberResponse response, Map<UUID, UserSummaryResponse> usersById) {
    return new WorkspaceMemberResponse(
        response.id(),
        response.workspaceId(),
        response.userId(),
        usersById.get(response.userId()),
        response.role(),
        response.status(),
        response.invitedBy(),
        usersById.get(response.invitedBy()),
        response.joinedAt(),
        response.createdAt(),
        response.updatedAt());
  }
}
