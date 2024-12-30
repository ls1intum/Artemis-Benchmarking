import { Component, computed, input } from '@angular/core';

/**
 * A component that will take care of item count statistics of a pagination.
 */
@Component({
  selector: 'jhi-item-count',
  template: ` <div>Showing {{ first() }} - {{ second() }} of {{ total() }} items.</div> `,
})
export default class ItemCountComponent {
  page = input<number | undefined>();
  totalItems = input<number | undefined>();
  itemsPerPage = input<number | undefined>();

  // Automatically computed signals for `first`, `second`, and `total`
  readonly first = computed(() => {
    const page = this.page();
    const itemsPerPage = this.itemsPerPage();
    if (page && itemsPerPage) {
      return (page - 1) * itemsPerPage + 1;
    }
    return undefined;
  });

  readonly second = computed(() => {
    const page = this.page();
    const totalItems = this.totalItems();
    const itemsPerPage = this.itemsPerPage();
    if (page && totalItems !== undefined && itemsPerPage) {
      return page * itemsPerPage < totalItems ? page * itemsPerPage : totalItems;
    }
    return undefined;
  });

  readonly total = computed(() => this.totalItems());
}
