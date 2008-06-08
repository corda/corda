/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "types.h"

.text

BytesPerWord = 4
LinkageArea = 24

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

   // calculate aligned stack frame size
   add  r15,r4,LinkageArea + LocalArea + 15
   and  r15,r15,-16

   // save and update stack pointer
   sub  r16,r1,r15
   stwu r16,(r1)

   // save our argument registers so we can clobber them
   mr   r13,r3
   mr   r14,r9

   li   r16,0
   b    test
   
loop:
   lwzx r17,r16,r5
   stwx r17,r16,r1
   add  r16,r16,BytesPerWord
   
test:
   cmp  r16,r6
   blt  loop

   // do we need to load the floating point registers?
   cmp  r8,0
   be   gpr

   // yes, we do
   lfd  fr1,0(r8)
   lfd  fr2,8(r8)
   lfd  fr3,16(r8)
   lfd  fr4,24(r8)
   lfd  fr5,32(r8)
   lfd  fr6,40(r8)
   lfd  fr7,48(r8)
   lfd  fr8,56(r8)
   lfd  fr9,64(r8)
   lfd  fr10,72(r8)
   lfd  fr11,80(r8)
   lfd  fr12,88(r8)
   lfd  fr13,96(r8)
   
gpr:
   // do we need to load the general-purpose registers?
   cmp  r7,0
   be   call

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

call:
   // load and call function address
   mtctr r13
   bcctrl

   // handle return value based on expected type
   cmp  r14,VOID_TYPE
   bne  float
   b    exit

float:
   cmp  r14,FLOAT_TYPE
   be   copy
   cmp  r14,DOUBLE_TYPE
   bne  exit

copy:
   // move floating point return value to GPRs via memory
   sfd  fr1,8(r1)
   lwz  r3,8(r1)
   lwz  r4,12(r1)

exit:   
   // restore stack pointer
   addi r1,r1,r13

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
