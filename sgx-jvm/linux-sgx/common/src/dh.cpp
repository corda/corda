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
* File: 
*        dh.cpp
*Description: 
*        Encrypt and decrypt messages over DH session
*/
#include "dh.h"
#include "sgx_tcrypto.h"

bool encrypt_msg(pse_message_t* pse_msg, uint8_t* data, sgx_key_128bit_t* authenticated_encryption_key)
{
    /* get random IV */
    if(sgx_read_rand(pse_msg->payload_iv, PAYLOAD_IV_SIZE) != SGX_SUCCESS)
    {
        return false;
    }

    return (SGX_SUCCESS == sgx_rijndael128GCM_encrypt(
        authenticated_encryption_key,
        data,
        pse_msg->payload_size,
        reinterpret_cast<uint8_t *>(&(pse_msg->payload)),
        reinterpret_cast<uint8_t *>(&(pse_msg->payload_iv)),
        12,
        NULL,
        0,
        &pse_msg->payload_tag
        ));
}

bool decrypt_msg(pse_message_t* pse_msg, uint8_t* data, sgx_key_128bit_t* authenticated_encryption_key)
{
    return(SGX_SUCCESS == sgx_rijndael128GCM_decrypt(
        authenticated_encryption_key,
        pse_msg->payload,
        pse_msg->payload_size,
        data,
        reinterpret_cast<uint8_t *>(&(pse_msg->payload_iv)),
        12,
        NULL,
        0,
        &pse_msg->payload_tag
        ));
}

