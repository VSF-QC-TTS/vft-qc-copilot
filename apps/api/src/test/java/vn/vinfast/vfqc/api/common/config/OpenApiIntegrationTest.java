package vn.vinfast.vfqc.api.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import vn.vinfast.vfqc.api.support.PostgresIntegrationTestBase;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OpenApiIntegrationTest extends PostgresIntegrationTestBase {

  private final MockMvc mockMvc;

  @Test
  void apiDocs_ExposeConfiguredMetadataSecuritySchemeAndCorePaths() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("VinFast QC Copilot API"))
        .andExpect(jsonPath("$.info.version").value("v1"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/test-runs']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/test-runs/{testRunId}/events']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/export-jobs/{jobId}/download']").exists())
        .andExpect(jsonPath("$.paths['/internal/runner/events']").exists());
  }
}
