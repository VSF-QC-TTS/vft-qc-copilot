package vn.vinfast.vfqc.api.common.mail.event;

import vn.vinfast.vfqc.api.common.mail.MailTemplateModel;

/**
 * Domain event published inside a {@code @Transactional} method so that mail delivery is deferred
 * until after the transaction commits successfully. This prevents SMTP timeouts from holding open
 * database connections.
 *
 * @param model the mail template model to send
 */
public record MailSendEvent(MailTemplateModel model) {}
