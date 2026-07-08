package vn.vinfast.vfqc.api.runner.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.runner.dto.RegisterRunnerInstanceRequest;
import vn.vinfast.vfqc.api.runner.dto.RunnerHeartbeatRequest;
import vn.vinfast.vfqc.api.runner.dto.RunnerInstanceResponse;
import vn.vinfast.vfqc.api.runner.entity.RunnerInstance;
import vn.vinfast.vfqc.api.runner.enums.RunnerInstanceStatus;
import vn.vinfast.vfqc.api.runner.repository.RunnerInstanceRepository;
import vn.vinfast.vfqc.api.runner.service.RunnerInstanceService;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunnerInstanceServiceImpl implements RunnerInstanceService {

  private final RunnerInstanceRepository repository;
  private final Clock clock;

  @Override
  @Transactional
  public RunnerInstanceResponse register(RegisterRunnerInstanceRequest request) {
    log.info("Registering runner instance: key={}, hostname={}, version={}", request.runnerKey(), request.hostname(), request.version());
    var now = OffsetDateTime.now(clock);
    var runner =
        repository
            .findByRunnerKey(request.runnerKey())
            .orElseGet(
                () ->
                    RunnerInstance.builder()
                        .runnerKey(request.runnerKey())
                        .registeredAt(now)
                        .build());
    runner.setHostname(request.hostname());
    runner.setVersion(request.version());
    runner.setStatus(RunnerInstanceStatus.IDLE);
    runner.setLastHeartbeatAt(now);
    runner.setUpdatedAt(now);
    return response(repository.save(runner));
  }

  @Override
  @Transactional
  public RunnerInstanceResponse heartbeat(RunnerHeartbeatRequest request) {
    log.debug("Runner heartbeat received: key={}, status={}, runId={}", request.runnerKey(), request.status(), request.currentTestRunId());
    var now = OffsetDateTime.now(clock);
    var runner =
        repository
            .findByRunnerKey(request.runnerKey())
            .orElseGet(
                () ->
                    RunnerInstance.builder()
                        .runnerKey(request.runnerKey())
                        .status(RunnerInstanceStatus.IDLE)
                        .registeredAt(now)
                        .build());
    runner.setStatus(request.status() == null ? RunnerInstanceStatus.IDLE : request.status());
    runner.setCurrentCommandId(request.currentCommandId());
    runner.setCurrentTestRunId(request.currentTestRunId());
    runner.setLastHeartbeatAt(now);
    runner.setUpdatedAt(now);
    return response(repository.save(runner));
  }

  private RunnerInstanceResponse response(RunnerInstance runner) {
    return new RunnerInstanceResponse(
        runner.getId(),
        runner.getRunnerKey(),
        runner.getHostname(),
        runner.getVersion(),
        runner.getStatus(),
        runner.getCurrentCommandId(),
        runner.getCurrentTestRunId(),
        runner.getLastHeartbeatAt(),
        runner.getRegisteredAt(),
        runner.getUpdatedAt());
  }
}
