package vn.vinfast.vfqc.api.dataset.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.dto.DatasetVersionResponse;

public interface DatasetDraftService {

  /**
   * Creates a new editable DRAFT version, copying ACTIVE rows when an active version exists.
   *
   * @param user authenticated user with edit permission
   * @param datasetId dataset id
   * @return new draft version summary
   */
  DatasetVersionResponse createDraft(CurrentUser user, UUID datasetId);

  /**
   * Publishes a DRAFT dataset version, archiving the old ACTIVE version.
   *
   * @param user authenticated user with edit permission
   * @param versionId draft version id
   * @return published version summary
   */
  DatasetVersionResponse publish(CurrentUser user, UUID versionId);

  /**
   * Discards a DRAFT dataset version without deleting rows.
   *
   * @param user authenticated user with edit permission
   * @param versionId draft version id
   * @return discarded version summary
   */
  DatasetVersionResponse discard(CurrentUser user, UUID versionId);
}
