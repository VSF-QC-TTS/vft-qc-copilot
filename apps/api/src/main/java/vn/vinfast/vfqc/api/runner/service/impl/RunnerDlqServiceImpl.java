package vn.vinfast.vfqc.api.runner.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.common.masking.VfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.runner.service.RunnerDlqService;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunnerDlqServiceImpl implements RunnerDlqService {

  private final StringRedisTemplate redisTemplate;
  private final VfqcSensitiveDataMasker masker;

  @Override
  public void send(String streamName, Map<String, String> message, String error) {
    log.warn("Sending failed runner event message to DLQ stream: streamName={}, error={}", streamName, error);
    var payload = new LinkedHashMap<String, String>();
    if (message != null) {
      message.forEach((key, value) -> payload.put(key, masker.sanitizeText(value).value()));
    }
    payload.put("error", masker.sanitizeText(error).value());
    redisTemplate.opsForStream().add(streamName, payload);
  }
}
