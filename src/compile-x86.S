/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "types.h"

#define LOCAL(x) .L##x

.text

#ifdef __x86_64__

.globl vmInvoke
vmInvoke:
   pushq  %rbp
   movq   %rsp,%rbp
      
   // %rdi: thread
   // %rsi: function
   // %rdx: arguments
   // %rcx: argumentFootprint
   // %r8 : frameSize
   // %r9 : returnType (ignored)
   
   // allocate stack space, adding room for callee-saved registers
	 subq   %r8,%rsp
	 subq   $48,%rsp
   
   // save callee-saved registers
   movq   %rsp,%r9
   addq   %r8,%r9

   movq   %rbx,0(%r9)
   movq   %r12,8(%r9)
   movq   %r13,16(%r9)
   movq   %r14,24(%r9)
   movq   %r15,32(%r9)

   // we use rbx to hold the thread pointer, by convention
   mov    %rdi,%rbx

   // copy arguments into place
   movq   $0,%r9
   jmp    LOCAL(test)

LOCAL(loop):
   movq   (%rdx,%r9,1),%r8
   movq   %r8,(%rsp,%r9,1)
   addq   $8,%r9

LOCAL(test):
   cmpq   %rcx,%r9
   jb     LOCAL(loop)
   
   // call function
   call   *%rsi
   
   // restore stack pointer
   movq   %rbp,%rsp
   
   // restore callee-saved registers
   movq   %rsp,%r9
   subq   $48,%r9

   movq   0(%r9),%rbx
   movq   8(%r9),%r12
   movq   16(%r9),%r13
   movq   24(%r9),%r14
   movq   32(%r9),%r15

   // return
   popq   %rbp
   ret

#elif defined __i386__

#  if defined __APPLE__ || defined __MINGW32__
.globl _vmInvoke
_vmInvoke: 
#  else
.globl vmInvoke
vmInvoke:
#  endif
   pushl  %ebp
   movl   %esp,%ebp

   //  8(%ebp): thread
   // 12(%ebp): function
   // 16(%ebp): arguments
   // 20(%ebp): argumentFootprint
   // 24(%ebp): frameSize
   // 28(%ebp): returnType

   // allocate stack space, adding room for callee-saved registers
	 subl   24(%ebp),%esp
	 subl   $16,%esp
   
   // save callee-saved registers
   movl   %esp,%ecx
   addl   24(%ebp),%ecx

   movl   %ebx,0(%ecx)
   movl   %esi,4(%ecx)
   movl   %edi,8(%ecx)
   
   // we use ebx to hold the thread pointer, by convention
   mov    8(%ebp),%ebx
   
   // copy arguments into place
   movl   $0,%ecx
   movl   16(%ebp),%edx
   jmp    LOCAL(test)

LOCAL(loop):
   movl   (%edx,%ecx,1),%eax
   movl   %eax,(%esp,%ecx,1)
   addl   $4,%ecx

LOCAL(test):
   cmpl   20(%ebp),%ecx
   jb     LOCAL(loop)

   // call function
   call   *12(%ebp)
   
   // restore stack pointer
   movl   %ebp,%esp
   
   // restore callee-saved registers
   movl   %esp,%ecx
   subl   $16,%ecx

   movl   0(%ecx),%ebx
   movl   4(%ecx),%esi
   movl   8(%ecx),%edi

   // handle return value based on expected type
   movl   28(%ebp),%ecx
   
LOCAL(void):
   cmpl   $VOID_TYPE,%ecx
   jne    LOCAL(int64)
   jmp    LOCAL(exit)

LOCAL(int64):
   cmpl   $INT64_TYPE,%ecx
   jne    LOCAL(int32)
   jmp    LOCAL(exit)
   
LOCAL(int32):
   movl   $0,%edx

LOCAL(exit):
   popl   %ebp
   ret
   
#else
#  error unsupported platform
#endif
