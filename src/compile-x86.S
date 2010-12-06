/* Copyright (c) 2008-2010, Avian Contributors

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

#define THREAD_STACK 2216

#if defined __MINGW32__ || defined __CYGWIN32__

#define CALLEE_SAVED_REGISTER_FOOTPRINT 64

.globl GLOBAL(vmInvoke)
GLOBAL(vmInvoke):
   pushq  %rbp
   movq   %rsp,%rbp
   
   // %rcx: thread
   // %rdx: function
   // %r8 : arguments
   // %r9 : argumentsFootprint
   // 48(%rbp) : frameSize
   // 56(%rbp) : returnType (ignored)
   
   // allocate stack space, adding room for callee-saved registers
   movl   48(%rbp),%eax
   subq   %rax,%rsp
   subq   $CALLEE_SAVED_REGISTER_FOOTPRINT,%rsp
   
   // save callee-saved registers
   movq   %rsp,%r11
   addq   %rax,%r11

   movq   %rbx,0(%r11)
   movq   %r12,8(%r11)
   movq   %r13,16(%r11)
   movq   %r14,24(%r11)
   movq   %r15,32(%r11)
   movq   %rsi,40(%r11)
   movq   %rdi,48(%r11)
   
   // we use rbx to hold the thread pointer, by convention
   mov    %rcx,%rbx

   // copy arguments into place
   movq   $0,%r11
   jmp    LOCAL(vmInvoke_argumentTest)

LOCAL(vmInvoke_argumentLoop):
   movq   (%r8,%r11,1),%rsi
   movq   %rsi,(%rsp,%r11,1)
   addq   $8,%r11

LOCAL(vmInvoke_argumentTest):
   cmpq   %r9,%r11
   jb     LOCAL(vmInvoke_argumentLoop)
   
   // call function
   call   *%rdx

.globl GLOBAL(vmInvoke_returnAddress)
GLOBAL(vmInvoke_returnAddress):
   // restore stack pointer
   movq   %rbp,%rsp

   // clear MyThread::stack to avoid confusing another thread calling
   // java.lang.Thread.getStackTrace on this one.  See
   // MyProcess::getStackTrace in compile.cpp for details on how we get
   // a reliable stack trace from a thread that might be interrupted at
   // any point in its execution.
   movq   $0,THREAD_STACK(%rbx)
   
.globl GLOBAL(vmInvoke_safeStack)
GLOBAL(vmInvoke_safeStack):

#ifdef AVIAN_CONTINUATIONS
#  include "continuations-x86.S"
#endif // AVIAN_CONTINUATIONS

   // restore callee-saved registers (below the stack pointer, but in
   // the red zone)
   movq   %rsp,%r11
   subq   $CALLEE_SAVED_REGISTER_FOOTPRINT,%r11

   movq   0(%r11),%rbx
   movq   8(%r11),%r12
   movq   16(%r11),%r13
   movq   24(%r11),%r14
   movq   32(%r11),%r15
   movq   40(%r11),%rsi
   movq   48(%r11),%rdi

   // return
   popq   %rbp
   ret
      
.globl GLOBAL(vmJumpAndInvoke)
GLOBAL(vmJumpAndInvoke):
#ifdef AVIAN_CONTINUATIONS
   //     %rcx: thread
   //     %rdx: address
   //     %r8 : base
   //     %r9 : (unused)
   // 40(%rsp): argumentFootprint
   // 48(%rsp): arguments
   // 56(%rsp): frameSize

   movq   %r8,%rbp
   
   // restore (pseudo)-stack pointer (we don't want to touch the real
   // stack pointer, since we haven't copied the arguments yet)
   movq   %rbp,%r9
         
   // allocate new frame, adding room for callee-saved registers
   movl   56(%rsp),%eax
   subq   %rax,%r9
   subq   $CALLEE_SAVED_REGISTER_FOOTPRINT,%r9
   
   movq   %rcx,%rbx
   
   // set return address
   leaq   GLOBAL(vmInvoke_returnAddress)(%rip),%r10
   movq   %r10,(%r9)
   
   // copy arguments into place
   movq   $0,%r11
   movl   48(%rsp),%r8d
   movl   40(%rsp),%eax
   jmp    LOCAL(vmJumpAndInvoke_argumentTest)

LOCAL(vmJumpAndInvoke_argumentLoop):
   movq   (%r8,%r11,1),%r10
   movq   %r10,8(%r9,%r11,1)
   addq   $8,%r11

LOCAL(vmJumpAndInvoke_argumentTest):
   cmpq   %rax,%r11
   jb     LOCAL(vmJumpAndInvoke_argumentLoop)

   // the arguments have been copied, so we can set the real stack
   // pointer now
   movq   %r9,%rsp
   
   jmp    *%rdx
#else // not AVIAN_CONTINUATIONS
   // vmJumpAndInvoke should only be called when continuations are
   // enabled
   int3
#endif // not AVIAN_CONTINUATIONS

#else // not __MINGW32__ || __CYGWIN32__

#define CALLEE_SAVED_REGISTER_FOOTPRINT 48

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
   subq   $CALLEE_SAVED_REGISTER_FOOTPRINT,%rsp
   
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
   
   // clear MyThread::stack to avoid confusing another thread calling
   // java.lang.Thread.getStackTrace on this one.  See
   // MyProcess::getStackTrace in compile.cpp for details on how we get
   // a reliable stack trace from a thread that might be interrupted at
   // any point in its execution.
   movq   $0,THREAD_STACK(%rbx)
   
.globl GLOBAL(vmInvoke_safeStack)
GLOBAL(vmInvoke_safeStack):

#ifdef AVIAN_CONTINUATIONS
#  include "continuations-x86.S"
#endif // AVIAN_CONTINUATIONS
   
   // restore callee-saved registers (below the stack pointer, but in
   // the red zone)
   movq   %rsp,%r9
   subq   $CALLEE_SAVED_REGISTER_FOOTPRINT,%r9

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
   //    %rcx: (unused)
   //    %r8 : argumentFootprint
   //    %r9 : arguments
   // 8(%rsp): frameSize

   movq   %rdx,%rbp
   
   // restore (pseudo)-stack pointer (we don't want to touch the real
   // stack pointer, since we haven't copied the arguments yet)
   movq   %rbp,%rcx
         
   // allocate new frame, adding room for callee-saved registers
   movl   8(%rsp),%eax
   subq   %rax,%rcx
   subq   $CALLEE_SAVED_REGISTER_FOOTPRINT,%rcx
   
   movq   %rdi,%rbx
   
   // set return address
   movq   GLOBAL(vmInvoke_returnAddress)@GOTPCREL(%rip),%r10
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

   // the arguments have been copied, so we can set the real stack
   // pointer now
   movq   %rcx,%rsp
   
   jmp    *%rsi
#else // not AVIAN_CONTINUATIONS
   // vmJumpAndInvoke should only be called when continuations are
   // enabled
   int3
#endif // not AVIAN_CONTINUATIONS

#endif // not __MINGW32__ || __CYGWIN32__
   
#elif defined __i386__

#define THREAD_STACK 2144
   
#define CALLEE_SAVED_REGISTER_FOOTPRINT 16
   
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
   subl   $CALLEE_SAVED_REGISTER_FOOTPRINT,%esp
   
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

.globl GLOBAL(vmInvoke_returnAddress)
GLOBAL(vmInvoke_returnAddress):
   // restore stack pointer, preserving the area containing saved
   // registers
   movl   %ebp,%ecx
   subl   $CALLEE_SAVED_REGISTER_FOOTPRINT,%ecx
   movl   %ecx,%esp
   
   // clear MyThread::stack to avoid confusing another thread calling
   // java.lang.Thread.getStackTrace on this one.  See
   // MyProcess::getStackTrace in compile.cpp for details on how we get
   // a reliable stack trace from a thread that might be interrupted at
   // any point in its execution.
   movl   $0,THREAD_STACK(%ebx)
   
.globl GLOBAL(vmInvoke_safeStack)
GLOBAL(vmInvoke_safeStack):

#ifdef AVIAN_CONTINUATIONS
#  include "continuations-x86.S"
#endif // AVIAN_CONTINUATIONS
   
   // restore callee-saved registers
   movl   0(%esp),%ebx
   movl   4(%esp),%esi
   movl   8(%esp),%edi

   // handle return value based on expected type
   movl   28(%ebp),%ecx
   
   addl   $CALLEE_SAVED_REGISTER_FOOTPRINT,%esp
   
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
   // 16(%esp): (unused)
   // 20(%esp): argumentFootprint
   // 24(%esp): arguments
   // 28(%esp): frameSize

   movl   12(%esp),%ebp
   
   // restore (pseudo)-stack pointer (we don't want to touch the real
   // stack pointer, since we haven't copied the arguments yet)
   movl   %ebp,%ecx
   
   // allocate new frame, adding room for callee-saved registers
   subl   28(%esp),%ecx
   subl   $CALLEE_SAVED_REGISTER_FOOTPRINT,%ecx
   
   movl   4(%esp),%ebx
   
   // set return address
#if defined __MINGW32__ || defined __CYGWIN32__
   movl   $GLOBAL(vmInvoke_returnAddress),%esi
#else
   call   LOCAL(getPC)
#  if defined __APPLE__
LOCAL(vmJumpAndInvoke_offset):
   leal   GLOBAL(vmInvoke_returnAddress)-LOCAL(vmJumpAndInvoke_offset)(%esi),%esi
#  else
   addl   $_GLOBAL_OFFSET_TABLE_,%esi
   movl   GLOBAL(vmInvoke_returnAddress)@GOT(%esi),%esi
#  endif
#endif
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

   movl   8(%esp),%esi
   
   // the arguments have been copied, so we can set the real stack
   // pointer now
   movl   %ecx,%esp

   jmp    *%esi
#else // not AVIAN_CONTINUATIONS
   // vmJumpAndInvoke should only be called when continuations are
   // enabled
   int3
#endif // AVIAN_CONTINUATIONS
   
#else
#error unsupported architecture
#endif //def __x86_64__
