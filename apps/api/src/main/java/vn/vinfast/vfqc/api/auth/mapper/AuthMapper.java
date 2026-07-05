package vn.vinfast.vfqc.api.auth.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.vinfast.vfqc.api.auth.dto.CurrentUserResponse;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.user.entity.AppUser;

@Mapper(componentModel = "spring")
public interface AuthMapper {

  @Mapping(target = "name", source = "displayName")
  CurrentUserResponse toCurrentUserResponse(AppUser user);

  @Mapping(target = "avatarUrl", ignore = true)
  CurrentUserResponse toCurrentUserResponse(CurrentUser user);
}
