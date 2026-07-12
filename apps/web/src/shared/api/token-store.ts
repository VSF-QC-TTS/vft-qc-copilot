export interface TokenStore {
  getAccessToken(): string | undefined;
  setAccessToken(token: string): void;
  clear(): void;
}

export function createTokenStore(): TokenStore {
  let accessToken: string | undefined;

  return {
    getAccessToken() {
      return accessToken;
    },
    setAccessToken(token) {
      accessToken = token;
    },
    clear() {
      accessToken = undefined;
    },
  };
}
