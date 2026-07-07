package vn.vinfast.vfqc.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import vn.vinfast.vfqc.api.support.PostgresIntegrationTestBase;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WorkspaceMemberIntegrationTest extends PostgresIntegrationTestBase {

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;

  @Test
  void workspaceResponses_IncludeUserSummariesForCreatorAndMembers() throws Exception {
    var owner = user("workspace-owner@vinfast.vn");
    owner.setDisplayName("Workspace Owner");
    owner.setAvatarUrl("https://example.test/owner.png");
    userRepository.save(owner);
    var member = user("workspace-member@vinfast.vn");
    member.setDisplayName("Workspace Member");
    member.setAvatarUrl("https://example.test/member.png");
    userRepository.save(member);
    var workspace = workspace(owner);

    var addResult =
        mockMvc
            .perform(
                post("/api/v1/workspaces/" + workspace.getId() + "/members")
                    .with(bearer(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("email", member.getEmail(), "role", "MEMBER"))))
            .andExpect(status().isOk())
            .andReturn();

    var addJson = objectMapper.readTree(addResult.getResponse().getContentAsString());
    assertThat(addJson.at("/user/email").asText()).isEqualTo("workspace-member@vinfast.vn");
    assertThat(addJson.at("/user/name").asText()).isEqualTo("Workspace Member");
    assertThat(addJson.at("/user/avatarUrl").asText()).isEqualTo("https://example.test/member.png");
    assertThat(addJson.at("/invitedByUser/email").asText()).isEqualTo("workspace-owner@vinfast.vn");

    var listResult =
        mockMvc
            .perform(get("/api/v1/workspaces/" + workspace.getId() + "/members").with(bearer(owner)))
            .andExpect(status().isOk())
            .andReturn();
    var listJson = objectMapper.readTree(listResult.getResponse().getContentAsString());
    assertThat(listJson.at("/items/0/user/email").asText()).isEqualTo("workspace-owner@vinfast.vn");
    assertThat(listJson.at("/items/1/user/email").asText()).isEqualTo("workspace-member@vinfast.vn");

    var workspacesResult =
        mockMvc.perform(get("/api/v1/workspaces").with(bearer(owner))).andExpect(status().isOk()).andReturn();
    var workspacesJson = objectMapper.readTree(workspacesResult.getResponse().getContentAsString());
    assertThat(workspacesJson.at("/items/0/createdByUser/email").asText())
        .isEqualTo("workspace-owner@vinfast.vn");
  }
}
