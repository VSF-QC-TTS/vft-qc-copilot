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
import vn.vinfast.vfqc.api.provider.enums.ProviderScope;
import vn.vinfast.vfqc.api.provider.enums.ProviderStatus;
import vn.vinfast.vfqc.api.provider.enums.ProviderType;

@Entity
@Table(name = "llm_providers")
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class LlmProvider {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ProviderScope scope;

  @Column(name = "owner_user_id")
  private UUID ownerUserId;

  @Column(name = "workspace_id")
  private UUID workspaceId;

  @Column(nullable = false)
  private String name;

  @Column(name = "name_lookup", nullable = false)
  private String nameLookup;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider_type", nullable = false)
  private ProviderType providerType;

  @Column(name = "secret_id")
  private UUID secretId;

  @Column(name = "base_url")
  private String baseUrl;

  @Column(name = "default_model")
  private String defaultModel;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "available_models_json", columnDefinition = "jsonb")
  private Map<String, Object> availableModelsJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "config_json", columnDefinition = "jsonb")
  private Map<String, Object> configJson;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ProviderStatus status;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
