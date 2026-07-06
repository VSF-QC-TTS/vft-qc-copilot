package vn.vinfast.vfqc.api.target.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.vinfast.vfqc.api.target.entity.ChatbotTarget;
import vn.vinfast.vfqc.api.target.enums.ChatbotTargetStatus;

public interface ChatbotTargetRepository extends JpaRepository<ChatbotTarget, UUID> {

  Optional<ChatbotTarget> findByIdAndDeletedAtIsNull(UUID id);

  List<ChatbotTarget> findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID projectId);

  boolean existsByProjectIdAndNameLookupAndDeletedAtIsNull(UUID projectId, String nameLookup);

  long countByProjectIdAndStatusAndDeletedAtIsNull(UUID projectId, ChatbotTargetStatus status);

  @Query(
      """
      select count(t)
      from ChatbotTarget t
      where t.projectId = :projectId
        and t.status = :status
        and t.deletedAt is null
        and t.responseExtractionConfigJson is not null
      """)
  long countWithExtractionByProjectIdAndStatus(UUID projectId, ChatbotTargetStatus status);
}
