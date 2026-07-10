package vn.vinfast.vfqc.api.auth.mapper;

import org.mapstruct.Mapper;
import vn.vinfast.vfqc.api.auth.dto.CurrentUserResponse;
import vn.vinfast.vfqc.api.user.entity.AppUser;

@Mapper(componentModel = "spring")
public interface AuthMapper {

  CurrentUserResponse toCurrentUserResponse(AppUser user);
}
