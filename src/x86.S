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

#ifdef __WINDOWS__
#  if defined __APPLE__ || defined __MINGW32__ || defined __CYGWIN32__
.globl _detectFeature
_detectFeature: 
#  else
.globl detectFeature
detectFeature:
#  endif
   pushq %rbp
   movq %rsp, %rbp
   pushq %rdx
   pushq %rcx
   pushq %rbx
   pushq %rsi
   pushq %rdi
   movl %ecx, %edi
   movl %edx, %esi
   movl $1, %eax
   cpuid
   andl %esi, %edx
   andl %edi, %ecx
   orl %edx, %ecx
   test %ecx, %ecx
   je LOCAL(NOSSE)
   movl $1, %eax
   jmp LOCAL(SSEEND)
LOCAL(NOSSE):
   movl $0, %eax
LOCAL(SSEEND):
   popq %rdi
   popq %rsi
   popq %rbx
   popq %rcx
   popq %rdx
   movq %rbp,%rsp
   popq %rbp
   ret

#  if defined __APPLE__ || defined __MINGW32__ || defined __CYGWIN32__
.globl _vmNativeCall
_vmNativeCall: 
#  else
.globl vmNativeCall
vmNativeCall:
#  endif
   pushq %rbp
   //save nonvolatile registers
   pushq %r12
   pushq %r13
   pushq %r14
   pushq %r15
   movq %rsp, %rbp
   
   
   // %rcx: function
   // %rdx: arguments
   // %r8:  arguments count
   // %r9:  return type
   
   movq %rcx, %r10
   movq %rdx, %r11
   movq %r8, %r12
   movq %r9, %r13
   
   // %r10: function
   // %r11: arguments
   // %r12: arguments count
   // %r13: return type
   
   //allocate initial stack space
   subq $32, %rsp
   
   //first arg
   cmp $0, %r12
   je LOCAL(call)
   movq 0(%r11),%rcx
   movq 0(%r11),%xmm0
   subq $1, %r12
   
   //second arg
   cmp $0, %r12
   je LOCAL(call)
   movq 8(%r11),%rdx
   movq 8(%r11),%xmm1
   subq $1, %r12
   
   //third arg
   cmp $0, %r12
   je LOCAL(call)
   movq 16(%r11),%r8
   movq 16(%r11),%xmm2
   subq $1, %r12
   
   //fourth arg
   cmp $0, %r12
   je LOCAL(call)
   movq 24(%r11),%r9
   movq 24(%r11),%xmm3
   subq $1, %r12
   
   
   //calculate stack space for arguments, aligned
   movq $8, %r15
   leaq (%r15, %r12, 8), %r15
   andq $0xFFFFFFFFFFFFFFF0, %r15
   
   //reserve stack space for arguments
   subq %r15, %rsp
   
   //reset the counter
   addq $3, %r12
   jmp LOCAL(loopend)
   
LOCAL(loop):
   movq (%r11, %r12, 8), %r14
   movq %r14, (%rsp, %r12, 8);
   subq $1, %r12
   
LOCAL(loopend):
   //we don't need to move arg 3 and lower
   cmpq $3, %r12
   jne LOCAL(loop)
   
LOCAL(call):
   call *%r10
   
LOCAL(void):
   cmpq   $VOID_TYPE,%r13
   jne    LOCAL(float)
   jmp    LOCAL(exit)
   
LOCAL(float):
   cmpq   $FLOAT_TYPE,%r13
   je     LOCAL(copy)
   cmpq   $DOUBLE_TYPE,%r13
   jne    LOCAL(exit)
   
LOCAL(copy):
   movq   %xmm0,%rax
   
LOCAL(exit):
   
   movq %rbp, %rsp
   //return nonvolatile registers to their former state
   popq %r15
   popq %r14
   popq %r13
   popq %r12
   
   popq %rbp
   ret

.globl _vmJump
_vmJump:
   movq   %rdx,%rbp
   movq   %r8,%rsp
   movq   %r9,%rbx
   jmp    *%rcx
   
#elif defined __LINUX__
#  if defined __APPLE__ || defined __MINGW32__ || defined __CYGWIN32__
.globl _detectFeature
_detectFeature: 
#  else
.globl detectFeature
detectFeature:
#  endif
   pushq %rbp
   movq %rsp, %rbp
   pushq %rdx
   pushq %rcx
   pushq %rbx
   movl $1, %eax
   cpuid
   andl %esi, %edx
   andl %edi, %ecx
   orl %edx, %ecx
   test %ecx, %ecx
   je LOCAL(NOSSE)
   movl $1, %eax
   jmp LOCAL(SSEEND)
LOCAL(NOSSE):
   movl $0, %eax
LOCAL(SSEEND):
   popq %rbx
   popq %rcx
   popq %rdx
   movq %rbp,%rsp
   popq %rbp
   ret

#  if defined __APPLE__ || defined __MINGW32__ || defined __CYGWIN32__
.globl _vmNativeCall
_vmNativeCall: 
#  else
.globl vmNativeCall
vmNativeCall:
#  endif
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
   
#endif //def __WINDOWS__
#elif defined __i386__
#  if defined __APPLE__ || defined __MINGW32__ || defined __CYGWIN32__
.globl _detectFeature
_detectFeature: 
#  else
.globl detectFeature
detectFeature:
#  endif
   pushl %ebp
   movl %esp, %ebp
   pushl %edx
   pushl %ecx
   pushl %ebx
   pushl %esi
   pushl %edi
   movl 12(%ebp), %esi
   movl 8(%ebp), %edi
   movl $1, %eax
   cpuid
   andl %esi, %edx
   andl %edi, %ecx
   orl %edx, %ecx
   test %ecx, %ecx
   je LOCAL(NOSSE)
   movl $1, %eax
   jmp LOCAL(SSEEND)
LOCAL(NOSSE):
   movl $0, %eax
LOCAL(SSEEND):
   popl %edi
   popl %esi
   popl %ebx
   popl %ecx
   popl %edx
   movl %ebp,%esp
   popl %ebp
   ret
   
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
#endif //def __x86_64__
