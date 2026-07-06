package vn.vinfast.vfqc.api.target.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.target.dto.InspectCurlRequest;
import vn.vinfast.vfqc.api.target.dto.InspectCurlResponse;

public interface CurlInspectionService {

  /**
   * Safely inspects raw cURL text without executing or persisting it.
   *
   * @param user authenticated user with project edit permission
   * @param projectId project owning the future target configuration
   * @param request raw cURL payload
   * @return sanitized preview and safe finding labels
   */
  InspectCurlResponse inspect(CurrentUser user, UUID projectId, InspectCurlRequest request);
}
