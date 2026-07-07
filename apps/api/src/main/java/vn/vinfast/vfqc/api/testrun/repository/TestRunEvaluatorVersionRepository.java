package vn.vinfast.vfqc.api.testrun.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.testrun.entity.TestRunEvaluatorVersion;

public interface TestRunEvaluatorVersionRepository
    extends JpaRepository<TestRunEvaluatorVersion, Long> {

  List<TestRunEvaluatorVersion> findByTestRunIdOrderByOrderIndexAsc(UUID testRunId);
}
