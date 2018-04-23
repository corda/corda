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
 * File: sgx_verify_report.cpp
 * Description:
 *     API for report verification
 */

#include "sgx_utils.h"
#include "util.h"
#include <stdlib.h>
#include <string.h>
#include "se_memcpy.h"
#include "sgx_trts.h"
#include "sgx_tcrypto.h"
#include "se_cdefs.h"

// add a version to tservice.
SGX_ACCESS_VERSION(tservice, 3)

sgx_status_t sgx_verify_report(const sgx_report_t *report)
{
    sgx_mac_t mac;
    sgx_key_request_t key_request;
    sgx_key_128bit_t key;
    sgx_status_t err = SGX_ERROR_UNEXPECTED;
    //check parameter
    if(!report||!sgx_is_within_enclave(report, sizeof(*report)))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    memset(&mac, 0, sizeof(sgx_mac_t));
    memset(&key_request, 0, sizeof(sgx_key_request_t));
    memset(&key, 0, sizeof(sgx_key_128bit_t));

    //prepare the key_request
    key_request.key_name = SGX_KEYSELECT_REPORT;
    memcpy_s(&key_request.key_id, sizeof(key_request.key_id), &report->key_id, sizeof(report->key_id));

    //get the report key
    // Since the key_request is not an input parameter by caller,
    // we suppose sgx_get_key would never return the following error code:
    //      SGX_ERROR_INVALID_PARAMETER
    //      SGX_ERROR_INVALID_ATTRIBUTE
    //      SGX_ERROR_INVALID_CPUSVN
    //      SGX_ERROR_INVALID_ISVSVN
    //      SGX_ERROR_INVALID_KEYNAME
    err = sgx_get_key(&key_request, &key);
    if(err != SGX_SUCCESS)
    {
        return err; // err must be SGX_ERROR_OUT_OF_MEMORY or SGX_ERROR_UNEXPECTED
    }
    //get the report mac
    err = sgx_rijndael128_cmac_msg((sgx_cmac_128bit_key_t*)&key, (const uint8_t *)(&report->body), sizeof(sgx_report_body_t), &mac);
    memset_s (&key, sizeof(sgx_key_128bit_t), 0, sizeof(sgx_key_128bit_t));
    if (SGX_SUCCESS != err)
    {
        if(err != SGX_ERROR_OUT_OF_MEMORY)
            err = SGX_ERROR_UNEXPECTED;
        return err;
    }
    if(consttime_memequal(mac, report->mac, sizeof(sgx_mac_t)) == 0)
    {
        return SGX_ERROR_MAC_MISMATCH;
    }
    else
    {
        return SGX_SUCCESS;
    }
}
