/* arm.S: JNI gluecode for ARM
   Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/types.h"

.text

#define LOCAL(x) .L##x

#ifdef __APPLE__
#  define GLOBAL(x) _##x
#else
#  define GLOBAL(x) x
#endif

.globl GLOBAL(vmNativeCall)
.align 2
GLOBAL(vmNativeCall):
  // arguments:
  //   x0 -> x19      : function
  //   w1 -> w20      : stackTotal
  //   x2             : memoryTable
  //   w3             : memoryCount
  //   x4 -> x21      : gprTable
  //   x5 -> x22      : vfpTable
  //   w6 -> w23      : returnType

  // allocate frame
  stp   x29, x30, [sp,#-64]!
  mov   x29, sp

  // save callee-saved register values so we can clobber them
  stp   x19, x20, [sp,#16]
  stp   x21, x22, [sp,#32]
  str   x23, [sp,#48]

  // move arguments into callee-saved registers
  mov   x19, x0
  mov   w20, w1
  mov   x21, x4
  mov   x22, x5
  mov   w23, w6

  // setup stack arguments if necessary
  sub   sp, sp, w20, uxtw // allocate stack
  mov   x9, sp
LOCAL(loop):
  cmp   w3, wzr
  b.eq  LOCAL(populateGPRs)
  ldr   x0, [x2], #8
  str   x0, [x9], #8
  sub   w3, w3, #8
  b     LOCAL(loop)

LOCAL(populateGPRs):
  cmp   x21, xzr
  b.eq  LOCAL(populateVFPs)
  ldp   x0, x1, [x21]
  ldp   x2, x3, [x21,#16]
  ldp   x4, x5, [x21,#32]
  ldp   x6, x7, [x21,#48]

LOCAL(populateVFPs):
  cmp   x22, xzr
  b.eq  LOCAL(doCall)
  ldp   d0, d1, [x22]
  ldp   d2, d3, [x22,#16]
  ldp   d4, d5, [x22,#32]
  ldp   d6, d7, [x22,#48]

LOCAL(doCall):
  blr   x19         // call function
  add   sp, sp, w20, uxtw // deallocate stack

  cmp   w23,#FLOAT_TYPE
  b.ne   LOCAL(double)
  fmov  w0,s0
  b     LOCAL(exit)

LOCAL(double):
  cmp   w23,#DOUBLE_TYPE
  b.ne   LOCAL(exit)
  fmov  x0,d0

LOCAL(exit):
  ldp   x19, x20, [sp,#16]
  ldp   x21, x22, [sp,#32]
  ldr   x23, [sp,#48]
  ldp   x29, x30, [sp],#64
  ret

.globl GLOBAL(vmJump)
.align 2
GLOBAL(vmJump):
  mov   x30, x0
  mov   x0, x4
  mov   x1, x5
  mov   sp, x2
  mov   x19, x3
  br    x30

#define CHECKPOINT_THREAD 8
#define CHECKPOINT_STACK 48

.globl GLOBAL(vmRun)
.align 2
GLOBAL(vmRun):
  // x0: function
  // x1: arguments
  // x2: checkpoint

  // allocate frame
  stp   x29, x30, [sp,#-96]!
  mov   x29, sp

  // save callee-saved register values
  stp   x19, x20, [sp,#16]
  stp   x21, x22, [sp,#32]
  stp   x23, x24, [sp,#48]
  stp   x25, x26, [sp,#64]
  stp   x27, x28, [sp,#80]

  mov   x19, sp
  str   x19, [x2, #CHECKPOINT_STACK]

  mov   x19, x0
  ldr   x0, [x2, #CHECKPOINT_THREAD]

  blr   x19

.globl GLOBAL(vmRun_returnAddress)
.align 2
GLOBAL(vmRun_returnAddress):
  ldp   x19, x20, [sp,#16]
  ldp   x21, x22, [sp,#32]
  ldp   x23, x24, [sp,#48]
  ldp   x25, x26, [sp,#64]
  ldp   x27, x28, [sp,#80]
  ldp   x29, x30, [sp],#96
  br    x30
