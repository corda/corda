/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_SYSTEM_SIGNAL_H
#define AVIAN_SYSTEM_SIGNAL_H

#include <avian/common.h>

namespace avian {
namespace system {

// Crash the process.
// On posix, the just calls abort. On windows, we dereference a null pointer in
// order to trigger the crash dump logic.
NO_RETURN void crash();

// Registrar for unix-like "signals" (implemented with structured exceptions on
// windows).
// TODO: remove dependence on generated code having a well-known "thread"
// register. Use a thread-local variable instead.
class SignalRegistrar {
 public:
  class Handler {
   public:
    // This function receives state information about the paused thread.
    // Returns whether to resume execution after the failure point.
    virtual bool handleSignal(void** ip,
                              void** frame,
                              void** stack,
                              void** thread) = 0;
  };

  enum Signal {
    // "Segmentation fault" exceptions (mostly null pointer dereference, but
    // generally access to any non-mapped memory)
    SegFault,
    DivideByZero,
  };

  SignalRegistrar();
  ~SignalRegistrar();

  // Register a handler for the given signal.
  // After this method call, anytime the given signal is raised, it will be
  // handled by the given handler.
  // Returns true upon success, false upon failure
  bool registerHandler(Signal signal, Handler* handler);

  // Unregister a handler for the given signal.
  // After this method call, the given signal will no longer be handled (or,
  // rather, it go back to being handled by whatever was registered to handle it
  // before us).
  // Returns true upon success, false upon failure
  bool unregisterHandler(Signal signal);

  // Set the directory that a crash dump will be written to should an unhandled
  // exception be thrown.
  // Note: this only currently does anything on windows.
  // TODO: move this out of this class, into a separate "CrashDumper" class or
  // somesuch.
  void setCrashDumpDirectory(const char* crashDumpDirectory);

  // This is internal, implementation-specific data. It's declared in the
  // specific implementation.
  struct Data;

 private:
  Data* data;
};

}  // namespace system
}  // namespace avian

#endif
