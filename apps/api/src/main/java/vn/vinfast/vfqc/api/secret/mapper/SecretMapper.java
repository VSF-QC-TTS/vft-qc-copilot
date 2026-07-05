package vn.vinfast.vfqc.api.secret.mapper;

import org.mapstruct.Mapper;
import vn.vinfast.vfqc.api.secret.dto.SecretResponse;
import vn.vinfast.vfqc.api.secret.dto.SecretSummaryResponse;
import vn.vinfast.vfqc.api.secret.entity.Secret;

@Mapper(componentModel = "spring")
public interface SecretMapper {

  SecretResponse toResponse(Secret secret);

  SecretSummaryResponse toSummary(Secret secret);
}
