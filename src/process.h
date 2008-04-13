/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef PROCESS_H
#define PROCESS_H

#include "common.h"
#include "system.h"
#include "machine.h"
#include "constants.h"

namespace vm {

inline int16_t
codeReadInt16(Thread* t, object code, unsigned& ip)
{
  uint8_t v1 = codeBody(t, code, ip++);
  uint8_t v2 = codeBody(t, code, ip++);
  return ((v1 << 8) | v2);
}

inline int32_t
codeReadInt32(Thread* t, object code, unsigned& ip)
{
  uint8_t v1 = codeBody(t, code, ip++);
  uint8_t v2 = codeBody(t, code, ip++);
  uint8_t v3 = codeBody(t, code, ip++);
  uint8_t v4 = codeBody(t, code, ip++);
  return ((v1 << 24) | (v2 << 16) | (v3 << 8) | v4);
}

inline object
resolveClassInObject(Thread* t, object container, unsigned classOffset)
{
  object o = cast<object>(container, classOffset);
  if (objectClass(t, o) == arrayBody(t, t->m->types, Machine::ByteArrayType)) {
    PROTECT(t, container);

    o = resolveClass(t, o);
    if (UNLIKELY(t->exception)) return 0;
    
    set(t, container, classOffset, o);
  }
  return o; 
}

inline object
resolveClassInPool(Thread* t, object pool, unsigned index)
{
  object o = singletonObject(t, pool, index);
  if (objectClass(t, o) == arrayBody(t, t->m->types, Machine::ByteArrayType)) {
    PROTECT(t, pool);

    o = resolveClass(t, o);
    if (UNLIKELY(t->exception)) return 0;
    
    set(t, pool, SingletonBody + (index * BytesPerWord), o);
  }
  return o; 
}

inline object
resolve(Thread* t, object pool, unsigned index,
        object (*find)(vm::Thread*, object, object, object),
        object (*makeError)(vm::Thread*, object))
{
  object o = singletonObject(t, pool, index);
  if (objectClass(t, o) == arrayBody(t, t->m->types, Machine::ReferenceType))
  {
    PROTECT(t, pool);

    object reference = o;
    PROTECT(t, reference);

    object class_ = resolveClassInObject(t, o, ReferenceClass);
    if (UNLIKELY(t->exception)) return 0;
    
    o = findInHierarchy
      (t, class_, referenceName(t, reference), referenceSpec(t, reference),
       find, makeError);
    if (UNLIKELY(t->exception)) return 0;
    
    set(t, pool, SingletonBody + (index * BytesPerWord), o);
  }

  return o;
}

inline object
resolveField(Thread* t, object pool, unsigned index)
{
  return resolve(t, pool, index, findFieldInClass, makeNoSuchFieldError);
}

inline object
resolveMethod(Thread* t, object pool, unsigned index)
{
  return resolve(t, pool, index, findMethodInClass, makeNoSuchMethodError);
}

inline bool
isSuperclass(Thread* t, object class_, object base)
{
  for (object oc = classSuper(t, base); oc; oc = classSuper(t, oc)) {
    if (oc == class_) {
      return true;
    }
  }
  return false;
}

inline bool
isSpecialMethod(Thread* t, object method, object class_)
{
  return (classFlags(t, class_) & ACC_SUPER)
    and strcmp(reinterpret_cast<const int8_t*>("<init>"), 
               &byteArrayBody(t, methodName(t, method), 0)) != 0
    and isSuperclass(t, methodClass(t, method), class_);
}

inline object
findMethod(Thread* t, object method, object class_)
{
  return arrayBody(t, classVirtualTable(t, class_), 
                   methodOffset(t, method));
}

void*
resolveNativeMethod2(Thread* t, object method);

inline void*
resolveNativeMethod(Thread* t, object method)
{
  if (methodCode(t, method)) {
    return pointerValue(t, methodCode(t, method));
  } else {
    return resolveNativeMethod2(t, method);
  }
}

inline object
findInterfaceMethod(Thread* t, object method, object class_)
{
  assert(t, (classVmFlags(t, class_) & BootstrapFlag) == 0);

  object interface = methodClass(t, method);
  object itable = classInterfaceTable(t, class_);
  for (unsigned i = 0; i < arrayLength(t, itable); i += 2) {
    if (arrayBody(t, itable, i) == interface) {
      return arrayBody(t, arrayBody(t, itable, i + 1),
                       methodOffset(t, method));
    }
  }
  abort(t);
}

inline void
populateMultiArray(Thread* t, object array, int32_t* counts,
                   unsigned index, unsigned dimensions)
{
  if (index + 1 == dimensions or counts[index] == 0) {
    return;
  }

  PROTECT(t, array);

  object spec = className(t, objectClass(t, array));
  PROTECT(t, spec);

  object elementSpec = makeByteArray
    (t, byteArrayLength(t, spec) - 1, false);
  memcpy(&byteArrayBody(t, elementSpec, 0),
         &byteArrayBody(t, spec, 1),
         byteArrayLength(t, spec) - 1);

  object class_ = resolveClass(t, elementSpec);
  PROTECT(t, class_);

  for (int32_t i = 0; i < counts[index]; ++i) {
    object a = makeArray(t, counts[index + 1], true);
    setObjectClass(t, a, class_);
    set(t, array, ArrayBody + (i * BytesPerWord), a);
    
    populateMultiArray(t, a, counts, index + 1, dimensions);
  }
}

int
findLineNumber(Thread* t, object method, unsigned ip);

} // namespace vm

#endif//PROCESS_H
