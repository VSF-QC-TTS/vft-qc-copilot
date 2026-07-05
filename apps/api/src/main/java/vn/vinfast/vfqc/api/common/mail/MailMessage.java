package vn.vinfast.vfqc.api.common.mail;

public record MailMessage(String to, String subject, String body, boolean html) {}
