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

#ifndef _PSE_OP_LOGIC_H_
#define _PSE_OP_LOGIC_H_
#include "sgx_urts.h"
#include "aesm_error.h"
#include "arch.h"
#include "aeerror.h"
#include "aesm_long_lived_thread.h"

class PSEOPAESMLogic{
public:
    static aesm_error_t prepare_for_ps_request(void);

    static aesm_error_t get_ps_cap(uint64_t* ps_cap);

    static aesm_error_t create_session(
                uint32_t* session_id,
                uint8_t* se_dh_msg1, uint32_t se_dh_msg1_size);

    static aesm_error_t exchange_report(
        uint32_t session_id,
        const uint8_t* se_dh_msg2, uint32_t se_dh_msg2_size,
        uint8_t* se_dh_msg3, uint32_t se_dh_msg3_size);

    static aesm_error_t invoke_service(
        const uint8_t* pse_message_req, uint32_t pse_message_req_size,
        uint8_t* pse_message_resp, uint32_t pse_message_resp_size);

    static aesm_error_t close_session(
        uint32_t session_id);

    static aesm_error_t establish_ephemeral_session(bool force_redo);

    static ae_error_t certificate_provisioning_and_long_term_pairing_func(bool& is_new_pairing);

};
#endif

