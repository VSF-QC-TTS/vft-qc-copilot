package vn.vinfast.vfqc.api.provider.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderTestLogResponse;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderTestResponse;
import vn.vinfast.vfqc.api.provider.entity.LlmProviderTestLog;

@Mapper(componentModel = "spring")
public interface LlmProviderTestLogMapper {

  LlmProviderTestLogResponse toResponse(LlmProviderTestLog log);

  @Mapping(source = "requestJsonSanitized", target = "request")
  @Mapping(source = "tokenUsageJson", target = "tokenUsage")
  LlmProviderTestResponse toTestResponse(LlmProviderTestLog log);
}
