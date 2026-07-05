package vn.vinfast.vfqc.api.dataset.entity;

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
import vn.vinfast.vfqc.api.dataset.enums.DatasetVersionStatus;

@Entity
@Table(name = "dataset_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatasetVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "dataset_id", nullable = false)
  private UUID datasetId;

  @Column(nullable = false)
  private int version;

  @Column(name = "schema_id", nullable = false)
  private UUID schemaId;

  @Column(name = "schema_version", nullable = false)
  private int schemaVersion;

  @Column(name = "base_version_id")
  private UUID baseVersionId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "import_mapping_json", columnDefinition = "jsonb")
  private Map<String, Object> importMappingJson;

  @Column(name = "row_count", nullable = false)
  private int rowCount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DatasetVersionStatus status;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "published_at")
  private OffsetDateTime publishedAt;

  @Column(name = "discarded_at")
  private OffsetDateTime discardedAt;
}
