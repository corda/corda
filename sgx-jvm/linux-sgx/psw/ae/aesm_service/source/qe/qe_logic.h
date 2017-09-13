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

#ifndef _QE_LOGIC_H_
#define _QE_LOGIC_H_

#include "sgx_urts.h"
#include "aesm_error.h"
#include "arch.h"
#include "aeerror.h"
#include "tlv_common.h"
#include "se_thread.h"
#include "oal/oal.h"
#include <time.h>
#include <string.h>

class QEAESMLogic{
public:
    static aesm_error_t init_quote(sgx_target_info_t *target_info,
        uint8_t *gid, uint32_t gid_size, uint16_t pce_isv_svn,
        uint16_t qe_isv_svn, const sgx_cpu_svn_t qe_cpu_svn);

    static aesm_error_t get_quote(const uint8_t *report,
        uint32_t quote_type,
        const uint8_t *spid,
        const uint8_t *nonce,
        const uint8_t *sigrl, uint32_t sigrl_size,
        uint8_t *qe_report,
        uint8_t *quote, uint32_t buf_size, uint16_t pce_isv_svn);
};
#endif

