#include "types.h"

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

.globl vmJump
vmJump:
   movq   %rsi,%rbp
   movq   %rdx,%rsp
   movq   %rcx,%rbx
   jmp    *%rdi

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
     
#  if defined __APPLE__ || defined __MINGW32__
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
