export class LogMessage {
  constructor(
    public message: string,
    public error: boolean,
    public timestamp: Date,
  ) {}
}
