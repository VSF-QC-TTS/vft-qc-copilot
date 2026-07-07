package vn.vinfast.vfqc.api.evaluator.entity;

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
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorOperator;

@Entity
@Table(name = "evaluator_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluatorRule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "evaluator_version_id", nullable = false)
  private UUID evaluatorVersionId;

  @Column(name = "field_source", nullable = false)
  private String fieldSource;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EvaluatorOperator operator;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "expected_value_json", columnDefinition = "jsonb")
  private Map<String, Object> expectedValueJson;

  private BigDecimal weight;

  @Column(nullable = false)
  private boolean required;

  @Column(name = "order_index", nullable = false)
  private int orderIndex;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "config_json", columnDefinition = "jsonb")
  private Map<String, Object> configJson;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
