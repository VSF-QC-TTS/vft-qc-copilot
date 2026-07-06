package vn.vinfast.vfqc.api.target.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.vinfast.vfqc.api.common.error.ApiException;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.entity.DatasetSchema;
import vn.vinfast.vfqc.api.dataset.entity.DatasetSchemaColumn;
import vn.vinfast.vfqc.api.dataset.enums.DatasetColumnDataType;
import vn.vinfast.vfqc.api.dataset.repository.DatasetSchemaColumnRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetSchemaRepository;
import vn.vinfast.vfqc.api.project.entity.Project;
import vn.vinfast.vfqc.api.secret.entity.Secret;
import vn.vinfast.vfqc.api.secret.service.SecretLookupService;
import vn.vinfast.vfqc.api.target.dto.DatasetMappingDto;
import vn.vinfast.vfqc.api.target.dto.SecretBindingDto;
import vn.vinfast.vfqc.api.target.dto.TargetRequestConfigDto;
import vn.vinfast.vfqc.api.target.enums.SecretBindingLocation;
import vn.vinfast.vfqc.api.target.enums.TargetBodyType;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;

@ExtendWith(MockitoExtension.class)
class ChatbotTargetConfigValidatorTest {

  @Mock private SecretLookupService secretLookupService;
  @Mock private DatasetSchemaRepository schemaRepository;
  @Mock private DatasetSchemaColumnRepository columnRepository;

  @Test
  void validateRequestConfig_SensitiveHeaderWithoutBinding_ThrowsValidationError() {
    // Arrange
    var validator =
        new ChatbotTargetConfigValidator(
            new TargetUrlValidator(), secretLookupService, schemaRepository, columnRepository);
    var config =
        new TargetRequestConfigDto(
            "POST",
            "https://chatbot.example.test/api",
            Map.of("Authorization", "Bearer sk-secret"),
            Map.of(),
            TargetBodyType.JSON,
            "{}",
            List.of(),
            List.of());

    // Act / Assert
    assertThatThrownBy(() -> validator.validateRequestConfig(user(), project(), config))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void validateRequestConfig_ValidSecretBindingAndDatasetMapping_DoesNotThrow() {
    // Arrange
    var project = project();
    var secretId = UUID.randomUUID();
    var schemaId = UUID.randomUUID();
    var validator =
        new ChatbotTargetConfigValidator(
            new TargetUrlValidator(), secretLookupService, schemaRepository, columnRepository);
    var config =
        new TargetRequestConfigDto(
            "POST",
            "https://chatbot.example.test/api",
            Map.of("Authorization", "Bearer ****"),
            Map.of(),
            TargetBodyType.JSON,
            "{\"question\":\"$dataset.question\"}",
            List.of(new SecretBindingDto(SecretBindingLocation.HEADER, "Authorization", secretId)),
            List.of(new DatasetMappingDto(SecretBindingLocation.BODY_JSON, "question", "question")));
    when(secretLookupService.requireActiveForUse(user(), secretId, project.getWorkspaceId()))
        .thenReturn(new Secret());
    when(schemaRepository.findTopByProjectIdOrderByVersionDesc(project.getId()))
        .thenReturn(Optional.of(DatasetSchema.builder().id(schemaId).build()));
    when(columnRepository.findBySchemaIdOrderByOrderIndexAsc(schemaId))
        .thenReturn(
            List.of(
                DatasetSchemaColumn.builder()
                    .name("question")
                    .dataType(DatasetColumnDataType.TEXT)
                    .build()));

    // Act / Assert
    assertThatCode(() -> validator.validateRequestConfig(user(), project, config))
        .doesNotThrowAnyException();
  }

  private CurrentUser user() {
    return new CurrentUser(
        UUID.fromString("00000000-0000-0000-0000-000000000101"),
        "user@vinfast.vn",
        "User",
        SystemRole.USER,
        UserStatus.ACTIVE);
  }

  private Project project() {
    return Project.builder()
        .id(UUID.fromString("00000000-0000-0000-0000-000000000201"))
        .workspaceId(UUID.fromString("00000000-0000-0000-0000-000000000202"))
        .build();
  }
}
