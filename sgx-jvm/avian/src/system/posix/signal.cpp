/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "signal.h"
#include "sys/types.h"
#ifdef __APPLE__
#include "CoreFoundation/CoreFoundation.h"
#include "sys/ucontext.h"
#undef assert
#elif defined(__ANDROID__)
#include <asm/sigcontext.h> /* for sigcontext */
#include <asm/signal.h>     /* for stack_t */
typedef struct ucontext {
  unsigned long uc_flags;
  struct ucontext* uc_link;
  stack_t uc_stack;
  struct sigcontext uc_mcontext;
  unsigned long uc_sigmask;
} ucontext_t;
#else
#if defined __FreeBSD__
#include "limits.h"
#endif
#include "ucontext.h"
#endif

#include "avian/arch.h"
#include <avian/system/system.h>
#include <avian/system/signal.h>

namespace avian {
namespace system {

namespace posix {

const int InvalidSignal = -1;
const int SegFaultSignal = SIGSEGV;
const unsigned SegFaultSignalIndex = 0;
#ifdef __APPLE__
const int AltSegFaultSignal = SIGBUS;
#else
const int AltSegFaultSignal = InvalidSignal;
#endif
const unsigned AltSegFaultSignalIndex = 1;
const int DivideByZeroSignal = SIGFPE;
const unsigned DivideByZeroSignalIndex = 2;

const int signals[] = {SegFaultSignal, AltSegFaultSignal, DivideByZeroSignal};

const unsigned SignalCount = 3;
}

struct SignalRegistrar::Data {
  Handler* handlers[posix::SignalCount];
  struct sigaction oldHandlers[posix::SignalCount];

  bool registerHandler(Handler* handler, int index);

  Data()
  {
    if (instance) {
      crash();
    }

    instance = this;
  }

  ~Data()
  {
    instance = 0;
  }

  static SignalRegistrar::Data* instance;
};

SignalRegistrar::Data* SignalRegistrar::Data::instance = 0;

namespace posix {

using namespace vm;

void handleSignal(int signal, siginfo_t*, void* context)
{
  ucontext_t* c = static_cast<ucontext_t*>(context);

  void* ip = reinterpret_cast<void*>(IP_REGISTER(c));
  void* stack = reinterpret_cast<void*>(STACK_REGISTER(c));
  void* thread = reinterpret_cast<void*>(THREAD_REGISTER(c));
#ifdef FRAME_REGISTER
  void* frame = reinterpret_cast<void*>(FRAME_REGISTER(c));
#else
  void* frame = 0;
#endif

  unsigned index;

  switch (signal) {
  case SegFaultSignal:
  case AltSegFaultSignal:
  case DivideByZeroSignal: {
    switch (signal) {
    case SegFaultSignal:
      index = SegFaultSignalIndex;
      break;

    case AltSegFaultSignal:
      index = AltSegFaultSignalIndex;
      break;

    case DivideByZeroSignal:
      index = DivideByZeroSignalIndex;
      break;

    default:
      crash();
    }

    bool jump = SignalRegistrar::Data::instance->handlers[index]->handleSignal(
        &ip, &frame, &stack, &thread);

    if (jump) {
      // I'd like to use setcontext here (and get rid of the
      // sigprocmask call), but it doesn't work on my Linux x86_64
      // system, and I can't tell from the documentation if it's even
      // supposed to work.

      sigset_t set;
      sigemptyset(&set);
      sigaddset(&set, signal);
      pthread_sigmask(SIG_UNBLOCK, &set, 0);

      vmJump(ip, frame, stack, thread, 0, 0);
    } else {
      crash();
    }
  } break;

  default:
    crash();
  }
}

}  // namespace posix

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
  if (handler) {
    handlers[index] = handler;

    struct sigaction sa;
    memset(&sa, 0, sizeof(struct sigaction));
    sigemptyset(&(sa.sa_mask));
    sa.sa_flags = SA_SIGINFO;
    sa.sa_sigaction = posix::handleSignal;

    return sigaction(posix::signals[index], &sa, oldHandlers + index) == 0;
  } else if (handlers[index]) {
    handlers[index] = 0;
    return sigaction(posix::signals[index], oldHandlers + index, 0) == 0;
  } else {
    return false;
  }
}

bool SignalRegistrar::registerHandler(Signal signal, Handler* handler)
{
  switch (signal) {
  case SegFault:
    if (!data->registerHandler(handler, posix::SegFaultSignalIndex)) {
      return false;
    }
    if (posix::AltSegFaultSignal != posix::InvalidSignal) {
      return data->registerHandler(handler, posix::AltSegFaultSignalIndex);
    } else {
      return true;
    }
  case DivideByZero:
    return data->registerHandler(handler, posix::DivideByZeroSignalIndex);
  default:
    crash();
  }
}

bool SignalRegistrar::unregisterHandler(Signal signal)
{
  switch (signal) {
  case SegFault:
    if (!data->registerHandler(0, posix::SegFaultSignalIndex)) {
      return false;
    }
    if (posix::AltSegFaultSignal != posix::InvalidSignal) {
      return data->registerHandler(0, posix::AltSegFaultSignalIndex);
    } else {
      return true;
    }
  case DivideByZero:
    return data->registerHandler(0, posix::DivideByZeroSignalIndex);
  default:
    crash();
  }
}

void SignalRegistrar::setCrashDumpDirectory(const char*)
{
  // Do nothing, not currently supported on posix
}

}  // namespace system
}  // namespace avian
