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
  /*
    arguments:
    r0 -> r4       : function
    r1 -> r5       : stackTotal
    r2             : memoryTable
    r3             : memoryCount
    [sp, #0] -> r6 : gprTable
    [sp, #4] -> r7 : vfpTable
    [sp, #8] -> r8 : returnType
  */
  mov   ip, sp           // save stack frame
  stmfd sp!, {r4-r8, lr} // save clobbered non-volatile regs

  // mv args into non-volatile regs
  mov   r4, r0
  mov   r5, r1
  ldr   r6, [ip]
  ldr   r7, [ip, #4]
  ldr   r8, [ip, #8]

  // setup stack arguments if necessary
  sub   sp, sp, r5 // allocate stack
  mov   ip, sp
LOCAL(loop):
  tst   r3, r3
  ldrne r0, [r2], #4
  strne r0, [ip], #4
  subne r3, r3, #4
  bne   LOCAL(loop)

  // setup argument registers if necessary
  tst     r6, r6
#if (defined __APPLE__) && (defined __clang_major__) && (__clang_major__ >= 4)
  ldmiane r6, {r0-r3}
#else
  ldmneia r6, {r0-r3}
#endif
#if defined(__ARM_PCS_VFP)
  // and VFP registers
  vldmia  r7, {d0-d7}
#endif

#if defined(__ARM_ARCH_4__) || defined(__ARM_ARCH_4T__)
  mov   lr, pc
  bx    r4
#else
  blx   r4         // call function
#endif
  add   sp, sp, r5 // deallocate stack

#if defined(__ARM_PCS_VFP)
  cmp   r8,#FLOAT_TYPE
  bne   LOCAL(double)
  fmrs  r0,s0
  b     LOCAL(exit)

LOCAL(double):
  cmp   r8,#DOUBLE_TYPE
  bne   LOCAL(exit)
  fmrrd r0,r1,d0
#endif

LOCAL(exit):
  ldmfd sp!, {r4-r8, pc} // restore non-volatile regs and return

.globl GLOBAL(vmJump)
.align 2
GLOBAL(vmJump):
  mov   lr, r0
  ldr   r0, [sp]
  ldr   r1, [sp, #4]
  mov   sp, r2
  mov   r8, r3
  bx    lr

#define CHECKPOINT_THREAD 4
#define CHECKPOINT_STACK 24

.globl GLOBAL(vmRun)
.align 2
GLOBAL(vmRun):
  // r0: function
  // r1: arguments
  // r2: checkpoint
  stmfd sp!, {r4-r11, lr}
  // align stack
  sub   sp, sp, #12

  str   sp, [r2, #CHECKPOINT_STACK]

  mov   r12, r0
  ldr   r0, [r2, #CHECKPOINT_THREAD]

#if defined(__ARM_ARCH_4__) || defined(__ARM_ARCH_4T__)
  mov   lr, pc
  bx    r12
#else
  blx   r12
#endif

.globl GLOBAL(vmRun_returnAddress)
.align 2
GLOBAL(vmRun_returnAddress):
  add   sp, sp, #12
  ldmfd sp!, {r4-r11, lr}
  bx    lr
