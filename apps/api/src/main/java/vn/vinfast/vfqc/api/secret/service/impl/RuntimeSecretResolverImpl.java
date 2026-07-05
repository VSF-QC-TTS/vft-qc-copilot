package vn.vinfast.vfqc.api.secret.service.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.secret.service.RuntimeSecretResolver;
import vn.vinfast.vfqc.api.secret.service.SecretEncryptionService;
import vn.vinfast.vfqc.api.secret.service.SecretLookupService;

@Service
@RequiredArgsConstructor
public class RuntimeSecretResolverImpl implements RuntimeSecretResolver {

  private final SecretLookupService secretLookupService;
  private final SecretEncryptionService encryptionService;

  @Override
  public String resolve(CurrentUser user, UUID secretId, UUID workspaceId) {
    var secret = secretLookupService.requireActiveForUse(user, secretId, workspaceId);
    return encryptionService.decrypt(secret.getEncryptedValue());
  }
}
