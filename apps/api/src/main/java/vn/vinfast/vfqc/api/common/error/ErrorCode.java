package vn.vinfast.vfqc.api.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed", "error.validation"),
  AUTHENTICATION_REQUIRED(
      HttpStatus.UNAUTHORIZED, "Authentication is required", "error.auth.required"),
  INVALID_CREDENTIALS(
      HttpStatus.UNAUTHORIZED, "Invalid credentials", "error.auth.invalidCredentials"),
  REFRESH_TOKEN_MISSING(
      HttpStatus.UNAUTHORIZED, "Refresh token is missing", "error.auth.refreshMissing"),
  REFRESH_TOKEN_INVALID(
      HttpStatus.UNAUTHORIZED, "Refresh token is invalid", "error.auth.refreshInvalid"),
  REFRESH_TOKEN_EXPIRED(
      HttpStatus.UNAUTHORIZED, "Refresh token is expired", "error.auth.refreshExpired"),
  REGISTRATION_DISABLED(
      HttpStatus.FORBIDDEN, "Self registration is disabled", "error.auth.registrationDisabled"),
  EMAIL_NOT_VERIFIED(
      HttpStatus.FORBIDDEN, "Email verification is required", "error.auth.emailNotVerified"),
  EMAIL_TOKEN_MISSING(
      HttpStatus.BAD_REQUEST, "Email token is missing", "error.auth.emailTokenMissing"),
  EMAIL_TOKEN_INVALID(
      HttpStatus.BAD_REQUEST, "Email token is invalid", "error.auth.emailTokenInvalid"),
  EMAIL_TOKEN_EXPIRED(
      HttpStatus.BAD_REQUEST, "Email token is expired", "error.auth.emailTokenExpired"),
  USER_DISABLED(HttpStatus.FORBIDDEN, "User is disabled", "error.user.disabled"),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied", "error.accessDenied"),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found", "error.resource.notFound"),
  DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Resource already exists", "error.resource.duplicate"),
  INVALID_RESOURCE_NAME(
      HttpStatus.BAD_REQUEST, "Resource name is invalid", "error.resource.invalidName"),
  WORKSPACE_NAME_ALREADY_EXISTS(
      HttpStatus.CONFLICT, "Workspace name already exists", "error.workspace.nameAlreadyExists"),
  PROJECT_NAME_ALREADY_EXISTS(
      HttpStatus.CONFLICT, "Project name already exists", "error.project.nameAlreadyExists"),
  DATASET_NAME_ALREADY_EXISTS(
      HttpStatus.CONFLICT, "Dataset name already exists", "error.dataset.nameAlreadyExists"),
  DATASET_DRAFT_ALREADY_EXISTS(
      HttpStatus.CONFLICT,
      "Dataset already has a draft version",
      "error.dataset.draftAlreadyExists"),
  DATASET_VERSION_NOT_EDITABLE(
      HttpStatus.CONFLICT, "Dataset version is not editable", "error.dataset.versionNotEditable"),
  DATASET_REQUIRED_COLUMN_MISSING(
      HttpStatus.BAD_REQUEST,
      "Required dataset column is missing",
      "error.dataset.requiredColumnMissing"),
  DATASET_UNKNOWN_COLUMN(
      HttpStatus.BAD_REQUEST,
      "Dataset row contains an unknown column",
      "error.dataset.unknownColumn"),
  DATASET_IMPORT_MAPPING_INVALID(
      HttpStatus.BAD_REQUEST,
      "Dataset import mapping is invalid",
      "error.dataset.import.mappingInvalid"),
  DATASET_IMPORT_JOB_INVALID_STATE(
      HttpStatus.CONFLICT,
      "Dataset import job state does not allow this operation",
      "error.dataset.import.invalidState"),
  INVALID_STATE(
      HttpStatus.CONFLICT, "Resource state does not allow this operation", "error.state.invalid"),
  SECRET_SCOPE_MISMATCH(
      HttpStatus.BAD_REQUEST, "Secret scope is not compatible", "error.secret.scopeMismatch"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "error.internal");

  private final HttpStatus status;
  private final String defaultDetail;
  private final String messageKey;

  public ApiException throwIt() {
    return new ApiException(this);
  }

  public ApiException throwIt(String detail) {
    return new ApiException(this, detail);
  }
}
