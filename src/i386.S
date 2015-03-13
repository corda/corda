/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/types.h"

#define LOCAL(x) .L##x

#if defined __APPLE__ \
   || ((defined __MINGW32__ || defined __CYGWIN32__) && ! defined __x86_64__)
#  define GLOBAL(x) _##x
#else
#  define GLOBAL(x) x   
#endif
   
.text

#define CHECKPOINT_THREAD 4
#define CHECKPOINT_STACK 24
#define CHECKPOINT_BASE 28

.globl GLOBAL(vmNativeCall)
GLOBAL(vmNativeCall):
   pushl  %ebp
   movl   %esp,%ebp

   //  8(%ebp): function
   // 12(%ebp): stack
   // 16(%ebp): stackSize
   // 20(%ebp): returnType
   
   // reserve space for arguments
   movl   16(%ebp),%ecx
   
   subl   %ecx,%esp

//#  ifdef __APPLE__
   // align to a 16 byte boundary
   andl   $0xFFFFFFF0,%esp
//#  endif
   
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
     
.globl GLOBAL(vmJump)
GLOBAL(vmJump):
   movl   4(%esp),%esi
   movl   8(%esp),%ebp
   movl   16(%esp),%ebx
   movl   20(%esp),%eax
   movl   24(%esp),%edx
   movl   12(%esp),%esp
   jmp    *%esi

#define VMRUN_FRAME_SIZE 24

.globl GLOBAL(vmRun)
GLOBAL(vmRun):
   //  8(%ebp): function
   // 12(%ebp): arguments
   // 16(%ebp): checkpoint
   pushl  %ebp
   movl   %esp,%ebp
   subl   $VMRUN_FRAME_SIZE,%esp
   
   movl   %ebx,8(%esp)
   movl   %esi,12(%esp)
   movl   %edi,16(%esp)

   movl   12(%ebp),%eax
   movl   %eax,4(%esp)

   movl   16(%ebp),%ecx
   movl   CHECKPOINT_THREAD(%ecx),%eax
   movl   %eax,0(%esp)

   movl   %esp,CHECKPOINT_STACK(%ecx)

   call   *8(%ebp)
   
.globl GLOBAL(vmRun_returnAddress)
GLOBAL(vmRun_returnAddress):
   
   movl   8(%esp),%ebx
   movl   12(%esp),%esi
   movl   16(%esp),%edi

   addl   $VMRUN_FRAME_SIZE,%esp
   popl   %ebp
   ret
