package vn.vinfast.vfqc.api.exportjob.service.impl;

import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.exportjob.entity.ExportJob;
import vn.vinfast.vfqc.api.exportjob.service.ExportFileNamePolicy;

@Slf4j
@Component
public class ExportFileNamePolicyImpl implements ExportFileNamePolicy {

  @Override
  public String fileNameFor(ExportJob job) {
    var prefix = job.getJobType().name().toLowerCase(Locale.ROOT).replace('_', '-');
    var filename = prefix + "-" + job.getId() + ".xlsx";
    log.debug("Generated filename for job: jobId={}, filename={}", job.getId(), filename);
    return filename;
  }

  @Override
  public String storagePathFor(ExportJob job, String fileName) {
    var path = job.getCreatedAt().getYear() + "/" + job.getId() + "/" + fileName;
    log.debug("Generated storage path for job: jobId={}, path={}", job.getId(), path);
    return path;
  }
}
