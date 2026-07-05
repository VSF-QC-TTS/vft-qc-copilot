package vn.vinfast.vfqc.api.common.mail.impl;

import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.common.mail.MailMessage;
import vn.vinfast.vfqc.api.common.mail.MailStrategy;
import vn.vinfast.vfqc.api.common.mail.model.AdminInviteMailModel;

@Component
public class AdminInviteMailStrategy implements MailStrategy<AdminInviteMailModel> {

  @Override
  public Class<AdminInviteMailModel> modelType() {
    return AdminInviteMailModel.class;
  }

  @Override
  public MailMessage build(AdminInviteMailModel model) {
    return new MailMessage(
        model.recipientEmail(),
        "Set up your VFQC account",
        """
        Hello %s,

        An administrator created a VFQC account for you.
        Use this link to set your password and activate the account:
        %s

        If you did not expect this invitation, ignore this email.
        """
            .formatted(model.displayName(), model.inviteLink()),
        false);
  }
}
