package vn.vinfast.vfqc.api.dataset.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.dataset.entity.DatasetSchemaColumn;

public interface DatasetSchemaColumnRepository extends JpaRepository<DatasetSchemaColumn, Long> {

  List<DatasetSchemaColumn> findBySchemaIdOrderByOrderIndexAsc(UUID schemaId);
}
