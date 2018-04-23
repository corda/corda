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


#include "utility.h"

bool verify_hmac_sha256(
    const uint8_t* mac_key,
    uint32_t key_len,
    const uint8_t* data_buf,
    uint32_t buf_size,
    const uint8_t* mac_buf)
{
    uint8_t data_mac[SGX_SHA256_HASH_SIZE];

    if(!mac_key || !data_buf || !mac_buf)
    {
        return false;
    }

    // compute HMAC-SHA256 of the message with the specified Key
    if (ippsHMAC_Message(data_buf, buf_size, mac_key, key_len, data_mac, SGX_SHA256_HASH_SIZE, IPP_ALG_HASH_SHA256) != ippStsNoErr)
    {
        return false;
    }

    // compare HMAC values
    if(consttime_memequal(mac_buf, data_mac, SGX_SHA256_HASH_SIZE) == 0)
    {
        return false;
    }
    return true;
}

ae_error_t error_reinterpret(pse_op_error_t op_error)
{
    switch(op_error)
    {
        case OP_SUCCESS:
            return AE_SUCCESS;
        case OP_ERROR_MAX_NUM_SESSION_REACHED:
            return PSE_OP_MAX_NUM_SESSION_REACHED;
        case OP_ERROR_INVALID_SESSION:
            return PSE_OP_SESSION_INVALID;
        case OP_ERROR_INVALID_EPH_SESSION:
            /*  Ephemeral session is invalid  */
            return PSE_OP_EPHEMERAL_SESSION_INVALID;
        case OP_ERROR_PSDA_SESSION_LOST:
            return AESM_PSDA_SESSION_LOST;
        case OP_ERROR_EPH_SESSION_ESTABLISHMENT_INTEGRITY_ERROR:
            /*  Wrong message detected when establishing ephemeral session  */
            return PSE_OP_ERROR_EPH_SESSION_ESTABLISHMENT_INTEGRITY_ERROR;
        case OP_ERROR_UNSEAL_PAIRING_BLOB:
            return PSE_PAIRING_BLOB_UNSEALING_ERROR;
        case OP_ERROR_INVALID_PAIRING_BLOB:
            return PSE_PAIRING_BLOB_INVALID_ERROR;
        case OP_ERROR_PSDA_BUSY:
            return PSE_OP_PSDA_BUSY_ERROR; 
        case OP_ERROR_LTPB_SEALING_OUT_OF_DATE:
            return PSE_OP_LTPB_SEALING_OUT_OF_DATE;
        case OP_ERROR_KDF_MISMATCH:
            return PSE_OP_ERROR_KDF_MISMATCH;
        default:
            return PSE_OP_INTERNAL_ERROR;
        }
}
