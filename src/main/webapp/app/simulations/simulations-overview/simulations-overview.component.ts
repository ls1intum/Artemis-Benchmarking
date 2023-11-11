import { Component, OnInit } from '@angular/core';
import { Simulation } from '../../models/simulation';
import { SimulationsService } from '../simulations.service';

@Component({
  selector: 'jhi-simulations-overview',
  templateUrl: './simulations-overview.component.html',
  styleUrls: ['./simulations-overview.component.scss'],
})
export class SimulationsOverviewComponent implements OnInit {
  private simulations: Simulation[] = [];

  constructor(private simulationsService: SimulationsService) {}

  ngOnInit(): void {
    this.simulationsService.getSimulations().subscribe(simulations => {
      this.simulations = simulations;
    });
  }
}
