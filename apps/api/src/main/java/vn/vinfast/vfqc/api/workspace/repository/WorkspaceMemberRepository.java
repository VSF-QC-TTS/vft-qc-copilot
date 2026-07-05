package vn.vinfast.vfqc.api.workspace.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.workspace.entity.WorkspaceMember;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceMemberStatus;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

  Optional<WorkspaceMember> findByWorkspaceIdAndUserIdAndStatus(
      UUID workspaceId, UUID userId, WorkspaceMemberStatus status);

  List<WorkspaceMember> findByUserIdAndStatus(UUID userId, WorkspaceMemberStatus status);

  List<WorkspaceMember> findByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);

  boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
