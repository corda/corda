/* Copyright (c) 2010-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef CLASSPATH_COMMON_H
#define CLASSPATH_COMMON_H

#include <avian/util/string.h>
#include <avian/util/runtime-array.h>

using namespace avian::util;

namespace vm {

object
getTrace(Thread* t, unsigned skipCount)
{
  class Visitor: public Processor::StackVisitor {
   public:
    Visitor(Thread* t, int skipCount):
      t(t), trace(0), skipCount(skipCount)
    { }

    virtual bool visit(Processor::StackWalker* walker) {
      if (skipCount == 0) {
        object method = walker->method();
        if (isAssignableFrom
            (t, type(t, Machine::ThrowableType), methodClass(t, method))
            and vm::strcmp(reinterpret_cast<const int8_t*>("<init>"),
                           &byteArrayBody(t, methodName(t, method), 0))
            == 0)
        {
          return true;
        } else {
          trace = makeTrace(t, walker);
          return false;
        }
      } else {
        -- skipCount;
        return true;
      }
    }

    Thread* t;
    object trace;
    unsigned skipCount;
  } v(t, skipCount);

  t->m->processor->walkStack(t, &v);

  if (v.trace == 0) v.trace = makeObjectArray(t, 0);

  return v.trace;
}

bool
compatibleArrayTypes(Thread* t, object a, object b)
{
  return classArrayElementSize(t, a)
    and classArrayElementSize(t, b)
    and (a == b
         or (not ((classVmFlags(t, a) & PrimitiveFlag)
                  or (classVmFlags(t, b) & PrimitiveFlag))));
}

void
arrayCopy(Thread* t, object src, int32_t srcOffset, object dst,
          int32_t dstOffset, int32_t length)
{
  if (LIKELY(src and dst)) {
    if (LIKELY(compatibleArrayTypes
               (t, objectClass(t, src), objectClass(t, dst))))
    {
      unsigned elementSize = classArrayElementSize(t, objectClass(t, src));

      if (LIKELY(elementSize)) {
        intptr_t sl = fieldAtOffset<uintptr_t>(src, BytesPerWord);
        intptr_t dl = fieldAtOffset<uintptr_t>(dst, BytesPerWord);
        if (LIKELY(length > 0)) {
          if (LIKELY(srcOffset >= 0 and srcOffset + length <= sl and
                     dstOffset >= 0 and dstOffset + length <= dl))
          {
            uint8_t* sbody = &fieldAtOffset<uint8_t>(src, ArrayBody);
            uint8_t* dbody = &fieldAtOffset<uint8_t>(dst, ArrayBody);
            if (src == dst) {
              memmove(dbody + (dstOffset * elementSize),
                      sbody + (srcOffset * elementSize),
                      length * elementSize);
            } else {
              memcpy(dbody + (dstOffset * elementSize),
                     sbody + (srcOffset * elementSize),
                     length * elementSize);
            }

            if (classObjectMask(t, objectClass(t, dst))) {
              mark(t, dst, ArrayBody + (dstOffset * BytesPerWord), length);
            }

            return;
          } else {
            throwNew(t, Machine::IndexOutOfBoundsExceptionType);
          }
        } else {
          return;
        }
      }
    }
  } else {
    throwNew(t, Machine::NullPointerExceptionType);
    return;
  }

  throwNew(t, Machine::ArrayStoreExceptionType);
}

void
runOnLoadIfFound(Thread* t, System::Library* library)
{
  void* p = library->resolve("JNI_OnLoad");

#ifdef PLATFORM_WINDOWS
  if (p == 0) {
    p = library->resolve("_JNI_OnLoad@8");
    if (p == 0) {
      p = library->resolve("JNI_OnLoad@8");
    }
  }
#endif

  if (p) {
    jint (JNICALL * JNI_OnLoad)(Machine*, void*);
    memcpy(&JNI_OnLoad, &p, sizeof(void*));
    JNI_OnLoad(t->m, 0);
  }
}

System::Library*
loadLibrary(Thread* t, const char* name)
{
  ACQUIRE(t, t->m->classLock);

  System::Library* last = t->m->libraries;
  for (System::Library* lib = t->m->libraries; lib; lib = lib->next()) {
    if (lib->name() and ::strcmp(lib->name(), name) == 0) {
      // already loaded
      return lib;
    }
    last = lib;
  }

  System::Library* lib;
  if (t->m->system->success(t->m->system->load(&lib, name))) {
    last->setNext(lib);
    return lib;
  } else {
    return 0;
  }
}

System::Library*
loadLibrary(Thread* t, const char* path, const char* name, bool mapName,
            bool runOnLoad, bool throw_ = true)
{
  ACQUIRE(t, t->m->classLock);

  char* mappedName;
  unsigned nameLength = strlen(name);
  if (mapName) {
    const char* builtins = findProperty(t, "avian.builtins");
    if (builtins) {
      const char* s = builtins;
      while (*s) {
        if (::strncmp(s, name, nameLength) == 0
            and (s[nameLength] == ',' or s[nameLength] == 0))
        {
          // library is built in to this executable
          if (runOnLoad and not t->m->triedBuiltinOnLoad) {
            t->m->triedBuiltinOnLoad = true;
            // todo: release the classLock before calling this to
            // avoid the possibility of deadlock:
            runOnLoadIfFound(t, t->m->libraries);
          }
          return t->m->libraries;
        } else {
          while (*s and *s != ',') ++ s;
          if (*s) ++ s;
        }
      }
    }

    const char* prefix = t->m->system->libraryPrefix();
    const char* suffix = t->m->system->librarySuffix();
    unsigned mappedNameLength = nameLength + strlen(prefix) + strlen(suffix);

    mappedName = static_cast<char*>
      (t->m->heap->allocate(mappedNameLength + 1));

    snprintf(mappedName, mappedNameLength + 1, "%s%s%s", prefix, name, suffix);

    name = mappedName;
    nameLength = mappedNameLength;
  } else {
    mappedName = 0;
  }

  THREAD_RESOURCE2
    (t, char*, mappedName, unsigned, nameLength, if (mappedName) {
      t->m->heap->free(mappedName, nameLength + 1);
    });

  System::Library* lib = 0;
  for (Tokenizer tokenizer(path, t->m->system->pathSeparator());
       tokenizer.hasMore();)
  {
    String token(tokenizer.next());

    unsigned fullNameLength = token.length + 1 + nameLength;
    THREAD_RUNTIME_ARRAY(t, char, fullName, fullNameLength + 1);

    snprintf(RUNTIME_ARRAY_BODY(fullName), fullNameLength + 1,
             "%.*s/%s", token.length, token.text, name);

    lib = loadLibrary(t, RUNTIME_ARRAY_BODY(fullName));
    if (lib) break;
  }

  if (lib == 0) {
    lib = loadLibrary(t, name);
  }

  if (lib) {
    if (runOnLoad) {
      runOnLoadIfFound(t, lib);
    }
  } else if (throw_) {
    throwNew(t, Machine::UnsatisfiedLinkErrorType, "library not found: %s",
             name);
  }

  return lib;
}

object
clone(Thread* t, object o)
{
  PROTECT(t, o);

  object class_ = objectClass(t, o);
  unsigned size = baseSize(t, o, class_) * BytesPerWord;
  object clone;

  if (classArrayElementSize(t, class_)) {
    clone = static_cast<object>(allocate(t, size, classObjectMask(t, class_)));
    memcpy(clone, o, size);
    // clear any object header flags:
    setObjectClass(t, o, objectClass(t, o));
  } else if (instanceOf(t, type(t, Machine::CloneableType), o)) {
    clone = make(t, class_);
    memcpy(reinterpret_cast<void**>(clone) + 1,
           reinterpret_cast<void**>(o) + 1,
           size - BytesPerWord);
  } else {
    throwNew(t, Machine::CloneNotSupportedExceptionType, "%s",
             &byteArrayBody(t, className(t, objectClass(t, o)), 0)); 
  }

  return clone;
}

object
makeStackTraceElement(Thread* t, object e)
{
  PROTECT(t, e);

  object class_ = className(t, methodClass(t, traceElementMethod(t, e)));
  PROTECT(t, class_);

  THREAD_RUNTIME_ARRAY(t, char, s, byteArrayLength(t, class_));
  replace('/', '.', RUNTIME_ARRAY_BODY(s),
          reinterpret_cast<char*>(&byteArrayBody(t, class_, 0)));
  class_ = makeString(t, "%s", RUNTIME_ARRAY_BODY(s));

  object method = methodName(t, traceElementMethod(t, e));
  PROTECT(t, method);

  method = t->m->classpath->makeString
    (t, method, 0, byteArrayLength(t, method) - 1);

  unsigned line = t->m->processor->lineNumber
    (t, traceElementMethod(t, e), traceElementIp(t, e));

  object file = classSourceFile(t, methodClass(t, traceElementMethod(t, e)));
  file = file ? t->m->classpath->makeString
    (t, file, 0, byteArrayLength(t, file) - 1) : 0;

  return makeStackTraceElement(t, class_, method, file, line);
}

object
translateInvokeResult(Thread* t, unsigned returnCode, object o)
{
  switch (returnCode) {
  case ByteField:
    return makeByte(t, intValue(t, o));

  case BooleanField:
    return makeBoolean(t, intValue(t, o) != 0);

  case CharField:
    return makeChar(t, intValue(t, o));

  case ShortField:
    return makeShort(t, intValue(t, o));

  case FloatField:
    return makeFloat(t, intValue(t, o));

  case IntField:
  case LongField:
  case ObjectField:
  case VoidField:
    return o;

  case DoubleField:
    return makeDouble(t, longValue(t, o));

  default:
    abort(t);
  }
}

object
resolveClassBySpec(Thread* t, object loader, const char* spec,
                   unsigned specLength)
{
  switch (*spec) {
  case 'L': {
    THREAD_RUNTIME_ARRAY(t, char, s, specLength - 1);
    memcpy(RUNTIME_ARRAY_BODY(s), spec + 1, specLength - 2);
    RUNTIME_ARRAY_BODY(s)[specLength - 2] = 0;
    return resolveClass(t, loader, RUNTIME_ARRAY_BODY(s));
  }
  
  case '[': {
    THREAD_RUNTIME_ARRAY(t, char, s, specLength + 1);
    memcpy(RUNTIME_ARRAY_BODY(s), spec, specLength);
    RUNTIME_ARRAY_BODY(s)[specLength] = 0;
    return resolveClass(t, loader, RUNTIME_ARRAY_BODY(s));
  }

  default:
    return primitiveClass(t, *spec);
  }
}

object
resolveJType(Thread* t, object loader, const char* spec, unsigned specLength)
{
  return getJClass(t, resolveClassBySpec(t, loader, spec, specLength));
}

object
resolveParameterTypes(Thread* t, object loader, object spec,
                      unsigned* parameterCount, unsigned* returnTypeSpec)
{
  PROTECT(t, loader);
  PROTECT(t, spec);

  object list = 0;
  PROTECT(t, list);

  unsigned offset = 1;
  unsigned count = 0;
  while (byteArrayBody(t, spec, offset) != ')') {
    switch (byteArrayBody(t, spec, offset)) {
    case 'L': {
      unsigned start = offset;
      ++ offset;
      while (byteArrayBody(t, spec, offset) != ';') ++ offset;
      ++ offset;

      object type = resolveClassBySpec
        (t, loader, reinterpret_cast<char*>(&byteArrayBody(t, spec, start)),
         offset - start);
      
      list = makePair(t, type, list);

      ++ count;
    } break;
  
    case '[': {
      unsigned start = offset;
      while (byteArrayBody(t, spec, offset) == '[') ++ offset;
      switch (byteArrayBody(t, spec, offset)) {
      case 'L':
        ++ offset;
        while (byteArrayBody(t, spec, offset) != ';') ++ offset;
        ++ offset;
        break;

      default:
        ++ offset;
        break;
      }
      
      object type = resolveClassBySpec
        (t, loader, reinterpret_cast<char*>(&byteArrayBody(t, spec, start)),
         offset - start);
      
      list = makePair(t, type, list);
      ++ count;
    } break;

    default:
      list = makePair
        (t, primitiveClass(t, byteArrayBody(t, spec, offset)), list);
      ++ offset;
      ++ count;
      break;
    }
  }

  *parameterCount = count;
  *returnTypeSpec = offset + 1;
  return list;
}

object
resolveParameterJTypes(Thread* t, object loader, object spec,
                       unsigned* parameterCount, unsigned* returnTypeSpec)
{
  object list = resolveParameterTypes
    (t, loader, spec, parameterCount, returnTypeSpec);

  PROTECT(t, list);
  
  object array = makeObjectArray
    (t, type(t, Machine::JclassType), *parameterCount);
  PROTECT(t, array);

  for (int i = *parameterCount - 1; i >= 0; --i) {
    object c = getJClass(t, pairFirst(t, list));
    set(t, array, ArrayBody + (i * BytesPerWord), c);
    list = pairSecond(t, list);
  }

  return array;
}

object
resolveExceptionJTypes(Thread* t, object loader, object addendum)
{
  if (addendum == 0 or methodAddendumExceptionTable(t, addendum) == 0) {
    return makeObjectArray(t, type(t, Machine::JclassType), 0);
  }

  PROTECT(t, loader);
  PROTECT(t, addendum);

  object array = makeObjectArray
    (t, type(t, Machine::JclassType),
     shortArrayLength(t, methodAddendumExceptionTable(t, addendum)));
  PROTECT(t, array);

  for (unsigned i = 0; i < shortArrayLength
         (t, methodAddendumExceptionTable(t, addendum)); ++i)
  {
    uint16_t index = shortArrayBody
      (t, methodAddendumExceptionTable(t, addendum), i) - 1;

    object o = singletonObject(t, addendumPool(t, addendum), index);

    if (objectClass(t, o) == type(t, Machine::ReferenceType)) {
      o = resolveClass(t, loader, referenceName(t, o));
    
      set(t, addendumPool(t, addendum), SingletonBody + (index * BytesPerWord),
          o);
    }

    o = getJClass(t, o);

    set(t, array, ArrayBody + (i * BytesPerWord), o);
  }

  return array;
}

object
invoke(Thread* t, object method, object instance, object args)
{
  PROTECT(t, method);
  PROTECT(t, instance);
  PROTECT(t, args);

  if (methodFlags(t, method) & ACC_STATIC) {
    instance = 0;
  }

  if ((args == 0 ? 0 : objectArrayLength(t, args))
      != methodParameterCount(t, method))
  {
    throwNew(t, Machine::IllegalArgumentExceptionType);
  }

  if (methodParameterCount(t, method)) {
    PROTECT(t, method);

    unsigned specLength = byteArrayLength(t, methodSpec(t, method));
    THREAD_RUNTIME_ARRAY(t, char, spec, specLength);
    memcpy(RUNTIME_ARRAY_BODY(spec),
           &byteArrayBody(t, methodSpec(t, method), 0), specLength);
    unsigned i = 0;
    for (MethodSpecIterator it(t, RUNTIME_ARRAY_BODY(spec)); it.hasNext();) {
      object type;
      bool objectType = false;
      const char* p = it.next();
      switch (*p) {
      case 'Z': type = vm::type(t, Machine::BooleanType); break;
      case 'B': type = vm::type(t, Machine::ByteType); break;
      case 'S': type = vm::type(t, Machine::ShortType); break;
      case 'C': type = vm::type(t, Machine::CharType); break;
      case 'I': type = vm::type(t, Machine::IntType); break;
      case 'F': type = vm::type(t, Machine::FloatType); break;
      case 'J': type = vm::type(t, Machine::LongType); break;
      case 'D': type = vm::type(t, Machine::DoubleType); break;

      case 'L':
      case '[': {
        objectType = true;
        unsigned nameLength;
        if (*p == 'L') {
          ++ p;
          nameLength = it.s - p;
        } else {
          nameLength = (it.s - p) + 1;
        }
        THREAD_RUNTIME_ARRAY(t, char, name, nameLength);
        memcpy(RUNTIME_ARRAY_BODY(name), p, nameLength - 1);
        RUNTIME_ARRAY_BODY(name)[nameLength - 1] = 0;
        type = resolveClass
          (t, classLoader(t, methodClass(t, method)),
           RUNTIME_ARRAY_BODY(name));
      } break;

      default:
        abort();
      }

      object arg = objectArrayBody(t, args, i++);
      if ((arg == 0 and (not objectType))
          or (arg and (not instanceOf(t, type, arg))))
      {
        // fprintf(stderr, "%s is not a %s\n", arg ? &byteArrayBody(t, className(t, objectClass(t, arg)), 0) : reinterpret_cast<const int8_t*>("<null>"), &byteArrayBody(t, className(t, type), 0));

        throwNew(t, Machine::IllegalArgumentExceptionType);
      }
    }
  }

  unsigned returnCode = methodReturnCode(t, method);

  THREAD_RESOURCE0(t, {
      if (t->exception) {
        object exception = t->exception;
        t->exception = makeThrowable
          (t, Machine::InvocationTargetExceptionType, 0, 0, exception);
        
        set(t, t->exception, InvocationTargetExceptionTarget,
            throwableCause(t, t->exception));
      }
    });

  object result;
  if (args) {
    result = t->m->processor->invokeArray(t, method, instance, args);
  } else {
    result = t->m->processor->invoke(t, method, instance);
  }

  return translateInvokeResult(t, returnCode, result);
}

// only safe to call during bootstrap when there's only one thread
// running:
void
intercept(Thread* t, object c, const char* name, const char* spec,
          void* function, bool updateRuntimeData)
{
  object m = findMethodOrNull(t, c, name, spec);
  if (m) {
    PROTECT(t, m);

    object clone;
    if (updateRuntimeData) {
      clone = methodClone(t, m);

      // make clone private to prevent vtable updates at compilation
      // time.  Otherwise, our interception might be bypassed by calls
      // through the vtable.
      methodFlags(t, clone) |= ACC_PRIVATE;
    }

    methodFlags(t, m) |= ACC_NATIVE;

    if (updateRuntimeData) {
      object native = makeNativeIntercept(t, function, true, clone);
      
      PROTECT(t, native);
      
      object runtimeData = getMethodRuntimeData(t, m);
      
      set(t, runtimeData, MethodRuntimeDataNative, native);
    }
  } else {
    // If we can't find the method, just ignore it, since ProGuard may
    // have stripped it out as unused.  Otherwise, the code below can
    // be uncommented for debugging purposes.

    // fprintf(stderr, "unable to find %s%s in %s\n",
    //         name, spec, &byteArrayBody(t, className(t, c), 0));

    // abort(t);
  }
}

Finder*
getFinder(Thread* t, const char* name, unsigned nameLength)
{
  ACQUIRE(t, t->m->referenceLock);
    
  for (object p = root(t, Machine::VirtualFileFinders);
       p; p = finderNext(t, p))
  {
    if (byteArrayLength(t, finderName(t, p)) == nameLength
        and strncmp(reinterpret_cast<const char*>
                    (&byteArrayBody(t, finderName(t, p), 0)),
                    name, nameLength))
    {
      return static_cast<Finder*>(finderFinder(t, p));
    }
  }

  object n = makeByteArray(t, nameLength + 1);
  memcpy(&byteArrayBody(t, n, 0), name, nameLength);

  void* p = t->m->libraries->resolve
    (reinterpret_cast<const char*>(&byteArrayBody(t, n, 0)));

  if (p) {
    uint8_t* (*function)(unsigned*);
    memcpy(&function, &p, BytesPerWord);

    unsigned size;
    uint8_t* data = function(&size);
    if (data) {
      Finder* f = makeFinder(t->m->system, t->m->heap, data, size);
      object finder = makeFinder
        (t, f, n, root(t, Machine::VirtualFileFinders));

      setRoot(t, Machine::VirtualFileFinders, finder);

      return f;
    }
  }

  return 0;
}

} // namespace vm

#endif//CLASSPATH_COMMON_H
