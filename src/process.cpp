#include "process.h"

using namespace vm;

namespace {

unsigned
mangledSize(int8_t c)
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

unsigned
mangle(int8_t c, char* dst)
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

unsigned
jniNameLength(Thread* t, object method, bool decorate)
{
  unsigned size = 5;

  object className = ::className(t, methodClass(t, method));
  for (unsigned i = 0; i < byteArrayLength(t, className) - 1; ++i) {
    size += mangledSize(byteArrayBody(t, className, i));
  }

  ++ size;

  object methodName = ::methodName(t, method);
  for (unsigned i = 0; i < byteArrayLength(t, methodName) - 1; ++i) {
    size += mangledSize(byteArrayBody(t, methodName, i));
  }

  if (decorate) {
    size += 2;

    object methodSpec = ::methodSpec(t, method);
    for (unsigned i = 1; i < byteArrayLength(t, methodSpec) - 1
           and byteArrayBody(t, methodSpec, i) != ')'; ++i)
    {
      size += mangledSize(byteArrayBody(t, methodSpec, i));
    }
  }

  return size;
}

void
makeJNIName(Thread* t, char* name, object method, bool decorate)
{
  memcpy(name, "Java_", 5);
  name += 5;

  object className = ::className(t, methodClass(t, method));
  for (unsigned i = 0; i < byteArrayLength(t, className) - 1; ++i) {
    name += mangle(byteArrayBody(t, className, i), name);
  }

  *(name++) = '_';

  object methodName = ::methodName(t, method);
  for (unsigned i = 0; i < byteArrayLength(t, methodName) - 1; ++i) {
    name += mangle(byteArrayBody(t, methodName, i), name);
  }
  
  if (decorate) {
    *(name++) = '_';
    *(name++) = '_';

    object methodSpec = ::methodSpec(t, method);
    for (unsigned i = 1; i < byteArrayLength(t, methodSpec) - 1
           and byteArrayBody(t, methodSpec, i) != ')'; ++i)
    {
      name += mangle(byteArrayBody(t, methodSpec, i), name);
    }
  }

  *(name++) = 0;
}

void*
resolveNativeMethod(Thread* t, const char* undecorated, const char* decorated)
{
  for (System::Library* lib = t->m->firstLibrary; lib; lib = lib->next()) {
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

} // namespace

namespace vm {

void*
resolveNativeMethod2(Thread* t, object method)
{
  unsigned undecoratedSize = jniNameLength(t, method, false);
  char undecorated[undecoratedSize + 5]; // extra 5 is for code below
  makeJNIName(t, undecorated, method, false);

  unsigned decoratedSize = jniNameLength(t, method, true);
  char decorated[decoratedSize + 5]; // extra 5 is for code below
  makeJNIName(t, decorated, method, true);

  void* p = ::resolveNativeMethod(t, undecorated, decorated);
  if (p) {
    return p;
  }

#ifdef __MINGW32__
  // on windows, we also try the _%s@%d variant, since the SWT
  // libraries use it.
  unsigned footprint = methodParameterFootprint(t, method) + 1;
  if (methodFlags(t, method) & ACC_STATIC) {
    ++ footprint;
  }

  snprintf(undecorated + undecoratedSize - 1, 5, "@%d",
           footprint * BytesPerWord);

  snprintf(decorated + decoratedSize - 1, 5, "@%d",
           footprint * BytesPerWord);

  p = ::resolveNativeMethod(t, undecorated, decorated);
  if (p) {
    return p;
  }
#endif

  return 0;
}

ExceptionHandler*
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

int
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
