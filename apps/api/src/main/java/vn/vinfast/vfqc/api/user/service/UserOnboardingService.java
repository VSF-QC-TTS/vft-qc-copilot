package vn.vinfast.vfqc.api.user.service;

import vn.vinfast.vfqc.api.user.entity.AppUser;

public interface UserOnboardingService {

  void ensurePersonalWorkspace(AppUser user);
}
