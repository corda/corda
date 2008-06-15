/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "types.h"

.text

#define BYTES_PER_WORD 4
#define LOCAL(x) L##x

#ifdef __APPLE__
.globl _vmNativeCall
_vmNativeCall: 
#else
.globl vmNativeCall
vmNativeCall:
#endif
   // save return address
   mflr r0
   stw  r0,8(r1)
   
   // r3 aka r13: function
   // r4        : stackTotal
   // r5        : memoryTable
   // r6        : memoryCount
   // r7        : gprTable
   // r8        : fprTable
   // r9 aka r14: returnType
   
   // r15       : stack frame size
   // r16       : temporary
   // r17       : temporary
   
   // save registers used for local variables
   stw  r13,24(r1)
   stw  r14,28(r1)
   stw  r15,32(r1)
   stw  r16,36(r1)
   stw  r17,40(r1)

   // allocate stack space
	 stwux	r1,r1,r4

   // save our argument registers so we can clobber them
   mr   r13,r3
   mr   r14,r9

   li   r16,0
   b    LOCAL(test)
   
LOCAL(loop):
   lwzx r17,r16,r5
   stwx r17,r16,r1
   addi r16,r16,BYTES_PER_WORD
   
LOCAL(test):
   cmplw r16,r6
   blt  LOCAL(loop)

   // do we need to load the floating point registers?
   cmpwi r8,0
   beq   LOCAL(gpr)

   // yes, we do
   lfd  f1,0(r8)
   lfd  f2,8(r8)
   lfd  f3,16(r8)
   lfd  f4,24(r8)
   lfd  f5,32(r8)
   lfd  f6,40(r8)
   lfd  f7,48(r8)
   lfd  f8,56(r8)
   lfd  f9,64(r8)
   lfd  f10,72(r8)
   lfd  f11,80(r8)
   lfd  f12,88(r8)
   lfd  f13,96(r8)
   
LOCAL(gpr):
   // do we need to load the general-purpose registers?
   cmpwi r7,0
   beq  LOCAL(call)

   // yes, we do
   mr   r16,r7
   lwz  r3,0(r16)
   lwz  r4,4(r16)
   lwz  r5,8(r16)
   lwz  r6,12(r16)
   lwz  r7,16(r16)
   lwz  r8,20(r16)
   lwz  r9,24(r16)
   lwz  r10,28(r16)

LOCAL(call):
   // load and call function address
   mtctr r13
   bctrl

   // handle return value based on expected type
   cmpwi r14,VOID_TYPE
   bne  LOCAL(float)
   b    LOCAL(exit)

LOCAL(float):
   cmpwi r14,FLOAT_TYPE
   beq  LOCAL(copy)
   cmpwi r14,DOUBLE_TYPE
   bne  LOCAL(exit)

LOCAL(copy):
   // move floating point return value to GPRs via memory
   stfd f1,8(r1)
   lwz  r3,8(r1)
   lwz  r4,12(r1)

LOCAL(exit):
   // restore stack pointer
	 lwz	r1,0(r1)

   // restore registers used for local variables
   lwz  r13,24(r1)
   lwz  r14,28(r1)
   lwz  r15,32(r1)
   lwz  r16,36(r1)
   lwz  r17,40(r1)

   // load return address
   lwz  r0,8(r1)
   mtlr r0

   // return
   blr
   
#ifdef __APPLE__
.globl _vmJump
_vmJump:
#else
.globl vmJump
vmJump:
#endif
   mtlr r3
   mr   r13,r4
   mr   r1,r5
   mr   r14,r6
   blr
