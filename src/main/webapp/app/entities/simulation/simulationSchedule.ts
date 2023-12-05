export class SimulationSchedule {
  constructor(
    public id: number | undefined,
    public startDateTime: Date,
    public timeOfDay: Date,
    public cycle: Cycle,
    public endDateTime?: Date,
    public dayOfWeek?: DayOfWeek,
  ) {}
}

export enum Cycle {
  DAILY = 'DAILY',
  WEEKLY = 'WEEKLY',
}

export enum DayOfWeek {
  MONDAY = 'MONDAY',
  TUESDAY = 'TUESDAY',
  WEDNESDAY = 'WEDNESDAY',
  THURSDAY = 'THURSDAY',
  FRIDAY = 'FRIDAY',
  SATURDAY = 'SATURDAY',
  SUNDAY = 'SUNDAY',
}
