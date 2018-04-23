/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifdef __x86_64__

#define CONTINUATION_NEXT 8
#define CONTINUATION_ADDRESS 32
#define CONTINUATION_RETURN_ADDRESS_OFFSET 40
#define CONTINUATION_FRAME_POINTER_OFFSET 48
#define CONTINUATION_LENGTH 56
#define CONTINUATION_BODY 64

   // call the next continuation, if any
   movq   TARGET_THREAD_CONTINUATION(%rbx),%rcx
   cmpq   $0,%rcx
   je     LOCAL(vmInvoke_exit)

   // allocate a frame of size (continuation.length * BYTES_PER_WORD)
   // + CALLEE_SAVED_REGISTER_FOOTPRINT
   movq   CONTINUATION_LENGTH(%rcx),%rsi
   shlq   $3,%rsi
   subq   %rsi,%rsp
   subq   $CALLEE_SAVED_REGISTER_FOOTPRINT,%rsp

   // copy the continuation body into the frame
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

   // set the return address to vmInvoke_returnAddress
   movq   CONTINUATION_RETURN_ADDRESS_OFFSET(%rcx),%rdi
#if defined __MINGW32__ || defined __CYGWIN32__
   leaq   GLOBAL(vmInvoke_returnAddress)(%rip),%r10
#else
   movq   GLOBAL(vmInvoke_returnAddress)@GOTPCREL(%rip),%r10
#endif
   movq   %r10,(%rsp,%rdi,1)

#ifdef AVIAN_USE_FRAME_POINTER
   // save the current base pointer in the frame and update it
   movq   CONTINUATION_FRAME_POINTER_OFFSET(%rcx),%rdi
   movq   %rbp,(%rsp,%rdi,1)
   addq   %rsp,%rdi
   movq   %rdi,%rbp
#endif

   // consume the continuation
   movq   CONTINUATION_NEXT(%rcx),%rdi
   movq   %rdi,TARGET_THREAD_CONTINUATION(%rbx)

   // call the continuation unless we're handling an exception
   movq   TARGET_THREAD_EXCEPTION(%rbx),%rsi
   cmpq   $0,%rsi
   jne    LOCAL(vmInvoke_handleException)
   jmp    *CONTINUATION_ADDRESS(%rcx)

LOCAL(vmInvoke_handleException):
   // we're handling an exception - call the exception handler instead
   movq   $0,TARGET_THREAD_EXCEPTION(%rbx)
   movq   TARGET_THREAD_EXCEPTIONSTACKADJUSTMENT(%rbx),%rdi
   subq   %rdi,%rsp
   movq   TARGET_THREAD_EXCEPTIONOFFSET(%rbx),%rdi
   movq   %rsi,(%rsp,%rdi,1)
   
   jmp    *TARGET_THREAD_EXCEPTIONHANDLER(%rbx)

LOCAL(vmInvoke_exit):

#elif defined __i386__

#define CONTINUATION_NEXT 4
#define CONTINUATION_ADDRESS 16
#define CONTINUATION_RETURN_ADDRESS_OFFSET 20
#define CONTINUATION_FRAME_POINTER_OFFSET 24
#define CONTINUATION_LENGTH 28
#define CONTINUATION_BODY 32
   
#ifdef AVIAN_USE_FRAME_POINTER
#  define CONTINUATION_ALIGNMENT_PADDING 8
#else
#  define CONTINUATION_ALIGNMENT_PADDING 12
#endif

   // call the next continuation, if any
   movl   TARGET_THREAD_CONTINUATION(%ebx),%ecx
   cmpl   $0,%ecx
   je     LOCAL(vmInvoke_exit)

   // allocate a frame of size (continuation.length * BYTES_PER_WORD),
   // plus stack alignment padding
   movl   CONTINUATION_LENGTH(%ecx),%esi
   shll   $2,%esi
   leal   CONTINUATION_ALIGNMENT_PADDING(%esi),%esi
   subl   %esi,%esp
   
   // copy the continuation body into the frame
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

   // set the return address to vmInvoke_returnAddress
   movl   CONTINUATION_RETURN_ADDRESS_OFFSET(%ecx),%edi
#if defined __MINGW32__ || defined __CYGWIN32__
   movl   $GLOBAL(vmInvoke_returnAddress),%esi
#else
   call   LOCAL(getPC)
#  if defined __APPLE__
LOCAL(vmInvoke_offset):
   leal   GLOBAL(vmInvoke_returnAddress)-LOCAL(vmInvoke_offset)(%esi),%esi
#  else
   addl   $_GLOBAL_OFFSET_TABLE_,%esi
   movl   GLOBAL(vmInvoke_returnAddress)@GOT(%esi),%esi
#  endif
#endif
   movl   %esi,(%esp,%edi,1)
   
#ifdef AVIAN_USE_FRAME_POINTER
   // save the current base pointer in the frame and update it
   movl   CONTINUATION_FRAME_POINTER_OFFSET(%ecx),%edi
   movl   %ebp,(%esp,%edi,1)
   addl   %esp,%edi
   movl   %edi,%ebp
#endif
   
   // consume the continuation
   movl   CONTINUATION_NEXT(%ecx),%edi
   movl   %edi,TARGET_THREAD_CONTINUATION(%ebx)

   // call the continuation unless we're handling an exception
   movl   TARGET_THREAD_EXCEPTION(%ebx),%esi
   cmpl   $0,%esi
   jne    LOCAL(vmInvoke_handleException)

   jmp    *CONTINUATION_ADDRESS(%ecx)

LOCAL(vmInvoke_handleException):
   // we're handling an exception - call the exception handler instead
   movl   $0,TARGET_THREAD_EXCEPTION(%ebx)
   movl   TARGET_THREAD_EXCEPTIONSTACKADJUSTMENT(%ebx),%edi
   subl   %edi,%esp
   movl   TARGET_THREAD_EXCEPTIONOFFSET(%ebx),%edi
   movl   %esi,(%esp,%edi,1)
   
   jmp    *TARGET_THREAD_EXCEPTIONHANDLER(%ebx)

LOCAL(vmInvoke_exit):

#else
#  error unsupported architecture
#endif
   
