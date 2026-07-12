export interface PageInfo {
  limit: number;
  nextCursor: string | null;
  hasMore: boolean;
}

export interface CollectionResponse<T> {
  items: T[];
  page: PageInfo;
}
