package vn.vinfast.vfqc.api.support;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.vinfast.vfqc.api.auth.repository.AuthEmailTokenRepository;
import vn.vinfast.vfqc.api.auth.repository.AuthRefreshTokenRepository;
import vn.vinfast.vfqc.api.auth.service.JwtTokenService;
import vn.vinfast.vfqc.api.provider.repository.LlmProviderRepository;
import vn.vinfast.vfqc.api.provider.repository.LlmProviderTestLogRepository;
import vn.vinfast.vfqc.api.secret.repository.SecretRepository;
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

@SpringBootTest(
    properties = {
      "spring.profiles.active=test",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.open-in-view=false",
      "spring.flyway.enabled=true",
      "spring.data.redis.repositories.enabled=false",
      "vfqc.security.jwt.secret-key=dev-only-change-me-dev-only-change-me",
      "vfqc.security.encryption-key=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      "vfqc.client.base-url=http://localhost",
      "vfqc.server.base-url=http://localhost",
      "vfqc.admin.seed.email=",
      "vfqc.admin.seed.password=",
      "vfqc.mail.enabled=false"
    })
@AutoConfigureMockMvc
@Testcontainers
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
  @Autowired private JwtTokenService jwtTokenService;

  @AfterEach
  void cleanDatabase() {
    providerTestLogRepository.deleteAll();
    providerRepository.deleteAll();
    secretRepository.deleteAll();
    emailTokenRepository.deleteAll();
    refreshTokenRepository.deleteAll();
    workspaceMemberRepository.deleteAll();
    workspaceRepository.deleteAll();
    userRepository.deleteAll();
  }

  protected AppUser user(String email) {
    var now = OffsetDateTime.now();
    return userRepository.save(
        new AppUser()
            .setEmail(email)
            .setDisplayName("Test User")
            .setPasswordHash("{noop}password")
            .setSystemRole(SystemRole.USER)
            .setStatus(UserStatus.ACTIVE)
            .setCreatedAt(now)
            .setUpdatedAt(now));
  }

  protected Workspace workspace(AppUser owner) {
    var now = OffsetDateTime.now();
    var workspace =
        workspaceRepository.save(
            new Workspace()
                .setName("Workspace " + UUID.randomUUID())
                .setNameLookup("workspace " + UUID.randomUUID())
                .setType(WorkspaceType.TEAM)
                .setCreatedBy(owner.getId())
                .setCreatedAt(now)
                .setUpdatedAt(now));
    workspaceMemberRepository.save(
        new WorkspaceMember()
            .setWorkspaceId(workspace.getId())
            .setUserId(owner.getId())
            .setRole(WorkspaceRole.OWNER)
            .setStatus(WorkspaceMemberStatus.ACTIVE)
            .setInvitedBy(owner.getId())
            .setJoinedAt(now)
            .setCreatedAt(now)
            .setUpdatedAt(now));
    return workspace;
  }

  protected RequestPostProcessor bearer(AppUser user) {
    return request -> {
      request.addHeader("Authorization", "Bearer " + jwtTokenService.issueAccessToken(user));
      return request;
    };
  }
}
