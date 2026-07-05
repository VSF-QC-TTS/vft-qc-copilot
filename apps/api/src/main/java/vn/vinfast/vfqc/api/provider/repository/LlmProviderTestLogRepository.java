package vn.vinfast.vfqc.api.provider.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.provider.entity.LlmProviderTestLog;

public interface LlmProviderTestLogRepository extends JpaRepository<LlmProviderTestLog, Long> {

  List<LlmProviderTestLog> findByLlmProviderIdOrderByTestedAtDesc(UUID llmProviderId, Pageable pageable);
}
