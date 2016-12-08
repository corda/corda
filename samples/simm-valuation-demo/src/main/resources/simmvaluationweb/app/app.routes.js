"use strict";
var router_1 = require('@angular/router');
var portfolio_1 = require('./portfolio');
var valuations_1 = require('./valuations');
var create_trade_1 = require('./create-trade');
var view_trade_1 = require('./view-trade');
var routes = [
    { path: '', redirectTo: '/portfolio', pathMatch: 'full' },
    { path: 'portfolio', component: portfolio_1.PortfolioComponent },
    { path: 'valuations', component: valuations_1.ValuationsComponent },
    { path: 'create-trade', component: create_trade_1.CreateTradeComponent },
    { path: 'view-trade/:tradeId', component: view_trade_1.ViewTradeComponent }
];
exports.appRouterProviders = [
    router_1.provideRouter(routes)
];
//# sourceMappingURL=app.routes.js.map