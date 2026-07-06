package vn.vinfast.vfqc.api.target.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "chatbot_target_examples")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatbotTargetExample {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "chatbot_target_id", nullable = false)
  private UUID chatbotTargetId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "request_json", columnDefinition = "jsonb")
  private Map<String, Object> requestJson;

  @Column(name = "response_status_code")
  private Integer responseStatusCode;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_headers_json", columnDefinition = "jsonb")
  private Map<String, Object> responseHeadersJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_body_json", columnDefinition = "jsonb")
  private Map<String, Object> responseBodyJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "extracted_output_json", columnDefinition = "jsonb")
  private Map<String, Object> extractedOutputJson;

  @Column(name = "latency_ms")
  private Integer latencyMs;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "tested_by", nullable = false)
  private UUID testedBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
}
