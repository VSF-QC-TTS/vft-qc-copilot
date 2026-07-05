package vn.vinfast.vfqc.api.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.vinfast.vfqc.api.common.observability.RequestTraceFilter;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ProblemDetail handleApiException(ApiException exception, HttpServletRequest request) {
    return problem(exception.getErrorCode(), exception.getMessage(), request.getRequestURI());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    var problem =
        problem(
            ErrorCode.VALIDATION_ERROR,
            ErrorCode.VALIDATION_ERROR.getDefaultDetail(),
            request.getRequestURI());
    var fields =
        exception.getBindingResult().getFieldErrors().stream().map(this::fieldViolation).toList();
    problem.setProperty("fieldErrors", fields);
    return problem;
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ProblemDetail handleConstraintViolation(
      ConstraintViolationException exception, HttpServletRequest request) {
    var problem =
        problem(
            ErrorCode.VALIDATION_ERROR,
            ErrorCode.VALIDATION_ERROR.getDefaultDetail(),
            request.getRequestURI());
    List<FieldViolation> fields =
        exception.getConstraintViolations().stream().map(FieldViolation::toFieldVioLation).toList();
    problem.setProperty("fieldErrors", fields);
    return problem;
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleDataIntegrity(
      DataIntegrityViolationException exception, HttpServletRequest request) {
    return problem(
        ErrorCode.DUPLICATE_RESOURCE,
        ErrorCode.DUPLICATE_RESOURCE.getDefaultDetail(),
        request.getRequestURI());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
    return problem(
        ErrorCode.ACCESS_DENIED,
        ErrorCode.ACCESS_DENIED.getDefaultDetail(),
        request.getRequestURI());
  }

  @ExceptionHandler(AuthenticationException.class)
  public ProblemDetail handleAuthentication(
      AuthenticationException exception, HttpServletRequest request) {
    return problem(
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.AUTHENTICATION_REQUIRED.getDefaultDetail(),
        request.getRequestURI());
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
    return problem(
        ErrorCode.INTERNAL_ERROR,
        ErrorCode.INTERNAL_ERROR.getDefaultDetail(),
        request.getRequestURI());
  }

  private FieldViolation fieldViolation(FieldError error) {
    return new FieldViolation(error.getField(), error.getDefaultMessage());
  }

  private ProblemDetail problem(ErrorCode code, String detail, String instance) {
    var problem = ProblemDetail.forStatusAndDetail(code.getStatus(), detail);
    problem.setTitle(code.getDefaultDetail());
    problem.setType(
        URI.create(
            "https://vfqc.vinfast.vn/problems/" + code.name().toLowerCase().replace('_', '-')));
    problem.setProperty("code", code.name());
    problem.setProperty("messageKey", code.getMessageKey());
    problem.setProperty(RequestTraceFilter.TRACE_ID, MDC.get(RequestTraceFilter.TRACE_ID));
    problem.setInstance(URI.create(instance));
    return problem;
  }
}
