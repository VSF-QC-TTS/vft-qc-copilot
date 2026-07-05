package vn.vinfast.vfqc.api.dataset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "dataset_rows")
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class DatasetRow {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "dataset_version_id", nullable = false)
  private UUID datasetVersionId;

  @Column(name = "row_index", nullable = false)
  private int rowIndex;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "cells_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> cellsJson;

  @Column(nullable = false)
  private int revision;

  @Column(name = "row_hash")
  private String rowHash;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
