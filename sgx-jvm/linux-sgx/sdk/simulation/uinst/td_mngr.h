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


#ifndef _TD_MNGR_H_
#define _TD_MNGR_H_

#include "arch.h"
#include "rts.h"

// use tcs->reserved field to save some information
typedef struct _tcs_sim_t
{
    uintptr_t saved_aep;
    size_t    tcs_state;
    uintptr_t saved_dtv;
    uintptr_t saved_fs_gs_0;
} tcs_sim_t;

#define TCS_STATE_INACTIVE   0  //The TCS is available for a normal EENTER
#define TCS_STATE_ACTIVE     1  //A Processor is currently executing in the context of this TCS

// The 1st parameter of enter_enclave function (enclave_enclave.S) is tcs.
// If the parameter is changed, or EENTER_PROLOG is changed, the macro should be updated accordingly.
#ifdef SE_GNU64 
#define GET_TCS_PTR(xbp) (tcs_t *)(*(uintptr_t *)((size_t)(xbp) - 10 * sizeof(uintptr_t)))
#else
#define GET_TCS_PTR(xbp) (tcs_t *)(*(uintptr_t *)((size_t)(xbp) + 2 * sizeof(uintptr_t)))
#endif

#ifdef __cplusplus
extern "C" {
#endif

//Add the implementation to get the _tls_array pointer in GNU here.
#include "gnu_tls.h"
extern uint8_t __ImageBase;
int td_mngr_set_td(void *enclave_base, tcs_t *tcs);
int td_mngr_restore_td(tcs_t *tcs);

#ifdef __cplusplus
}
#endif

#endif
