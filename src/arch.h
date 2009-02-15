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
vmJump(void* address, void* base, void* stack, void* thread);

#if (defined __i386__) || (defined __x86_64__)
#  include "x86.h"
#elif defined __POWERPC__
#  include "powerpc.h"
#else
#  error unsupported architecture
#endif

#endif//ARCH_H
