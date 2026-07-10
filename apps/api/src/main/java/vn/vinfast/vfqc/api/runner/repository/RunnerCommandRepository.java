package vn.vinfast.vfqc.api.runner.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import vn.vinfast.vfqc.api.runner.entity.RunnerCommand;
import vn.vinfast.vfqc.api.runner.enums.RunnerCommandStatus;

public interface RunnerCommandRepository extends JpaRepository<RunnerCommand, UUID> {

  Optional<RunnerCommand> findByIdempotencyKey(String idempotencyKey);

  List<RunnerCommand> findByTestRunIdOrderByCreatedAtAsc(UUID testRunId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<RunnerCommand> findByStatusInAndAttemptsLessThanOrderByCreatedAtAsc(
      Set<RunnerCommandStatus> statuses, int maxAttempts, Pageable pageable);

  default List<RunnerCommand> findPublishable(
      RunnerCommandStatus pending, RunnerCommandStatus failed, int maxAttempts) {
    return findByStatusInAndAttemptsLessThanOrderByCreatedAtAsc(
        Set.of(pending, failed), maxAttempts, PageRequest.of(0, 25));
  }
}
