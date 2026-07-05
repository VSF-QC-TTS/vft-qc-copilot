package vn.vinfast.vfqc.api.provider.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.vinfast.vfqc.api.provider.enums.ProviderTestStatus;

@Entity
@Table(name = "llm_provider_test_logs")
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class LlmProviderTestLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "llm_provider_id", nullable = false)
  private UUID llmProviderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ProviderTestStatus status;

  private String model;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "request_json_sanitized", columnDefinition = "jsonb")
  private Map<String, Object> requestJsonSanitized;

  @Column(name = "response_preview")
  private String responsePreview;

  @Column(name = "error_code")
  private String errorCode;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "latency_ms")
  private Integer latencyMs;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "token_usage_json", columnDefinition = "jsonb")
  private Map<String, Object> tokenUsageJson;

  @Column(name = "tested_by", nullable = false)
  private UUID testedBy;

  @Column(name = "tested_at", nullable = false)
  private OffsetDateTime testedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
}
