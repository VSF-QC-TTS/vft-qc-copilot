package vn.vinfast.vfqc.api.testrun.entity;

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
import vn.vinfast.vfqc.api.testrun.enums.TestRunStatus;
import vn.vinfast.vfqc.api.testrun.enums.TestRunType;

@Entity
@Table(name = "test_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestRun {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Enumerated(EnumType.STRING)
  @Column(name = "run_type", nullable = false)
  private TestRunType runType;

  @Column(name = "dataset_id", nullable = false)
  private UUID datasetId;

  @Column(name = "dataset_version_id", nullable = false)
  private UUID datasetVersionId;

  @Column(name = "dataset_schema_id", nullable = false)
  private UUID datasetSchemaId;

  @Column(name = "dataset_schema_version", nullable = false)
  private int datasetSchemaVersion;

  @Column(name = "judge_provider_id")
  private UUID judgeProviderId;

  @Column(name = "judge_model")
  private String judgeModel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TestRunStatus status;

  @Column(name = "progress_completed", nullable = false)
  private int progressCompleted;

  @Column(name = "progress_total", nullable = false)
  private int progressTotal;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "summary_metrics_json", columnDefinition = "jsonb")
  private Map<String, Object> summaryMetricsJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "domain_config_snapshot_json", columnDefinition = "jsonb")
  private Map<String, Object> domainConfigSnapshotJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "promptfoo_config_snapshot_json", columnDefinition = "jsonb")
  private Map<String, Object> promptfooConfigSnapshotJson;

  @Column(name = "domain_config_hash", nullable = false)
  private String domainConfigHash;

  @Column(name = "promptfoo_config_hash", nullable = false)
  private String promptfooConfigHash;

  @Column(name = "started_by", nullable = false)
  private UUID startedBy;

  @Column(name = "started_at")
  private OffsetDateTime startedAt;

  @Column(name = "finished_at")
  private OffsetDateTime finishedAt;

  @Column(name = "cancel_requested_at")
  private OffsetDateTime cancelRequestedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
