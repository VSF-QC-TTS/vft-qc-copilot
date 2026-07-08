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
import vn.vinfast.vfqc.api.runner.enums.RunnerCommandStatus;
import vn.vinfast.vfqc.api.runner.enums.RunnerCommandType;

@Entity
@Table(name = "runner_commands")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RunnerCommand {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "test_run_id", nullable = false)
  private UUID testRunId;

  @Column(name = "runner_id")
  private UUID runnerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "command_type", nullable = false)
  private RunnerCommandType commandType;

  @Column(name = "idempotency_key", nullable = false)
  private String idempotencyKey;

  @Column(name = "stream_id")
  private String streamId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RunnerCommandStatus status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "sanitized_error")
  private String sanitizedError;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "published_at")
  private OffsetDateTime publishedAt;

  @Column(name = "acknowledged_at")
  private OffsetDateTime acknowledgedAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
