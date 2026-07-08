package vn.vinfast.vfqc.api.testrun.repository;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import vn.vinfast.vfqc.api.testrun.entity.TestRun;

public interface TestRunRepository extends JpaRepository<TestRun, UUID> {

  Page<TestRun> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

  Optional<TestRun> findByIdAndProjectId(UUID id, UUID projectId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<TestRun> findWithLockById(UUID id);
}
