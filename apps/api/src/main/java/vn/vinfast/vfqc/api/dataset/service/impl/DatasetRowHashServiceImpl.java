package vn.vinfast.vfqc.api.dataset.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.dataset.service.DatasetRowHashService;

@Service
public class DatasetRowHashServiceImpl implements DatasetRowHashService {

  private final ObjectWriter canonicalJsonWriter;

  public DatasetRowHashServiceImpl(ObjectMapper objectMapper) {
    this.canonicalJsonWriter =
        objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true).writer();
  }

  @Override
  public String hash(Map<String, Object> cells) {
    try {
      var canonicalJson = canonicalJsonWriter.writeValueAsString(cells == null ? Map.of() : cells);
      var digest = MessageDigest.getInstance("SHA-256");
      var bytes = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
      return "sha256:" + HexFormat.of().formatHex(bytes);
    } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Unable to hash dataset row cells", exception);
    }
  }
}
