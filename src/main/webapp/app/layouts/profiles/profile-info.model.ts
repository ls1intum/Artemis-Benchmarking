export interface InfoResponse {
  'display-ribbon-on-profiles'?: string;
  git?: GitInfo;
  build?: any;
  activeProfiles?: string[];
}

export interface GitInfo {
  branch: string;
  commit: {
    id: {
      abbrev: string;
    };
    time: string;
    user: {
      name: string;
    };
  };
}

export class ProfileInfo {
  constructor(
    public activeProfiles?: string[],
    public ribbonEnv?: string,
    public inProduction = false, // default value
    public openAPIEnabled = false, // default value
    public git?: GitInfo,
  ) {}
}
