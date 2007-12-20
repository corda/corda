#include "types.h"

.text

#ifdef __x86_64__

.globl vmInvoke
vmInvoke:
   pushq  %rbp
   movq   %rsp,%rbp
   
   // rbx is a callee-saved register (so are r12-r15, but we don't use those)
   pushq  %rbx
      
   // %rdi: thread
   // %rsi: function
   // %rdx: stack
   // %rcx: stackSize
   // %r8 : returnType

   mov    %rdi,%rbx

   // copy memory arguments into place
   pushq  %rcx
   movq   $0,%r9
   jmp    test

loop:
   push   (%rdx,%r9,8)
   inc    %r9

test:
   cmpq   %rcx,%r9
   jb     loop
   
   // call function
   call   *%rsi

   // pop arguments
   mov    -16(%rbp),%rcx
   sal    $3,%rcx
   addq   %rcx,%rsp

   // pop argument stack size
   addq   $8,%rsp
   
   popq   %rbx
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
   
.globl vmInvoke
vmInvoke:
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
   jmp    test

loop:
   push   (%edx,%ecx,4)
   inc    %ecx

test:
   cmpl   20(%ebp),%ecx
   jb     loop

   // call function
   call   *12(%ebp)

   // pop arguments
   mov    20(%ebp),%ecx
   sal    $2,%ecx
   addl   %ecx,%esp

   // handle return value based on expected type
   movl   24(%ebp),%ecx
   
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
   popl   %edi
   popl   %esi
   popl   %ebx
   movl   %ebp,%esp
   popl   %ebp
   ret
     
.globl vmJump
vmJump:
   movl   4(%esp),%eax
   movl   8(%esp),%ebp
   movl   16(%esp),%ebx
   movl   12(%esp),%esp
   jmp    *%eax
   
#else
#  error unsupported platform
#endif
