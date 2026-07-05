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
import vn.vinfast.vfqc.api.dataset.enums.DatasetColumnDataType;

@Entity
@Table(name = "dataset_schema_columns")
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class DatasetSchemaColumn {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "schema_id", nullable = false)
  private UUID schemaId;

  @Column(nullable = false)
  private String name;

  @Column(name = "display_name")
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(name = "data_type", nullable = false)
  private DatasetColumnDataType dataType;

  @Column(nullable = false)
  private boolean required;

  private String description;

  @Column(name = "order_index", nullable = false)
  private int orderIndex;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
