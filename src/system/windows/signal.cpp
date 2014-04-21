/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "windows.h"
#include "sys/timeb.h"

#ifdef _MSC_VER
#define FTIME _ftime_s
#else
#define FTIME _ftime
#endif

#ifndef WINAPI_FAMILY

#ifndef WINAPI_PARTITION_DESKTOP
#define WINAPI_PARTITION_DESKTOP 1
#endif

#ifndef WINAPI_FAMILY_PARTITION
#define WINAPI_FAMILY_PARTITION(x) (x)
#endif

#endif

#include <avian/system/signal.h>
#include <avian/common.h>

namespace avian {
namespace system {

namespace windows {

const unsigned HandlerCount = 2;

}  // namespace windows

struct SignalRegistrar::Data {
  Handler* handlers[windows::HandlerCount];
  const char* crashDumpDirectory;

#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
  LPTOP_LEVEL_EXCEPTION_FILTER oldHandler;
#endif

  Data() : crashDumpDirectory(0),
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
    oldHandler(0)
#endif
  {
    if (instance) {
      crash();
    }
    instance = this;
    memset(handlers, 0, sizeof(handlers));
  }

  ~Data()
  {
    instance = 0;
  }

  bool registerHandler(Handler* handler, int index);

  bool findHandler() {
    for (unsigned i = 0; i < windows::HandlerCount; ++i) {
      if (handlers[i]) return true;
    }
    return false;
  }

  static SignalRegistrar::Data* instance;
};

SignalRegistrar::Data* SignalRegistrar::Data::instance = 0;

namespace windows {

#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)

#pragma pack(push, 4)
struct MINIDUMP_EXCEPTION_INFORMATION {
  DWORD thread;
  LPEXCEPTION_POINTERS exception;
  BOOL exceptionInCurrentAddressSpace;
};
#pragma pack(pop)

struct MINIDUMP_USER_STREAM_INFORMATION;
struct MINIDUMP_CALLBACK_INFORMATION;

enum MINIDUMP_TYPE {
  MiniDumpNormal = 0,
  MiniDumpWithFullMemory = 2
};

typedef BOOL (*MiniDumpWriteDumpType)(HANDLE processHandle,
                                      DWORD processId,
                                      HANDLE file,
                                      MINIDUMP_TYPE type,
                                      const MINIDUMP_EXCEPTION_INFORMATION
                                      * exception,
                                      const MINIDUMP_USER_STREAM_INFORMATION
                                      * userStream,
                                      const MINIDUMP_CALLBACK_INFORMATION
                                      * callback);

#endif

void dump(LPEXCEPTION_POINTERS e, const char* directory)
{
  HINSTANCE dbghelp = LoadLibrary("dbghelp.dll");

  if (dbghelp) {
    MiniDumpWriteDumpType MiniDumpWriteDump = reinterpret_cast
        <MiniDumpWriteDumpType>(GetProcAddress(dbghelp, "MiniDumpWriteDump"));

    if (MiniDumpWriteDump) {
      char name[MAX_PATH];
      _timeb tb;
      FTIME(&tb);
      vm::snprintf(name,
                   MAX_PATH,
                   "%s\\crash-%" LLD ".mdmp",
                   directory,
                   (static_cast<int64_t>(tb.time) * 1000) + static_cast
                   <int64_t>(tb.millitm));

      HANDLE file
          = CreateFile(name, FILE_WRITE_DATA, 0, 0, CREATE_ALWAYS, 0, 0);

      if (file != INVALID_HANDLE_VALUE) {
        MINIDUMP_EXCEPTION_INFORMATION exception
            = {GetCurrentThreadId(), e, true};

        MiniDumpWriteDump(GetCurrentProcess(),
                          GetCurrentProcessId(),
                          file,
                          MiniDumpWithFullMemory,
                          &exception,
                          0,
                          0);

        CloseHandle(file);
      }
    }

    FreeLibrary(dbghelp);
  }
}

LONG CALLBACK handleException(LPEXCEPTION_POINTERS e)
{
  SignalRegistrar::Handler* handler = 0;
  if (e->ExceptionRecord->ExceptionCode == EXCEPTION_ACCESS_VIOLATION) {
    handler = SignalRegistrar::Data::instance->handlers[SignalRegistrar::SegFault];
  } else if (e->ExceptionRecord->ExceptionCode
             == EXCEPTION_INT_DIVIDE_BY_ZERO) {
    handler = SignalRegistrar::Data::instance->handlers[SignalRegistrar::DivideByZero];
  }

  if (handler) {
#ifdef ARCH_x86_32
    void* ip = reinterpret_cast<void*>(e->ContextRecord->Eip);
    void* base = reinterpret_cast<void*>(e->ContextRecord->Ebp);
    void* stack = reinterpret_cast<void*>(e->ContextRecord->Esp);
    void* thread = reinterpret_cast<void*>(e->ContextRecord->Ebx);
#elif defined ARCH_x86_64
    void* ip = reinterpret_cast<void*>(e->ContextRecord->Rip);
    void* base = reinterpret_cast<void*>(e->ContextRecord->Rbp);
    void* stack = reinterpret_cast<void*>(e->ContextRecord->Rsp);
    void* thread = reinterpret_cast<void*>(e->ContextRecord->Rbx);
#endif

    bool jump = handler->handleSignal(&ip, &base, &stack, &thread);

#ifdef ARCH_x86_32
    e->ContextRecord->Eip = reinterpret_cast<DWORD>(ip);
    e->ContextRecord->Ebp = reinterpret_cast<DWORD>(base);
    e->ContextRecord->Esp = reinterpret_cast<DWORD>(stack);
    e->ContextRecord->Ebx = reinterpret_cast<DWORD>(thread);
#elif defined ARCH_x86_64
    e->ContextRecord->Rip = reinterpret_cast<DWORD64>(ip);
    e->ContextRecord->Rbp = reinterpret_cast<DWORD64>(base);
    e->ContextRecord->Rsp = reinterpret_cast<DWORD64>(stack);
    e->ContextRecord->Rbx = reinterpret_cast<DWORD64>(thread);
#endif

    if (jump) {
      return EXCEPTION_CONTINUE_EXECUTION;
    } else if (SignalRegistrar::Data::instance->crashDumpDirectory) {
      dump(e, SignalRegistrar::Data::instance->crashDumpDirectory);
    }
  }

  return EXCEPTION_CONTINUE_SEARCH;
}

}  // namespace windows

SignalRegistrar::SignalRegistrar()
{
  data = new (malloc(sizeof(Data))) Data();
}

SignalRegistrar::~SignalRegistrar()
{
  data->~Data();
  free(data);
}

bool SignalRegistrar::Data::registerHandler(Handler* handler, int index)
{
  if(index != SegFault && index != DivideByZero) {
    crash();
  }

  if (handler) {
    handlers[index] = handler;

#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
    if (oldHandler == 0) {
#ifdef ARCH_x86_32
      oldHandler = SetUnhandledExceptionFilter(windows::handleException);
#elif defined ARCH_x86_64
      AddVectoredExceptionHandler(1, windows::handleException);
      oldHandler = reinterpret_cast<LPTOP_LEVEL_EXCEPTION_FILTER>(1);
#endif
    }
#else
#pragma message( \
    "TODO: http://msdn.microsoft.com/en-us/library/windowsphone/develop/system.windows.application.unhandledexception(v=vs.105).aspx")
#endif

    return true;
  } else if (handlers[index]) {
    handlers[index] = 0;

    if (not findHandler()) {
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
#ifdef ARCH_x86_32
      SetUnhandledExceptionFilter(oldHandler);
      oldHandler = 0;
#elif defined ARCH_x86_64
// do nothing, handlers are never "unregistered" anyway
#endif
#else
#pragma message( \
    "TODO: http://msdn.microsoft.com/en-us/library/windowsphone/develop/system.windows.application.unhandledexception(v=vs.105).aspx")
#endif
    }

    return true;
  } else {
    return false;
  }
}

NO_RETURN void crash()
{
  // trigger an EXCEPTION_ACCESS_VIOLATION, which we will catch and
  // generate a debug dump for
  *static_cast<volatile int*>(0) = 0;

  // Some (all?) compilers don't realize that we can't possibly continue past
  // the above statement.
  abort();
}

bool SignalRegistrar::registerHandler(Signal signal, Handler* handler)
{
  return data->registerHandler(handler, signal);
}

bool SignalRegistrar::unregisterHandler(Signal signal)
{
  return data->registerHandler(0, signal);
}

void SignalRegistrar::setCrashDumpDirectory(const char* crashDumpDirectory)
{
  data->crashDumpDirectory = crashDumpDirectory;
}

}  // namespace system
}  // namespace avian
