#include "types.h"

.text
.globl amd64Call
.type amd64Call, @function

amd64Call:
   pushq  %rbp

   // %rdi aka  0(%rbp): function
   // %rsi aka  8(%rbp): stack
   // %rdx aka 16(%rbp): stackSize
   // %rcx aka 24(%rbp): gprTable
   //  %r8 aka 32(%rbp): sseTable
   //  %r9 aka 40(%rbp): returnType

   // save our argument registers so we can clobber them
   pushq  %r9
   pushq  %r8
   pushq  %rcx
   pushq  %rdx
   pushq  %rsi
   pushq  %rdi
   
   movq   %rsp,%rbp

   // reserve space for arguments passed via memory
   subq   %rdx,%rsp

   // copy memory arguments into place
   movq   $0,%rcx
   jmp    test

loop:
   movq   %rcx,%rax
   movq   %rcx,%rdx
   addq   %rsp,%rdx
   addq   8(%rbp),%rax
	 movq	  (%rax),%rax
   movq   %rax,(%rdx)
   addq   $8,%rcx

test:
   cmpq   16(%rbp),%rcx
   jb     loop
   
   // do we need to load the general-purpose registers?
   cmpq   $0,24(%rbp)
   je     sse

   // yes, we do
   movq   24(%rbp),%rax
   movq   0(%rax),%rdi
   movq   8(%rax),%rsi
   movq   16(%rax),%rcx
   movq   24(%rax),%rdx
   movq   32(%rax),%r8
   movq   40(%rax),%r9

sse:
   // do we need to load the SSE registers?
   cmpq   $0,32(%rbp)
   je     call

   // yes, we do
   movq   32(%rbp),%rax
   movq   0(%rax),%xmm0
   movq   8(%rax),%xmm1
   movq   16(%rax),%xmm2
   movq   24(%rax),%xmm3
   movq   32(%rax),%xmm4
   movq   40(%rax),%xmm5
   movq   48(%rax),%xmm6
   movq   64(%rax),%xmm7

call:
   call   *0(%rbp)
   
   // clear space reserved for memory arguments
   movq   16(%rbp),%rcx
   addq   %rcx,%rsp

   // handle return value based on expected type
   movq   40(%rbp),%rcx

void:   
   cmpq   $VOID_TYPE,%rcx
   jne    float
   jmp    exit
   
float:   
   cmpq   $FLOAT_TYPE,%rcx
   je     copy
   cmpq   $DOUBLE_TYPE,%rcx
   jne    exit
   
copy:
   movq   %xmm0,%rax

exit:
   movq   %rbp,%rsp

   // pop our argument registers
   addq   $48,%rsp
   
   popq   %rbp
   ret
