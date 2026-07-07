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
import vn.vinfast.vfqc.api.dataset.enums.DatasetImportStatus;
import vn.vinfast.vfqc.api.dataset.enums.DatasetSourceType;

@Entity
@Table(name = "dataset_import_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatasetImportJob {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "target_project_id", nullable = false)
  private UUID targetProjectId;

  @Column(name = "target_dataset_id")
  private UUID targetDatasetId;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false)
  private DatasetSourceType sourceType;

  @Column(name = "source_file_name")
  private String sourceFileName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DatasetImportStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "mapping_json", columnDefinition = "jsonb")
  private Map<String, Object> mappingJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "preview_json", columnDefinition = "jsonb")
  private Map<String, Object> previewJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "source_data_json", columnDefinition = "jsonb")
  private Map<String, Object> sourceDataJson;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "finished_at")
  private OffsetDateTime finishedAt;
}
