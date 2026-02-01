import { ArtemisServer } from 'app/core/util/artemisServer';

export interface ArtemisServerConfiguration {
  server: ArtemisServer;
  url: string;
  cleanupEnabled: boolean;
  isLocal: boolean;
  prometheusInstancesArtemis: string[];
  prometheusInstancesVcs: string[];
  prometheusInstancesCi: string[];
}
