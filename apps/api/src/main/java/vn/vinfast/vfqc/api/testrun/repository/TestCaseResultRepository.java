package vn.vinfast.vfqc.api.testrun.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.testrun.entity.TestCaseResult;

public interface TestCaseResultRepository extends JpaRepository<TestCaseResult, Long> {

  Page<TestCaseResult> findByTestRunIdOrderByIdAsc(UUID testRunId, Pageable pageable);

  List<TestCaseResult> findByTestRunIdAndDatasetRowIdIn(UUID testRunId, Collection<Long> datasetRowIds);

  long countByTestRunId(UUID testRunId);
}
