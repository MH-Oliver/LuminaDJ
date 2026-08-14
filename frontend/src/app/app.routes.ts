import { Routes } from '@angular/router';
import { StartPageComponent } from './start-page/start-page.component';
import { SetupPageComponent } from './setup-page/setup-page.component';
import { SessionSetupComponent } from './session-setup/session-setup.component';
import { ActiveSessionComponent } from './active-session/active-session.component'; // NEU

export const routes: Routes = [
  { path: '', component: StartPageComponent },
  { path: 'setup', component: SetupPageComponent },
  { path: 'session-setup', component: SessionSetupComponent },
  { path: 'active-session', component: ActiveSessionComponent } // NEU
];
