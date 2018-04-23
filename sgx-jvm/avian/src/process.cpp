/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/process.h"

#include <avian/util/runtime-array.h>

using namespace vm;

namespace {

unsigned mangledSize(int8_t c)
{
  switch (c) {
  case '_':
  case ';':
  case '[':
    return 2;

  case '$':
    return 6;

  default:
    return 1;
  }
}

unsigned mangle(int8_t c, char* dst)
{
  switch (c) {
  case '/':
    dst[0] = '_';
    return 1;

  case '_':
    dst[0] = '_';
    dst[1] = '1';
    return 2;

  case ';':
    dst[0] = '_';
    dst[1] = '2';
    return 2;

  case '[':
    dst[0] = '_';
    dst[1] = '3';
    return 2;

  case '$':
    memcpy(dst, "_00024", 6);
    return 6;

  default:
    dst[0] = c;
    return 1;
  }
}

unsigned jniNameLength(Thread* t UNUSED, GcMethod* method, bool decorate)
{
  unsigned size = 0;

  GcByteArray* className = method->class_()->name();
  for (unsigned i = 0; i < className->length() - 1; ++i) {
    size += mangledSize(className->body()[i]);
  }

  ++size;

  GcByteArray* methodName = method->name();
  for (unsigned i = 0; i < methodName->length() - 1; ++i) {
    size += mangledSize(methodName->body()[i]);
  }

  if (decorate) {
    size += 2;

    GcByteArray* methodSpec = method->spec();
    for (unsigned i = 1;
         i < methodSpec->length() - 1 and methodSpec->body()[i] != ')';
         ++i) {
      size += mangledSize(methodSpec->body()[i]);
    }
  }

  return size;
}

void makeJNIName(Thread* t UNUSED,
                 const char* prefix,
                 unsigned prefixLength,
                 char* name,
                 GcMethod* method,
                 bool decorate)
{
  memcpy(name, prefix, prefixLength);
  name += prefixLength;

  GcByteArray* className = method->class_()->name();
  for (unsigned i = 0; i < className->length() - 1; ++i) {
    name += mangle(className->body()[i], name);
  }

  *(name++) = '_';

  GcByteArray* methodName = method->name();
  for (unsigned i = 0; i < methodName->length() - 1; ++i) {
    name += mangle(methodName->body()[i], name);
  }

  if (decorate) {
    *(name++) = '_';
    *(name++) = '_';

    GcByteArray* methodSpec = method->spec();
    for (unsigned i = 1;
         i < methodSpec->length() - 1 and methodSpec->body()[i] != ')';
         ++i) {
      name += mangle(methodSpec->body()[i], name);
    }
  }

  *(name++) = 0;
}

void* resolveNativeMethod(Thread* t,
                          const char* undecorated,
                          const char* decorated)
{
  for (System::Library* lib = t->m->libraries; lib; lib = lib->next()) {
    void* p = lib->resolve(undecorated);
    if (p) {
      return p;
    } else {
      p = lib->resolve(decorated);
      if (p) {
        return p;
      }
    }
  }

  return 0;
}

void* resolveNativeMethod(Thread* t,
                          GcMethod* method,
                          const char* prefix,
                          unsigned prefixLength,
                          int footprint UNUSED)
{
  unsigned undecoratedSize = prefixLength + jniNameLength(t, method, false);
  // extra 6 is for code below:
  THREAD_RUNTIME_ARRAY(t, char, undecorated, undecoratedSize + 1 + 6);
  makeJNIName(t,
              prefix,
              prefixLength,
              RUNTIME_ARRAY_BODY(undecorated) + 1,
              method,
              false);

  unsigned decoratedSize = prefixLength + jniNameLength(t, method, true);
  // extra 6 is for code below:
  THREAD_RUNTIME_ARRAY(t, char, decorated, decoratedSize + 1 + 6);
  makeJNIName(
      t, prefix, prefixLength, RUNTIME_ARRAY_BODY(decorated) + 1, method, true);

  void* p = resolveNativeMethod(t,
                                RUNTIME_ARRAY_BODY(undecorated) + 1,
                                RUNTIME_ARRAY_BODY(decorated) + 1);
  if (p) {
    return p;
  }

#ifdef PLATFORM_WINDOWS
  // on windows, we also try the _%s@%d and %s@%d variants
  if (footprint == -1) {
    footprint = method->parameterFootprint() + 1;
    if (method->flags() & ACC_STATIC) {
      ++footprint;
    }
  }

  *RUNTIME_ARRAY_BODY(undecorated) = '_';
  vm::snprintf(RUNTIME_ARRAY_BODY(undecorated) + undecoratedSize + 1,
               5,
               "@%d",
               footprint * BytesPerWord);

  *RUNTIME_ARRAY_BODY(decorated) = '_';
  vm::snprintf(RUNTIME_ARRAY_BODY(decorated) + decoratedSize + 1,
               5,
               "@%d",
               footprint * BytesPerWord);

  p = resolveNativeMethod(
      t, RUNTIME_ARRAY_BODY(undecorated), RUNTIME_ARRAY_BODY(decorated));
  if (p) {
    return p;
  }

  // one more try without the leading underscore
  p = resolveNativeMethod(t,
                          RUNTIME_ARRAY_BODY(undecorated) + 1,
                          RUNTIME_ARRAY_BODY(decorated) + 1);
  if (p) {
    return p;
  }
#endif

  return 0;
}

GcNative* resolveNativeMethod(Thread* t, GcMethod* method)
{
  void* p = resolveNativeMethod(t, method, "Avian_", 6, 3);
  if (p) {
    return makeNative(t, p, true);
  }

  p = resolveNativeMethod(t, method, "Java_", 5, -1);
  if (p) {
    return makeNative(t, p, false);
  }

  return 0;
}

}  // namespace

namespace vm {

void resolveNative(Thread* t, GcMethod* method)
{
  PROTECT(t, method);

  assertT(t, method->flags() & ACC_NATIVE);

  initClass(t, method->class_());

  if (getMethodRuntimeData(t, method)->native() == 0) {
    GcNative* native = resolveNativeMethod(t, method);
    if (UNLIKELY(native == 0)) {
      throwNew(t,
               GcUnsatisfiedLinkError::Type,
               "%s.%s%s",
               method->class_()->name()->body().begin(),
               method->name()->body().begin(),
               method->spec()->body().begin());
    }

    PROTECT(t, native);

    GcMethodRuntimeData* runtimeData = getMethodRuntimeData(t, method);

    // ensure other threads only see the methodRuntimeDataNative field
    // populated once the object it points to has been populated:
    storeStoreMemoryBarrier();

    runtimeData->setNative(t, native);
  }
}

int findLineNumber(Thread* t UNUSED, GcMethod* method, unsigned ip)
{
  if (method->flags() & ACC_NATIVE) {
    return NativeLine;
  }

  // our parameter indicates the instruction following the one we care
  // about, so we back up first:
  --ip;

  GcLineNumberTable* lnt = method->code()->lineNumberTable();
  if (lnt) {
    unsigned bottom = 0;
    unsigned top = lnt->length();
    for (unsigned span = top - bottom; span; span = top - bottom) {
      unsigned middle = bottom + (span / 2);
      uint64_t ln = lnt->body()[middle];

      if (ip >= lineNumberIp(ln)
          and (middle + 1 == lnt->length()
               or ip < lineNumberIp(lnt->body()[middle + 1]))) {
        return lineNumberLine(ln);
      } else if (ip < lineNumberIp(ln)) {
        top = middle;
      } else if (ip > lineNumberIp(ln)) {
        bottom = middle + 1;
      }
    }

    if (top < lnt->length()) {
      return lineNumberLine(lnt->body()[top]);
    } else {
      return UnknownLine;
    }
  } else {
    return UnknownLine;
  }
}

}  // namespace vm
