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


#include "deriv.h"
#include "sgx_tcrypto.h"

// The built-in seal key in simulation mode
static const uint8_t BASE_SEAL_KEY[] = {
    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
    0x88, 0x99, 0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff,
};

// The built-in report key in simulation mode
static const uint8_t BASE_REPORT_KEY[] = {
    0xff, 0x00, 0xff, 0x00, 0xff, 0x00, 0xff, 0x00,
    0xff, 0x00, 0xff, 0x00, 0xff, 0x00, 0xff, 0x00,
};

// The built-in EINIT token key in simulation mode
static const uint8_t BASE_EINITTOKEN_KEY[] = {
    0xaa, 0x55, 0xaa, 0x55, 0xaa, 0x55, 0xaa, 0x55,
    0xaa, 0x55, 0xaa, 0x55, 0xaa, 0x55, 0xaa, 0x55,
};

// The built-in provision key in simulation mode
static const uint8_t BASE_PROVISION_KEY[] = {
    0xbb, 0xaa, 0xbb, 0xee, 0xff, 0x00, 0x00, 0xdd,
    0xbb, 0xaa, 0xbb, 0xee, 0xff, 0x00, 0x00, 0xdd,
};

// The built-in provision-seal key in simulation mode
static const uint8_t BASE_PROV_SEAL_KEY[] = {
    0x50, 0x52, 0x4f, 0x56, 0x49, 0x53, 0x49, 0x4f,
    0x4e, 0x53, 0x45, 0x41, 0x4c, 0x4b, 0x45, 0x59,
};

const uint8_t* get_base_key(uint16_t key_name)
{
    switch (key_name) {
    case SGX_KEYSELECT_SEAL:
        return BASE_SEAL_KEY;
    case SGX_KEYSELECT_REPORT:
        return BASE_REPORT_KEY;
    case SGX_KEYSELECT_EINITTOKEN:
        return BASE_EINITTOKEN_KEY;
    case SGX_KEYSELECT_PROVISION:
        return BASE_PROVISION_KEY;
    case SGX_KEYSELECT_PROVISION_SEAL:
        return BASE_PROV_SEAL_KEY;
    }

    // Should not come here - error should have been reported
    // when the key name is not supported in the caller.
    return (uint8_t*)0;
}

// Compute the CMAC of derivation data with corresponding base key
// and save it to `okey'.
void derive_key(const derivation_data_t* dd, sgx_key_128bit_t okey)
{
    sgx_rijndael128_cmac_msg((const sgx_cmac_128bit_key_t*)(get_base_key(dd->key_name)),
                             dd->ddbuf, dd->size, (sgx_cmac_128bit_tag_t*)okey);
}

// Compute the CMAC of a `buf' with a given `key'.
void cmac(const sgx_key_128bit_t *key, const uint8_t* buf, int buf_len, sgx_mac_t* cmac)
{
    sgx_rijndael128_cmac_msg((const sgx_cmac_128bit_key_t*)key, buf, buf_len, cmac);
}
