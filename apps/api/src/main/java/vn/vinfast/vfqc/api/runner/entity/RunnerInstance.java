package vn.vinfast.vfqc.api.runner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vinfast.vfqc.api.runner.enums.RunnerInstanceStatus;

@Entity
@Table(name = "runner_instances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RunnerInstance {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "runner_key", nullable = false)
  private String runnerKey;

  private String hostname;

  private String version;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RunnerInstanceStatus status;

  @Column(name = "current_test_run_id")
  private UUID currentTestRunId;

  @Column(name = "current_command_id")
  private UUID currentCommandId;

  @Column(name = "last_heartbeat_at", nullable = false)
  private OffsetDateTime lastHeartbeatAt;

  @Column(name = "registered_at", nullable = false)
  private OffsetDateTime registeredAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
