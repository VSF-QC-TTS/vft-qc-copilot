package vn.vinfast.vfqc.api.runner.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.vinfast.vfqc.api.runner.entity.RunnerEvent;

public interface RunnerEventRepository extends JpaRepository<RunnerEvent, Long> {

  Optional<RunnerEvent> findByEventId(String eventId);

  Slice<RunnerEvent> findByTestRunIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(
      UUID testRunId, long sequenceNo, Pageable pageable);

  @Query("select coalesce(max(event.sequenceNo), 0) from RunnerEvent event where event.testRunId = :testRunId")
  long maxSequenceNo(@Param("testRunId") UUID testRunId);
}
