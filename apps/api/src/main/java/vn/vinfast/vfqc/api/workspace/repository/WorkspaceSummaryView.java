package vn.vinfast.vfqc.api.workspace.repository;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceType;

public interface WorkspaceSummaryView {

  UUID getId();

  String getName();

  WorkspaceType getType();

  WorkspaceRole getCurrentUserRole();

  UUID getCreatedBy();

  OffsetDateTime getCreatedAt();

  OffsetDateTime getUpdatedAt();
}
