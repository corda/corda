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
   
.globl vmNativeCall
vmNativeCall:
   pushq  %rbp
   movq   %rsp,%rbp

   // %rdi aka -48(%rbp): function
   // %rsi aka -40(%rbp): stack
   // %rdx aka -32(%rbp): stackSize
   // %rcx aka -24(%rbp): gprTable
   //  %r8 aka -16(%rbp): sseTable
   //  %r9 aka  -8(%rbp): returnType

   // save our argument registers so we can clobber them
   pushq  %r9
   pushq  %r8
   pushq  %rcx
   pushq  %rdx
   pushq  %rsi
   pushq  %rdi

   // reserve space for arguments passed via memory
   subq   %rdx,%rsp
   
   // align to a 16 byte boundary
   andq   $0xFFFFFFFFFFFFFFF0,%rsp

   // copy memory arguments into place
   movq   $0,%rcx
   jmp    LOCAL(test)

LOCAL(loop):
   movq   %rcx,%rax
   movq   %rcx,%rdx
   addq   %rsp,%rdx
   addq   -40(%rbp),%rax
   movq	  (%rax),%rax
   movq   %rax,(%rdx)
   addq   $8,%rcx

LOCAL(test):
   cmpq   -32(%rbp),%rcx
   jb     LOCAL(loop)
   
   // do we need to load the general-purpose registers?
   cmpq   $0,-24(%rbp)
   je     LOCAL(sse)

   // yes, we do
   movq   -24(%rbp),%rax
   movq   0(%rax),%rdi
   movq   8(%rax),%rsi
   movq   16(%rax),%rdx
   movq   24(%rax),%rcx
   movq   32(%rax),%r8
   movq   40(%rax),%r9

LOCAL(sse):
   // do we need to load the SSE registers?
   cmpq   $0,-16(%rbp)
   je     LOCAL(call)

   // yes, we do
   movq   -16(%rbp),%rax
   movq   0(%rax),%xmm0
   movq   8(%rax),%xmm1
   movq   16(%rax),%xmm2
   movq   24(%rax),%xmm3
   movq   32(%rax),%xmm4
   movq   40(%rax),%xmm5
   movq   48(%rax),%xmm6
   movq   64(%rax),%xmm7

LOCAL(call):
   call   *-48(%rbp)
   
   // handle return value based on expected type
   movq   -8(%rbp),%rcx

LOCAL(void):
   cmpq   $VOID_TYPE,%rcx
   jne    LOCAL(float)
   jmp    LOCAL(exit)
   
LOCAL(float):
   cmpq   $FLOAT_TYPE,%rcx
   je     LOCAL(copy)
   cmpq   $DOUBLE_TYPE,%rcx
   jne    LOCAL(exit)
   
LOCAL(copy):
   movq   %xmm0,%rax

LOCAL(exit):
   movq   %rbp,%rsp
   popq   %rbp
   ret

.globl vmJump
vmJump:
   movq   %rsi,%rbp
   movq   %rdx,%rsp
   movq   %rcx,%rbx
   jmp    *%rdi

#elif defined __i386__

#  if defined __APPLE__ || defined __MINGW32__ || defined __CYGWIN32__
.globl _vmNativeCall
_vmNativeCall: 
#  else
.globl vmNativeCall
vmNativeCall:
#  endif
   pushl  %ebp
   movl   %esp,%ebp

   //  8(%ebp): function
   // 12(%ebp): stack
   // 16(%ebp): stackSize
   // 20(%ebp): returnType
   
   // reserve space for arguments
   movl   16(%ebp),%ecx
   
   subl   %ecx,%esp

#  ifdef __APPLE__
   // align to a 16 byte boundary on Darwin
   andl   $0xFFFFFFF0,%esp
#  endif
   
   // copy arguments into place
   movl   $0,%ecx
   jmp    LOCAL(test)

LOCAL(loop):
   movl   %ecx,%eax
   movl   %ecx,%edx
   addl   %esp,%edx
   addl   12(%ebp),%eax
	 movl	  (%eax),%eax
   movl   %eax,(%edx)
   addl   $4,%ecx

LOCAL(test):
   cmpl   16(%ebp),%ecx
   jb     LOCAL(loop)

   // call function
   call   *8(%ebp)

   // handle return value based on expected type
   movl   20(%ebp),%ecx
   
LOCAL(void):
   cmpl   $VOID_TYPE,%ecx
   jne    LOCAL(int64)
   jmp    LOCAL(exit)

LOCAL(int64):
   cmpl   $INT64_TYPE,%ecx
   jne    LOCAL(float)
   jmp    LOCAL(exit)
   
LOCAL(float):
   cmpl   $FLOAT_TYPE,%ecx
   jne    LOCAL(double)
   fstps  8(%ebp)
   movl   8(%ebp),%eax
   jmp    LOCAL(exit)

LOCAL(double):
   cmpl   $DOUBLE_TYPE,%ecx
   jne    LOCAL(exit)
   fstpl  8(%ebp)
   movl   8(%ebp),%eax
   movl   12(%ebp),%edx

LOCAL(exit):
   movl   %ebp,%esp
   popl   %ebp
   ret
     
#  if defined __APPLE__ || defined __MINGW32__ || defined __CYGWIN32__
.globl _vmJump
_vmJump:
#  else
.globl vmJump
vmJump:
#  endif
   movl   4(%esp),%eax
   movl   8(%esp),%ebp
   movl   16(%esp),%ebx
   movl   12(%esp),%esp
   jmp    *%eax

#else
#  error unsupported platform
#endif
