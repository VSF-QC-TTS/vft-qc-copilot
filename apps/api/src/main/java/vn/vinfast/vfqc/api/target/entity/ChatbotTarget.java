package vn.vinfast.vfqc.api.target.entity;

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
import vn.vinfast.vfqc.api.target.enums.ChatbotTargetStatus;

@Entity
@Table(name = "chatbot_targets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatbotTarget {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(nullable = false)
  private String name;

  @Column(name = "name_lookup", nullable = false)
  private String nameLookup;

  private String description;

  @Column(name = "raw_curl_sanitized")
  private String rawCurlSanitized;

  @Column(name = "raw_curl_hash")
  private String rawCurlHash;

  private String method;

  private String url;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "headers_json", columnDefinition = "jsonb")
  private Map<String, Object> headersJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "query_params_json", columnDefinition = "jsonb")
  private Map<String, Object> queryParamsJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "body_json", columnDefinition = "jsonb")
  private Map<String, Object> bodyJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "auth_config_json", columnDefinition = "jsonb")
  private Map<String, Object> authConfigJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "dataset_mapping_json", columnDefinition = "jsonb")
  private Map<String, Object> datasetMappingJson;

  @Column(name = "latest_example_id")
  private Long latestExampleId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_extraction_config_json", columnDefinition = "jsonb")
  private Map<String, Object> responseExtractionConfigJson;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ChatbotTargetStatus status;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
