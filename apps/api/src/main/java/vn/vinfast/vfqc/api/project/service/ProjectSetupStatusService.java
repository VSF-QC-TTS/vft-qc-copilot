package vn.vinfast.vfqc.api.project.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.project.dto.ProjectSetupStatusResponse;

public interface ProjectSetupStatusService {

  /**
   * Builds the read-only setup readiness summary for a project.
   *
   * @param user authenticated user with project view permission
   * @param projectId project id
   * @return setup status response with counts and blockers
   */
  ProjectSetupStatusResponse get(CurrentUser user, UUID projectId);
}
