package vn.vinfast.vfqc.api.auth.service;

import vn.vinfast.vfqc.api.auth.enums.AuthEmailTokenPurpose;
import vn.vinfast.vfqc.api.user.entity.AppUser;

public record AuthEmailTokenConsumption(AppUser user, AuthEmailTokenPurpose purpose) {}
