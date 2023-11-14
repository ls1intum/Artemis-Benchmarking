import { Component, OnInit } from '@angular/core';
import { Simulation } from '../../models/simulation';
import { SimulationsService } from '../simulations.service';
import { SimulationRun, Status } from '../../models/simulationRun';

@Component({
  selector: 'jhi-simulations-overview',
  templateUrl: './simulations-overview.component.html',
  styleUrls: ['./simulations-overview.component.scss'],
})
export class SimulationsOverviewComponent implements OnInit {
  simulations: Simulation[] = [];
  selectedRun?: SimulationRun;

  constructor(private simulationsService: SimulationsService) {}

  ngOnInit(): void {
    this.simulationsService.getSimulations().subscribe(simulations => {
      this.simulations = simulations;
      this.selectedRun = simulations[4].runs[0];
    });
  }

  protected readonly Status = Status;
}
