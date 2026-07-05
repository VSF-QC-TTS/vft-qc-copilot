package vn.vinfast.vfqc.api.dataset.service;

import java.util.List;
import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.dto.DatasetSchemaResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetVariableResponse;

public interface DatasetSchemaService {

  /**
   * Loads the latest PROJECT dataset schema for a project.
   *
   * @param user authenticated user with project view permission
   * @param projectId project id
   * @return latest project dataset schema with columns
   */
  DatasetSchemaResponse getProjectSchema(CurrentUser user, UUID projectId);

  /**
   * Lists UI-facing dataset variables generated from the latest project schema.
   *
   * @param user authenticated user with project view permission
   * @param projectId project id
   * @return dataset variables in `$dataset.<columnName>` format
   */
  List<DatasetVariableResponse> variables(CurrentUser user, UUID projectId);
}
