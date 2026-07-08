package vn.vinfast.vfqc.api.exportjob.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.exportjob.entity.ExportJob;

public interface ExportJobRepository extends JpaRepository<ExportJob, UUID> {}
