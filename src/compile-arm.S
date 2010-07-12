/* Copyright (c) 2010, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifdef AVIAN_CONTINUATIONS
#  error "Continuations not yet supported on ARM port"
#endif

#include "types.h"

.text

#define BYTES_PER_WORD 4

#define LOCAL(x) L##x
   
#ifdef __APPLE__
#  define GLOBAL(x) _##x
#else
#  define GLOBAL(x) x   
#endif
      
.globl GLOBAL(vmInvoke)
GLOBAL(vmInvoke):
  /*
    arguments
    r0       : thread
    r1       : function
    r2       : arguments
    r3       : argumentFootprint
    [sp, #0] : frameSize (not used)
    [sp, #4] : returnType
  */

  // save stack frame
  mov   ip, sp

  // save all non-volatile registers
  stmfd sp!, {r4-r11, lr}

  // save return type
  ldr   r4, [ip, #4]
  str   r4, [sp, #-4]!

  // we're at the bottom of our local stack frame; save it
  mov   ip, sp

  // align stack, if necessary
  eor   r4, sp, r3
  tst   r4, #4
  subne sp, sp, #4

  // copy arguments into place
  sub   sp, r3
  mov   r4, #0
  b     LOCAL(vmInvoke_argumentTest)

LOCAL(vmInvoke_argumentLoop):
  ldr   r5, [r2, r4]
  str   r5, [sp, r4]
  add   r4, r4, #BYTES_PER_WORD

LOCAL(vmInvoke_argumentTest):
  cmp   r4, r3
  blt   LOCAL(vmInvoke_argumentLoop)

  // save the beginning of our stack frame
  str   ip, [sp, #-8]!

  // we use ip (r12) to hold the thread pointer, by convention
  mov   ip, r0

  // load and call function address
  blx   r1

LOCAL(vmInvoke_returnAddress):
  // restore stack pointer
  ldr   sp, [sp]

  // restore return type
  ldr   ip, [sp]

  // restore callee-saved registers
  ldmfd sp!, {r4-r11, lr}

LOCAL(vmInvoke_void):
  cmp   ip, #VOID_TYPE
  beq   LOCAL(vmInvoke_return)

LOCAL(vmInvoke_int64):
  cmp   ip, #INT64_TYPE
  beq   LOCAL(vmInvoke_return)

LOCAL(vmInvoke_int32):
  mov   r1, #0

LOCAL(vmInvoke_return):
  bx    lr

.globl GLOBAL(vmJumpAndInvoke)
GLOBAL(vmJumpAndInvoke):
  // vmJumpAndInvoke should only be called when continuations are
  // enabled
  bkpt
