package vn.vinfast.vfqc.api.common.mail.model;

import vn.vinfast.vfqc.api.common.mail.MailTemplateModel;

public record PasswordResetMailModel(String recipientEmail, String displayName, String resetLink)
    implements MailTemplateModel {}
