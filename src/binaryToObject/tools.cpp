/* Copyright (c) 2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "tools.h"

namespace avian {

namespace tools {


Platform* Platform::first = 0;

PlatformInfo::OperatingSystem PlatformInfo::osFromString(const char* os) {
  if(strcmp(os, "linux") == 0) {
    return Linux;
  } else if(strcmp(os, "windows") == 0) {
    return Windows;
  } else if(strcmp(os, "darwin") == 0) {
    return Darwin;
  } else {
    return UnknownOS;
  }
}

PlatformInfo::Architecture PlatformInfo::archFromString(const char* arch) {
  if(strcmp(arch, "i386") == 0) {
    return x86;
  } else if(strcmp(arch, "x86_64") == 0) {
    return x86_64;
  } else if(strcmp(arch, "powerpc") == 0) {
    return PowerPC;
  } else if(strcmp(arch, "arm") == 0) {
    return Arm;
  } else {
    return UnknownArch;
  }
}

Platform* Platform::getPlatform(PlatformInfo info) {
  for(Platform* p = first; p; p = p->next) {
    if(p->info == info) {
      return p;
    }
  }
  return 0;
}

} // namespace tools

} // namespace avian