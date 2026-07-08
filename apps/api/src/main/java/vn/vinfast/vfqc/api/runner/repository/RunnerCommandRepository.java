package vn.vinfast.vfqc.api.runner.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.runner.entity.RunnerCommand;

public interface RunnerCommandRepository extends JpaRepository<RunnerCommand, UUID> {

  Optional<RunnerCommand> findByIdempotencyKey(String idempotencyKey);

  List<RunnerCommand> findByTestRunIdOrderByCreatedAtAsc(UUID testRunId);
}
