package vn.vinfast.vfqc.api.project.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.project.entity.Project;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

  Optional<Project> findByIdAndDeletedAtIsNull(UUID id);

  List<Project> findByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID workspaceId);

  boolean existsByWorkspaceIdAndNameLookupAndDeletedAtIsNull(UUID workspaceId, String nameLookup);
}
