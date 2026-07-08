package vn.vinfast.vfqc.api.runner.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.runner.entity.RunnerInstance;

public interface RunnerInstanceRepository extends JpaRepository<RunnerInstance, UUID> {

  Optional<RunnerInstance> findByRunnerKey(String runnerKey);
}
