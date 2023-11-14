import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { Mode, Simulation } from '../../models/simulation';
import { SimulationRun, Status } from '../../models/simulationRun';

@Component({
  selector: 'jhi-simulation-card',
  templateUrl: './simulation-card.component.html',
  styleUrls: ['./simulation-card.component.scss'],
})
export class SimulationCardComponent implements OnInit, OnChanges {
  @Input()
  simulation!: Simulation;
  mostRecentRun?: SimulationRun;

  constructor() {}

  protected readonly Simulation = Simulation;
  protected readonly Mode = Mode;

  ngOnInit(): void {
    this.mostRecentRun = this.simulation.runs.sort((a, b) => new Date(a.startDateTime).getTime() - new Date(b.startDateTime).getTime())[0];
  }
  ngOnChanges(): void {
    this.mostRecentRun = this.simulation.runs.sort((a, b) => new Date(a.startDateTime).getTime() - new Date(b.startDateTime).getTime())[0];
  }

  protected readonly Status = Status;
}
