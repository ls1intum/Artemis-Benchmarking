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

export function getOrder(dayOfWeek: DayOfWeek): number {
  switch (dayOfWeek) {
    case DayOfWeek.MONDAY:
      return 1;
    case DayOfWeek.TUESDAY:
      return 2;
    case DayOfWeek.WEDNESDAY:
      return 3;
    case DayOfWeek.THURSDAY:
      return 4;
    case DayOfWeek.FRIDAY:
      return 5;
    case DayOfWeek.SATURDAY:
      return 6;
    case DayOfWeek.SUNDAY:
      return 7;
  }
}
