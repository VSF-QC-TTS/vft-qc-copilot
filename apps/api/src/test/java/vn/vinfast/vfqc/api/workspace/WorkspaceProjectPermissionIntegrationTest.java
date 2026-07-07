package vn.vinfast.vfqc.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import vn.vinfast.vfqc.api.support.PostgresIntegrationTestBase;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WorkspaceProjectPermissionIntegrationTest extends PostgresIntegrationTestBase {

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;

  @Test
  void workspaceApis_EnforceRoleBoundariesAndHideResourcesFromNonMembers() throws Exception {
    var owner = user("workspace-owner-boundary@vinfast.vn");
    var admin = user("workspace-admin-boundary@vinfast.vn");
    var member = user("workspace-member-boundary@vinfast.vn");
    var viewer = user("workspace-viewer-boundary@vinfast.vn");
    var outsider = user("workspace-outsider-boundary@vinfast.vn");
    var candidate = user("workspace-candidate-boundary@vinfast.vn");
    var workspace = workspace(owner);
    workspaceMember(workspace, admin, WorkspaceRole.ADMIN, owner);
    workspaceMember(workspace, member, WorkspaceRole.MEMBER, owner);
    workspaceMember(workspace, viewer, WorkspaceRole.VIEWER, owner);

    mockMvc
        .perform(get("/api/v1/workspaces/" + workspace.getId()).with(bearer(viewer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentUserRole").value("VIEWER"));
    mockMvc
        .perform(get("/api/v1/workspaces/" + workspace.getId() + "/members").with(bearer(viewer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(4));

    mockMvc
        .perform(
            patch("/api/v1/workspaces/" + workspace.getId())
                .with(bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Member Cannot Rename"))))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            patch("/api/v1/workspaces/" + workspace.getId())
                .with(bearer(viewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Viewer Cannot Rename"))))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            patch("/api/v1/workspaces/" + workspace.getId())
                .with(bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Admin Renamed Workspace"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Admin Renamed Workspace"));

    mockMvc
        .perform(
            post("/api/v1/workspaces/" + workspace.getId() + "/members")
                .with(bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", candidate.getEmail(), "role", "MEMBER"))))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/v1/workspaces/" + workspace.getId()).with(bearer(outsider)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/workspaces/" + workspace.getId() + "/members").with(bearer(outsider)))
        .andExpect(status().isNotFound());
  }

  @Test
  void projectApis_EnforceEditManageBoundariesAndHiddenNotFound() throws Exception {
    var owner = user("project-owner-boundary@vinfast.vn");
    var admin = user("project-admin-boundary@vinfast.vn");
    var member = user("project-member-boundary@vinfast.vn");
    var viewer = user("project-viewer-boundary@vinfast.vn");
    var outsider = user("project-outsider-boundary@vinfast.vn");
    var workspace = workspace(owner);
    workspaceMember(workspace, admin, WorkspaceRole.ADMIN, owner);
    workspaceMember(workspace, member, WorkspaceRole.MEMBER, owner);
    workspaceMember(workspace, viewer, WorkspaceRole.VIEWER, owner);

    var projectId = createProject(owner, workspace.getId(), "Owner Project");

    mockMvc
        .perform(get("/api/v1/projects/" + projectId).with(bearer(viewer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Owner Project"));
    mockMvc
        .perform(get("/api/v1/projects/" + projectId).with(bearer(outsider)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            post("/api/v1/workspaces/" + workspace.getId() + "/projects")
                .with(bearer(viewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Viewer Cannot Create"))))
        .andExpect(status().isForbidden());
    var memberProjectId = createProject(member, workspace.getId(), "Member Project");
    assertThat(memberProjectId).isNotNull();

    mockMvc
        .perform(
            patch("/api/v1/projects/" + projectId)
                .with(bearer(viewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("description", "Viewer cannot edit"))))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            patch("/api/v1/projects/" + projectId)
                .with(bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("description", "Member can edit"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Member can edit"));

    mockMvc
        .perform(post("/api/v1/projects/" + projectId + "/archive").with(bearer(member)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").isNotEmpty());
    mockMvc
        .perform(delete("/api/v1/projects/" + projectId).with(bearer(member)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(delete("/api/v1/projects/" + projectId).with(bearer(admin)))
        .andExpect(status().isNoContent());
  }

  private UUID createProject(vn.vinfast.vfqc.api.user.entity.AppUser actor, UUID workspaceId, String name)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/workspaces/" + workspaceId + "/projects")
                    .with(bearer(actor))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("name", name, "description", "Permission boundary test"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(name))
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).at("/id").asText());
  }

  private String json(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }
}
