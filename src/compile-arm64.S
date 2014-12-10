/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/types.h"
#include "avian/target-fields.h"

.text

#define BYTES_PER_WORD 4

#define LOCAL(x) .L##x
   
#ifdef __APPLE__
#  define GLOBAL(x) _##x
#else
#  define GLOBAL(x) x   
#endif

#define CONTINUATION_NEXT 4
#define CONTINUATION_ADDRESS 16
#define CONTINUATION_RETURN_ADDRESS_OFFSET 20
#define CONTINUATION_FRAME_POINTER_OFFSET 24
#define CONTINUATION_LENGTH 28
#define CONTINUATION_BODY 32
   
.globl GLOBAL(vmInvoke)
.align 2
GLOBAL(vmInvoke):
  // arguments:
  //   x0 : thread
  //   x1 : function
  //   x2 : arguments
  //   w3 : argumentFootprint
  //   w4 : frameSize (not used)
  //   w5 : returnType

  // allocate frame
  stp   x29, x30, [sp,#-96]!

  // save callee-saved register values
  stp   x19, x20, [sp,#16]
  stp   x21, x22, [sp,#32]
  stp   x23, x24, [sp,#48]
  stp   x25, x26, [sp,#64]
  stp   x27, x28, [sp,#80]

  // save return type
  str   w5, [sp,#-16]!

  mov   x5, sp
  str   x5, [x0,#TARGET_THREAD_SCRATCH]

  // copy arguments into place
  sub   sp, sp, w3, uxtw
  mov   x5, #0
  b     LOCAL(vmInvoke_argumentTest)

LOCAL(vmInvoke_argumentLoop):
  ldr   x5, [x2, x4]
  str   x5, [sp, x4]
  add   x4, x4, #BYTES_PER_WORD

LOCAL(vmInvoke_argumentTest):
  cmp   x4, x3
  b.lt   LOCAL(vmInvoke_argumentLoop)

  // we use x19 to hold the thread pointer, by convention
  mov   x19, x0

  // load and call function address
  blr   x1

.globl GLOBAL(vmInvoke_returnAddress)
.align 2
GLOBAL(vmInvoke_returnAddress):
  // restore stack pointer
  ldr   x5, [x19, #TARGET_THREAD_SCRATCH]
  mov   sp, x5

  // clear MyThread::stack to avoid confusing another thread calling
  // java.lang.Thread.getStackTrace on this one.  See
  // MyProcess::getStackTrace in compile.cpp for details on how we get
  // a reliable stack trace from a thread that might be interrupted at
  // any point in its execution.
  mov  x5, #0
  str  x5, [x19, #TARGET_THREAD_STACK]

.globl GLOBAL(vmInvoke_safeStack)
.align 2
GLOBAL(vmInvoke_safeStack):

#ifdef AVIAN_CONTINUATIONS
#error todo
#endif // AVIAN_CONTINUATIONS

  mov   x5, #0
  str   x5, [x19, #TARGET_THREAD_STACK]

  // restore return type
  ldr   w5, [sp], #4

  // restore callee-saved register values
  ldp   x19, x20, [sp,#16]
  ldp   x21, x22, [sp,#32]
  ldp   x23, x24, [sp,#48]
  ldp   x25, x26, [sp,#64]
  ldp   x27, x28, [sp,#80]
  ldp   x29, x30, [sp],#96

LOCAL(vmInvoke_return):
  br    x30

.globl GLOBAL(vmJumpAndInvoke)
.align 2
GLOBAL(vmJumpAndInvoke):
#ifdef AVIAN_CONTINUATIONS
#error todo
#else // not AVIAN_CONTINUATIONS
   // vmJumpAndInvoke should only be called when continuations are
   // enabled, so we force a crash if we reach here:
   brk 0
#endif // not AVIAN_CONTINUATIONS
