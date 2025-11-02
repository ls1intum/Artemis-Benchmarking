export interface InfoResponse {
  'display-ribbon-on-profiles'?: string;
  git?: GitInfo;
  build?: any;
  activeProfiles?: string[];
}

interface GitUser {
  name: string;
}

interface GitCommitId {
  abbrev: string;
}

interface GitCommit {
  id: GitCommitId;
  time: string;
  user: GitUser;
}

interface GitInfo {
  branch: string;
  commit: GitCommit;
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
