/* Copyright (c) 2008-2014, Avian Contributors

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
        GcMethod* method = walker->method();
        if (isAssignableFrom
            (t, type(t, GcThrowable::Type), cast<GcClass>(t, method->class_()))
            and vm::strcmp(reinterpret_cast<const int8_t*>("<init>"),
                           &byteArrayBody(t, method->name(), 0))
            == 0)
        {
          return true;
        } else {
          trace = reinterpret_cast<object>(makeTrace(t, walker));
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
               (t, reinterpret_cast<object>(objectClass(t, src)), reinterpret_cast<object>(objectClass(t, dst)))))
    {
      unsigned elementSize = objectClass(t, src)->arrayElementSize();

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

            if (objectClass(t, dst)->objectMask()) {
              mark(t, dst, ArrayBody + (dstOffset * BytesPerWord), length);
            }

            return;
          } else {
            throwNew(t, GcIndexOutOfBoundsException::Type);
          }
        } else {
          return;
        }
      }
    }
  } else {
    throwNew(t, GcNullPointerException::Type);
    return;
  }

  throwNew(t, GcArrayStoreException::Type);
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
    throwNew(t, GcUnsatisfiedLinkError::Type,
             "library not found in %s: %s", path, name);
  }

  return lib;
}

object
clone(Thread* t, object o)
{
  PROTECT(t, o);

  GcClass* class_ = objectClass(t, o);
  unsigned size = baseSize(t, o, class_) * BytesPerWord;
  object clone;

  if (class_->arrayElementSize()) {
    clone = static_cast<object>(allocate(t, size, class_->objectMask()));
    memcpy(clone, o, size);
    // clear any object header flags:
    setObjectClass(t, o, objectClass(t, o));
  } else if (instanceOf(t, type(t, GcCloneable::Type), o)) {
    clone = make(t, class_);
    memcpy(reinterpret_cast<void**>(clone) + 1,
           reinterpret_cast<void**>(o) + 1,
           size - BytesPerWord);
  } else {
    object classNameSlash = objectClass(t, o)->name();
    THREAD_RUNTIME_ARRAY(t, char, classNameDot, byteArrayLength(t, classNameSlash));
    replace('/', '.', RUNTIME_ARRAY_BODY(classNameDot),
            reinterpret_cast<char*>(&byteArrayBody(t, classNameSlash, 0)));
    throwNew(t, GcCloneNotSupportedException::Type, "%s",
             RUNTIME_ARRAY_BODY(classNameDot));
  }

  return clone;
}

object
makeStackTraceElement(Thread* t, object e)
{
  PROTECT(t, e);

  GcMethod* method = cast<GcMethod>(t, traceElementMethod(t, e));
  PROTECT(t, method);

  object class_name = className(t, method->class_());
  PROTECT(t, class_name);

  THREAD_RUNTIME_ARRAY(t, char, s, byteArrayLength(t, class_name));
  replace('/', '.', RUNTIME_ARRAY_BODY(s),
          reinterpret_cast<char*>(&byteArrayBody(t, class_name, 0)));
  class_name = makeString(t, "%s", RUNTIME_ARRAY_BODY(s));

  object method_name = method->name();
  PROTECT(t, method_name);

  method_name = t->m->classpath->makeString
    (t, method_name, 0, byteArrayLength(t, method_name) - 1);

  unsigned line = t->m->processor->lineNumber
    (t, method, traceElementIp(t, e));

  object file = classSourceFile(t, method->class_());
  file = file ? t->m->classpath->makeString
    (t, file, 0, byteArrayLength(t, file) - 1) : 0;

  return reinterpret_cast<object>(makeStackTraceElement(t, class_name, method_name, file, line));
}

object
translateInvokeResult(Thread* t, unsigned returnCode, object o)
{
  switch (returnCode) {
  case ByteField:
    return reinterpret_cast<object>(makeByte(t, intValue(t, o)));

  case BooleanField:
    return reinterpret_cast<object>(makeBoolean(t, intValue(t, o) != 0));

  case CharField:
    return reinterpret_cast<object>(makeChar(t, intValue(t, o)));

  case ShortField:
    return reinterpret_cast<object>(makeShort(t, intValue(t, o)));

  case FloatField:
    return reinterpret_cast<object>(makeFloat(t, intValue(t, o)));

  case IntField:
  case LongField:
  case ObjectField:
  case VoidField:
    return o;

  case DoubleField:
    return reinterpret_cast<object>(makeDouble(t, longValue(t, o)));

  default:
    abort(t);
  }
}

GcClass*
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

      GcClass* type = resolveClassBySpec
        (t, loader, reinterpret_cast<char*>(&byteArrayBody(t, spec, start)),
         offset - start);

      list = reinterpret_cast<object>(makePair(t, reinterpret_cast<object>(type), list));

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

      GcClass* type = resolveClassBySpec
        (t, loader, reinterpret_cast<char*>(&byteArrayBody(t, spec, start)),
         offset - start);

      list = reinterpret_cast<object>(makePair(t, reinterpret_cast<object>(type), list));
      ++ count;
    } break;

    default:
      list = reinterpret_cast<object>(makePair
        (t, reinterpret_cast<object>(primitiveClass(t, byteArrayBody(t, spec, offset))), list));
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
    (t, type(t, GcJclass::Type), *parameterCount);
  PROTECT(t, array);

  for (int i = *parameterCount - 1; i >= 0; --i) {
    object c = getJClass(t, cast<GcClass>(t, pairFirst(t, list)));
    set(t, array, ArrayBody + (i * BytesPerWord), c);
    list = pairSecond(t, list);
  }

  return array;
}

object
resolveExceptionJTypes(Thread* t, object loader, object addendum)
{
  if (addendum == 0 or methodAddendumExceptionTable(t, addendum) == 0) {
    return makeObjectArray(t, type(t, GcJclass::Type), 0);
  }

  PROTECT(t, loader);
  PROTECT(t, addendum);

  object array = makeObjectArray
    (t, type(t, GcJclass::Type),
     shortArrayLength(t, methodAddendumExceptionTable(t, addendum)));
  PROTECT(t, array);

  for (unsigned i = 0; i < shortArrayLength
         (t, methodAddendumExceptionTable(t, addendum)); ++i)
  {
    uint16_t index = shortArrayBody
      (t, methodAddendumExceptionTable(t, addendum), i) - 1;

    object o = singletonObject(t, cast<GcSingleton>(t, addendumPool(t, addendum)), index);

    if (objectClass(t, o) == type(t, GcReference::Type)) {
      o = reinterpret_cast<object>(resolveClass(t, loader, referenceName(t, o)));

      set(t, addendumPool(t, addendum), SingletonBody + (index * BytesPerWord),
          o);
    }

    o = getJClass(t, cast<GcClass>(t, o));

    set(t, array, ArrayBody + (i * BytesPerWord), o);
  }

  return array;
}

object
invoke(Thread* t, GcMethod* method, object instance, object args)
{
  PROTECT(t, method);
  PROTECT(t, instance);
  PROTECT(t, args);

  if (method->flags() & ACC_STATIC) {
    instance = 0;
  }

  if ((args == 0 ? 0 : objectArrayLength(t, args))
      != method->parameterCount())
  {
    throwNew(t, GcIllegalArgumentException::Type);
  }

  if (method->parameterCount()) {
    unsigned specLength = byteArrayLength(t, method->spec());
    THREAD_RUNTIME_ARRAY(t, char, spec, specLength);
    memcpy(RUNTIME_ARRAY_BODY(spec),
           &byteArrayBody(t, method->spec(), 0), specLength);
    unsigned i = 0;
    for (MethodSpecIterator it(t, RUNTIME_ARRAY_BODY(spec)); it.hasNext();) {
      GcClass* type;
      bool objectType = false;
      const char* p = it.next();
      switch (*p) {
      case 'Z': type = vm::type(t, GcBoolean::Type); break;
      case 'B': type = vm::type(t, GcByte::Type); break;
      case 'S': type = vm::type(t, GcShort::Type); break;
      case 'C': type = vm::type(t, GcChar::Type); break;
      case 'I': type = vm::type(t, GcInt::Type); break;
      case 'F': type = vm::type(t, GcFloat::Type); break;
      case 'J': type = vm::type(t, GcLong::Type); break;
      case 'D': type = vm::type(t, GcDouble::Type); break;

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
          (t, classLoader(t, method->class_()),
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

        throwNew(t, GcIllegalArgumentException::Type);
      }
    }
  }

  initClass(t, cast<GcClass>(t, method->class_()));

  unsigned returnCode = method->returnCode();

  THREAD_RESOURCE0(t, {
      if (t->exception) {
        t->exception = makeThrowable
          (t, GcInvocationTargetException::Type, 0, 0, t->exception);

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
intercept(Thread* t, GcClass* c, const char* name, const char* spec,
          void* function, bool updateRuntimeData)
{
  GcMethod* m = findMethodOrNull(t, c, name, spec);
  if (m) {
    PROTECT(t, m);

    m->flags() |= ACC_NATIVE;

    if (updateRuntimeData) {
      GcMethod* clone = methodClone(t, m);

      // make clone private to prevent vtable updates at compilation
      // time.  Otherwise, our interception might be bypassed by calls
      // through the vtable.
      clone->flags() |= ACC_PRIVATE;

      object native = reinterpret_cast<object>(makeNativeIntercept(t, function, true, reinterpret_cast<object>(clone)));

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

  object n = reinterpret_cast<object>(makeByteArray(t, nameLength + 1));
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
      object finder = reinterpret_cast<object>(makeFinder
        (t, f, n, root(t, Machine::VirtualFileFinders)));

      setRoot(t, Machine::VirtualFileFinders, finder);

      return f;
    }
  }

  return 0;
}

object
getDeclaredClasses(Thread* t, object c, bool publicOnly)
{
  object addendum = classAddendum(t, c);
  if (addendum) {
    object table = classAddendumInnerClassTable(t, addendum);
    if (table) {
      PROTECT(t, table);

      unsigned count = 0;
      for (unsigned i = 0; i < arrayLength(t, table); ++i) {
        object reference = arrayBody(t, table, i);
        object outer = innerClassReferenceOuter(t, reference);
        if (outer and byteArrayEqual(t, outer, className(t, c))
            and ((not publicOnly)
                 or (innerClassReferenceFlags(t, reference) & ACC_PUBLIC)))
        {
          ++ count;
        }
      }

      object result = makeObjectArray(t, type(t, GcJclass::Type), count);
      PROTECT(t, result);

      for (unsigned i = 0; i < arrayLength(t, table); ++i) {
        object reference = arrayBody(t, table, i);
        object outer = innerClassReferenceOuter(t, reference);
        if (outer and byteArrayEqual(t, outer, className(t, c))
            and ((not publicOnly)
                 or (innerClassReferenceFlags(t, reference) & ACC_PUBLIC)))
        {
          object inner = getJClass(
              t,
              resolveClass(
                  t,
                  classLoader(t, c),
                  innerClassReferenceInner(t, arrayBody(t, table, i))));

          -- count;
          set(t, result, ArrayBody + (count * BytesPerWord), inner);
        }
      }

      return result;
    }
  }

  return makeObjectArray(t, type(t, GcJclass::Type), 0);
}

object
getDeclaringClass(Thread* t, object c)
{
  object addendum = classAddendum(t, c);
  if (addendum) {
    object table = classAddendumInnerClassTable(t, addendum);
    if (table) {
      for (unsigned i = 0; i < arrayLength(t, table); ++i) {
        object reference = arrayBody(t, table, i);
        if (innerClassReferenceOuter(t, reference) and strcmp
            (&byteArrayBody(t, innerClassReferenceInner(t, reference), 0),
             &byteArrayBody(t, className(t, c), 0)) == 0)
        {
          return getJClass(
              t,
              resolveClass(t,
                           classLoader(t, c),
                           innerClassReferenceOuter(t, reference)));
        }
      }
    }
  }

  return 0;
}

unsigned
classModifiers(Thread* t, object c)
{
  object addendum = classAddendum(t, c);
  if (addendum) {
    object table = classAddendumInnerClassTable(t, addendum);
    if (table) {
      for (unsigned i = 0; i < arrayLength(t, table); ++i) {
        object reference = arrayBody(t, table, i);
        if (0 == strcmp
            (&byteArrayBody(t, className(t, c), 0),
             &byteArrayBody(t, innerClassReferenceInner(t, reference), 0)))
        {
          return innerClassReferenceFlags(t, reference);
        }
      }
    }
  }

  return classFlags(t, c);
}

} // namespace vm

#endif//CLASSPATH_COMMON_H
