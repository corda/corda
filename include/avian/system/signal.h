/* Copyright (c) 2008-2013, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_SYSTEM_SIGNAL_H
#define AVIAN_SYSTEM_SIGNAL_H

namespace avian {
namespace system {

// Registrar for unix-like "signals" (implemented with structured exceptions on windows).
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

  SignalRegistrar();
  ~SignalRegistrar();

  // Register a handler for segfault signals.
  // After this method call, any segfault exceptions (mostly null pointer
  // dereference, but generally access to any non-mapped memory) will be handled
  // by the given handler. Pass null (0) to unregister a handler.
  // Returns true upon success, false upon failure
  bool handleSegFault(Handler* handler);

  // Register a handler for divide-by-zero signals.
  // After this method call, any divide-by-zero exceptions will be handled by
  // the given handler. Pass null (0) to unregister a handler.
  // Returns true upon success, false upon failure
  bool handleDivideByZero(Handler* handler);

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

} // namespace system
} // namespace avian

#endif
