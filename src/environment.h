/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_ENVIRONMENT_H
#define AVIAN_ENVIRONMENT_H

#ifndef AVIAN_TARGET_PLATFORM
#error build system should have defined AVIAN_TARGET_PLATFORM
#endif

#ifndef AVIAN_TARGET_ARCH
#error build system should have defined AVIAN_TARGET_ARCH
#endif

#define AVIAN_PLATFORM_UNKNOWN 0
#define AVIAN_PLATFORM_LINUX 1
#define AVIAN_PLATFORM_WINDOWS 2
#define AVIAN_PLATFORM_DARWIN 3

#define AVIAN_ARCH_UNKNOWN 0
#define AVIAN_ARCH_X86 (1 << 8)
#define AVIAN_ARCH_X86_64 (2 << 8)
#define AVIAN_ARCH_ARM (3 << 8)
#define AVIAN_ARCH_POWERPC (4 << 8)

#endif