package vn.vinfast.vfqc.api.provider.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.provider.entity.LlmProvider;
import vn.vinfast.vfqc.api.provider.enums.ProviderStatus;

public interface LlmProviderRepository extends JpaRepository<LlmProvider, UUID> {

  Optional<LlmProvider> findByIdAndDeletedAtIsNull(UUID id);

  List<LlmProvider> findByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID workspaceId);

  List<LlmProvider> findByOwnerUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID ownerUserId);

  boolean existsByWorkspaceIdAndNameLookupAndDeletedAtIsNull(UUID workspaceId, String nameLookup);

  boolean existsByOwnerUserIdAndNameLookupAndDeletedAtIsNull(UUID ownerUserId, String nameLookup);

  long countByWorkspaceIdAndStatusAndDeletedAtIsNull(UUID workspaceId, ProviderStatus status);
}
