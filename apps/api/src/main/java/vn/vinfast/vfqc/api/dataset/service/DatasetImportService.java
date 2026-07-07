package vn.vinfast.vfqc.api.dataset.service;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.dto.ConfirmDatasetImportJobRequest;
import vn.vinfast.vfqc.api.dataset.dto.DatasetImportJobResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetResponse;
import vn.vinfast.vfqc.api.dataset.enums.DatasetSourceType;

public interface DatasetImportService {

  DatasetImportJobResponse createExcelPreview(
      CurrentUser user,
      UUID projectId,
      DatasetSourceType sourceType,
      MultipartFile file,
      String sheetName,
      Integer previewRowCount);

  DatasetImportJobResponse get(CurrentUser user, UUID importJobId);

  DatasetResponse confirm(
      CurrentUser user, UUID importJobId, ConfirmDatasetImportJobRequest request);
}
