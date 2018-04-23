/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <avian/common.h>

namespace avian {
namespace codegen {
namespace runtime {

static bool isNaN(double v)
{
  return fpclassify(v) == FP_NAN;
}

static bool isNaN(float v)
{
  return fpclassify(v) == FP_NAN;
}

int64_t compareDoublesG(uint64_t bi, uint64_t ai)
{
  double a = vm::bitsToDouble(ai);
  double b = vm::bitsToDouble(bi);

  if (isNaN(a) or isNaN(b)) {
    return 1;
  } else if (a < b) {
    return -1;
  } else if (a > b) {
    return 1;
  } else if (a == b) {
    return 0;
  } else {
    return 1;
  }
}

int64_t compareDoublesL(uint64_t bi, uint64_t ai)
{
  double a = vm::bitsToDouble(ai);
  double b = vm::bitsToDouble(bi);

  if (isNaN(a) or isNaN(b)) {
    return -1;
  } else if (a < b) {
    return -1;
  } else if (a > b) {
    return 1;
  } else if (a == b) {
    return 0;
  } else {
    return -1;
  }
}

int64_t compareFloatsG(uint32_t bi, uint32_t ai)
{
  float a = vm::bitsToFloat(ai);
  float b = vm::bitsToFloat(bi);

  if (isNaN(a) or isNaN(b)) {
    return 1;
  }
  if (a < b) {
    return -1;
  } else if (a > b) {
    return 1;
  } else if (a == b) {
    return 0;
  } else {
    return 1;
  }
}

int64_t compareFloatsL(uint32_t bi, uint32_t ai)
{
  float a = vm::bitsToFloat(ai);
  float b = vm::bitsToFloat(bi);

  if (isNaN(a) or isNaN(b)) {
    return -1;
  }
  if (a < b) {
    return -1;
  } else if (a > b) {
    return 1;
  } else if (a == b) {
    return 0;
  } else {
    return -1;
  }
}

int64_t compareLongs(uint64_t b, uint64_t a)
{
  if (a < b) {
    return -1;
  } else if (a > b) {
    return 1;
  } else {
    return 0;
  }
}

uint64_t addDouble(uint64_t b, uint64_t a)
{
  return vm::doubleToBits(vm::bitsToDouble(a) + vm::bitsToDouble(b));
}

uint64_t subtractDouble(uint64_t b, uint64_t a)
{
  return vm::doubleToBits(vm::bitsToDouble(a) - vm::bitsToDouble(b));
}

uint64_t multiplyDouble(uint64_t b, uint64_t a)
{
  return vm::doubleToBits(vm::bitsToDouble(a) * vm::bitsToDouble(b));
}

uint64_t divideDouble(uint64_t b, uint64_t a)
{
  return vm::doubleToBits(vm::bitsToDouble(a) / vm::bitsToDouble(b));
}

uint64_t moduloDouble(uint64_t b, uint64_t a)
{
  return vm::doubleToBits(fmod(vm::bitsToDouble(a), vm::bitsToDouble(b)));
}

uint64_t negateDouble(uint64_t a)
{
  return vm::doubleToBits(-vm::bitsToDouble(a));
}

uint64_t squareRootDouble(uint64_t a)
{
  return vm::doubleToBits(sqrt(vm::bitsToDouble(a)));
}

uint64_t doubleToFloat(int64_t a)
{
  return vm::floatToBits(static_cast<float>(vm::bitsToDouble(a)));
}

int64_t doubleToInt(int64_t a)
{
  double f = vm::bitsToDouble(a);
  switch (fpclassify(f)) {
  case FP_NAN:
    return 0;
  case FP_INFINITE:
    return signbit(f) ? INT32_MIN : INT32_MAX;
  default:
    return f >= INT32_MAX
               ? INT32_MAX
               : (f <= INT32_MIN ? INT32_MIN : static_cast<int32_t>(f));
  }
}

int64_t doubleToLong(int64_t a)
{
  double f = vm::bitsToDouble(a);
  switch (fpclassify(f)) {
  case FP_NAN:
    return 0;
  case FP_INFINITE:
    return signbit(f) ? INT64_MIN : INT64_MAX;
  default:
    return f >= INT64_MAX
               ? INT64_MAX
               : (f <= INT64_MIN ? INT64_MIN : static_cast<int64_t>(f));
  }
}

uint64_t addFloat(uint32_t b, uint32_t a)
{
  return vm::floatToBits(vm::bitsToFloat(a) + vm::bitsToFloat(b));
}

uint64_t subtractFloat(uint32_t b, uint32_t a)
{
  return vm::floatToBits(vm::bitsToFloat(a) - vm::bitsToFloat(b));
}

uint64_t multiplyFloat(uint32_t b, uint32_t a)
{
  return vm::floatToBits(vm::bitsToFloat(a) * vm::bitsToFloat(b));
}

uint64_t divideFloat(uint32_t b, uint32_t a)
{
  return vm::floatToBits(vm::bitsToFloat(a) / vm::bitsToFloat(b));
}

uint64_t moduloFloat(uint32_t b, uint32_t a)
{
  return vm::floatToBits(fmod(vm::bitsToFloat(a), vm::bitsToFloat(b)));
}

uint64_t negateFloat(uint32_t a)
{
  return vm::floatToBits(-vm::bitsToFloat(a));
}

uint64_t absoluteFloat(uint32_t a)
{
  return vm::floatToBits(fabsf(vm::bitsToFloat(a)));
}

int64_t absoluteLong(int64_t a)
{
  return a > 0 ? a : -a;
}

int64_t absoluteInt(int32_t a)
{
  return a > 0 ? a : -a;
}

uint64_t floatToDouble(int32_t a)
{
  return vm::doubleToBits(static_cast<double>(vm::bitsToFloat(a)));
}

int64_t floatToInt(int32_t a)
{
  float f = vm::bitsToFloat(a);
  switch (fpclassify(f)) {
  case FP_NAN:
    return 0;
  case FP_INFINITE:
    return signbit(f) ? INT32_MIN : INT32_MAX;
  default:
    return f >= INT32_MAX
               ? INT32_MAX
               : (f <= INT32_MIN ? INT32_MIN : static_cast<int32_t>(f));
  }
}

int64_t floatToLong(int32_t a)
{
  float f = vm::bitsToFloat(a);
  switch (fpclassify(f)) {
  case FP_NAN:
    return 0;
  case FP_INFINITE:
    return signbit(f) ? INT64_MIN : INT64_MAX;
  default:
    return static_cast<int64_t>(f);
  }
}

uint64_t intToDouble(int32_t a)
{
  return vm::doubleToBits(static_cast<double>(a));
}

uint64_t intToFloat(int32_t a)
{
  return vm::floatToBits(static_cast<float>(a));
}

uint64_t longToDouble(int64_t a)
{
  return vm::doubleToBits(static_cast<double>(a));
}

uint64_t longToFloat(int64_t a)
{
  return vm::floatToBits(static_cast<float>(a));
}

}  // namespace runtime
}  // namespace codegen
}  // namespace avian
