import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ProfileService } from '../profiles/profile.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import dayjs from 'dayjs/esm';

@Component({
  selector: 'footer',
  templateUrl: './footer.component.html',
  imports: [FaIconComponent],
})
export default class FooterComponent {
  readonly gitBranch = computed(() => this.profileInfo()?.git?.branch);
  readonly gitCommitId = computed(() => this.profileInfo()?.git?.commit.id.abbrev);
  readonly gitCommitUser = computed(() => this.profileInfo()?.git?.commit.user.name);
  readonly gitTimestamp = computed(() => {
    const commitTime = this.profileInfo()?.git?.commit.time;
    return commitTime ? dayjs(commitTime).format('DD.MM.YYYY, HH:mm') : undefined;
  });

  private readonly profileService = inject(ProfileService);

  // The app runs with zoneless change detection, so the profile info has to reach the template through a signal.
  // Assigning plain fields from a subscription does not mark the view dirty and leaves the footer rendered empty.
  private readonly profileInfo = toSignal(this.profileService.getProfileInfo());
}
