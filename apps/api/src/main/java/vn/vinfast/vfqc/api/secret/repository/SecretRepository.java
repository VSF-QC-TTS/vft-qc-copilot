package vn.vinfast.vfqc.api.secret.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.secret.entity.Secret;
import vn.vinfast.vfqc.api.secret.enums.SecretStatus;

public interface SecretRepository extends JpaRepository<Secret, UUID> {

  Optional<Secret> findByIdAndDeletedAtIsNull(UUID id);

  List<Secret> findByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID workspaceId);

  List<Secret> findByOwnerUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID ownerUserId);

  boolean existsByWorkspaceIdAndNameLookupAndDeletedAtIsNull(UUID workspaceId, String nameLookup);

  boolean existsByOwnerUserIdAndNameLookupAndDeletedAtIsNull(UUID ownerUserId, String nameLookup);

  Optional<Secret> findByIdAndStatusAndDeletedAtIsNull(UUID id, SecretStatus status);
}
