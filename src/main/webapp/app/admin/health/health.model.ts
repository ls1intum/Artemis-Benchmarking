export type HealthStatus = 'UP' | 'DOWN' | 'UNKNOWN' | 'OUT_OF_SERVICE';

export type HealthKey = 'diskSpace' | 'mail' | 'ping' | 'livenessState' | 'readinessState' | 'db';

export interface Health {
  status: HealthStatus;
  components?: Map<HealthKey, HealthDetails>;
}

export interface HealthDetails {
  status: HealthStatus;
  details?: Record<string, unknown>;
}

export interface HealthEntry {
  key: HealthKey;
  value: HealthDetails;
}

// shared constant used across components
export const HEALTH_LABELS: Record<HealthKey, string> = {
  diskSpace: 'Disk space',
  mail: 'Email',
  ping: 'Application',
  livenessState: 'Liveness state',
  readinessState: 'Readiness state',
  db: 'Database',
};

export const HEALTH_STATUS_LABELS: Record<HealthStatus, string> = {
  UP: 'UP',
  DOWN: 'DOWN',
  UNKNOWN: 'UNKNOWN',
  OUT_OF_SERVICE: 'OUT_OF_SERVICE',
};
