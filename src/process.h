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
resolveClass(Thread* t, object container,
             object& (*class_)(vm::Thread*, object))
{
  object o = class_(t, container);
  if (objectClass(t, o) == arrayBody(t, t->m->types, Machine::ByteArrayType))
  {
    PROTECT(t, container);

    o = resolveClass(t, o);
    if (UNLIKELY(t->exception)) return 0;
    
    set(t, class_(t, container), o);
  }
  return o; 
}

inline object
resolveClass(Thread* t, object pool, unsigned index)
{
  object o = arrayBody(t, pool, index);
  if (objectClass(t, o) == arrayBody(t, t->m->types, Machine::ByteArrayType))
  {
    PROTECT(t, pool);

    o = resolveClass(t, o);
    if (UNLIKELY(t->exception)) return 0;
    
    set(t, arrayBody(t, pool, index), o);
  }
  return o; 
}

inline object
resolve(Thread* t, object pool, unsigned index,
        object (*find)(vm::Thread*, object, object, object),
        object (*makeError)(vm::Thread*, object))
{
  object o = arrayBody(t, pool, index);
  if (objectClass(t, o) == arrayBody(t, t->m->types, Machine::ReferenceType))
  {
    PROTECT(t, pool);

    object reference = o;
    PROTECT(t, reference);

    object class_ = resolveClass(t, o, referenceClass);
    if (UNLIKELY(t->exception)) return 0;
    
    o = findInHierarchy
      (t, class_, referenceName(t, reference), referenceSpec(t, reference),
       find, makeError);
    if (UNLIKELY(t->exception)) return 0;
    
    set(t, arrayBody(t, pool, index), o);
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

inline bool
methodVirtual(Thread* t, object method)
{
  return (methodFlags(t, method) & (ACC_STATIC | ACC_FINAL | ACC_PRIVATE))
    == 0;
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
  }
  return 0;
}

} // namespace vm

#endif//PROCESS_H
