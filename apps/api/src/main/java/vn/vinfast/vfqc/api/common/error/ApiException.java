package vn.vinfast.vfqc.api.common.error;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

  private final ErrorCode errorCode;

  public ApiException(ErrorCode errorCode) {
    super(errorCode.getDefaultDetail());
    this.errorCode = errorCode;
  }

  public ApiException(ErrorCode errorCode, String detail) {
    super(detail);
    this.errorCode = errorCode;
  }
}
