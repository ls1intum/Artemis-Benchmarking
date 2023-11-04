import SharedModule from 'app/shared/shared.module';
import FooterComponent from '../footer/footer.component';
import PageRibbonComponent from '../profiles/page-ribbon.component';
import MainComponent from './main.component';
import { RouterModule } from '@angular/router';
import { NgModule } from '@angular/core';

@NgModule({
  imports: [SharedModule, RouterModule, FooterComponent, PageRibbonComponent],
  declarations: [MainComponent],
})
export default class MainModule {}
