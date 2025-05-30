export class ArtemisUserPatternDTO {
  constructor(
    public usernamePattern: string,
    public passwordPattern: string,
    public from: number,
    public to: number,
    public createOnArtemis = false,
    public firstNamePattern: string,
    public lastNamePattern: string,
    public emailPattern: string,
  ) {}
}
