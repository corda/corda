/* Copyright (c) 2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "machine.h"
#include "constants.h"
#include "processor.h"

using namespace vm;

namespace {

void
setProperty(Thread* t, object method, object properties,
            const char* name, const void* value, const char* format = "%s")
{
  PROTECT(t, method);
  PROTECT(t, properties);
  
  object n = makeString(t, "%s", name);
  PROTECT(t, n);

  object v = makeString(t, format, value);

  t->m->processor->invoke(t, method, properties, n, v);
}

} // namespace

extern "C" JNIEXPORT void JNICALL
Avian_gnu_classpath_VMSystemProperties_preInit
(Thread* t, object, uintptr_t* arguments)
{
  object properties = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, properties);

  object method = resolveMethod
    (t, "java/util/Properties", "setProperty",
     "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;");

  if (UNLIKELY(t->exception)) {
    return;
  }

  PROTECT(t, method);

  setProperty(t, method, properties, "java.vm.name", "Avian");

  setProperty(t, method, properties, "java.lang.classpath",
              t->m->finder->path());

  setProperty(t, method, properties, "file.encoding", "ASCII");

#ifdef WIN32 
  setProperty(t, method, properties, "line.separator", "\r\n");
  setProperty(t, method, properties, "file.separator", "\\");
  setProperty(t, method, properties, "os.name", "Windows");

  TCHAR buffer[MAX_PATH];
  GetTempPath(MAX_PATH, buffer);
  setProperty(t, method, properties, "java.io.tmpdir", buffer);

  LPWSTR home = _wgetenv(L"USERPROFILE");
  setProperty(t, method, properties, "user.home", home, "%ls");
#else
  setProperty(t, method, properties, "line.separator", "\n");
  setProperty(t, method, properties, "file.separator", "/");
#  ifdef __APPLE__
  setProperty(t, method, properties, "os.name", "Mac OS X");
#  else
  setProperty(t, method, properties, "os.name", "Linux");
#  endif
  setProperty(t, method, properties, "java.io.tmpdir", "/tmp");
  setProperty(t, method, properties, "user.home", getenv("HOME"));
#endif
}
