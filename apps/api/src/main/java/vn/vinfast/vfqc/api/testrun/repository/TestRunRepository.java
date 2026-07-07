package vn.vinfast.vfqc.api.testrun.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.testrun.entity.TestRun;

public interface TestRunRepository extends JpaRepository<TestRun, UUID> {

  Page<TestRun> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

  Optional<TestRun> findByIdAndProjectId(UUID id, UUID projectId);
}
