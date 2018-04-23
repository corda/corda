/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_RUNTIME_H
#define AVIAN_CODEGEN_RUNTIME_H

namespace avian {
namespace codegen {
namespace runtime {

int64_t compareDoublesG(uint64_t bi, uint64_t ai);
int64_t compareDoublesL(uint64_t bi, uint64_t ai);
int64_t compareFloatsG(uint32_t bi, uint32_t ai);
int64_t compareFloatsL(uint32_t bi, uint32_t ai);
int64_t compareLongs(uint64_t b, uint64_t a);
uint64_t addDouble(uint64_t b, uint64_t a);
uint64_t subtractDouble(uint64_t b, uint64_t a);
uint64_t multiplyDouble(uint64_t b, uint64_t a);
uint64_t divideDouble(uint64_t b, uint64_t a);
uint64_t moduloDouble(uint64_t b, uint64_t a);
uint64_t negateDouble(uint64_t a);
uint64_t squareRootDouble(uint64_t a);
uint64_t doubleToFloat(int64_t a);
int64_t doubleToInt(int64_t a);
int64_t doubleToLong(int64_t a);
uint64_t addFloat(uint32_t b, uint32_t a);
uint64_t subtractFloat(uint32_t b, uint32_t a);
uint64_t multiplyFloat(uint32_t b, uint32_t a);
uint64_t divideFloat(uint32_t b, uint32_t a);
uint64_t moduloFloat(uint32_t b, uint32_t a);
uint64_t negateFloat(uint32_t a);
uint64_t absoluteFloat(uint32_t a);
int64_t absoluteLong(int64_t a);
int64_t absoluteInt(int32_t a);
uint64_t floatToDouble(int32_t a);
int64_t floatToInt(int32_t a);
int64_t floatToLong(int32_t a);
uint64_t intToDouble(int32_t a);
uint64_t intToFloat(int32_t a);
uint64_t longToDouble(int64_t a);
uint64_t longToFloat(int64_t a);

}  // namespace runtime
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_RUNTIME_H
