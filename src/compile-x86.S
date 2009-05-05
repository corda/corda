/* Copyright (c) 2008-2009, Avian Contributors

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
   
#define THREAD_CONTINUATION 168
#define THREAD_EXCEPTION 64
#define THREAD_EXCEPTION_STACK 176
#define THREAD_EXCEPTION_OFFSET 184
#define THREAD_EXCEPTION_HANDLER 192

#define CONTINUATION_NEXT 8
#define CONTINUATION_ADDRESS 24
#define CONTINUATION_RETURN_ADDRESS_OFFSET 32
#define CONTINUATION_FRAME_POINTER_OFFSET 40
#define CONTINUATION_LENGTH 48
#define CONTINUATION_BODY 56

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
   jmp    LOCAL(vmInvoke_argumentTest)

LOCAL(vmInvoke_argumentLoop):
   movq   (%rdx,%r9,1),%r8
   movq   %r8,(%rsp,%r9,1)
   addq   $8,%r9

LOCAL(vmInvoke_argumentTest):
   cmpq   %rcx,%r9
   jb     LOCAL(vmInvoke_argumentLoop)
   
   // call function
   call   *%rsi
   
LOCAL(vmInvoke_returnAddress):
   // restore stack pointer
   movq   %rbp,%rsp

   // call the next continuation, if any
   movq   THREAD_CONTINUATION(%rbx),%rcx
   cmpq   $0,%rcx
   je     LOCAL(vmInvoke_exit)

   movq   CONTINUATION_LENGTH(%rcx),%rsi
   shrq   $3,%rsi
	 subq   %rsi,%rsp
   
   movq   CONTINUATION_BODY(%rcx),%rdi
   
   movq   $0,%r9
   jmp    LOCAL(vmInvoke_continuationTest)

LOCAL(vmInvoke_continuationLoop):
   movq   (%rdi,%r9,1),%r8
   movq   %r8,(%rsp,%r9,1)
   addq   $8,%r9
   
LOCAL(vmInvoke_continuationTest):
   cmpq   %rsi,%r9
   jb     LOCAL(vmInvoke_continuationLoop)
   
   movq   CONTINUATION_RETURN_ADDRESS_OFFSET(%rcx),%rdi
   movq   LOCAL(vmInvoke_returnAddress)@GOTPCREL(%rip),%r10
   movq   %r10,(%rsp,%rdi,1)
   
   movq   CONTINUATION_FRAME_POINTER_OFFSET(%rcx),%rdi
   movq   %rbp,(%rsp,%rdi,1)
   subq   %rdi,%rbp
   
   movq   CONTINUATION_NEXT(%rcx),%rdi
   movq   %rdi,THREAD_CONTINUATION(%rbx)

   // call the continuation unless we're handling an exception
   movq   THREAD_EXCEPTION(%rbx),%rsi
   cmpq   $0,%rsi
   jne    LOCAL(vmInvoke_handleException)
   jmp    *CONTINUATION_ADDRESS(%rcx)

LOCAL(vmInvoke_handleException):
   // we're handling an exception - call the exception handler instead
   movq   $0,THREAD_EXCEPTION(%rbx)
   movq   THREAD_EXCEPTION_STACK(%rbx),%rsp
   movq   THREAD_EXCEPTION_OFFSET(%rbx),%rdi
   movq   %rsi,(%rsp,%rdi,1)
   
   jmp    *THREAD_EXCEPTION_HANDLER(%rbx)

LOCAL(vmInvoke_exit):
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
   
.globl vmCallWithContinuation
vmCallWithContinuation:
   // %rdi: thread
   // %rsi: address
   // %rdx: targetObject
   // %rcx: continuation
   // %r8 : base
   // %r9 : stack
   
   movq   %rdi,%rdx
   movq   %r8,%rbp
   movq   %r9,%rsp
   movq   LOCAL(vmInvoke_returnAddress)@GOTPCREL(%rip),%r10
   movq   %r10,(%rsp)
   movq   %rcx,8(%rsp)
   movq   %rdx,16(%rsp)
   jmp    *%rsi
   
#elif defined __i386__

#  if defined __APPLE__ || defined __MINGW32__ || defined __CYGWIN32__
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
   
   // restore stack pointer and callee-saved registers
   movl   %ebp,%ecx
   subl   $16,%ecx
   movl   %ecx,%esp

   movl   0(%esp),%ebx
   movl   4(%esp),%esi
   movl   8(%esp),%edi

   // handle return value based on expected type
   movl   28(%ebp),%ecx
   
   addl   $16,%esp
   
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
