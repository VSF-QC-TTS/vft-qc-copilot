package vn.vinfast.vfqc.api.common.mail;

public interface MailService {

  /**
   * Builds and sends an email for the provided mail model. Implementations must not log raw
   * tokens, passwords, or full magic links.
   *
   * @param model mail template model containing recipient and non-sensitive display data
   */
  void send(MailTemplateModel model);
}
