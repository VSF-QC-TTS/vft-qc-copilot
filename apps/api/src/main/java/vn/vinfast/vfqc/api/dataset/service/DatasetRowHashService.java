package vn.vinfast.vfqc.api.dataset.service;

import java.util.Map;

public interface DatasetRowHashService {

  /**
   * Creates a deterministic audit hash for dataset row cells.
   *
   * @param cells row cell payload already validated against the dataset schema
   * @return versioned SHA-256 hash over canonical JSON
   */
  String hash(Map<String, Object> cells);
}
