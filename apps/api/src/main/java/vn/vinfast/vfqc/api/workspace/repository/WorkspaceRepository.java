package vn.vinfast.vfqc.api.workspace.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.vinfast.vfqc.api.workspace.entity.Workspace;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceMemberStatus;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceType;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

  Optional<Workspace> findByIdAndDeletedAtIsNull(UUID id);

  boolean existsByNameLookupAndDeletedAtIsNull(String nameLookup);

  boolean existsByCreatedByAndTypeAndDeletedAtIsNull(UUID createdBy, WorkspaceType type);

  List<Workspace> findByDeletedAtIsNullOrderByCreatedAtDesc();

  @Query(
      """
      select w.id as id,
             w.name as name,
             w.type as type,
             m.role as currentUserRole,
             w.createdBy as createdBy,
             w.createdAt as createdAt,
             w.updatedAt as updatedAt
      from Workspace w
      join WorkspaceMember m on m.workspaceId = w.id
      where m.userId = :userId
        and m.status = :status
        and w.deletedAt is null
      order by w.createdAt desc
      """)
  List<WorkspaceSummaryView> findVisibleSummariesForUser(UUID userId, WorkspaceMemberStatus status);
}
