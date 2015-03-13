/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/types.h"
#include "avian/target-fields.h"

.text

#define BYTES_PER_WORD 8

#define LOCAL(x) .L##x

#ifdef __APPLE__
#  define GLOBAL(x) _##x
#else
#  define GLOBAL(x) x
#endif

#define CONTINUATION_NEXT 8
#define CONTINUATION_ADDRESS 32
#define CONTINUATION_RETURN_ADDRESS_OFFSET 40
#define CONTINUATION_FRAME_POINTER_OFFSET 48
#define CONTINUATION_LENGTH 56
#define CONTINUATION_BODY 64

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
  mov   x29, sp

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

  // copy arguments into place, reserving enough space for them, plus
  // alignment padding
  sub   x5, sp, w3, uxtw
  and   sp, x5, #-16

  mov   x4, #0
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
  str  xzr, [x19, #TARGET_THREAD_STACK]

.globl GLOBAL(vmInvoke_safeStack)
.align 2
GLOBAL(vmInvoke_safeStack):

#ifdef AVIAN_CONTINUATIONS
  // call the next continuation, if any
  ldr   x5, [x19,#TARGET_THREAD_CONTINUATION]
  cmp   x5, xzr
  b.eq  LOCAL(vmInvoke_exit)

  ldr   x6, [x5,#CONTINUATION_LENGTH]
  lsl   x6, x6, #3
  neg   x7, x6
  add   x7, x7, #-128 // 128 bytes for callee-saved register values
  mov   x4, sp
  add   sp, sp, x7
  str   x4, [sp]

  add   x7, x5, #CONTINUATION_BODY
  mov   x11, xzr
  b     LOCAL(vmInvoke_continuationTest)

LOCAL(vmInvoke_continuationLoop):
  ldr   x9, [x7,x11]
  str   x9, [sp,x11]
  add   x11, x11, #8

LOCAL(vmInvoke_continuationTest):
  cmp   x11, x6
  b.le  LOCAL(vmInvoke_continuationLoop)

  ldr   x7, [x5,#CONTINUATION_RETURN_ADDRESS_OFFSET]
  adr   x11, GLOBAL(vmInvoke_returnAddress)
  str   x11, [sp,x7]

  ldr   x7, [x5,#CONTINUATION_NEXT]
  str   x7, [x19,#TARGET_THREAD_CONTINUATION]

  // call the continuation unless we're handling an exception
  ldr   x7, [x19,#TARGET_THREAD_EXCEPTION]
  cmp   x7, xzr
  b.ne  LOCAL(vmInvoke_handleException)
  ldr   x7, [x5,#CONTINUATION_ADDRESS]
  br    x7

LOCAL(vmInvoke_handleException):
   // we're handling an exception - call the exception handler instead
  str   xzr, [x19,#TARGET_THREAD_EXCEPTION]
  ldr   x11, [x19,#TARGET_THREAD_EXCEPTIONSTACKADJUSTMENT]
  ldr   x9, [sp]
  neg   x11, x11
  add   sp, sp, x11
  str   x9, [sp]
  ldr   x11, [x19,#TARGET_THREAD_EXCEPTIONOFFSET]
  str   x7, [sp,x11]

  ldr   x7, [x19,#TARGET_THREAD_EXCEPTIONHANDLER]
  br    x7

LOCAL(vmInvoke_exit):
  str   xzr, [x19, #TARGET_THREAD_STACK]

#endif // AVIAN_CONTINUATIONS

  // restore return type
  ldr   w5, [sp],#16

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
   // x0: thread
   // x1: address
   // x2: stack
   // x3: argumentFootprint
   // x4: arguments
   // x5: frameSize

   // allocate new frame, adding room for callee-saved registers, plus
   // 8 bytes of padding since the calculation of frameSize assumes 8
   // bytes have already been allocated to save the return address,
   // which is not true in this case
   sub   x2, x2, x5
   sub   x2, x2, #136

   mov   x19, x0

   // copy arguments into place
   mov   x6, xzr
   b     LOCAL(vmJumpAndInvoke_argumentTest)

LOCAL(vmJumpAndInvoke_argumentLoop):
   ldr   x12, [x4,x6]
   str   x12, [x2,x6]
   add   x6, x6, #4

LOCAL(vmJumpAndInvoke_argumentTest):
   cmp   x6, x3
   ble   LOCAL(vmJumpAndInvoke_argumentLoop)

   // the arguments have been copied, so we can set the real stack
   // pointer now
   mov   sp, x2

   // set return address to vmInvoke_returnAddress
   adr   x30, GLOBAL(vmInvoke_returnAddress)

   br    x1

#else // not AVIAN_CONTINUATIONS
   // vmJumpAndInvoke should only be called when continuations are
   // enabled, so we force a crash if we reach here:
   brk 0
#endif // not AVIAN_CONTINUATIONS
