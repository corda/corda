/* Copyright (c) 2008-2013, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <stdint.h>

namespace avian {
namespace jvm {
namespace debug {

// print out a single instruction (no newline)
// returns number of characters printed
int printInstruction(uint8_t* code, unsigned& ip, const char* prefix = "");
void disassembleCode(const char* prefix, uint8_t* code, unsigned length);

}  // namespace debug
}  // namespace jvm
}  // namespace avian
