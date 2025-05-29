export class Account {
  constructor(
    public activated: boolean,
    public authorities: string[],
    public login: string,
    public email: string,
    public langKey: string,
    public firstName?: string,
    public lastName?: string,
    public imageUrl?: string,
  ) {}
}
