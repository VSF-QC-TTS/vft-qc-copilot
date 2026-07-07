package vn.vinfast.vfqc.api.testrun.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
import vn.vinfast.vfqc.api.testrun.enums.TestCaseResultStatus;

@Entity
@Table(name = "test_case_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCaseResult {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "test_run_id", nullable = false)
  private UUID testRunId;

  @Column(name = "test_run_target_id", nullable = false)
  private UUID testRunTargetId;

  @Column(name = "dataset_row_id", nullable = false)
  private Long datasetRowId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TestCaseResultStatus status;

  private BigDecimal score;

  @Column(name = "passed")
  private Boolean passed;

  @Column(name = "error_category")
  private String errorCategory;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "request_json", columnDefinition = "jsonb")
  private Map<String, Object> requestJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_json", columnDefinition = "jsonb")
  private Map<String, Object> responseJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "extracted_output_json", columnDefinition = "jsonb")
  private Map<String, Object> extractedOutputJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "grading_result_json", columnDefinition = "jsonb")
  private Map<String, Object> gradingResultJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "component_results_json", columnDefinition = "jsonb")
  private Map<String, Object> componentResultsJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "token_usage_json", columnDefinition = "jsonb")
  private Map<String, Object> tokenUsageJson;

  @Column(name = "latency_ms")
  private Integer latencyMs;

  @Column(name = "machine_summary")
  private String machineSummary;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
