package vn.vinfast.vfqc.api.common.mail.model;

import vn.vinfast.vfqc.api.common.mail.MailTemplateModel;

public record EmailVerificationMailModel(
    String recipientEmail, String displayName, String verificationLink)
    implements MailTemplateModel {}
