import { Component, inject, OnInit } from '@angular/core';
import { ProfileService } from '../profiles/profile.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import dayjs from 'dayjs/esm';

@Component({
  selector: 'jhi-footer',
  templateUrl: './footer.component.html',
  imports: [FaIconComponent],
})
export default class FooterComponent implements OnInit {
  gitBranch?: string;
  gitCommitId?: string;
  gitTimestamp?: string;
  gitCommitUser?: string;

  private readonly profileService = inject(ProfileService);

  ngOnInit(): void {
    this.profileService.getProfileInfo().subscribe(profileInfo => {
      this.gitBranch = profileInfo.git?.branch;
      this.gitCommitId = profileInfo.git?.commit.id.abbrev;
      if (profileInfo.git?.commit.time) {
        this.gitTimestamp = dayjs(profileInfo.git.commit.time).format('DD.MM.YYYY, HH:mm');
      }
      this.gitCommitUser = profileInfo.git?.commit.user.name;
    });
  }
}
