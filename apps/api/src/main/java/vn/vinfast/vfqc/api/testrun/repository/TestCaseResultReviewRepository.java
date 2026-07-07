package vn.vinfast.vfqc.api.testrun.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.testrun.entity.TestCaseResultReview;

public interface TestCaseResultReviewRepository extends JpaRepository<TestCaseResultReview, Long> {

  Optional<TestCaseResultReview> findByTestCaseResultId(Long testCaseResultId);

  List<TestCaseResultReview> findByTestCaseResultIdIn(Collection<Long> testCaseResultIds);
}
