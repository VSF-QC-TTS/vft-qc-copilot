package vn.vinfast.vfqc.api.common.mail.impl;

import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.common.mail.MailMessage;
import vn.vinfast.vfqc.api.common.mail.MailStrategy;
import vn.vinfast.vfqc.api.common.mail.model.PasswordResetMailModel;

@Component
public class PasswordResetMailStrategy implements MailStrategy<PasswordResetMailModel> {

  @Override
  public Class<PasswordResetMailModel> modelType() {
    return PasswordResetMailModel.class;
  }

  @Override
  public MailMessage build(PasswordResetMailModel model) {
    return new MailMessage(
        model.recipientEmail(),
        "Reset your VFQC password",
        """
        Hello %s,

        Reset your VFQC password by opening this link:
        %s

        If you did not request a password reset, ignore this email.
        """
            .formatted(model.displayName(), model.resetLink()),
        false);
  }
}
