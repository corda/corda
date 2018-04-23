/*
 * Copyright (C) 2011-2017 Intel Corporation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *   * Neither the name of Intel Corporation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */


#include "enter_enclave.h"


/* int __morestack(const tcs_t *tcs, const int fn, const void *ocall_table, const void *ms, CTrustThread *trust_thread); */
.file "enter_enclave.S"
.data
g_xsave_size: .long 0
g_clean_ymm:  .long 0
.text
DECLARE_GLOBAL_FUNC set_xsave_info
#if defined(LINUX32)
    mov     SE_WORDSIZE*1(%esp), %ecx
    mov     SE_WORDSIZE*2(%esp), %edx
#else
    mov     %rdi, %rcx
    mov     %rsi, %rdx
#endif
    lea_symbol g_xsave_size, %xax
    movl    %ecx, (%xax)
    lea_symbol g_clean_ymm, %xax
    movl    %edx, (%xax)
    ret

DECLARE_GLOBAL_FUNC __morestack

//__morestack:
EENTER_PROLOG
    movl    frame_arg1, %edi                    /* fn */
#if defined(__x86_64__)
    /* we defined fn as int, so we do sign extend.*/
    movslq  %edi,   %rdi
#endif
    mov frame_arg3, %xsi                        /* ms */

.Ldo_eenter:
    # clean the upper bits of YMM registers
    lea_symbol  g_clean_ymm, %xbx
    movl (%xbx), %ecx
    cmpl $0, %ecx
    je   1f
    vzeroupper
1:
    mov frame_arg0, %xbx                        /* tcs addr */
    lea_pic .Lasync_exit_pointer, %xcx          /* aep addr */
    mov $SE_EENTER, %xax                        /* EENTER leaf */

.Leenter_inst:
    ENCLU

/* 
 * at this point, we may have returned due to a normal EEXIT,
 * or we may have returned due to an OCALL. We differentiate 
 * by popping the top of the stack. If it is not OCMD_ERET, we have
 * an untrusted bridge to call at that address.
 */

/* We have an ocall. Call our bridge function. */

    cmp     $OCMD_ERET, %xdi
    je      .Leret

.Ldo_ocall:
/* call ocall
 * int ocall(const unsigned int proc, void *ocall_table, const void *ms, CEnclave *enclave);
 *
 * When EEXIT'ed from tRTS due to an OCALL,
 * - `rdi' holds OCALL index
 *- `rsi' holds the pointer to marshalling structure 
 */
.type sgx_ocall,@function
.hidden sgx_ocall
#if defined(__i386__)
    sub $(3*SE_WORDSIZE), %xsp              /* make stack 16 bytes aligned */
    push        frame_arg0
    push        frame_arg4
    push        %esi
    push        frame_arg2
    push        %edi
#endif

#if defined(__x86_64__)
    mov     %rsi, %rdx          /* param2 */
    /*mov     %rdi, %rdi */     /* param0 */
    mov     frame_arg2, %rsi    /* param1 */
    mov     frame_arg4, %rcx    /* param3 */
    mov     frame_arg0, %r8     /* param4 */
#endif

    call    stack_sticker
    xor     %xsi, %xsi
    mov     %eax, %esi
    cmp     $SE_ERROR_READ_LOCK_FAIL, %esi
    je      .Loret
    mov     $ECMD_ORET, %xdi    /* oret */
    jmp     .Ldo_eenter
    /* never return here */
    ud2
.Leret:

.Loret:
    EENTER_EPILOG

.Lasync_exit_pointer:
    ENCLU

.size __morestack, .-__morestack


DECLARE_GLOBAL_FUNC get_aep
    lea_pic     .Lasync_exit_pointer, %xax
    ret

DECLARE_GLOBAL_FUNC get_eenterp
    lea_pic .Leenter_inst, %xax
    ret

DECLARE_GLOBAL_FUNC get_eretp
    lea_pic .Leret, %xax
    ret

/*
 * function stack_sticker is the wrapper of ocall,
 * before call ocall, update the ret address and frame pointer (BP) on the stack
 *
 * Stack before:
 *                         |__morestack stack   |<--|
 *                          -------------           |
 *                         |return adress       |   |
 *                  xbp -> | caller xbp         | --|
 *                         |                    |
 *                  xsp -> |                    |
 *                          -------------
 *
 * Stack after:
 *                         |__morestack stack   |
 *                           ------------------
 *                         | __morestack(inside)|
 *                  xbp -> | xbp_t              | ---->the frame point of __morestack
 *                         |                    |
 *                  xsp -> |                    |
 *                         | <ecall>            |
 *                           ------------------
 * int stack_sticker(unsigned int proc, sgx_ocall_table_t *ocall_table, void *ms, CTrustThread *trust_thread, tcs_t *tcs)
 */
DECLARE_GLOBAL_FUNC stack_sticker
.hidden stack_sticker
.type push_ocall_frame,@function
.hidden push_ocall_frame
.type pop_ocall_frame,@function
.hidden pop_ocall_frame
.cfi_startproc
    push %xbp
.cfi_def_cfa_offset     2*SE_WORDSIZE
.cfi_offset             xbp,-2*SE_WORDSIZE
    mov %xsp, %xbp
.cfi_def_cfa_register   xbp
#if defined(__i386__)
    sub $(10*SE_WORDSIZE), %xsp                 /* We should have the stack 16 bytes aligned */
#elif defined(__x86_64__)
    sub $(10*SE_WORDSIZE), %xsp
#endif

    /* save the return address and the frame point */
    mov (0*SE_WORDSIZE)(%xbp), %xax
    mov %xax, (4*SE_WORDSIZE)(%xsp)
    mov (1*SE_WORDSIZE)(%xbp), %xax
    mov %xax, (5*SE_WORDSIZE)(%xsp)

#if defined(__i386__)
    /* save the first 4 parameters */
    mov frame_arg0, %xax
    mov %xax, (0*SE_WORDSIZE)(%xsp)
    mov frame_arg1, %xax
    mov %xax, (1*SE_WORDSIZE)(%xsp)
    mov frame_arg2, %xax
    mov %xax, (2*SE_WORDSIZE)(%xsp)
    mov frame_arg3, %xax
    mov %xax, (3*SE_WORDSIZE)(%xsp)

    /* update the frame infomation */
    mov frame_arg3, %xax                /* the pointer of trustthread instance */
    push %xax
    mov frame_arg4, %xax                /* tcs */
    push %xax
    push %xbp
    /*just for call push_ocall_frame and return, no stack 16 bytes alignment needed */
    call push_ocall_frame

    /* recover parameters */
    add $(3*SE_WORDSIZE), %xsp
#elif defined(__x86_64__)
    /* save the first 4 parameters */
    mov %xdi, (0*SE_WORDSIZE)(%xsp)
    mov %xsi, (1*SE_WORDSIZE)(%xsp)
    mov %xdx, (2*SE_WORDSIZE)(%xsp)
    mov %xcx, (3*SE_WORDSIZE)(%xsp)

    /* update the frame infomation */
    mov %xcx, %xdx                      /* the pointer of trustthread instance */
    mov %r8,  %xsi                      /* tcs */
    mov %xbp, %xdi
    call push_ocall_frame

    /* recover parameters */
    mov (0*SE_WORDSIZE)(%xsp), %xdi
    mov (1*SE_WORDSIZE)(%xsp), %xsi
    mov (2*SE_WORDSIZE)(%xsp), %xdx
    mov (3*SE_WORDSIZE)(%xsp), %xcx
#endif

    /* start the ocall */
    call        sgx_ocall
    
    /* save the return value */
    mov %xax, (0*SE_WORDSIZE)(%xsp)

#if defined(__i386__)
    /* pop the frame infomation */
    mov frame_arg3, %xax                /* the pointer of trustthread instance */
    push %xax
    mov frame_arg4, %xax                /* tcs is not used for now */
    push %xax
    call pop_ocall_frame

    /* recover parameters */
    add $(2*SE_WORDSIZE), %xsp
    
#elif defined(__x86_64__)
    /* pop the frame infomation */
    mov (3*SE_WORDSIZE)(%xsp), %xsi     /* the pointer of trustthread instance */
    call pop_ocall_frame
#endif

    /* recover the return address and frame point */
    mov (4*SE_WORDSIZE)(%xsp), %xax
    mov %xax, (0*SE_WORDSIZE)(%xbp)
    mov (5*SE_WORDSIZE)(%xsp), %xax
    mov %xax, (1*SE_WORDSIZE)(%xbp)
    
    /* recover the return value */
    mov (0*SE_WORDSIZE)(%xsp), %xax

    leave
    ret
    .cfi_endproc

/* 
 * void sgx_debug_load_state_add_element(debug_enclave_info_t* new_enclave_info, debug_enclave_info_t** g_debug_enclave_info_list)
 * On x86, calling convention is "void sgx_debug_load_state_add_element(eax, edx)"
 * On x64, calling convention is "void sgx_debug_load_state_add_element(rdi, rsi)"
 * Add an new enclave info to list head and raise enclave load event
 */
DECLARE_GLOBAL_FUNC sgx_debug_load_state_add_element
#if defined(__i386__)
    movl %eax, (%edx)
#else
    movq %rdi, (%rsi)
#endif
    ret

/*
 * void sgx_debug_unload_state_remove_element(debug_enclave_info_t* enclave_info, debug_enclave_info_t** pre_enclave_info, debug_enclave_info_t* next_enclave_info)
 * On x86, calling convention is "void se_debug_load_state_remove_element(eax, edx, ecx)"
 * On x64, calling convention is "void se_debug_load_state_remove_element(rdi, rsi, rdx)"
 * eax is to pass the removed enclave info to debugger.
 * Remove an enclave info from list and raise enclave unload event
 */
DECLARE_GLOBAL_FUNC sgx_debug_unload_state_remove_element
#if defined(__i386__)
    movl %ecx, (%edx) 
#else
    movq %rdx, (%rsi)
#endif
    ret

/* We do not need executable stack.*/   
.section        .note.GNU-stack,"",@progbits
