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
  if (objectClass(t, o) == arrayBody(t, t->m->types, Machine::ByteArrayType))
  {
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
  if (objectClass(t, o) == arrayBody(t, t->m->types, Machine::ByteArrayType))
  {
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

inline void*
resolveNativeMethod(Thread* t, object method)
{
  for (System::Library* lib = t->m->libraries; lib; lib = lib->next()) {
    void* p = lib->resolve(reinterpret_cast<const char*>
                           (&byteArrayBody(t, methodCode(t, method), 0)));
    if (p) {
      return p;
    }
#ifdef __MINGW32__
    else {
      // on windows, we also try the _%s@%d variant, since the SWT
      // libraries use it.

      unsigned footprint = methodParameterFootprint(t, method) + 1;
      if (methodFlags(t, method) & ACC_STATIC) {
        ++ footprint;
      }

      unsigned size = byteArrayLength(t, methodCode(t, method)) + 5;
      char buffer[size];
      snprintf(buffer, size, "_%s@%d",
               &byteArrayBody(t, methodCode(t, method), 0),
               footprint * BytesPerWord);

      p = lib->resolve(buffer);
      if (p) {
        return p;
      }
    }
#endif
  }
  return 0;
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

inline ExceptionHandler*
findExceptionHandler(Thread* t, object method, unsigned ip)
{
  PROTECT(t, method);

  object eht = codeExceptionHandlerTable(t, methodCode(t, method));
      
  if (eht) {
    for (unsigned i = 0; i < exceptionHandlerTableLength(t, eht); ++i) {
      ExceptionHandler* eh = exceptionHandlerTableBody(t, eht, i);

      if (ip - 1 >= exceptionHandlerStart(eh)
          and ip - 1 < exceptionHandlerEnd(eh))
      {
        object catchType = 0;
        if (exceptionHandlerCatchType(eh)) {
          object e = t->exception;
          t->exception = 0;
          PROTECT(t, e);

          PROTECT(t, eht);
          catchType = resolveClassInPool
            (t, codePool(t, methodCode(t, method)),
             exceptionHandlerCatchType(eh) - 1);

          if (catchType) {
            eh = exceptionHandlerTableBody(t, eht, i);
            t->exception = e;
          } else {
            // can't find what we're supposed to catch - move on.
            continue;
          }
        }

        if (catchType == 0 or instanceOf(t, catchType, t->exception)) {
          return eh;
        }
      }
    }
  }

  return 0;
}

inline int
findLineNumber(Thread* t, object method, unsigned ip)
{
  if (methodFlags(t, method) & ACC_NATIVE) {
    return NativeLine;
  }

  // our parameter indicates the instruction following the one we care
  // about, so we back up first:
  -- ip;

  object code = methodCode(t, method);
  object lnt = codeLineNumberTable(t, code);
  if (lnt) {
    unsigned bottom = 0;
    unsigned top = lineNumberTableLength(t, lnt);
    for (unsigned span = top - bottom; span; span = top - bottom) {
      unsigned middle = bottom + (span / 2);
      LineNumber* ln = lineNumberTableBody(t, lnt, middle);

      if (ip >= lineNumberIp(ln)
          and (middle + 1 == lineNumberTableLength(t, lnt)
               or ip < lineNumberIp(lineNumberTableBody(t, lnt, middle + 1))))
      {
        return lineNumberLine(ln);
      } else if (ip < lineNumberIp(ln)) {
        top = middle;
      } else if (ip > lineNumberIp(ln)) {
        bottom = middle + 1;
      }
    }

    abort(t);
  } else {
    return UnknownLine;
  }
}

} // namespace vm

#endif//PROCESS_H
