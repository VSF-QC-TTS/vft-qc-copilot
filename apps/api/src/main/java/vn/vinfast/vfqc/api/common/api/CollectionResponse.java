package vn.vinfast.vfqc.api.common.api;

import java.util.List;

public record CollectionResponse<T>(List<T> items, PageInfo page) {

  public static <T> CollectionResponse<T> of(List<T> items) {
    return new CollectionResponse<>(items, new PageInfo(items.size(), null, false));
  }
}
