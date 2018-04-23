/* Copyright (c) 2008-2015, Avian Contributors

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
#include <avian/util/tokenizer.h>

using namespace avian::util;

namespace vm {

object getTrace(Thread* t, unsigned skipCount)
{
  class Visitor : public Processor::StackVisitor {
   public:
    Visitor(Thread* t, int skipCount) : t(t), trace(0), skipCount(skipCount)
    {
    }

    virtual bool visit(Processor::StackWalker* walker)
    {
      if (skipCount == 0) {
        GcMethod* method = walker->method();
        if (isAssignableFrom(t, type(t, GcThrowable::Type), method->class_())
            and vm::strcmp(reinterpret_cast<const int8_t*>("<init>"),
                           method->name()->body().begin()) == 0) {
          return true;
        } else {
          trace = makeTrace(t, walker);
          return false;
        }
      } else {
        --skipCount;
        return true;
      }
    }

    Thread* t;
    object trace;
    unsigned skipCount;
  } v(t, skipCount);

  t->m->processor->walkStack(t, &v);

  if (v.trace == 0)
    v.trace = makeObjectArray(t, 0);

  return v.trace;
}

bool compatibleArrayTypes(Thread* t UNUSED, GcClass* a, GcClass* b)
{
  return a->arrayElementSize() and b->arrayElementSize()
         and (a == b or (not((a->vmFlags() & PrimitiveFlag)
                             or (b->vmFlags() & PrimitiveFlag))));
}

void arrayCopy(Thread* t,
               object src,
               int32_t srcOffset,
               object dst,
               int32_t dstOffset,
               int32_t length)
{
  if (LIKELY(src and dst)) {
    if (LIKELY(compatibleArrayTypes(
            t, objectClass(t, src), objectClass(t, dst)))) {
      unsigned elementSize = objectClass(t, src)->arrayElementSize();

      if (LIKELY(elementSize)) {
        intptr_t sl = fieldAtOffset<uintptr_t>(src, BytesPerWord);
        intptr_t dl = fieldAtOffset<uintptr_t>(dst, BytesPerWord);
        if (LIKELY(length > 0)) {
          if (LIKELY(srcOffset >= 0 and srcOffset + length <= sl
                     and dstOffset >= 0 and dstOffset + length <= dl)) {
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

void runOnLoadIfFound(Thread* t, System::Library* library)
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
    jint(JNICALL * JNI_OnLoad)(Machine*, void*);
    memcpy(&JNI_OnLoad, &p, sizeof(void*));
    JNI_OnLoad(t->m, 0);
  }
}

System::Library* loadLibrary(Thread* t, const char* name)
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

System::Library* loadLibrary(Thread* t,
                             const char* path,
                             const char* name,
                             bool mapName,
                             bool runOnLoad,
                             bool throw_ = true)
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
            and (s[nameLength] == ',' or s[nameLength] == 0)) {
          // library is built in to this executable
          if (runOnLoad and not t->m->triedBuiltinOnLoad) {
            t->m->triedBuiltinOnLoad = true;
            // todo: release the classLock before calling this to
            // avoid the possibility of deadlock:
            runOnLoadIfFound(t, t->m->libraries);
          }
          return t->m->libraries;
        } else {
          while (*s and *s != ',')
            ++s;
          if (*s)
            ++s;
        }
      }
    }

    const char* prefix = t->m->system->libraryPrefix();
    const char* suffix = t->m->system->librarySuffix();
    unsigned mappedNameLength = nameLength + strlen(prefix) + strlen(suffix);

    mappedName = static_cast<char*>(t->m->heap->allocate(mappedNameLength + 1));

    snprintf(mappedName, mappedNameLength + 1, "%s%s%s", prefix, name, suffix);

    name = mappedName;
    nameLength = mappedNameLength;
  } else {
    mappedName = 0;
  }

  THREAD_RESOURCE2(t, char*, mappedName, unsigned, nameLength, if (mappedName) {
    t->m->heap->free(mappedName, nameLength + 1);
  });

  System::Library* lib = 0;
  for (Tokenizer tokenizer(path, t->m->system->pathSeparator());
       tokenizer.hasMore();) {
    String token(tokenizer.next());

    unsigned fullNameLength = token.length + 1 + nameLength;
    THREAD_RUNTIME_ARRAY(t, char, fullName, fullNameLength + 1);

    snprintf(RUNTIME_ARRAY_BODY(fullName),
             fullNameLength + 1,
             "%.*s/%s",
             token.length,
             token.text,
             name);

    lib = loadLibrary(t, RUNTIME_ARRAY_BODY(fullName));
    if (lib)
      break;
  }

  if (lib == 0) {
    lib = loadLibrary(t, name);
  }

  if (lib) {
    if (runOnLoad) {
      runOnLoadIfFound(t, lib);
    }
  } else if (throw_) {
    throwNew(t,
             GcUnsatisfiedLinkError::Type,
             "library not found in %s: %s",
             path,
             name);
  }

  return lib;
}

GcStackTraceElement* makeStackTraceElement(Thread* t, GcTraceElement* e)
{
  PROTECT(t, e);

  GcMethod* method = cast<GcMethod>(t, e->method());
  PROTECT(t, method);

  GcByteArray* class_name = method->class_()->name();
  PROTECT(t, class_name);

  THREAD_RUNTIME_ARRAY(t, char, s, class_name->length());
  replace('/',
          '.',
          RUNTIME_ARRAY_BODY(s),
          reinterpret_cast<char*>(class_name->body().begin()));
  GcString* class_name_string = makeString(t, "%s", RUNTIME_ARRAY_BODY(s));
  PROTECT(t, class_name_string);

  GcByteArray* method_name = method->name();
  PROTECT(t, method_name);

  GcString* method_name_string = t->m->classpath->makeString(
      t, method_name, 0, method_name->length() - 1);
  PROTECT(t, method_name_string);

  unsigned line = t->m->processor->lineNumber(t, method, e->ip());

  GcByteArray* file = method->class_()->sourceFile();
  GcString* file_string
      = file ? t->m->classpath->makeString(t, file, 0, file->length() - 1) : 0;

  return makeStackTraceElement(
      t, class_name_string, method_name_string, file_string, line);
}

GcObject* translateInvokeResult(Thread* t, unsigned returnCode, object o)
{
  switch (returnCode) {
  case ByteField:
    return makeByte(t, cast<GcInt>(t, o)->value());

  case BooleanField:
    return makeBoolean(t, cast<GcInt>(t, o)->value() != 0);

  case CharField:
    return makeChar(t, cast<GcInt>(t, o)->value());

  case ShortField:
    return makeShort(t, cast<GcInt>(t, o)->value());

  case FloatField:
    return makeFloat(t, cast<GcInt>(t, o)->value());

  case IntField:
  case LongField:
  case ObjectField:
  case VoidField:
    return reinterpret_cast<GcObject*>(o);

  case DoubleField:
    return makeDouble(t, cast<GcLong>(t, o)->value());

  default:
    abort(t);
  }
}

GcClass* resolveClassBySpec(Thread* t,
                            GcClassLoader* loader,
                            const char* spec,
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

GcJclass* resolveJType(Thread* t,
                       GcClassLoader* loader,
                       const char* spec,
                       unsigned specLength)
{
  return getJClass(t, resolveClassBySpec(t, loader, spec, specLength));
}

GcPair* resolveParameterTypes(Thread* t,
                              GcClassLoader* loader,
                              GcByteArray* spec,
                              unsigned* parameterCount,
                              unsigned* returnTypeSpec)
{
  PROTECT(t, loader);
  PROTECT(t, spec);

  GcPair* list = 0;
  PROTECT(t, list);

  unsigned offset = 1;
  unsigned count = 0;
  while (spec->body()[offset] != ')') {
    switch (spec->body()[offset]) {
    case 'L': {
      unsigned start = offset;
      ++offset;
      while (spec->body()[offset] != ';')
        ++offset;
      ++offset;

      GcClass* type
          = resolveClassBySpec(t,
                               loader,
                               reinterpret_cast<char*>(&spec->body()[start]),
                               offset - start);

      list = makePair(t, type, list);

      ++count;
    } break;

    case '[': {
      unsigned start = offset;
      while (spec->body()[offset] == '[')
        ++offset;
      switch (spec->body()[offset]) {
      case 'L':
        ++offset;
        while (spec->body()[offset] != ';')
          ++offset;
        ++offset;
        break;

      default:
        ++offset;
        break;
      }

      GcClass* type
          = resolveClassBySpec(t,
                               loader,
                               reinterpret_cast<char*>(&spec->body()[start]),
                               offset - start);

      list = makePair(t, type, list);
      ++count;
    } break;

    default:
      list = makePair(t, primitiveClass(t, spec->body()[offset]), list);
      ++offset;
      ++count;
      break;
    }
  }

  *parameterCount = count;
  *returnTypeSpec = offset + 1;
  return list;
}

object resolveParameterJTypes(Thread* t,
                              GcClassLoader* loader,
                              GcByteArray* spec,
                              unsigned* parameterCount,
                              unsigned* returnTypeSpec)
{
  GcPair* list
      = resolveParameterTypes(t, loader, spec, parameterCount, returnTypeSpec);

  PROTECT(t, list);

  object array = makeObjectArray(t, type(t, GcJclass::Type), *parameterCount);
  PROTECT(t, array);

  for (int i = *parameterCount - 1; i >= 0; --i) {
    object c = getJClass(t, cast<GcClass>(t, list->first()));
    reinterpret_cast<GcArray*>(array)->setBodyElement(t, i, c);
    list = cast<GcPair>(t, list->second());
  }

  return array;
}

object resolveExceptionJTypes(Thread* t,
                              GcClassLoader* loader,
                              GcMethodAddendum* addendum)
{
  if (addendum == 0 or addendum->exceptionTable() == 0) {
    return makeObjectArray(t, type(t, GcJclass::Type), 0);
  }

  PROTECT(t, loader);
  PROTECT(t, addendum);

  GcShortArray* exceptionTable
      = cast<GcShortArray>(t, addendum->exceptionTable());
  PROTECT(t, exceptionTable);

  object array
      = makeObjectArray(t, type(t, GcJclass::Type), exceptionTable->length());
  PROTECT(t, array);

  for (unsigned i = 0; i < exceptionTable->length(); ++i) {
    uint16_t index = exceptionTable->body()[i] - 1;

    object o = singletonObject(t, addendum->pool()->as<GcSingleton>(t), index);

    if (objectClass(t, o) == type(t, GcReference::Type)) {
      o = resolveClass(t, loader, cast<GcReference>(t, o)->name());

      addendum->pool()->setBodyElement(
          t, index, reinterpret_cast<uintptr_t>(o));
    }

    o = getJClass(t, cast<GcClass>(t, o));

    reinterpret_cast<GcArray*>(array)->setBodyElement(t, i, o);
  }

  return array;
}

object invoke(Thread* t, GcMethod* method, object instance, object args)
{
  PROTECT(t, method);
  PROTECT(t, instance);
  PROTECT(t, args);

  if (method->flags() & ACC_STATIC) {
    instance = 0;
  }

  if ((args == 0 ? 0 : objectArrayLength(t, args))
      != method->parameterCount()) {
    throwNew(t, GcIllegalArgumentException::Type);
  }

  if (method->parameterCount()) {
    unsigned specLength = method->spec()->length();
    THREAD_RUNTIME_ARRAY(t, char, spec, specLength);
    memcpy(
        RUNTIME_ARRAY_BODY(spec), method->spec()->body().begin(), specLength);
    unsigned i = 0;
    for (MethodSpecIterator it(t, RUNTIME_ARRAY_BODY(spec)); it.hasNext();) {
      GcClass* type;
      bool objectType = false;
      const char* p = it.next();
      switch (*p) {
      case 'Z':
        type = vm::type(t, GcBoolean::Type);
        break;
      case 'B':
        type = vm::type(t, GcByte::Type);
        break;
      case 'S':
        type = vm::type(t, GcShort::Type);
        break;
      case 'C':
        type = vm::type(t, GcChar::Type);
        break;
      case 'I':
        type = vm::type(t, GcInt::Type);
        break;
      case 'F':
        type = vm::type(t, GcFloat::Type);
        break;
      case 'J':
        type = vm::type(t, GcLong::Type);
        break;
      case 'D':
        type = vm::type(t, GcDouble::Type);
        break;

      case 'L':
      case '[': {
        objectType = true;
        unsigned nameLength;
        if (*p == 'L') {
          ++p;
          nameLength = it.s - p;
        } else {
          nameLength = (it.s - p) + 1;
        }
        THREAD_RUNTIME_ARRAY(t, char, name, nameLength);
        memcpy(RUNTIME_ARRAY_BODY(name), p, nameLength - 1);
        RUNTIME_ARRAY_BODY(name)[nameLength - 1] = 0;
        type = resolveClass(
            t, method->class_()->loader(), RUNTIME_ARRAY_BODY(name));
      } break;

      default:
        abort();
      }

      object arg = objectArrayBody(t, args, i++);
      if ((arg == 0 and (not objectType))
          or (arg and (not instanceOf(t, type, arg)))) {
        if (false) {
          fprintf(stderr,
                  "%s is not a %s\n",
                  arg ? objectClass(t, arg)->name()->body().begin()
                      : reinterpret_cast<const int8_t*>("<null>"),
                  type->name()->body().begin());
        }

        throwNew(t, GcIllegalArgumentException::Type);
      }
    }
  }

  initClass(t, method->class_());

  unsigned returnCode = method->returnCode();

  THREAD_RESOURCE0(t, {
    if (t->exception) {
      t->exception = makeThrowable(
          t, GcInvocationTargetException::Type, 0, 0, t->exception);

      t->exception->as<GcInvocationTargetException>(t)
          ->setTarget(t, t->exception->cause());
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
void intercept(Thread* t,
               GcClass* c,
               const char* name,
               const char* spec,
               void* function,
               bool updateRuntimeData)
{
  GcMethod* m = findMethodOrNull(t, c, name, spec);
  if (m) {
    PROTECT(t, m);

    if (updateRuntimeData) {
      GcMethod* clone = methodClone(t, m);

      m->flags() |= ACC_NATIVE;

      // make clone private to prevent vtable updates at compilation
      // time.  Otherwise, our interception might be bypassed by calls
      // through the vtable.
      clone->flags() |= ACC_PRIVATE;

      GcNativeIntercept* native = makeNativeIntercept(t, function, true, clone);

      PROTECT(t, native);

      GcMethodRuntimeData* runtimeData = getMethodRuntimeData(t, m);

      runtimeData->setNative(t, native->as<GcNative>(t));
    } else {
      m->flags() |= ACC_NATIVE;
    }
  } else {
    // If we can't find the method, just ignore it, since ProGuard may
    // have stripped it out as unused.  Otherwise, the code below can
    // be enabled for debugging purposes.

    if (false) {
      fprintf(stderr,
              "unable to find %s%s in %s\n",
              name,
              spec,
              c->name()->body().begin());

      abort(t);
    }
  }
}

Finder* getFinder(Thread* t, const char* name, size_t nameLength)
{
  ACQUIRE(t, t->m->referenceLock);

  for (GcFinder* p = roots(t)->virtualFileFinders(); p; p = p->next()) {
    if (p->name()->length() == nameLength
        and strncmp(reinterpret_cast<const char*>(p->name()->body().begin()),
                    name,
                    nameLength)) {
      return static_cast<Finder*>(p->finder());
    }
  }

  GcByteArray* n = makeByteArray(t, nameLength + 1);
  memcpy(n->body().begin(), name, nameLength);

  void* p = t->m->libraries->resolve(
      reinterpret_cast<const char*>(n->body().begin()));

  if (p) {
    uint8_t* (*function)(size_t*);
    memcpy(&function, &p, BytesPerWord);

    size_t size = 0;
    uint8_t* data = function(&size);
    if (data) {
      Finder* f = makeFinder(t->m->system, t->m->heap, data, size);
      GcFinder* finder = makeFinder(t, f, n, roots(t)->virtualFileFinders());

      roots(t)->setVirtualFileFinders(t, finder);

      return f;
    }
  }

  return 0;
}

object getDeclaredClasses(Thread* t, GcClass* c, bool publicOnly)
{
  GcClassAddendum* addendum = c->addendum();
  if (addendum) {
    GcArray* table = cast<GcArray>(t, addendum->innerClassTable());
    if (table) {
      PROTECT(t, table);

      unsigned count = 0;
      for (unsigned i = 0; i < table->length(); ++i) {
        GcInnerClassReference* reference
            = cast<GcInnerClassReference>(t, table->body()[i]);
        GcByteArray* outer = reference->outer();
        if (outer and byteArrayEqual(t, outer, c->name())
            and ((not publicOnly) or (reference->flags() & ACC_PUBLIC))) {
          ++count;
        }
      }

      object result = makeObjectArray(t, type(t, GcJclass::Type), count);
      PROTECT(t, result);

      for (unsigned i = 0; i < table->length(); ++i) {
        GcInnerClassReference* reference
            = cast<GcInnerClassReference>(t, table->body()[i]);
        GcByteArray* outer = reference->outer();
        if (outer and byteArrayEqual(t, outer, c->name())
            and ((not publicOnly) or (reference->flags() & ACC_PUBLIC))) {
          object inner
              = getJClass(t, resolveClass(t, c->loader(), reference->inner()));

          --count;
          reinterpret_cast<GcArray*>(result)->setBodyElement(t, count, inner);
        }
      }

      return result;
    }
  }

  return makeObjectArray(t, type(t, GcJclass::Type), 0);
}

unsigned classModifiers(Thread* t, GcClass* c)
{
  GcClassAddendum* addendum = c->addendum();
  if (addendum) {
    GcArray* table = cast<GcArray>(t, addendum->innerClassTable());
    if (table) {
      for (unsigned i = 0; i < table->length(); ++i) {
        GcInnerClassReference* reference
            = cast<GcInnerClassReference>(t, table->body()[i]);
        if (0 == strcmp(c->name()->body().begin(),
                        reference->inner()->body().begin())) {
          return reference->flags();
        }
      }
    }
  }

  return c->flags();
}

object makeMethod(Thread* t, GcJclass* class_, int index)
{
  GcMethod* method = cast<GcMethod>(
      t, cast<GcArray>(t, class_->vmClass()->methodTable())->body()[index]);
  PROTECT(t, method);

  GcClass* c
      = resolveClass(t, roots(t)->bootLoader(), "java/lang/reflect/Method");
  PROTECT(t, c);

  object instance = makeNew(t, c);
  PROTECT(t, instance);

  GcMethod* constructor = resolveMethod(t, c, "<init>", "(Lavian/VMMethod;)V");

  t->m->processor->invoke(t, constructor, instance, method);

  if (method->name()->body()[0] == '<') {
    object oldInstance = instance;

    c = resolveClass(
        t, roots(t)->bootLoader(), "java/lang/reflect/Constructor");

    object instance = makeNew(t, c);

    GcMethod* constructor
        = resolveMethod(t, c, "<init>", "(Ljava/lang/Method;)V");

    t->m->processor->invoke(t, constructor, instance, oldInstance);
  }

  return instance;
}

int64_t getPrimitive(Thread* t, object instance, int code, int offset)
{
  switch (code) {
  case ByteField:
    return fieldAtOffset<int8_t>(instance, offset);
  case BooleanField:
    return fieldAtOffset<uint8_t>(instance, offset);
  case CharField:
    return fieldAtOffset<uint16_t>(instance, offset);
  case ShortField:
    return fieldAtOffset<int16_t>(instance, offset);
  case IntField:
    return fieldAtOffset<int32_t>(instance, offset);
  case LongField:
    return fieldAtOffset<int64_t>(instance, offset);
  case FloatField:
    return fieldAtOffset<uint32_t>(instance, offset);
  case DoubleField:
    return fieldAtOffset<uint64_t>(instance, offset);
  default:
    abort(t);
  }
}

void setPrimitive(Thread* t,
                  object instance,
                  int code,
                  int offset,
                  int64_t value)
{
  switch (code) {
  case ByteField:
    fieldAtOffset<int8_t>(instance, offset) = static_cast<int8_t>(value);
    break;
  case BooleanField:
    fieldAtOffset<uint8_t>(instance, offset) = static_cast<uint8_t>(value);
    break;
  case CharField:
    fieldAtOffset<uint16_t>(instance, offset) = static_cast<uint16_t>(value);
    break;
  case ShortField:
    fieldAtOffset<int16_t>(instance, offset) = static_cast<int16_t>(value);
    break;
  case IntField:
    fieldAtOffset<int32_t>(instance, offset) = static_cast<int32_t>(value);
    break;
  case LongField:
    fieldAtOffset<int64_t>(instance, offset) = static_cast<int64_t>(value);
    break;
  case FloatField:
    fieldAtOffset<uint32_t>(instance, offset) = static_cast<uint32_t>(value);
    break;
  case DoubleField:
    fieldAtOffset<uint64_t>(instance, offset) = static_cast<uint64_t>(value);
    break;
  default:
    abort(t);
  }
}

int64_t invokeMethod(Thread* t, GcMethod* method, object instance, object args)
{
  THREAD_RESOURCE0(t, {
    if (t->exception) {
      GcThrowable* exception = t->exception;
      t->exception = makeThrowable(
          t, GcInvocationTargetException::Type, 0, 0, exception);
    }
  });

  unsigned returnCode = method->returnCode();

  return reinterpret_cast<int64_t>(translateInvokeResult(
      t, returnCode, t->m->processor->invokeArray(t, method, instance, args)));
}

}  // namespace vm

#endif  // CLASSPATH_COMMON_H
