package vn.vinfast.vfqc.api.exportjob.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vinfast.vfqc.api.exportjob.enums.ExportFormat;
import vn.vinfast.vfqc.api.exportjob.enums.ExportJobStatus;
import vn.vinfast.vfqc.api.exportjob.enums.ExportJobType;
import vn.vinfast.vfqc.api.exportjob.enums.ExportResultMode;

@Entity
@Table(name = "export_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportJob {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "job_type", nullable = false)
  private ExportJobType jobType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ExportFormat format;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ExportJobStatus status;

  @Column(name = "dataset_version_id")
  private UUID datasetVersionId;

  @Column(name = "test_run_id")
  private UUID testRunId;

  @Enumerated(EnumType.STRING)
  @Column(name = "result_mode")
  private ExportResultMode resultMode;

  @Column(name = "file_path")
  private String filePath;

  @Column(name = "file_name")
  private String fileName;

  @Column(name = "file_size_bytes")
  private Long fileSizeBytes;

  @Column(name = "error_code")
  private String errorCode;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "started_at")
  private OffsetDateTime startedAt;

  @Column(name = "finished_at")
  private OffsetDateTime finishedAt;

  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;
}
