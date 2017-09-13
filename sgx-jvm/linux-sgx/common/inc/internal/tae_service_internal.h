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

#ifndef _TAE_SERVICE_INTERNAL_H_
#define _TAE_SERVICE_INTERNAL_H_

#include <stdint.h>
#include "sgx.h"
#include "arch.h"
#include "sgx_tae_service.h"
#include "pse_types.h"

#pragma pack(push, 1)

typedef struct _se_ps_sec_prop_desc_internal
{
    uint32_t          desc_type;      /* Type of this descriptor. Must be 0 */
    sgx_prod_id_t     pse_prod_id;    /* REPORT(PSE).ProdID */
    sgx_isv_svn_t     pse_isvsvn;     /* REPORT(PSE).ISVSVN */
    uint32_t          pse_miscselect; /* REPORT(PSE).MISC_SELECT */
    uint32_t          reserved1;      /* For DESC_TYPE=0, MBZ */
    sgx_attributes_t  pse_attributes; /* REPORT(PSE).ATTRIBUTES */
    sgx_measurement_t pse_mr_signer;  /* REPORT(PSE).MRSIGNER */
    uint32_t          reserved2[16];
    /*the following will be provided by PSE from CSE_SEC_PROP */
    cse_sec_prop_t    cse_sec_prop;
} se_ps_sec_prop_desc_internal_t;

se_static_assert(sizeof(se_ps_sec_prop_desc_internal_t) == sizeof(sgx_ps_sec_prop_desc_t));

#pragma pack(pop)

#endif
