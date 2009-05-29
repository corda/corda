/* Copyright (c) 2008-2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "types.h"

#define LOCAL(x) .L##x

#if defined __APPLE__ || defined __MINGW32__ || defined __CYGWIN32__
#  define GLOBAL(x) _##x
#else
#  define GLOBAL(x) x   
#endif

.text

#ifdef __x86_64__
   
#define THREAD_CONTINUATION 168
#define THREAD_EXCEPTION 64
#define THREAD_EXCEPTION_STACK_ADJUSTMENT 176
#define THREAD_EXCEPTION_OFFSET 184
#define THREAD_EXCEPTION_HANDLER 192

#define CONTINUATION_NEXT 8
#define CONTINUATION_ADDRESS 32
#define CONTINUATION_RETURN_ADDRESS_OFFSET 40
#define CONTINUATION_FRAME_POINTER_OFFSET 48
#define CONTINUATION_LENGTH 56
#define CONTINUATION_BODY 64

.globl GLOBAL(vmInvoke)
GLOBAL(vmInvoke):
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
   
.globl GLOBAL(vmInvoke_returnAddress)
GLOBAL(vmInvoke_returnAddress):
   // restore stack pointer
   movq   %rbp,%rsp

#ifdef AVIAN_CONTINUATIONS
   // call the next continuation, if any
   movq   THREAD_CONTINUATION(%rbx),%rcx
   cmpq   $0,%rcx
   je     LOCAL(vmInvoke_exit)

   movq   CONTINUATION_LENGTH(%rcx),%rsi
   shlq   $3,%rsi
   subq   %rsi,%rsp
   subq   $48,%rsp
   
   leaq   CONTINUATION_BODY(%rcx),%rdi
   
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
   movq   vmInvoke_returnAddress@GOTPCREL(%rip),%r10
   movq   %r10,(%rsp,%rdi,1)
   
   movq   CONTINUATION_FRAME_POINTER_OFFSET(%rcx),%rdi
   movq   %rbp,(%rsp,%rdi,1)
   addq   %rsp,%rdi
   movq   %rdi,%rbp
   
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
   movq   THREAD_EXCEPTION_STACK_ADJUSTMENT(%rbx),%rdi
   subq   %rdi,%rsp
   movq   THREAD_EXCEPTION_OFFSET(%rbx),%rdi
   movq   %rsi,(%rsp,%rdi,1)
   
   jmp    *THREAD_EXCEPTION_HANDLER(%rbx)

LOCAL(vmInvoke_exit):
#endif // AVIAN_CONTINUATIONS
   
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
   
.globl GLOBAL(vmJumpAndInvoke)
GLOBAL(vmJumpAndInvoke):
#ifdef AVIAN_CONTINUATIONS
   //    %rdi: thread
   //    %rsi: address
   //    %rdx: base
   //    %rcx: stack
   //    %r8 : argumentFootprint
   //    %r9 : arguments
   // 8(%rsp): frameSize

   movq   %rdx,%rbp
   
   movq   %rdi,%rbx
   
   subq   8(%rsp),%rcx
   
   // set return address
   movq   vmInvoke_returnAddress@GOTPCREL(%rip),%r10
   movq   %r10,(%rcx)
   
   // copy arguments into place
   movq   $0,%r11
   jmp    LOCAL(vmJumpAndInvoke_argumentTest)

LOCAL(vmJumpAndInvoke_argumentLoop):
   movq   (%r9,%r11,1),%r10
   movq   %r10,8(%rcx,%r11,1)
   addq   $8,%r11

LOCAL(vmJumpAndInvoke_argumentTest):
   cmpq   %r8,%r11
   jb     LOCAL(vmJumpAndInvoke_argumentLoop)

   movq   %rcx,%rsp
   
   jmp    *%rsi
#else // not AVIAN_CONTINUATIONS
   // vmJumpAndInvoke should only be called when continuations are
   // enabled
   int3
#endif // not AVIAN_CONTINUATIONS
   
#elif defined __i386__
   
#define THREAD_CONTINUATION 96
#define THREAD_EXCEPTION 36
#define THREAD_EXCEPTION_STACK_ADJUSTMENT 100
#define THREAD_EXCEPTION_OFFSET 104
#define THREAD_EXCEPTION_HANDLER 108

#define CONTINUATION_NEXT 4
#define CONTINUATION_ADDRESS 16
#define CONTINUATION_RETURN_ADDRESS_OFFSET 20
#define CONTINUATION_FRAME_POINTER_OFFSET 24
#define CONTINUATION_LENGTH 28
#define CONTINUATION_BODY 32

.globl GLOBAL(vmInvoke)
GLOBAL(vmInvoke):
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
   jmp    LOCAL(vmInvoke_argumentTest)

LOCAL(vmInvoke_argumentLoop):
   movl   (%edx,%ecx,1),%eax
   movl   %eax,(%esp,%ecx,1)
   addl   $4,%ecx

LOCAL(vmInvoke_argumentTest):
   cmpl   20(%ebp),%ecx
   jb     LOCAL(vmInvoke_argumentLoop)

   // call function
   call   *12(%ebp)

.globl vmInvoke_returnAddress
vmInvoke_returnAddress:
   // restore stack pointer
   movl   %ebp,%ecx
   subl   $16,%ecx
   movl   %ecx,%esp

#ifdef AVIAN_CONTINUATIONS
   // call the next continuation, if any
   movl   THREAD_CONTINUATION(%ebx),%ecx
   cmpl   $0,%ecx
   je     LOCAL(vmInvoke_exit)

   movl   CONTINUATION_LENGTH(%ecx),%esi
   shll   $2,%esi
   subl   %esi,%esp
   subl   $16,%esp
   
   leal   CONTINUATION_BODY(%ecx),%edi

   push   %eax
   push   %edx
   
   movl   $0,%edx
   jmp    LOCAL(vmInvoke_continuationTest)

LOCAL(vmInvoke_continuationLoop):
   movl   (%edi,%edx,1),%eax
   movl   %eax,8(%esp,%edx,1)
   addl   $4,%edx
   
LOCAL(vmInvoke_continuationTest):
   cmpl   %esi,%edx
   jb     LOCAL(vmInvoke_continuationLoop)

   pop    %edx
   pop    %eax

   movl   CONTINUATION_RETURN_ADDRESS_OFFSET(%ecx),%edi
   call   LOCAL(getPC)
   addl   $_GLOBAL_OFFSET_TABLE_,%esi
   movl   vmInvoke_returnAddress@GOT(%esi),%esi
   movl   %esi,(%esp,%edi,1)
   
   movl   CONTINUATION_FRAME_POINTER_OFFSET(%ecx),%edi
   movl   %ebp,(%esp,%edi,1)
   addl   %esp,%edi
   movl   %edi,%ebp
   
   movl   CONTINUATION_NEXT(%ecx),%edi
   movl   %edi,THREAD_CONTINUATION(%ebx)

   // call the continuation unless we're handling an exception
   movl   THREAD_EXCEPTION(%ebx),%esi
   cmpl   $0,%esi
   jne    LOCAL(vmInvoke_handleException)

   jmp    *CONTINUATION_ADDRESS(%ecx)

LOCAL(vmInvoke_handleException):
   // we're handling an exception - call the exception handler instead
   movl   $0,THREAD_EXCEPTION(%ebx)
   movl   THREAD_EXCEPTION_STACK_ADJUSTMENT(%ebx),%edi
   subl   %edi,%esp
   movl   THREAD_EXCEPTION_OFFSET(%ebx),%edi
   movl   %esi,(%esp,%edi,1)
   
   jmp    *THREAD_EXCEPTION_HANDLER(%ebx)

LOCAL(vmInvoke_exit):
#endif // AVIAN_CONTINUATIONS
   
   // restore callee-saved registers
   movl   0(%esp),%ebx
   movl   4(%esp),%esi
   movl   8(%esp),%edi

   // handle return value based on expected type
   movl   28(%ebp),%ecx
   
   addl   $16,%esp
   
LOCAL(vmInvoke_void):
   cmpl   $VOID_TYPE,%ecx
   jne    LOCAL(vmInvoke_int64)
   jmp    LOCAL(vmInvoke_return)

LOCAL(vmInvoke_int64):
   cmpl   $INT64_TYPE,%ecx
   jne    LOCAL(vmInvoke_int32)
   jmp    LOCAL(vmInvoke_return)
   
LOCAL(vmInvoke_int32):
   movl   $0,%edx

LOCAL(vmInvoke_return):
   popl   %ebp
   ret

LOCAL(getPC):
   movl   (%esp),%esi
   ret
   
.globl GLOBAL(vmJumpAndInvoke)
GLOBAL(vmJumpAndInvoke):
#ifdef AVIAN_CONTINUATIONS
   //  4(%esp): thread
   //  8(%esp): address
   // 12(%esp): base
   // 16(%esp): stack
   // 20(%esp): argumentFootprint
   // 24(%esp): arguments
   // 28(%esp): frameSize

   movl   12(%esp),%ebp
   
   movl   16(%esp),%ecx
   subl   28(%esp),%ecx
   
   // set return address
   call   LOCAL(getPC)
   addl   $_GLOBAL_OFFSET_TABLE_,%esi
   movl   vmInvoke_returnAddress@GOT(%esi),%esi
   movl   %esi,(%ecx)
   
   // copy arguments into place
   movl   $0,%esi
   movl   20(%esp),%edx
   movl   24(%esp),%eax
   jmp    LOCAL(vmJumpAndInvoke_argumentTest)

LOCAL(vmJumpAndInvoke_argumentLoop):
   movl   (%eax,%esi,1),%edi
   movl   %edi,4(%ecx,%esi,1)
   addl   $4,%esi

LOCAL(vmJumpAndInvoke_argumentTest):
   cmpl   %edx,%esi
   jb     LOCAL(vmJumpAndInvoke_argumentLoop)

   movl   4(%esp),%ebx
   movl   8(%esp),%esi
   movl   %ecx,%esp

   jmp    *%esi
#else // not AVIAN_CONTINUATIONS
   // vmJumpAndInvoke should only be called when continuations are
   // enabled
   int3
#endif // AVIAN_CONTINUATIONS
   
#else
#  error unsupported platform
#endif
