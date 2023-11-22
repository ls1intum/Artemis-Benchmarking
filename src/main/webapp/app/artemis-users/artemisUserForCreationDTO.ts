export class ArtemisUserForCreationDTO {
  constructor(
    public username: string,
    public password: string,
    public serverWideId?: number,
  ) {}
}
