export interface RefreshCoordinator {
  refresh(): Promise<string>;
}

interface CreateRefreshCoordinatorOptions {
  refreshAction: () => Promise<string>;
  onFailure?: () => void;
}

export function createRefreshCoordinator({
  refreshAction,
  onFailure,
}: CreateRefreshCoordinatorOptions): RefreshCoordinator {
  let pendingRefresh: Promise<string> | undefined;

  return {
    refresh() {
      if (!pendingRefresh) {
        pendingRefresh = refreshAction()
          .catch((error: unknown) => {
            onFailure?.();
            throw error;
          })
          .finally(() => {
            pendingRefresh = undefined;
          });
      }

      return pendingRefresh;
    },
  };
}
