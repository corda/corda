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



#pragma once
#include "stdlib.h"
#include "sgx.h"
#include "sgx_urts.h"
#include "../include/sealed_data_defines.h"


class ReplayProtectedDRM
{
public:
    ReplayProtectedDRM();
    ~ReplayProtectedDRM(void);
    
    uint32_t init(uint8_t*  stored_sealed_activity_log);
    uint32_t init();
    uint32_t perform_function();
    uint32_t perform_function(uint8_t* stored_sealed_activity_log);
    uint32_t update_secret();
    uint32_t update_secret(uint8_t* stored_sealed_activity_log);

    uint32_t delete_secret();
    uint32_t delete_secret(uint8_t* stored_sealed_activity_log);

    uint32_t get_activity_log(uint8_t* stored_sealed_activity_log);

    
    static const uint32_t sealed_activity_log_length = SEALED_REPLAY_PROTECTED_PAY_LOAD_SIZE;
private:
    uint8_t  sealed_activity_log[sealed_activity_log_length];
    sgx_enclave_id_t enclave_id;
    sgx_launch_token_t launch_token;

};

