import { ArtemisServer } from '../../core/util/artemisServer';

export class ArtemisUser {
  constructor(
    public id: number | undefined,
    public serverWideId: number,
    public username: string,
    public password: string,
    public server: ArtemisServer,
  ) {}
}
