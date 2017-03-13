/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/target.h"

#include "codegen/compiler/regalloc.h"
#include "codegen/compiler/context.h"
#include "codegen/compiler/site.h"
#include "codegen/compiler/resource.h"
#include "codegen/compiler/read.h"

namespace avian {
namespace codegen {
namespace compiler {

RegisterAllocator::RegisterAllocator(Aborter* a,
                                     const RegisterFile* registerFile)
    : a(a), registerFile(registerFile)
{
}

unsigned totalFrameSize(Context* c);
Read* live(Context* c UNUSED, Value* v);

unsigned resourceCost(Context* c,
                      Value* v,
                      Resource* r,
                      SiteMask mask,
                      CostCalculator* costCalculator)
{
  if (r->reserved or r->freezeCount or r->referenceCount) {
    return Target::Impossible;
  } else {
    unsigned baseCost = costCalculator ? costCalculator->cost(c, mask) : 0;

    if (r->value) {
      assertT(c, r->value->findSite(r->site));

      if (v and r->value->isBuddyOf(v)) {
        return baseCost;
      } else if (r->value->uniqueSite(c, r->site)) {
        return baseCost + Target::StealUniquePenalty;
      } else {
        return baseCost = Target::StealPenalty;
      }
    } else {
      return baseCost;
    }
  }
}

bool pickRegisterTarget(Context* c,
                        Register i,
                        Value* v,
                        RegisterMask mask,
                        Register* target,
                        unsigned* cost,
                        CostCalculator* costCalculator)
{
  if (mask.contains(i)) {
    RegisterResource* r = c->registerResources + i.index();
    unsigned myCost
        = resourceCost(
              c,
              v,
              r,
              SiteMask(lir::Operand::RegisterPairMask, RegisterMask(i), NoFrameIndex),
              costCalculator) + Target::MinimumRegisterCost;

    if (mask.containsExactly(i)) {
      *cost = myCost;
      return true;
    } else if (myCost < *cost) {
      *cost = myCost;
      *target = i;
    }
  }
  return false;
}

Register pickRegisterTarget(Context* c,
                       Value* v,
                       RegisterMask mask,
                       unsigned* cost,
                       CostCalculator* costCalculator)
{
  Register target = NoRegister;
  *cost = Target::Impossible;

  if (mask & c->regFile->generalRegisters) {
    for (Register i : c->regFile->generalRegisters) {
      if (pickRegisterTarget(c, i, v, mask, &target, cost, costCalculator)) {
        return i;
      }
    }
  }

  if (mask & c->regFile->floatRegisters) {
    for (Register i : c->regFile->floatRegisters) {
      if (pickRegisterTarget(c, i, v, mask, &target, cost, costCalculator)) {
        return i;
      }
    }
  }

  return target;
}

Target pickRegisterTarget(Context* c,
                          Value* v,
                          RegisterMask mask,
                          CostCalculator* costCalculator)
{
  unsigned cost;
  Register number = pickRegisterTarget(c, v, mask, &cost, costCalculator);
  return Target(number, cost);
}

unsigned frameCost(Context* c,
                   Value* v,
                   int frameIndex,
                   CostCalculator* costCalculator)
{
  return resourceCost(c,
                      v,
                      c->frameResources + frameIndex,
                      SiteMask(lir::Operand::MemoryMask, 0, frameIndex),
                      costCalculator) + Target::MinimumFrameCost;
}

Target pickFrameTarget(Context* c, Value* v, CostCalculator* costCalculator)
{
  Target best;

  Value* p = v;
  do {
    if (p->home >= 0) {
      Target mine(p->home,
                  lir::Operand::Type::Memory,
                  frameCost(c, v, p->home, costCalculator));

      if (mine.cost == Target::MinimumFrameCost) {
        return mine;
      } else if (mine.cost < best.cost) {
        best = mine;
      }
    }
    p = p->buddy;
  } while (p != v);

  return best;
}

Target pickAnyFrameTarget(Context* c, Value* v, CostCalculator* costCalculator)
{
  Target best;

  unsigned count = totalFrameSize(c);
  for (unsigned i = 0; i < count; ++i) {
    Target mine(i, lir::Operand::Type::Memory, frameCost(c, v, i, costCalculator));
    if (mine.cost == Target::MinimumFrameCost) {
      return mine;
    } else if (mine.cost < best.cost) {
      best = mine;
    }
  }

  return best;
}

Target pickTarget(Context* c,
                  Value* value,
                  const SiteMask& mask,
                  unsigned registerPenalty,
                  Target best,
                  CostCalculator* costCalculator)
{
  if (mask.typeMask & lir::Operand::RegisterPairMask) {
    Target mine
        = pickRegisterTarget(c, value, mask.registerMask, costCalculator);

    mine.cost += registerPenalty;
    if (mine.cost == Target::MinimumRegisterCost) {
      return mine;
    } else if (mine.cost < best.cost) {
      best = mine;
    }
  }

  if (mask.typeMask & lir::Operand::MemoryMask) {
    if (mask.frameIndex >= 0) {
      Target mine(mask.frameIndex,
                  lir::Operand::Type::Memory,
                  frameCost(c, value, mask.frameIndex, costCalculator));
      if (mine.cost == Target::MinimumFrameCost) {
        return mine;
      } else if (mine.cost < best.cost) {
        best = mine;
      }
    } else if (mask.frameIndex == AnyFrameIndex) {
      Target mine = pickFrameTarget(c, value, costCalculator);
      if (mine.cost == Target::MinimumFrameCost) {
        return mine;
      } else if (mine.cost < best.cost) {
        best = mine;
      }
    }
  }

  return best;
}

Target pickTarget(Context* c,
                  Read* read,
                  bool intersectRead,
                  unsigned registerReserveCount,
                  CostCalculator* costCalculator)
{
  unsigned registerPenalty
      = (c->availableGeneralRegisterCount > registerReserveCount
             ? 0
             : Target::LowRegisterPenalty);

  Value* value = read->value;

  RegisterMask registerMask
      = (isFloatValue(value) ? AnyRegisterMask : (RegisterMask)c->regFile->generalRegisters);

  SiteMask mask(~0, registerMask, AnyFrameIndex);
  read->intersect(&mask);

  if (isFloatValue(value)) {
    RegisterMask floatMask = mask.registerMask & c->regFile->floatRegisters;
    if (floatMask) {
      mask.registerMask = floatMask;
    }
  }

  Target best;

  Value* successor = read->successor();
  if (successor) {
    Read* r = live(c, successor);
    if (r) {
      SiteMask intersection = mask;
      if (r->intersect(&intersection)) {
        best = pickTarget(
            c, value, intersection, registerPenalty, best, costCalculator);

        if (best.cost <= Target::MinimumFrameCost) {
          return best;
        }
      }
    }
  }

  best = pickTarget(c, value, mask, registerPenalty, best, costCalculator);
  if (best.cost <= Target::MinimumFrameCost) {
    return best;
  }

  if (intersectRead) {
    if (best.cost == Target::Impossible) {
      fprintf(stderr,
              "mask type %d reg %" LLD " frame %d\n",
              mask.typeMask,
              (uint64_t)mask.registerMask,
              mask.frameIndex);
      abort(c);
    }
    return best;
  }

  {
    Target mine = pickRegisterTarget(c, value, registerMask, costCalculator);

    mine.cost += registerPenalty;

    if (mine.cost == Target::MinimumRegisterCost) {
      return mine;
    } else if (mine.cost < best.cost) {
      best = mine;
    }
  }

  {
    Target mine = pickFrameTarget(c, value, costCalculator);
    if (mine.cost == Target::MinimumFrameCost) {
      return mine;
    } else if (mine.cost < best.cost) {
      best = mine;
    }
  }

  if (best.cost >= Target::StealUniquePenalty
      and c->availableGeneralRegisterCount == 0) {
    // there are no free registers left, so moving from memory to
    // memory isn't an option - try harder to find an available frame
    // site:
    best = pickAnyFrameTarget(c, value, costCalculator);
    assertT(c, best.cost <= 3);
  }

  if (best.cost == Target::Impossible) {
    abort(c);
  }

  return best;
}

}  // namespace regalloc
}  // namespace codegen
}  // namespace avian
