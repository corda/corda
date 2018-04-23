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


#include "se_memcpy.h"
#include "util.h"
#include "uae_service_internal.h"
#include "crypto_wrapper.h"

/* This hard code depends on enclaveSinger private key of PvE, an Intel Generic
Enclave Signing Key currently:
trunk/psw/ae/common/sgx_qe_pve_private_key.pem */
static const uint8_t PVE_PUBLIC_KEY[] = {
    0XAB,	0X93,	0XBB,	0XF7,	0X4A,	0XA2,	0XDF,	0X51,
    0X91,	0X46,	0X57,	0X93,	0X1D,	0XB0,	0XC,	0XDB,
    0X24,	0X1E,	0XF4,	0X91,	0X38,	0X3F,	0X83,	0X4D,
    0X71,	0XB7,	0X3D,	0X2F,	0X4E,	0X8F,	0X1D,	0X7C,
    0X68,	0X4C,	0X75,	0XEF,	0X4D,	0XFE,	0X72,	0XE3,
    0X42,	0X5,	0X99,	0X8D,	0X66,	0X94,	0X1D,	0XC3,
    0X16,	0X24,	0XB8,	0XA6,	0XC8,	0XBB,	0X3E,	0XB7,
    0X14,	0XC7,	0X9E,	0X5E,	0X50,	0X1F,	0X1,	0X34,
    0X2,	0X17,	0XD7,	0X12,	0XBE,	0XA6,	0XCD,	0XD2,
    0XF8,	0X58,	0XE4,	0X9B,	0XEB,	0XDC,	0X96,	0XE,
    0XF1,	0XAB,	0X83,	0XD1,	0XF1,	0X43,	0XB4,	0X67,
    0XC6,	0XDF,	0XC1,	0X94,	0X9F,	0X88,	0X21,	0XE7,
    0X55,	0XA5,	0X18,	0X9D,	0XC3,	0X79,	0X7C,	0X26,
    0XA0,	0X3B,	0X46,	0X15,	0XCF,	0X2E,	0X69,	0X81,
    0X8F,	0XCD,	0XD0,	0X98,	0X37,	0X2A,	0X27,	0X1,
    0XEC,	0X95,	0X2A,	0X7F,	0XE8,	0XC6,	0XCA,	0X8D,
    0XCA,	0XA2,	0XCB,	0X6A,	0X37,	0XD4,	0XDC,	0X7E,
    0X4F,	0XC6,	0X2A,	0XAF,	0X7B,	0X52,	0XEF,	0X93,
    0X58,	0X72,	0X2A,	0XFA,	0X2,	0XEE,	0XBA,	0XC4,
    0XFA,	0X52,	0XD8,	0XA2,	0XFA,	0X1,	0X83,	0XE3,
    0XA6,	0X5D,	0X87,	0X60,	0XCD,	0XA,	0X62,	0X9D,
    0X28,	0X8,	0X2C,	0X72,	0X36,	0XC9,	0X2E,	0XF6,
    0X9F,	0X96,	0X84,	0X60,	0XE9,	0X8E,	0X72,	0XE9,
    0X83,	0XD8,	0X25,	0XDD,	0X27,	0X74,	0X32,	0X26,
    0XAD,	0X98,	0XB7,	0X8B,	0X6,	0X45,	0X9C,	0X75,
    0X10,	0XA6,	0X2C,	0XFF,	0X60,	0X83,	0XFF,	0XE,
    0XB4,	0X88,	0X20,	0X4E,	0XB2,	0X59,	0XE7,	0XEC,
    0XA1,	0X5F,	0X10,	0XBF,	0X94,	0X2C,	0XF9,	0X26,
    0X80,	0X64,	0X7E,	0X1F,	0XAA,	0X6E,	0X28,	0X7B,
    0XC,	0XD7,	0X7E,	0XA,	0X89,	0X9D,	0X4E,	0XDB,
    0XED,	0X60,	0XFF,	0X2,	0XE,	0XA7,	0XD0,	0X7C,
    0X5D,	0X2,	0XDA,	0X15,	0X72,	0XD6,	0X95,	0X97,
    0XF,	0X49,	0X58,	0XCA,	0XBC,	0X6D,	0X94,	0XED,
    0X6,	0XE1,	0XD8,	0XC8,	0X3,	0XD3,	0X4C,	0XB5,
    0X72,	0X28,	0X5E,	0X10,	0XB4,	0X6E,	0XAF,	0X4A,
    0X6E,	0X81,	0X66,	0XF6,	0XED,	0XE9,	0X1E,	0X69,
    0XDE,	0X9B,	0XDC,	0X33,	0X62,	0X9D,	0X2F,	0X5,
    0X6A,	0X74,	0X2B,	0XCF,	0X1E,	0XDE,	0XDB,	0X32,
    0X63,	0X4C,	0XE7,	0XC5,	0XDC,	0XCD,	0X31,	0X21,
    0X5A,	0X5D,	0XFD,	0XDD,	0XA1,	0XBC,	0X3C,	0X40,
    0X6E,	0X37,	0X51,	0XBC,	0X1,	0X5B,	0X49,	0XCA,
    0XAE,	0X9B,	0X38,	0XF4,	0X74,	0X8D,	0X6B,	0X58,
    0XDC,	0XDF,	0XE1,	0X68,	0X8A,	0X43,	0XB4,	0XFE,
    0X98,	0X7F,	0X1D,	0X4A,	0XB0,	0X4D,	0XF5,	0X28,
    0X6F,	0XBE,	0XE4,	0X93,	0X30,	0XC8,	0XDB,	0X6A,
    0X1C,	0X84,	0X44,	0X18,	0X8D,	0X3F,	0XC,	0XCE,
    0X50,	0X4E,	0XBE,	0XF0,	0X75,	0XE1,	0X7F,	0XBC,
    0X4F,	0X4E,	0X9,	0X60,	0XF4,	0XC3,	0XFC,	0XC2
};


static sgx_status_t get_launch_token_internal(
    const enclave_css_t *p_signature,
    const sgx_attributes_t *p_attributes,
    token_t *p_token)
{
    memset(p_token, 0xEE, sizeof(token_t));
    memset(&(p_token->body.reserved1), 0,
        sizeof(p_token->body.reserved1));
    memset(&(p_token->reserved2), 0,
        sizeof(p_token->reserved2));

    p_token->body.valid = 1;
    // In spec, lic_token.cpu_svn = 1, which 1 should be the least significate one.
    memset(&p_token->cpu_svn_le, 0, sizeof(p_token->cpu_svn_le));
    memset(&p_token->cpu_svn_le, 1, 1);
    p_token->isv_svn_le = 1;
    if(memcpy_s(&(p_token->body.attributes),
        sizeof(p_token->body.attributes),
        p_attributes,
        sizeof(sgx_attributes_t))){
            return SGX_ERROR_UNEXPECTED;
    }
    if(memcpy_s(&(p_token->body.mr_enclave),
        sizeof(p_token->body.mr_enclave),
        &(p_signature->body.enclave_hash),
        sizeof(p_signature->body.enclave_hash))){
            return SGX_ERROR_UNEXPECTED;
    }
    p_token->attributes_le.flags = SGX_FLAGS_INITTED;
    p_token->attributes_le.xfrm = SGX_XFRM_LEGACY;
   
    unsigned int signer_len = sizeof(p_token->body.mr_signer);
    sgx_status_t ret = sgx_EVP_Digest(EVP_sha256(), (const uint8_t *)&(p_signature->key.modulus), 
                sizeof(p_signature->key.modulus), 
                (uint8_t *)&(p_token->body.mr_signer), 
                &signer_len);
    if(ret != SGX_SUCCESS && ret != SGX_ERROR_OUT_OF_MEMORY)
    {
        return SGX_ERROR_UNEXPECTED;
    }

    return ret;
}

sgx_status_t get_launch_token(
    const enclave_css_t *p_signature,
    const sgx_attributes_t *p_attribute,
    sgx_launch_token_t *p_launch_token)
{
    if(!p_signature || !p_attribute || !p_launch_token){
        return SGX_ERROR_INVALID_PARAMETER;
    }

    if(((p_attribute->flags) & SGX_FLAGS_PROVISION_KEY) &&
        memcmp(PVE_PUBLIC_KEY, &p_signature->key.modulus,
        sizeof(PVE_PUBLIC_KEY)))
        return SGX_ERROR_SERVICE_INVALID_PRIVILEGE;

    return get_launch_token_internal(p_signature,
        p_attribute,
        (token_t *)p_launch_token);
}
