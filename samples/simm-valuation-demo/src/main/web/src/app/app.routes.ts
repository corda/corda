import { provideRouter, RouterConfig } from '@angular/router';
import { PortfolioComponent } from './portfolio';
import { ValuationsComponent } from './valuations';
import { CreateTradeComponent } from './create-trade';
import { ViewTradeComponent } from './view-trade';

const routes: RouterConfig = [
  { path: '', redirectTo: '/portfolio', pathMatch: 'full' },
  { path: 'portfolio', component: PortfolioComponent },
  { path: 'valuations', component: ValuationsComponent },
  { path: 'create-trade', component: CreateTradeComponent },
  { path: 'view-trade/:tradeId', component: ViewTradeComponent }

  // { path: '**', component: PageNotFoundComponent }
];

export const appRouterProviders = [
  provideRouter(routes)
];
