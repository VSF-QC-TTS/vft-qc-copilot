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
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.vinfast.vfqc.api.runner.enums.RunnerEventStatus;
import vn.vinfast.vfqc.api.runner.enums.RunnerEventType;

@Entity
@Table(name = "runner_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RunnerEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_id", nullable = false)
  private String eventId;

  @Column(name = "test_run_id", nullable = false)
  private UUID testRunId;

  @Column(name = "runner_id")
  private UUID runnerId;

  @Column(name = "command_id")
  private UUID commandId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false)
  private RunnerEventType eventType;

  @Column(name = "sequence_no", nullable = false)
  private long sequenceNo;

  @Column(name = "stream_id")
  private String streamId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RunnerEventStatus status;

  @Column(nullable = false)
  private int attempts;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_json", columnDefinition = "jsonb")
  private Map<String, Object> payloadJson;

  @Column(name = "sanitized_error")
  private String sanitizedError;

  @Column(name = "occurred_at")
  private OffsetDateTime occurredAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
