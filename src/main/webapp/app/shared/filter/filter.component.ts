import SharedModule from '../shared.module';
import { IFilterOptions } from './filter.model';
import { Component, Input } from '@angular/core';

@Component({
  selector: 'jhi-filter',
  standalone: true,
  imports: [SharedModule],
  templateUrl: './filter.component.html',
})
export default class FilterComponent {
  @Input() filters!: IFilterOptions;

  clearAllFilters(): void {
    this.filters.clear();
  }

  clearFilter(filterName: string, value: string): void {
    this.filters.removeFilter(filterName, value);
  }
}
