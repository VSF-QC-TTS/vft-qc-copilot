package vn.vinfast.vfqc.api.common.mail;

public interface MailStrategy<T extends MailTemplateModel> {

  Class<T> modelType();

  MailMessage build(T model);
}
