#include "types.h"

.text

.globl vmInvoke
vmInvoke:
#ifdef __x86_64__

   pushq  %rbp
   movq   %rsp,%rbp
   
   // %rdi: function
   // %rsi: stack
   // %rdx: stackSize
   // %rcx: returnType

   // reserve space for arguments
   subq   %rdx,%rsp

   // copy memory arguments into place
   movq   $0,%r8
   jmp    test

loop:
   movq   %r8,%rax
   movq   %r8,%r9
   addq   %rsp,%r9
   addq   %rsi,%rax
	 movq	  (%rax),%rax
   movq   %rax,(%r9)
   addq   $8,%r8

test:
   cmpq   %rdx,%r8
   jb     loop
   
   // call function
   call   *%rdi
   
   movq   %rbp,%rsp   
   popq   %rbp
   ret
   
#elif defined __i386__
   
   pushl  %ebp
   movl   %esp,%ebp

   //  8(%ebp): function
   // 12(%ebp): stack
   // 16(%ebp): stackSize
   // 20(%ebp): returnType
   
   // reserve space for arguments
   movl   16(%ebp),%ecx
   
   subl   %ecx,%esp

   // copy arguments into place
   movl   $0,%ecx
   jmp    test

loop:
   movl   %ecx,%eax
   movl   %ecx,%edx
   addl   %esp,%edx
   addl   12(%ebp),%eax
	 movl	  (%eax),%eax
   movl   %eax,(%edx)
   addl   $4,%ecx

test:
   cmpl   16(%ebp),%ecx
   jb     loop

   // call function
   call   *8(%ebp)

   // handle return value based on expected type
   movl   20(%ebp),%ecx
   
void:   
   cmpl   $VOID_TYPE,%ecx
   jne    int64
   jmp    exit

int64: 
   cmpl   $INT64_TYPE,%ecx
   jne    int32
   jmp    exit
   
int32:
   movl   $0,%edx

exit:
   movl   %ebp,%esp
   popl   %ebp
   ret
      
#else
#  error unsupported platform
#endif
