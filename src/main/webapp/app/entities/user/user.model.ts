export interface IUser {
  id: number;
  login?: string;
}

export class User implements IUser {
  constructor(
    public id: number,
    public login: string,
  ) {}
}
