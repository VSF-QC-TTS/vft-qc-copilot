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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorType;

@Entity
@Table(name = "test_run_evaluator_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestRunEvaluatorVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "test_run_id", nullable = false)
  private UUID testRunId;

  @Column(name = "evaluator_id", nullable = false)
  private UUID evaluatorId;

  @Column(name = "evaluator_version_id", nullable = false)
  private UUID evaluatorVersionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "evaluator_type", nullable = false)
  private EvaluatorType evaluatorType;

  @Column(nullable = false)
  private int version;

  @Column(name = "order_index", nullable = false)
  private int orderIndex;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "config_json", columnDefinition = "jsonb")
  private Map<String, Object> configJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "rules_json", columnDefinition = "jsonb")
  private List<Map<String, Object>> rulesJson;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
}
