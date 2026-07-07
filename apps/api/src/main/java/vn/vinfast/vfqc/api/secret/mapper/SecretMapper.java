package vn.vinfast.vfqc.api.secret.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.vinfast.vfqc.api.secret.dto.SecretResponse;
import vn.vinfast.vfqc.api.secret.dto.SecretSummaryResponse;
import vn.vinfast.vfqc.api.secret.entity.Secret;

@Mapper(componentModel = "spring")
public interface SecretMapper {

  @Mapping(target = "ownerUser", ignore = true)
  SecretResponse toResponse(Secret secret);

  @Mapping(target = "ownerUser", ignore = true)
  SecretSummaryResponse toSummary(Secret secret);
}
