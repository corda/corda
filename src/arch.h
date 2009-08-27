/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef ARCH_H
#define ARCH_H

#include "common.h"

extern "C" void NO_RETURN
vmJump(void* address, void* base, void* stack, void* thread,
       uintptr_t returnLow, uintptr_t returnHigh);

#if (defined ARCH_x86_32) || (defined ARCH_x86_64)
#  include "x86.h"
#elif defined ARCH_powerpc
#  include "powerpc.h"
#elif defined ARCH_arm
#  include "arm.h"
#else
#  error unsupported architecture
#endif

#endif//ARCH_H
