package vn.vinfast.vfqc.api.dataset.service;

import java.util.UUID;

public interface DatasetSchemaBootstrapService {

  /**
   * Creates the default PROJECT dataset schema for a newly created project.
   *
   * @param projectId project id that owns the schema
   * @param createdBy user id that created the project/schema
   */
  void createDefaultProjectSchema(UUID projectId, UUID createdBy);
}
