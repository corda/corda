import { FixedLegViewModel } from './FixedLegViewModel'
import { FloatingLegViewModel } from './FloatingLegViewModel'
import { CommonViewModel } from './CommonViewModel'

export class DealViewModel {
  constructor() {}

  fixedLeg = new FixedLegViewModel();
  floatingLeg = new FloatingLegViewModel();
  common = new CommonViewModel();
}
