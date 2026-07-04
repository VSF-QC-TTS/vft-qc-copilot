# VinFast QC Copilot - Workspace & Project Implementation

Version: `v0.2`  
Status: Spring Boot 4.0.7-first Implementation Spec  
Applies to: `apps/api`

---

## 1. Purpose

This document explains Workspace, WorkspaceMember, Project and Project Setup Status implementation.

---

## 2. Design Principles

1. Workspace groups projects, members, workspace secrets, providers and datasets.
2. Project is the main container for chatbot target, dataset, evaluator and test runs.
3. User-created workspaces are `TEAM`.
4. `PERSONAL` workspace may be created during onboarding only.
5. Workspace creator becomes `OWNER`.
6. Project creation creates default project dataset schema in the same transaction.
7. Project delete is soft delete.
8. Archived project is not deleted project.
9. Setup status is read-only aggregation for UI wizard.
10. Real run validation belongs to TestRun preflight.

---

## 3. Package Layout

```text
workspace
  controller/WorkspaceController.java
  controller/WorkspaceMemberController.java
  dto/request/CreateWorkspaceRequest.java
  dto/request/UpdateWorkspaceRequest.java
  dto/request/AddWorkspaceMemberRequest.java
  dto/request/UpdateWorkspaceMemberRoleRequest.java
  dto/request/UpdateWorkspaceMemberStatusRequest.java
  dto/response/WorkspaceResponse.java
  dto/response/WorkspaceMemberResponse.java
  dto/response/WorkspaceUserSummaryResponse.java
  entity/Workspace.java
  entity/WorkspaceMember.java
  enums/WorkspaceType.java
  enums/WorkspaceRole.java
  enums/WorkspaceMemberStatus.java
  repository/WorkspaceRepository.java
  repository/WorkspaceMemberRepository.java
  mapper/WorkspaceMapper.java
  mapper/WorkspaceMemberMapper.java
  service/WorkspaceService.java
  service/WorkspaceMemberService.java
  service/WorkspaceLookupService.java
  service/impl/WorkspaceServiceImpl.java
  service/impl/WorkspaceMemberServiceImpl.java
  service/impl/WorkspaceLookupServiceImpl.java

project
  controller/ProjectController.java
  controller/ProjectSetupStatusController.java
  dto/request/CreateProjectRequest.java
  dto/request/UpdateProjectRequest.java
  dto/response/ProjectResponse.java
  dto/response/ProjectSetupStatusResponse.java
  entity/Project.java
  repository/ProjectRepository.java
  mapper/ProjectMapper.java
  service/ProjectService.java
  service/ProjectSetupStatusService.java
  service/ProjectLookupService.java
  service/impl/ProjectServiceImpl.java
  service/impl/ProjectSetupStatusServiceImpl.java
  service/impl/ProjectLookupServiceImpl.java
  assembler/ProjectSetupStatusAssembler.java
```

---

## 4. Entity Rules

### Workspace

```java
@Entity
@Table(name = "workspaces")
public class Workspace {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(name = "name_lookup", nullable = false, length = 120)
  private String nameLookup;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private WorkspaceType type;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
  private OffsetDateTime deletedAt;
}
```

### WorkspaceMember

Rules:

1. Unique `(workspace_id, user_id)`.
2. Active member is required for normal workspace access.
3. Membership rows are not physically deleted in MVP.
4. Role/status changes update `updatedAt`.

### Project

Rules:

1. Unique active project name per workspace using `name_lookup`.
2. Soft delete with `deletedAt`.
3. Archive with `archivedAt`.
4. Deleted projects do not appear in normal lists.
5. Archived projects may appear if requested by filter.

---

## 5. Naming Policy

Use shared `ResourceNameNormalizer` because Spring does not provide project-specific naming rules.

Rules:

1. Trim leading/trailing whitespace.
2. Collapse repeated internal whitespace for lookup.
3. Preserve display casing in `name`.
4. Store normalized `name_lookup`.
5. Enforce DB unique index by scope.
6. Reject blank names, control chars and path separators.

Recommended DB indexes:

```sql
create unique index uk_workspaces_name_lookup_active
on workspaces (name_lookup)
where deleted_at is null;

create unique index uk_projects_workspace_name_lookup_active
on projects (workspace_id, name_lookup)
where deleted_at is null;
```

---

## 6. Service Rules

### Create workspace

Transaction:

1. Normalize name.
2. Validate type is `TEAM` for public create endpoint.
3. Check duplicate through DB constraint/repository.
4. Save workspace.
5. Save owner member.
6. Return response.

### Create project

Transaction:

1. Check workspace permission.
2. Normalize project name.
3. Save project.
4. Create default PROJECT dataset schema.
5. Return response.

Do not create project and schema in separate transactions.

### Update project

Rules:

1. Check project update permission.
2. Normalize new name if changed.
3. Check duplicate.
4. Update metadata only.
5. Do not mutate target/dataset/evaluator/run state here.

### Archive/delete project

Rules:

1. Archive sets `archivedAt`.
2. Delete sets `deletedAt`.
3. Do not cascade hard delete test runs/results/artifacts.
4. Archived/deleted projects cannot start new runs.

---

## 7. Setup Status

Setup status aggregates:

1. Has active dataset current version.
2. Has valid chatbot target.
3. Has valid evaluator latest version.
4. Has provider for LLM judge if needed.
5. Has no blocking missing secret references.

Rules:

1. Read-only.
2. Uses lookup/readiness services from modules.
3. Does not expose secrets.
4. Does not mutate anything.
5. Does not replace TestRun preflight.

---

## 8. Spring Usage

Use:

1. `@RestController` and `@RequestMapping`.
2. Java record DTOs with Jakarta validation.
3. `Pageable` internally for list endpoints.
4. `@Transactional` at service implementations.
5. `@Transactional(readOnly = true)` for query services.
6. MapStruct mappers.
7. `ProblemDetail` via `ApiException`/global handler.

Do not:

1. Inject repositories into controllers.
2. Manually build JSON maps for responses.
3. Implement duplicate checks only in frontend.
4. Hard-delete soft-deletable resources.

---

## 9. Tests

Required tests:

1. Create workspace creates owner membership.
2. Duplicate workspace/project name rejected case-insensitively.
3. Project creation creates default schema in same transaction.
4. Inaccessible project returns 404 when existence hiding applies.
5. Archive does not delete test runs.
6. Setup status does not expose sensitive config.
