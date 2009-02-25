/* Copyright (c) 2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "types.h"

.text

#define BYTES_PER_WORD 4
#define LINKAGE_AREA 6
#define ARGUMENT_BASE BYTES_PER_WORD * LINKAGE_AREA

#define LOCAL(x) L##x

#ifdef __APPLE__
.globl _vmInvoke
_vmInvoke: 
#else
.globl vmInvoke
vmInvoke:
#endif
   // save return address
   mflr r0
   stw  r0,8(r1)

   // r3: thread
   // r4: function
   // r5: arguments
   // r6: argumentFootprint
   // r7: frameSize
   // r8: returnType

   // r9: temporary

   // save return type
   stw  r8,44(r1)

   // allocate stack space, adding room for callee-saved registers
   subfic r9,r7,-80
	 stwux r1,r1,r9
   
   // save callee-saved registers
   add  r9,r7,r1
   
   stw  r13,0(r9)
   stw  r14,4(r9)
   stw  r15,8(r9)
   stw  r16,12(r9)
   stw  r17,16(r9)
   stw  r18,20(r9)
   stw  r19,24(r9)
   stw  r20,28(r9)
   stw  r21,32(r9)
   stw  r22,36(r9)
   stw  r23,40(r9)
   stw  r24,44(r9)
   stw  r25,48(r9)
   stw  r26,52(r9)
   stw  r27,56(r9)
   stw  r28,60(r9)
   stw  r29,64(r9)
   stw  r30,68(r9)
   stw  r31,72(r9)

   // we use r13 to hold the thread pointer, by convention
   mr   r13,r3
   
   // copy arguments into place
   li   r16,0
   b    LOCAL(test)
   
LOCAL(loop):
   lwzx r17,r16,r5
   addi r18,r16,ARGUMENT_BASE
   stwx r17,r18,r1
   addi r16,r16,BYTES_PER_WORD
   
LOCAL(test):
   cmplw r16,r6
   blt  LOCAL(loop)

   // load and call function address
   mtctr r4
   bctrl
   
   // restore stack pointer
	 lwz	r1,0(r1)
   
   // restore callee-saved registers
   subi r9,r1,80
   
   lwz  r13,0(r9)
   lwz  r14,4(r9)
   lwz  r15,8(r9)
   lwz  r16,12(r9)
   lwz  r17,16(r9)
   lwz  r18,20(r9)
   lwz  r19,24(r9)
   lwz  r20,28(r9)
   lwz  r21,32(r9)
   lwz  r22,36(r9)
   lwz  r23,40(r9)
   lwz  r24,44(r9)
   lwz  r25,48(r9)
   lwz  r26,52(r9)
   lwz  r27,56(r9)
   lwz  r28,60(r9)
   lwz  r29,64(r9)
   lwz  r30,68(r9)
   lwz  r31,72(r9)
   
   // handle return value based on expected type
   lwz  r8,44(r1)
   
LOCAL(void):
   cmplwi r8,VOID_TYPE
   bne    LOCAL(int64)
   b      LOCAL(exit)

LOCAL(int64):
   cmplwi r8,INT64_TYPE
   bne    LOCAL(int32)
   b      LOCAL(exit)
   
LOCAL(int32):
   mr   r3,r4
   li   r3,0

LOCAL(exit):
   // load return address
   lwz  r0,8(r1)
   mtlr r0

   // return
   blr
