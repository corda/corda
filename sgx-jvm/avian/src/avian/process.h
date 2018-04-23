/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef PROCESS_H
#define PROCESS_H

#include "avian/common.h"
#include <avian/system/system.h>
#include "avian/machine.h"
#include "avian/constants.h"

namespace vm {

inline int16_t codeReadInt16(Thread* t UNUSED, GcCode* code, unsigned& ip)
{
  uint8_t v1 = code->body()[ip++];
  uint8_t v2 = code->body()[ip++];
  return ((v1 << 8) | v2);
}

inline int32_t codeReadInt32(Thread* t UNUSED, GcCode* code, unsigned& ip)
{
  uint8_t v1 = code->body()[ip++];
  uint8_t v2 = code->body()[ip++];
  uint8_t v3 = code->body()[ip++];
  uint8_t v4 = code->body()[ip++];
  return ((v1 << 24) | (v2 << 16) | (v3 << 8) | v4);
}

inline bool isSuperclass(Thread* t UNUSED, GcClass* class_, GcClass* base)
{
  for (GcClass* oc = base->super(); oc; oc = oc->super()) {
    if (oc == class_) {
      return true;
    }
  }
  return false;
}

inline bool isSpecialMethod(Thread* t, GcMethod* method, GcClass* class_)
{
  return (class_->flags() & ACC_SUPER)
         and strcmp(reinterpret_cast<const int8_t*>("<init>"),
                    method->name()->body().begin()) != 0
         and isSuperclass(t, method->class_(), class_);
}

void resolveNative(Thread* t, GcMethod* method);

int findLineNumber(Thread* t, GcMethod* method, unsigned ip);

}  // namespace vm

#endif  // PROCESS_H
