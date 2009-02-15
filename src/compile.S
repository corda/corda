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
   
   // push callee-saved registers
   pushq  %rbx
   pushq  %r12
   pushq  %r13
   pushq  %r14
   pushq  %r15
      
   // %rdi: thread
   // %rsi: function
   // %rdx: stack
   // %rcx: stackSize
   // %r8 : returnType

   mov    %rdi,%rbx

   // copy arguments into place
   pushq  %rcx
   movq   $0,%r9
   jmp    LOCAL(test)

LOCAL(loop):
   push   (%rdx,%r9,8)
   inc    %r9

LOCAL(test):
   cmpq   %rcx,%r9
   jb     LOCAL(loop)
   
   // call function
   call   *%rsi

   // pop arguments
   mov    -48(%rbp),%rcx
   sal    $3,%rcx
   addq   %rcx,%rsp

   // pop argument stack size
   addq   $8,%rsp
   
   // pop callee-saved registers
   popq   %r15
   popq   %r14
   popq   %r13
   popq   %r12
   popq   %rbx
   
   movq   %rbp,%rsp   
   popq   %rbp
   ret

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

   // ebx, esi and edi are callee-saved registers
   pushl  %ebx
   pushl  %esi
   pushl  %edi

   //  8(%ebp): thread
   // 12(%ebp): function
   // 16(%ebp): stack
   // 20(%ebp): stackSize
   // 24(%ebp): returnType
   
   mov    8(%ebp),%ebx
   
   // copy arguments into place
   movl   $0,%ecx
   mov    16(%ebp),%edx
   jmp    LOCAL(test)

LOCAL(loop):
   push   (%edx,%ecx,4)
   inc    %ecx

LOCAL(test):
   cmpl   20(%ebp),%ecx
   jb     LOCAL(loop)

   // call function
   call   *12(%ebp)

   // pop arguments
   mov    20(%ebp),%ecx
   sal    $2,%ecx
   addl   %ecx,%esp

   // handle return value based on expected type
   movl   24(%ebp),%ecx
   
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
   popl   %edi
   popl   %esi
   popl   %ebx
   movl   %ebp,%esp
   popl   %ebp
   ret
   
#else
#  error unsupported platform
#endif
