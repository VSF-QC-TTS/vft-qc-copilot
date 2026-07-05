package vn.vinfast.vfqc.api.common.mail.impl;

import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.common.mail.MailMessage;
import vn.vinfast.vfqc.api.common.mail.MailStrategy;
import vn.vinfast.vfqc.api.common.mail.model.EmailVerificationMailModel;

@Component
public class EmailVerificationMailStrategy implements MailStrategy<EmailVerificationMailModel> {

  @Override
  public Class<EmailVerificationMailModel> modelType() {
    return EmailVerificationMailModel.class;
  }

  @Override
  public MailMessage build(EmailVerificationMailModel model) {
    return new MailMessage(
        model.recipientEmail(),
        "Verify your VFQC account",
        """
        Hello %s,

        Verify your VFQC account by opening this link:
        %s

        If you did not create this account, ignore this email.
        """
            .formatted(model.displayName(), model.verificationLink()),
        false);
  }
}
