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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import vn.vinfast.vfqc.api.dataset.enums.DatasetScope;
import vn.vinfast.vfqc.api.dataset.enums.DatasetSourceType;

@Entity
@Table(name = "datasets")
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class Dataset {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DatasetScope scope;

  @Column(name = "workspace_id")
  private UUID workspaceId;

  @Column(name = "project_id")
  private UUID projectId;

  @Column(name = "owner_user_id")
  private UUID ownerUserId;

  @Column(name = "current_version_id")
  private UUID currentVersionId;

  @Column(nullable = false)
  private String name;

  @Column(name = "name_lookup", nullable = false)
  private String nameLookup;

  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type")
  private DatasetSourceType sourceType;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
