import { VegaPage } from './app.po';

describe('vega App', function() {
  let page: VegaPage;

  beforeEach(() => {
    page = new VegaPage();
  });

  it('should display message saying app works', () => {
    page.navigateTo();
    expect(page.getParagraphText()).toEqual('app works!');
  });
});
