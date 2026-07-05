package vn.vinfast.vfqc.api.provider.mapper;

import org.mapstruct.Mapper;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderTestLogResponse;
import vn.vinfast.vfqc.api.provider.entity.LlmProviderTestLog;

@Mapper(componentModel = "spring")
public interface LlmProviderTestLogMapper {

  LlmProviderTestLogResponse toResponse(LlmProviderTestLog log);
}
