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
import vn.vinfast.vfqc.api.testrun.enums.TestRunTargetType;

@Entity
@Table(name = "test_run_targets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestRunTarget {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "test_run_id", nullable = false)
  private UUID testRunId;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false)
  private TestRunTargetType targetType;

  @Column(name = "chatbot_target_id")
  private UUID chatbotTargetId;

  @Column(name = "provider_id")
  private UUID providerId;

  private String model;

  @Column(nullable = false)
  private String name;

  @Column(name = "order_index", nullable = false)
  private int orderIndex;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "config_json", columnDefinition = "jsonb")
  private Map<String, Object> configJson;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
}
