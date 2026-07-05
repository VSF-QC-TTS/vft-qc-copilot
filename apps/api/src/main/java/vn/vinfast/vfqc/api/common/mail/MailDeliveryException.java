package vn.vinfast.vfqc.api.common.mail;

public class MailDeliveryException extends RuntimeException {

  public MailDeliveryException(String message) {
    super(message);
  }

  public MailDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }
}
