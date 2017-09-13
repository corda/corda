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


/**
 * set_tls.c
 *   Implemente the TLS support in simulation mode
 */

#include "td_mngr.h"
#include "util.h"

int td_mngr_set_td(void *enclave_base, tcs_t *tcs)
{
    dtv_t* dtv;
    tcs_sim_t *tcs_sim;

    if (!tcs)
        return 0;

    /* save the old DTV[0].pointer->val */
    dtv = GET_DTV();
    tcs_sim = (tcs_sim_t *)tcs->reserved;
    tcs_sim->saved_dtv = (uintptr_t)read_dtv_val(dtv);

    /* save the old fs:0x0 or gs:0x0 value */
    tcs_sim->saved_fs_gs_0 = GET_FS_GS_0();

    /* set the DTV[0].pointer->val to TLS address */
    uintptr_t *tib = GET_PTR(uintptr_t, enclave_base, tcs->ofs_base);
    set_dtv_val(dtv, tib);

    /* set the fs:0x0 or gs:0x0 to TLS address */
    SET_FS_GS_0(tib);
    return 1;
}

/* vim: set ts=4 sw=4 cin et: */
