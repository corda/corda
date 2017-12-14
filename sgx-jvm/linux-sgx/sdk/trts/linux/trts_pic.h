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


/*
 * This header contains constant definitions for tRTS.
 */

#ifndef TRTS_PIC_H__
#define TRTS_PIC_H__

#include "linux/linux-regs.h"
#include "rts_cmd.h"

#define SE_GUARD_PAGE_SIZE 0x10000

#define ENCLAVE_INIT_NOT_STARTED    0
#define ENCLAVE_INIT_IN_PROGRESS    1
#define ENCLAVE_INIT_DONE           2
#define ENCLAVE_CRASHED             3

/* Status */
#define SGX_SUCCESS                   0
#define SGX_ERROR_UNEXPECTED          0x000000001 // Unexpected error
#define SGX_ERROR_INVALID_FUNCTION    0x000001001 // Invalid ecall/ocall function
#define SGX_ERROR_INVALID_ENCLAVE     0x000002001 // The enclave image is incorrect
#define SGX_ERROR_ENCLAVE_CRASHED     0x000001006 // enclave is crashed
#define SGX_ERROR_STACK_OVERRUN       0x000001009 // enclave is running out of stack

#define STATIC_STACK_SIZE   688

/* Thread Data
 * c.f. data structure defintion for thread_data_t in `rts.h'.
 */
#define last_sp             (SE_WORDSIZE * 1)
#define stack_base_addr     (SE_WORDSIZE * 2)
#define stack_limit_addr    (SE_WORDSIZE * 3)
#define first_ssa_gpr       (SE_WORDSIZE * 4)
#define xsave_size          (SE_WORDSIZE * 7)
#define self_addr           0
#define stack_guard         (SE_WORDSIZE * 5)

/* SSA GPR */
#define ssa_sp_t            32
#define ssa_sp_u            144
#define ssa_bp_u            152
#define ssa_exit_info       160
#endif

#define EXIT_INFO_VALID     0x80000000
/* OCALL command */
#define OCALL_FLAG          0x04F434944

#define dtv    SE_WORDSIZE
#define tls    0 
.macro READ_TD_DATA offset
#ifdef SE_SIM
/* TLS support in simulation mode
 * see "sdk/simulation/uinst/linux/set_tls.c"
 * and "sdk/simulation/assembly/linux/gnu_tls.h"
 * TD address (tcs->ofs_base) is set to tcb_head->dtv->value.
 * The offset of tcb_head->dtv->value is SE_WORDSIZE.
 */

#if defined(LINUX32)
    mov     %gs:dtv, %xax
#elif defined(LINUX64)
    mov     %fs:dtv, %xax
#endif
    mov     tls(%xax), %xax
    mov     \offset(%xax), %xax

#else /* SE_SIM */

#if defined(LINUX32)
    mov     %fs:\offset, %xax
#elif defined(LINUX64)
    mov     %gs:\offset, %xax
#endif

#endif /* !SE_SIM */
.endm

.macro GET_STACK_BASE tcs
    mov      \tcs, %xax
    sub      $SE_GUARD_PAGE_SIZE, %xax
.endm
