package vn.vinfast.vfqc.api.support;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.vinfast.vfqc.api.auth.repository.AuthEmailTokenRepository;
import vn.vinfast.vfqc.api.auth.repository.AuthRefreshTokenRepository;
import vn.vinfast.vfqc.api.auth.service.JwtTokenService;
import vn.vinfast.vfqc.api.dataset.repository.DatasetRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetRowRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetSchemaColumnRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetSchemaRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetVersionRepository;
import vn.vinfast.vfqc.api.project.repository.ProjectRepository;
import vn.vinfast.vfqc.api.provider.repository.LlmProviderRepository;
import vn.vinfast.vfqc.api.provider.repository.LlmProviderTestLogRepository;
import vn.vinfast.vfqc.api.secret.repository.SecretRepository;
import vn.vinfast.vfqc.api.target.repository.ChatbotTargetExampleRepository;
import vn.vinfast.vfqc.api.target.repository.ChatbotTargetRepository;
import vn.vinfast.vfqc.api.user.entity.AppUser;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;
import vn.vinfast.vfqc.api.user.repository.UserRepository;
import vn.vinfast.vfqc.api.workspace.entity.Workspace;
import vn.vinfast.vfqc.api.workspace.entity.WorkspaceMember;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceMemberStatus;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceType;
import vn.vinfast.vfqc.api.workspace.repository.WorkspaceMemberRepository;
import vn.vinfast.vfqc.api.workspace.repository.WorkspaceRepository;

@SpringBootTest(properties = "spring.profiles.active=test")
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class PostgresIntegrationTestBase {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
  }

  @Autowired protected UserRepository userRepository;
  @Autowired protected WorkspaceRepository workspaceRepository;
  @Autowired protected WorkspaceMemberRepository workspaceMemberRepository;
  @Autowired protected SecretRepository secretRepository;
  @Autowired protected LlmProviderRepository providerRepository;
  @Autowired protected LlmProviderTestLogRepository providerTestLogRepository;
  @Autowired protected AuthEmailTokenRepository emailTokenRepository;
  @Autowired protected AuthRefreshTokenRepository refreshTokenRepository;
  @Autowired protected DatasetRowRepository datasetRowRepository;
  @Autowired protected DatasetVersionRepository datasetVersionRepository;
  @Autowired protected DatasetRepository datasetRepository;
  @Autowired protected DatasetSchemaColumnRepository datasetSchemaColumnRepository;
  @Autowired protected DatasetSchemaRepository datasetSchemaRepository;
  @Autowired protected ProjectRepository projectRepository;
  @Autowired protected ChatbotTargetExampleRepository chatbotTargetExampleRepository;
  @Autowired protected ChatbotTargetRepository chatbotTargetRepository;
  @Autowired private JwtTokenService jwtTokenService;

  @AfterEach
  void cleanDatabase() {
    providerTestLogRepository.deleteAll();
    providerRepository.deleteAll();
    secretRepository.deleteAll();
    var datasets = datasetRepository.findAll();
    datasets.forEach(dataset -> dataset.setCurrentVersionId(null));
    datasetRepository.saveAllAndFlush(datasets);
    var targets = chatbotTargetRepository.findAll();
    targets.forEach(target -> target.setLatestExampleId(null));
    chatbotTargetRepository.saveAllAndFlush(targets);
    chatbotTargetExampleRepository.deleteAll();
    chatbotTargetRepository.deleteAll();
    datasetRowRepository.deleteAll();
    datasetVersionRepository.deleteAll();
    datasetRepository.deleteAll();
    datasetSchemaColumnRepository.deleteAll();
    datasetSchemaRepository.deleteAll();
    projectRepository.deleteAll();
    emailTokenRepository.deleteAll();
    refreshTokenRepository.deleteAll();
    workspaceMemberRepository.deleteAll();
    workspaceRepository.deleteAll();
    userRepository.deleteAll();
  }

  protected AppUser user(String email) {
    var now = OffsetDateTime.now();
    return userRepository.save(
        AppUser.builder()
            .email(email)
            .displayName("Test User")
            .passwordHash("{noop}password")
            .systemRole(SystemRole.USER)
            .status(UserStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build());
  }

  protected AppUser platformAdmin(String email) {
    var now = OffsetDateTime.now();
    return userRepository.save(
        AppUser.builder()
            .email(email)
            .displayName("Platform Admin")
            .passwordHash("{noop}password")
            .systemRole(SystemRole.PLATFORM_ADMIN)
            .status(UserStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build());
  }

  protected Workspace workspace(AppUser owner) {
    var now = OffsetDateTime.now();
    var workspace =
        workspaceRepository.save(
            Workspace.builder()
                .name("Workspace " + UUID.randomUUID())
                .nameLookup("workspace " + UUID.randomUUID())
                .type(WorkspaceType.TEAM)
                .createdBy(owner.getId())
                .createdAt(now)
                .updatedAt(now)
                .build());
    workspaceMemberRepository.save(
        WorkspaceMember.builder()
            .workspaceId(workspace.getId())
            .userId(owner.getId())
            .role(WorkspaceRole.OWNER)
            .status(WorkspaceMemberStatus.ACTIVE)
            .invitedBy(owner.getId())
            .joinedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build());
    return workspace;
  }

  protected WorkspaceMember workspaceMember(
      Workspace workspace, AppUser user, WorkspaceRole role, AppUser invitedBy) {
    var now = OffsetDateTime.now();
    return workspaceMemberRepository.save(
        WorkspaceMember.builder()
            .workspaceId(workspace.getId())
            .userId(user.getId())
            .role(role)
            .status(WorkspaceMemberStatus.ACTIVE)
            .invitedBy(invitedBy.getId())
            .joinedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build());
  }

  protected RequestPostProcessor bearer(AppUser user) {
    return request -> {
      request.addHeader("Authorization", "Bearer " + jwtTokenService.issueAccessToken(user));
      return request;
    };
  }
}
