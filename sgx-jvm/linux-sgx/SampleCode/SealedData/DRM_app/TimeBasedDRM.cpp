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



#include "TimeBasedDRM.h"
#include "sgx_urts.h"
#include "sgx_uae_service.h"
#include "DRM_enclave_u.h"
#include <iostream>
using namespace std;

#define ENCLAVE_NAME    "DRM_enclave.signed.so"

TimeBasedDRM::TimeBasedDRM(void): enclave_id(0)
{
    int updated = 0;
    sgx_status_t sgx_ret = SGX_ERROR_UNEXPECTED;
    sgx_ret = sgx_create_enclave(ENCLAVE_NAME, SGX_DEBUG_FLAG,
        &launch_token, &updated, &enclave_id, NULL);
    if (sgx_ret)
    {
        cerr<<"cannot create enclave, error code = 0x"<< hex<< sgx_ret <<endl;
    }
}


TimeBasedDRM::~TimeBasedDRM(void)
{
    if(enclave_id)
        sgx_destroy_enclave(enclave_id);
}

uint32_t TimeBasedDRM:: init(uint8_t*  stored_time_based_policy)
{
    sgx_status_t sgx_ret = SGX_ERROR_UNEXPECTED;
    sgx_ps_cap_t ps_cap;
    memset(&ps_cap, 0, sizeof(sgx_ps_cap_t));
    sgx_ret = sgx_get_ps_cap(&ps_cap);
    if (sgx_ret)
    {
        cerr<<"cannot get platform service capability, error code = 0x"<< hex<<
            sgx_ret <<endl;
        return sgx_ret;
    }
    if (!SGX_IS_TRUSTED_TIME_AVAILABLE(ps_cap))
    {
        cerr<<"trusted time is not supported"<<endl;
        return SGX_ERROR_SERVICE_UNAVAILABLE;
    }
    uint32_t enclave_ret = 0;
    sgx_ret = create_time_based_policy(enclave_id, &enclave_ret,
        (uint8_t *)stored_time_based_policy, time_based_policy_length);
    if (sgx_ret)
    {
        cerr<<"call create_time_based_policy fail, error code = 0x"<< hex<<
            sgx_ret <<endl;
        return sgx_ret;
    } 
    if (enclave_ret)
    {
        cerr<<"cannot create_time_based_policy, function return fail, error code = 0x"
            << hex<< enclave_ret <<endl;
        return enclave_ret;
    }
    return 0;
}


uint32_t TimeBasedDRM:: init()
{
    return init(time_based_policy);
}



uint32_t TimeBasedDRM::perform_function(uint8_t* stored_time_based_policy)
{
    sgx_status_t sgx_ret = SGX_ERROR_UNEXPECTED;
    uint32_t enclave_ret = 0;
    sgx_ret = perform_time_based_policy(enclave_id, &enclave_ret,
        stored_time_based_policy, time_based_policy_length);
    if (sgx_ret)
    {
        cerr<<"call perform_time_based_policy fail, error code = 0x"<< hex<<
            sgx_ret <<endl;
        return sgx_ret;
    }
    if (enclave_ret)
    {
        cerr<<"cannot perform_time_based_policy, function return fail, error code = 0x"
            << hex<< enclave_ret <<endl;
        return enclave_ret;
    }
    return 0;
}

uint32_t TimeBasedDRM::perform_function()
{
    return perform_function(time_based_policy);
}
