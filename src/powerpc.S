/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "types.h"

.text

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

   // r3 aka 24(r1): function
   // r4 aka 28(r1): stack
   // r5 aka 32(r1): stackSize
   // r6 aka 36(r1): gprTable
   // r7 aka 40(r1): fprTable
   // r8 aka 48(r1): returnType

   // calculate aligned stack frame size
   add  r13,r5,LinkageArea + 15
   and  r13,r13,-16

   // save and update stack pointer
   sub  r14,r1,r9
   stwu r1,r14

   // save our argument registers so we can clobber them
   stw  r3,24(r1)
   stw  r4,28(r1)
   stw  r5,32(r1)
   stw  r6,36(r1)
   stw  r7,40(r1)
   stw  r8,48(r1)
   
   // todo

   // restore stack pointer
   addi r1,r1,r13

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
