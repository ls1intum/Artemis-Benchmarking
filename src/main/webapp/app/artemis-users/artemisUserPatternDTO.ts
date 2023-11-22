export class ArtemisUserPatternDTO {
  constructor(
    public usernamePattern: string,
    public passwordPattern: string,
    public from: number,
    public to: number,
  ) {}
}
