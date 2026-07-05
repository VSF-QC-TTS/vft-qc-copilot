package vn.vinfast.vfqc.api.common.mail.model;

import vn.vinfast.vfqc.api.common.mail.MailTemplateModel;

public record AdminInviteMailModel(String recipientEmail, String displayName, String inviteLink)
    implements MailTemplateModel {}
