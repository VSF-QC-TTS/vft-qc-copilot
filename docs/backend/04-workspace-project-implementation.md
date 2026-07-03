# VinFast QC Copilot - Workspace & Project Implementation

Version: `v0.1`  
Status: Backend Implementation Spec Draft  
Applies to: `apps/api` Spring Boot backend  
Aligned with: PRD v0.7, ERD v0.5, API Contract `03-workspace-project-api.md`

---

## 1. Purpose

This document explains how to implement Workspace, WorkspaceMember, Project and Project Setup Status features in the Spring Boot backend.

It is written for AI coding agents and backend developers so generated code follows the same package structure, service style, validation rules and domain invariants.

This file covers:

```text
1. Workspace implementation.
2. Workspace member implementation.
3. Project implementation.
4. Project setup status/readiness implementation.
5. Naming and duplicate validation.
6. Default dataset schema creation when project is created.
7. Permission checks.
8. DTO, mapper, transaction and logging rules.
```

This file does not redefine API request/response contracts. API shapes are defined in `docs/api/03-workspace-project-api.md`.

---

## 2. Design Principles

Workspace and Project code must follow these principles:

```text
1. Workspace groups projects, members, workspace secrets, workspace providers and workspace datasets.
2. Project is the main container for chatbot target, dataset, evaluator and test run configuration.
3. User registration should create a PERSONAL workspace automatically if onboarding is implemented.
4. User-created workspaces in normal UI are TEAM workspaces.
5. Project creation must create the default PROJECT dataset schema in the same transaction.
6. Delete is soft delete using deleted_at.
7. Archived project is not the same as deleted project.
8. Project setup-status is read-only aggregation and must not mutate state.
9. Setup-status helps UI wizard; concrete run validation still belongs to TestRun preflight.
10. Backend validation is authoritative even if frontend mirrors it.
```

Security and localization rules:

```text
1. Never expose resources from workspaces the user cannot access.
2. Use English code/log/error/messageKey values in backend.
3. Do not hardcode Vietnamese user-facing text in service/controller code.
4. Setup-status must not expose secrets, raw cURL, raw requests/responses or artifact content.
```

---

## 3. Package Layout

Recommended package layout:

```text
vn.vinfast.vfqc.api
  workspace
    controller
      WorkspaceController.java
      WorkspaceMemberController.java
    dto
      request
        CreateWorkspaceRequest.java
        UpdateWorkspaceRequest.java
        AddWorkspaceMemberRequest.java
        UpdateWorkspaceMemberRoleRequest.java
        UpdateWorkspaceMemberStatusRequest.java
      response
        WorkspaceResponse.java
        WorkspaceMemberResponse.java
        WorkspaceUserSummaryResponse.java
    entity
      Workspace.java
      WorkspaceMember.java
    enums
      WorkspaceType.java
      WorkspaceRole.java
      WorkspaceMemberStatus.java
    mapper
      WorkspaceMapper.java
      WorkspaceMemberMapper.java
    repository
      WorkspaceRepository.java
      WorkspaceMemberRepository.java
    service
      WorkspaceService.java
      WorkspaceMemberService.java
      WorkspaceLookupService.java
      impl
        WorkspaceServiceImpl.java
        WorkspaceMemberServiceImpl.java
        WorkspaceLookupServiceImpl.java

  project
    controller
      ProjectController.java
      ProjectSetupStatusController.java
    dto
      request
        CreateProjectRequest.java
        UpdateProjectRequest.java
      response
        ProjectResponse.java
        ProjectSetupStatusResponse.java
        ProjectSetupStepsResponse.java
        ProjectSetupCountsResponse.java
        ProjectSetupBlockerResponse.java
    entity
      Project.java
    mapper
      ProjectMapper.java
    repository
      ProjectRepository.java
    service
      ProjectService.java
      ProjectSetupStatusService.java
      ProjectLookupService.java
      impl
        ProjectServiceImpl.java
        ProjectSetupStatusServiceImpl.java
        ProjectLookupServiceImpl.java
    assembler
      ProjectSetupStatusAssembler.java

  common
    naming
      ResourceNameNormalizer.java
      NormalizedName.java
    permission
      WorkspacePermissionResolver.java
      ProjectPermissionResolver.java
```

Rules:

```text
1. Controllers stay thin.
2. Services own use-case logic.
3. Implementations live in `service/impl`.
4. Response mapping uses MapStruct unless response is an aggregate like setup-status.
5. Setup-status may use an assembler because it combines many modules.
```

---

## 4. Entity Implementation

### 4.1. Workspace

ERD table:

```text
workspaces
```

Recommended entity fields:

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "workspaces")
public class Workspace {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "name", nullable = false, length = 120)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  private WorkspaceType type;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
```

Notes:

```text
1. If the actual project uses auditing annotations, use them consistently instead of manually setting timestamps everywhere.
2. Do not expose entity directly from controllers.
3. If normalized name is persisted as a separate column later, update repositories and uniqueness checks accordingly.
```

---

### 4.2. WorkspaceMember

ERD table:

```text
workspace_members
```

Recommended entity fields:

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "workspace_members",
    uniqueConstraints = @UniqueConstraint(name = "uk_workspace_member_user", columnNames = {"workspace_id", "user_id"}))
public class WorkspaceMember {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false, updatable = false)
  private Long id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false)
  private WorkspaceRole role;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private WorkspaceMemberStatus status;

  @Column(name = "invited_by")
  private UUID invitedBy;

  @Column(name = "joined_at")
  private OffsetDateTime joinedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
```

Rules:

```text
1. Permission checks use only ACTIVE members.
2. MVP may create members as ACTIVE directly if invitation email flow is out of scope.
3. Do not physically delete membership rows unless product explicitly requires it.
```

---

### 4.3. Project

ERD table:

```text
projects
```

Recommended entity fields:

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "projects")
public class Project {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @Column(name = "name", nullable = false, length = 120)
  private String name;

  @Column(name = "description", length = 1000)
  private String description;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "archived_at")
  private OffsetDateTime archivedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
```

Rules:

```text
1. Normal project list excludes deleted projects.
2. Archived projects may be visible depending on filters but should not be selected for new runs unless policy allows it.
3. Deleting a project must not hard-delete test runs, results, runner events or artifacts.
```

---

## 5. Naming Implementation

Use one shared naming component for workspace/project and later modules.

Recommended value object:

```java
public record NormalizedName(String displayName, String lookupName) {}
```

Recommended normalizer interface:

```java
public interface ResourceNameNormalizer {

  /**
   * Normalizes a user-provided resource display name for persistence and duplicate checks.
   *
   * @param rawName raw name received from request DTO
   * @param maxLength maximum accepted display name length for the resource type
   * @return normalized display name and lookup name
   * @throws ApiException with VALIDATION_ERROR if the name is blank, too long or contains forbidden characters
   */
  NormalizedName normalizeDisplayName(String rawName, int maxLength);
}
```

Normalization rules from `01-api-conventions.md`:

```text
1. Trim leading/trailing whitespace.
2. Collapse repeated internal whitespace to one space for uniqueness comparison.
3. Preserve user's display casing in stored `name`.
4. Compare uniqueness case-insensitively using normalized lookup name.
5. Reject empty names after trimming.
6. Reject control characters and path separators that can confuse file/export paths.
```

Length limits:

```text
Workspace name: 120
Project name: 120
Description: 1000
```

Repository duplicate checks may use database expressions if no normalized column exists:

```java
@Query("""
    select count(p) > 0
    from Project p
    where p.workspaceId = :workspaceId
      and p.deletedAt is null
      and lower(trim(p.name)) = lower(trim(:displayName))
      and (:excludeProjectId is null or p.id <> :excludeProjectId)
    """)
boolean existsActiveProjectName(
    UUID workspaceId,
    String displayName,
    UUID excludeProjectId);
```

Better long-term option:

```text
Persist `name_lookup` or `normalized_name` column and unique index per scope.
```

Do not let frontend-only validation be the source of truth.

---

## 6. DTO Implementation

Use Java records.

### 6.1. CreateWorkspaceRequest

```java
public record CreateWorkspaceRequest(
    @NotBlank
    @Size(max = 120)
    @Schema(description = "Workspace display name", example = "VinFast QC Team")
    String name,

    @NotNull
    @Schema(description = "Workspace type. MVP user-created workspace must be TEAM.", example = "TEAM")
    WorkspaceType type) {}
```

Rules:

```text
1. MVP accepts TEAM for manual creation.
2. PERSONAL workspace should be created by onboarding flow, not repeatedly by public create endpoint.
```

---

### 6.2. CreateProjectRequest

```java
public record CreateProjectRequest(
    @NotBlank
    @Size(max = 120)
    @Schema(description = "Project display name", example = "VF Sales Chatbot")
    String name,

    @Size(max = 1000)
    @Schema(description = "Optional project description", example = "Evaluation project for sales chatbot")
    String description) {}
```

---

### 6.3. ProjectSetupStatusResponse

Use records matching API contract:

```java
public record ProjectSetupStatusResponse(
    UUID projectId,
    UUID workspaceId,
    ProjectSetupStepsResponse steps,
    ProjectSetupCountsResponse counts,
    boolean canRunSingle,
    boolean canRunComparison,
    List<ProjectSetupBlockerResponse> blockers) {}

public record ProjectSetupStepsResponse(
    boolean hasDataset,
    boolean hasActiveDatasetVersion,
    boolean hasActiveChatbotTarget,
    boolean hasResponseExtraction,
    boolean hasEvaluator,
    boolean hasUsableJudgeProvider) {}

public record ProjectSetupCountsResponse(
    long datasets,
    long activeDatasetVersions,
    long activeChatbotTargets,
    long activeChatbotTargetsWithExtraction,
    long activeEvaluators,
    long usableJudgeProviders,
    long usableBaselineProviders,
    long comparisonCandidates) {}

public record ProjectSetupBlockerResponse(
    String code,
    String step,
    String severity,
    String message,
    String actionEndpoint) {}
```

Message rule:

```text
Default blocker `message` may be English. Frontend can localize using `code`.
Do not hardcode Vietnamese blocker messages in backend.
```

---

## 7. Mapper Rules

Use MapStruct for normal entity-to-response mapping.

Example:

```java
@Mapper(componentModel = "spring")
public interface ProjectMapper {
  ProjectResponse toResponse(Project project);
}
```

For fields not directly on entity, use explicit parameters:

```java
@Mapper(componentModel = "spring")
public interface WorkspaceMapper {

  @Mapping(target = "currentUserRole", source = "currentUserRole")
  WorkspaceResponse toResponse(Workspace workspace, WorkspaceRole currentUserRole);
}
```

Rules:

```text
1. Do not write `toResponse()` methods in service implementations.
2. Use assembler for ProjectSetupStatus because it is an aggregate across modules.
3. Use MapStruct for WorkspaceResponse, WorkspaceMemberResponse and ProjectResponse.
```

---

## 8. Repository Rules

### 8.1. WorkspaceRepository

Recommended methods:

```java
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
  Optional<Workspace> findByIdAndDeletedAtIsNull(UUID id);

  @Query("""
      select w
      from Workspace w
      join WorkspaceMember wm on wm.workspaceId = w.id
      where wm.userId = :userId
        and wm.status = 'ACTIVE'
        and w.deletedAt is null
      order by w.name asc
      """)
  List<Workspace> findVisibleWorkspaces(UUID userId);
}
```

### 8.2. WorkspaceMemberRepository

Recommended methods:

```java
Optional<WorkspaceMember> findByWorkspaceIdAndUserIdAndStatus(
    UUID workspaceId,
    UUID userId,
    WorkspaceMemberStatus status);

boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

List<WorkspaceMember> findByWorkspaceIdAndStatusOrderByCreatedAtAsc(
    UUID workspaceId,
    WorkspaceMemberStatus status);
```

### 8.3. ProjectRepository

Recommended methods:

```java
Optional<Project> findByIdAndDeletedAtIsNull(UUID id);

List<Project> findByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID workspaceId);

boolean existsByWorkspaceIdAndDeletedAtIsNullAndNameIgnoreCase(UUID workspaceId, String name);
```

For robust normalized comparison, prefer custom query or normalized column as described in naming section.

---

## 9. Workspace Service Implementation

### 9.1. WorkspaceService interface

```java
public interface WorkspaceService {

  /**
   * Lists workspaces visible to the current authenticated user.
   *
   * @param currentUser authenticated user requesting workspace list
   * @param query optional search/filter/pagination parameters
   * @return collection response containing workspace summaries and current user's role
   */
  CollectionResponse<WorkspaceResponse> listWorkspaces(CurrentUser currentUser, WorkspaceQuery query);

  /**
   * Creates a TEAM workspace and adds the creator as OWNER in the same transaction.
   *
   * @param request workspace creation payload
   * @param currentUser authenticated user creating the workspace
   * @return created workspace response
   * @throws ApiException with VALIDATION_ERROR if type is not allowed or name is invalid
   * @throws ApiException with WORKSPACE_NAME_ALREADY_EXISTS if normalized name conflicts in the configured scope
   */
  WorkspaceResponse createWorkspace(CreateWorkspaceRequest request, CurrentUser currentUser);

  /**
   * Returns one workspace if the current user can view it.
   *
   * @param workspaceId workspace id to read
   * @param currentUser authenticated user requesting the workspace
   * @return workspace response with current user's role
   * @throws ApiException with WORKSPACE_NOT_FOUND if missing or hidden by access policy
   */
  WorkspaceResponse getWorkspace(UUID workspaceId, CurrentUser currentUser);

  /**
   * Updates workspace metadata such as display name.
   *
   * @param workspaceId workspace id to update
   * @param request partial update payload
   * @param currentUser authenticated user performing the update
   * @return updated workspace response
   */
  WorkspaceResponse updateWorkspace(UUID workspaceId, UpdateWorkspaceRequest request, CurrentUser currentUser);
}
```

### 9.2. Create workspace implementation

Implementation flow:

```text
1. Validate current user is ACTIVE.
2. Validate request.type is TEAM for manual create endpoint.
3. Normalize workspace name.
4. Check duplicate policy.
5. Create Workspace.
6. Create WorkspaceMember with role OWNER and status ACTIVE.
7. Commit both in one transaction.
8. Return WorkspaceResponse.
```

Transaction:

```text
@Transactional
```

Logging:

```java
log.info("Creating workspace: requestedBy={}", currentUser.id());
log.info("Workspace created: workspaceId={}, ownerUserId={}", workspace.getId(), currentUser.id());
```

Do not log all request fields if future fields might contain sensitive data.

---

## 10. Workspace Member Service Implementation

### 10.1. WorkspaceMemberService interface

```java
public interface WorkspaceMemberService {

  /**
   * Lists members of a workspace visible to the current user.
   *
   * @param workspaceId workspace id whose members should be listed
   * @param currentUser authenticated user performing the read
   * @return members in the workspace with user summaries
   */
  CollectionResponse<WorkspaceMemberResponse> listMembers(UUID workspaceId, CurrentUser currentUser);

  /**
   * Adds a user to the workspace or activates an invited user in MVP mode.
   *
   * @param workspaceId workspace id to modify
   * @param request target user and role to add
   * @param currentUser authenticated user performing the action
   * @return created or updated workspace member response
   * @throws ApiException with WORKSPACE_MEMBER_ALREADY_EXISTS if active membership already exists
   */
  WorkspaceMemberResponse addMember(UUID workspaceId, AddWorkspaceMemberRequest request, CurrentUser currentUser);

  /**
   * Updates a workspace member role.
   *
   * @param workspaceId workspace id
   * @param memberId workspace member id to update
   * @param request new role payload
   * @param currentUser authenticated user performing the action
   * @return updated member response
   */
  WorkspaceMemberResponse updateMemberRole(UUID workspaceId, Long memberId, UpdateWorkspaceMemberRoleRequest request, CurrentUser currentUser);
}
```

Rules:

```text
1. OWNER/ADMIN can manage members.
2. MEMBER/VIEWER cannot manage members.
3. Prevent removing/demoting the last OWNER unless product explicitly allows ownership transfer flow.
4. MVP may not implement email invitation sending yet.
5. If invitation email is not implemented, add member as ACTIVE only when user already exists.
```

---

## 11. Project Service Implementation

### 11.1. ProjectService interface

```java
public interface ProjectService {

  /**
   * Lists non-deleted projects inside a workspace visible to the current user.
   *
   * @param workspaceId workspace id that owns the projects
   * @param query search, sort and pagination parameters
   * @param currentUser authenticated user performing the read
   * @return project collection response
   */
  CollectionResponse<ProjectResponse> listProjects(UUID workspaceId, ProjectQuery query, CurrentUser currentUser);

  /**
   * Creates a new project and initializes its default PROJECT dataset schema in one transaction.
   *
   * @param workspaceId workspace id that will own the project
   * @param request project creation payload
   * @param currentUser authenticated user performing the action
   * @return created project response
   * @throws ApiException with PERMISSION_DENIED if user cannot create projects in the workspace
   * @throws ApiException with PROJECT_NAME_ALREADY_EXISTS if normalized project name conflicts in the workspace
   */
  ProjectResponse createProject(UUID workspaceId, CreateProjectRequest request, CurrentUser currentUser);

  /**
   * Returns project detail if the current user has project view permission.
   *
   * @param projectId project id to read
   * @param currentUser authenticated user performing the read
   * @return project response
   * @throws ApiException with PROJECT_NOT_FOUND if project is missing or hidden by access policy
   */
  ProjectResponse getProject(UUID projectId, CurrentUser currentUser);

  /**
   * Updates project metadata without mutating historical test run snapshots.
   *
   * @param projectId project id to update
   * @param request partial project metadata update
   * @param currentUser authenticated user performing the update
   * @return updated project response
   */
  ProjectResponse updateProject(UUID projectId, UpdateProjectRequest request, CurrentUser currentUser);

  /**
   * Soft-deletes a project when it has no active running/cancelling test runs.
   *
   * @param projectId project id to delete
   * @param currentUser authenticated user performing the delete
   */
  void deleteProject(UUID projectId, CurrentUser currentUser);
}
```

---

### 11.2. Create project implementation

Implementation flow:

```text
1. Require CREATE_PROJECT permission on workspace.
2. Normalize project name.
3. Check duplicate active project name inside workspace.
4. Persist Project.
5. Create default PROJECT dataset schema with columns `question` and `expected` in the same transaction.
6. Return ProjectResponse.
```

Important transaction invariant:

```text
Project and default dataset schema must be created atomically.
If schema creation fails, project creation must roll back.
```

Recommended cross-module dependency:

```text
ProjectServiceImpl -> DatasetSchemaInitializationService
```

Do not directly duplicate dataset schema creation logic inside ProjectService if dataset module already owns schema rules.

Example:

```java
@Slf4j
@Service
@RequiredArgsConstructor
class ProjectServiceImpl implements ProjectService {

  private final ProjectRepository projectRepository;
  private final WorkspacePermissionResolver workspacePermissionResolver;
  private final ResourceNameNormalizer nameNormalizer;
  private final DatasetSchemaInitializationService datasetSchemaInitializationService;
  private final ProjectMapper projectMapper;
  private final Clock clock;

  @Override
  @Transactional
  public ProjectResponse createProject(UUID workspaceId, CreateProjectRequest request, CurrentUser currentUser) {
    workspacePermissionResolver.require(currentUser, workspaceId, PermissionAction.CREATE_PROJECT);

    NormalizedName name = nameNormalizer.normalizeDisplayName(request.name(), 120);
    if (projectRepository.existsActiveProjectName(workspaceId, name.lookupName(), null)) {
      throw ErrorCode.PROJECT_NAME_ALREADY_EXISTS.exception();
    }

    log.info("Creating project: workspaceId={}, requestedBy={}", workspaceId, currentUser.id());

    Project project = Project.builder()
        .workspaceId(workspaceId)
        .name(name.displayName())
        .description(trimNullable(request.description()))
        .createdBy(currentUser.id())
        .createdAt(OffsetDateTime.now(clock))
        .updatedAt(OffsetDateTime.now(clock))
        .build();

    project = projectRepository.save(project);
    datasetSchemaInitializationService.createDefaultProjectSchema(project.getId(), currentUser.id());

    log.info("Project created: projectId={}, workspaceId={}", project.getId(), workspaceId);
    return projectMapper.toResponse(project);
  }
}
```

Notes:

```text
1. The example is illustrative. If the project uses auditing fields, do not manually set timestamps.
2. Do not call external services inside the transaction.
3. Do not publish Redis commands during project creation.
```

---

### 11.3. Update project implementation

Rules:

```text
1. Require UPDATE_PROJECT permission.
2. Reject empty PATCH body.
3. If name is present, normalize and check duplicate excluding current project id.
4. If description is present, trim and validate max length.
5. Do not modify dataset schema or existing test run snapshots.
6. Return updated ProjectResponse.
```

Transaction:

```text
@Transactional
```

---

### 11.4. Delete project implementation

Rules:

```text
1. Require DELETE_PROJECT permission.
2. Load project or return PROJECT_NOT_FOUND.
3. Check active test runs in PENDING/RUNNING/CANCELLING.
4. If active runs exist, return PROJECT_HAS_ACTIVE_RUNS.
5. Set deleted_at.
6. Do not hard-delete dependent results/artifacts/events.
```

Recommended cross-module dependency:

```text
ProjectServiceImpl -> TestRunReadinessQueryService or TestRunQueryService
```

Do not inject TestRunRepository directly if a query service already exists.

---

## 12. Project Setup Status Implementation

Endpoint:

```http
GET /api/v1/projects/{projectId}/setup-status
```

### 12.1. Service interface

```java
public interface ProjectSetupStatusService {

  /**
   * Computes a read-only setup readiness snapshot for the project setup wizard.
   *
   * @param projectId project id to inspect
   * @param currentUser authenticated user requesting the status
   * @return setup status showing coarse steps, counts, run readiness and blockers
   * @throws ApiException with PROJECT_NOT_FOUND if project is missing or hidden by access policy
   */
  ProjectSetupStatusResponse getSetupStatus(UUID projectId, CurrentUser currentUser);
}
```

### 12.2. Implementation flow

```text
1. Require VIEW_PROJECT permission.
2. Read dataset readiness.
3. Read chatbot target readiness.
4. Read evaluator readiness.
5. Read usable provider readiness for current user/workspace.
6. Compute comparisonCandidates.
7. Compute canRunSingle and canRunComparison.
8. Build blockers.
9. Return sanitized aggregate response.
```

Transaction:

```text
@Transactional(readOnly = true)
```

Logging:

```java
log.debug("Computing project setup status: projectId={}, requestedBy={}", projectId, currentUser.id());
log.info("Project setup status computed: projectId={}, canRunSingle={}, canRunComparison={}, blockerCount={}",
    projectId, response.canRunSingle(), response.canRunComparison(), response.blockers().size());
```

Use `debug` for routine start log to avoid noise.

---

### 12.3. Recommended query services

Avoid injecting many foreign repositories directly into `ProjectSetupStatusServiceImpl`.

Use read-only query services:

```text
DatasetReadinessQueryService
ChatbotTargetReadinessQueryService
EvaluatorReadinessQueryService
ProviderReadinessQueryService
```

Recommended readiness records:

```java
public record DatasetReadiness(
    long datasets,
    long activeDatasetVersions,
    boolean hasDataset,
    boolean hasActiveDatasetVersion,
    boolean hasNonEmptyActiveDatasetVersion) {}

public record ChatbotTargetReadiness(
    long activeChatbotTargets,
    long activeChatbotTargetsWithExtraction,
    boolean hasActiveChatbotTarget,
    boolean hasResponseExtraction) {}

public record EvaluatorReadiness(
    long activeEvaluators,
    boolean hasEvaluator,
    boolean requiresJudgeProvider) {}

public record ProviderReadiness(
    long usableJudgeProviders,
    long usableBaselineProviders,
    boolean hasUsableJudgeProvider) {}
```

Rules:

```text
1. Query services must not expose secret values.
2. Query services should return counts/booleans only.
3. Query services should be transactional read-only.
4. Query services should apply scope and current user visibility rules.
```

---

### 12.4. Readiness computation

Definitions:

```text
hasDataset:
  Project has at least one non-deleted dataset.

hasActiveDatasetVersion:
  At least one project dataset has current ACTIVE DatasetVersion with row_count > 0.

hasActiveChatbotTarget:
  Project has at least one ACTIVE chatbot target.

hasResponseExtraction:
  At least one ACTIVE chatbot target has a valid response_extraction_config_json.mainAnswerPath.

hasEvaluator:
  Project has at least one non-deleted evaluator with at least one version.

hasUsableJudgeProvider:
  Current user can use at least one ACTIVE personal or workspace LLM provider.
```

Single run readiness:

```text
canRunSingle =
  hasActiveDatasetVersion
  && hasActiveChatbotTarget
  && hasResponseExtraction
  && hasEvaluator
  && judgeProviderCondition
```

Judge provider condition:

```text
1. If project evaluators are rule-only, judge provider is not required for single run.
2. If selected/latest evaluator requires LLM Judge, at least one usable judge provider is required.
3. setup-status is approximate because concrete evaluator selection belongs to preflight.
```

Comparison readiness:

```text
comparisonCandidates = activeChatbotTargetsWithExtraction + usableBaselineProviders

canRunComparison =
  hasActiveDatasetVersion
  && hasEvaluator
  && comparisonCandidates >= 2
  && judgeProviderConditionForComparison
```

Rules:

```text
1. setup-status does not replace preflight.
2. setup-status should be fast enough for page load/wizard refresh.
3. setup-status must not validate every run option.
4. setup-status must not lock dataset/evaluator/target versions.
```

---

### 12.5. Blocker codes

Supported blocker codes:

```text
PROJECT_SETUP_NO_DATASET
PROJECT_SETUP_NO_ACTIVE_DATASET_VERSION
PROJECT_SETUP_EMPTY_DATASET_VERSION
PROJECT_SETUP_NO_ACTIVE_CHATBOT_TARGET
PROJECT_SETUP_RESPONSE_EXTRACTION_MISSING
PROJECT_SETUP_NO_EVALUATOR
PROJECT_SETUP_JUDGE_PROVIDER_REQUIRED
PROJECT_SETUP_COMPARISON_TARGETS_REQUIRED
PROJECT_SETUP_PROVIDER_ERROR
```

Recommended blocker builder:

```java
public final class ProjectSetupBlockers {
  public static ProjectSetupBlockerResponse noDataset(UUID projectId) {
    return new ProjectSetupBlockerResponse(
        "PROJECT_SETUP_NO_DATASET",
        "DATASET",
        "ERROR",
        "Create or import a dataset before starting a run.",
        "/api/v1/projects/" + projectId + "/datasets");
  }
}
```

Rules:

```text
1. `code` is the stable frontend localization key.
2. `message` is English fallback.
3. `step` should be stable: DATASET, CHATBOT_TARGET, EVALUATOR, PROVIDER, COMPARISON.
4. `severity` values: ERROR, WARNING, INFO.
5. `actionEndpoint` should point to the closest useful API/resource endpoint.
```

---

## 13. Controller Implementation

### 13.1. WorkspaceController

Example:

```java
@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
@Tag(name = "Workspaces", description = "Workspace management APIs")
class WorkspaceController {

  private final WorkspaceService workspaceService;

  @Operation(summary = "List visible workspaces")
  @GetMapping
  public CollectionResponse<WorkspaceResponse> listWorkspaces(
      WorkspaceQuery query,
      @AuthenticationPrincipal SecurityUserPrincipal principal) {
    return workspaceService.listWorkspaces(principal.currentUser(), query);
  }

  @Operation(summary = "Create a team workspace")
  @PostMapping
  public ResponseEntity<WorkspaceResponse> createWorkspace(
      @Valid @RequestBody CreateWorkspaceRequest request,
      @AuthenticationPrincipal SecurityUserPrincipal principal) {
    WorkspaceResponse response = workspaceService.createWorkspace(request, principal.currentUser());
    return ResponseEntity.created(URI.create("/api/v1/workspaces/" + response.id())).body(response);
  }
}
```

### 13.2. ProjectController

Example:

```java
@RestController
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Project management APIs")
class ProjectController {

  private final ProjectService projectService;

  @Operation(summary = "Create project in workspace")
  @PostMapping("/api/v1/workspaces/{workspaceId}/projects")
  public ResponseEntity<ProjectResponse> createProject(
      @PathVariable UUID workspaceId,
      @Valid @RequestBody CreateProjectRequest request,
      @AuthenticationPrincipal SecurityUserPrincipal principal) {
    ProjectResponse response = projectService.createProject(workspaceId, request, principal.currentUser());
    return ResponseEntity.created(URI.create("/api/v1/projects/" + response.id())).body(response);
  }
}
```

### 13.3. ProjectSetupStatusController

```java
@RestController
@RequestMapping("/api/v1/projects/{projectId}/setup-status")
@RequiredArgsConstructor
@Tag(name = "Project Setup", description = "Project setup readiness APIs")
class ProjectSetupStatusController {

  private final ProjectSetupStatusService projectSetupStatusService;

  @Operation(summary = "Get project setup readiness")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Setup status returned"),
      @ApiResponse(responseCode = "404", description = "Project not found or hidden")
  })
  @GetMapping
  public ProjectSetupStatusResponse getSetupStatus(
      @PathVariable UUID projectId,
      @AuthenticationPrincipal SecurityUserPrincipal principal) {
    return projectSetupStatusService.getSetupStatus(projectId, principal.currentUser());
  }
}
```

Controller rules:

```text
1. Keep controllers thin.
2. Use @Valid request records.
3. Add @Operation and @ApiResponses.
4. Do not call repositories directly.
5. Do not compute setup-status in controller.
```

---

## 14. Permission Rules

Workspace APIs:

```text
List visible workspaces:
  Authenticated ACTIVE user.

Create workspace:
  Authenticated ACTIVE user.

View workspace:
  Project/Workspace VIEW permission.

Update workspace:
  OWNER / ADMIN / PLATFORM_ADMIN.

Delete workspace:
  OWNER / PLATFORM_ADMIN unless policy expands it.

Manage members:
  OWNER / ADMIN / PLATFORM_ADMIN.
```

Project APIs:

```text
List projects:
  Workspace VIEW.

Create project:
  OWNER / ADMIN / MEMBER / PLATFORM_ADMIN.

View project:
  Workspace VIEW.

Update project:
  OWNER / ADMIN / MEMBER / PLATFORM_ADMIN.

Delete project:
  OWNER / ADMIN / MEMBER / PLATFORM_ADMIN, unless product tightens it later.

Setup status:
  Workspace VIEW.
```

Implementation rule:

```text
Use WorkspacePermissionResolver and ProjectPermissionResolver. Do not duplicate role checks in every service method.
```

---

## 15. Error Codes

Workspace/project implementation should use these stable codes:

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
VALIDATION_ERROR
PERMISSION_DENIED
WORKSPACE_NOT_FOUND
WORKSPACE_NAME_ALREADY_EXISTS
WORKSPACE_MEMBER_NOT_FOUND
WORKSPACE_MEMBER_ALREADY_EXISTS
WORKSPACE_LAST_OWNER_REQUIRED
PROJECT_NOT_FOUND
PROJECT_NAME_ALREADY_EXISTS
PROJECT_HAS_ACTIVE_RUNS
INVALID_STATE_TRANSITION
INTERNAL_ERROR
```

Rules:

```text
1. Duplicate names return 409 Conflict.
2. Missing or hidden resource returns 404 where API contract requires hiding.
3. Role/action denial returns 403 when resource visibility is already established.
4. Empty PATCH body returns 400 VALIDATION_ERROR.
```

---

## 16. Logging Rules

Use `@Slf4j` on service implementations.

Recommended logs:

```java
log.info("Creating workspace: requestedBy={}", currentUser.id());
log.info("Workspace created: workspaceId={}, ownerUserId={}", workspaceId, currentUser.id());
log.info("Creating project: workspaceId={}, requestedBy={}", workspaceId, currentUser.id());
log.info("Project created: projectId={}, workspaceId={}", projectId, workspaceId);
log.info("Project deleted: projectId={}, deletedBy={}", projectId, currentUser.id());
log.warn("Project setup blocked: projectId={}, blockers={}", projectId, blockerCodes);
```

Avoid:

```java
log.info("Request body: {}", request);
log.info("All workspace fields: {}", workspace);
```

Setup-status logging:

```text
1. Use debug for routine computation start.
2. Use info for computed status only if it helps product debugging and is not too noisy.
3. Use warn when blockers prevent run readiness after user attempts a run or setup action.
```

---

## 17. Transaction Rules

Recommended transaction annotations:

```text
WorkspaceService.listWorkspaces: @Transactional(readOnly = true)
WorkspaceService.createWorkspace: @Transactional
WorkspaceService.getWorkspace: @Transactional(readOnly = true)
WorkspaceService.updateWorkspace: @Transactional
WorkspaceMemberService.listMembers: @Transactional(readOnly = true)
WorkspaceMemberService.addMember: @Transactional
WorkspaceMemberService.updateMemberRole: @Transactional
ProjectService.listProjects: @Transactional(readOnly = true)
ProjectService.createProject: @Transactional
ProjectService.getProject: @Transactional(readOnly = true)
ProjectService.updateProject: @Transactional
ProjectService.deleteProject: @Transactional
ProjectSetupStatusService.getSetupStatus: @Transactional(readOnly = true)
```

Rules:

```text
1. Do not put @Transactional on controllers.
2. Project creation and default dataset schema initialization must be in the same transaction.
3. Do not call external services while holding the transaction.
4. No Redis command is needed for workspace/project setup-status.
```

---

## 18. Async and Redis Notes

Workspace/project APIs are normal synchronous CRUD/readiness APIs.

Rules:

```text
1. Do not use @Async for create workspace/project.
2. Do not use Redis as source of truth for workspace/project state.
3. setup-status should query PostgreSQL/read models, not Redis.
4. Redis Streams are relevant later for TestRun orchestration, not project creation.
```

Possible future async work:

```text
1. Sending invitation emails.
2. Audit/event notification.
3. Large workspace export.
```

If invitation email becomes async later, persist the membership/invitation state first, then dispatch async notification outside the core transaction boundary.

---

## 19. Testing Checklist

Workspace tests:

```text
1. Create TEAM workspace creates OWNER membership in same transaction.
2. PERSONAL workspace cannot be repeatedly created through normal create endpoint.
3. Workspace name is trimmed and duplicate checked case-insensitively.
4. OWNER/ADMIN can update workspace.
5. MEMBER/VIEWER cannot update workspace.
6. User outside workspace cannot view workspace.
7. Last OWNER protection works if role update/removal is implemented.
```

Project tests:

```text
1. MEMBER can create project in workspace.
2. VIEWER cannot create project.
3. Project creation creates default PROJECT dataset schema with question/expected.
4. If default schema creation fails, project insert rolls back.
5. Project name duplicate in same workspace returns PROJECT_NAME_ALREADY_EXISTS.
6. Same project name in different workspace is allowed.
7. Update project name checks duplicate excluding current project.
8. Delete project soft-deletes when no active runs exist.
9. Delete project rejects when active runs exist.
10. Hidden project returns PROJECT_NOT_FOUND.
```

Setup-status tests:

```text
1. New project with no dataset returns dataset blocker.
2. Dataset with no ACTIVE non-empty version blocks run.
3. Active target without response extraction blocks run.
4. Rule-only evaluator can run without judge provider.
5. LLM Judge evaluator requires usable judge provider.
6. canRunComparison requires at least two comparison candidates.
7. Setup-status does not expose secrets or raw target config.
8. VIEWER can read setup-status.
9. User outside workspace gets PROJECT_NOT_FOUND.
```

---

## 20. AI Code Generation Checklist

Before generating workspace/project code, AI agents must check:

```text
1. Existing root package.
2. Existing ErrorCode and ProblemDetail conventions.
3. Existing CurrentUser injection style.
4. Existing audit timestamp strategy.
5. Whether normalized name column exists.
6. Whether dataset module already exposes DatasetSchemaInitializationService.
7. Whether query services exist for setup-status dependencies.
```

When generating code:

```text
1. Use service interfaces with Javadoc.
2. Put implementations in service/impl.
3. Use @Slf4j on implementations.
4. Use @Transactional on service implementation methods.
5. Use records for DTOs.
6. Use validation and @Schema examples on request records.
7. Use MapStruct for normal responses.
8. Use assembler for setup-status aggregate response.
9. Use permission resolvers, not scattered role checks.
10. Keep logs safe and not noisy.
11. Do not hardcode Vietnamese text.
12. Do not use Redis or @Async for these synchronous APIs.
```
