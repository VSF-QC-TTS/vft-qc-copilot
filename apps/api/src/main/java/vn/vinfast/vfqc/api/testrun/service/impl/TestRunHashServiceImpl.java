package vn.vinfast.vfqc.api.testrun.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.testrun.service.TestRunHashService;

@Service
@RequiredArgsConstructor
public class TestRunHashServiceImpl implements TestRunHashService {

  private final ObjectMapper objectMapper;

  @Override
  public String hash(Map<String, Object> value) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var bytes = objectMapper.writeValueAsString(value == null ? Map.of() : value).getBytes(StandardCharsets.UTF_8);
      return HexFormat.of().formatHex(digest.digest(bytes));
    } catch (JsonProcessingException exception) {
      throw ErrorCode.INVALID_STATE.throwIt("Snapshot must be valid JSON");
    } catch (Exception exception) {
      throw ErrorCode.INTERNAL_ERROR.throwIt();
    }
  }
}
