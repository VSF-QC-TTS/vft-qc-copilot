export class RunnerError extends Error {
  public constructor(
    message: string,
    public readonly category: string,
  ) {
    super(message);
    this.name = 'RunnerError';
  }
}
