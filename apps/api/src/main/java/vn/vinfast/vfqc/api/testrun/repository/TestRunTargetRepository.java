package vn.vinfast.vfqc.api.testrun.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.testrun.entity.TestRunTarget;

public interface TestRunTargetRepository extends JpaRepository<TestRunTarget, UUID> {

  List<TestRunTarget> findByTestRunIdOrderByOrderIndexAsc(UUID testRunId);

  List<TestRunTarget> findByTestRunIdIn(Collection<UUID> testRunIds);
}
