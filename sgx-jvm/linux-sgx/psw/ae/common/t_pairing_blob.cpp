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

#include "sgx_trts.h"
#include "sgx_tseal.h"
#include <string.h>

#include "t_pairing_blob.h"


ae_error_t UnsealPairingBlob(const pairing_blob_t* pPairingBlob, pairing_data_t* pPairingData)
{
    ae_error_t status = PSE_PAIRING_BLOB_UNSEALING_ERROR;

    if (!pPairingBlob || !pPairingData)
    {
        return status;
    }

    do
    {

        // Verify that the pairing blob definition hasn't changed
        if (pPairingBlob->plaintext.seal_blob_type != PSE_SEAL_PAIRING_BLOB ||
            pPairingBlob->plaintext.pairing_blob_version != PSE_PAIRING_BLOB_VERSION)
            break;

        if (!sgx_is_within_enclave(pPairingData, sizeof(pairing_data_t)))
            break;

        memset_s(pPairingData, sizeof(*pPairingData), 0, sizeof(*pPairingData));

        uint32_t encrypted_data_len = sizeof(se_secret_pairing_data_t);
        uint32_t additional_MACtext_len = sizeof(se_plaintext_pairing_data_t);

        if (sgx_get_encrypt_txt_len((const sgx_sealed_data_t*)&pPairingBlob->sealed_pairing_data) != encrypted_data_len)
            break;

        if (sgx_get_add_mac_txt_len((const sgx_sealed_data_t*)&pPairingBlob->sealed_pairing_data) != additional_MACtext_len)
            break;

        sgx_status_t seStatus = sgx_unseal_data(
            (const sgx_sealed_data_t*)&pPairingBlob->sealed_pairing_data, 
            (uint8_t*)&pPairingData->plaintext, &additional_MACtext_len, 
            (uint8_t*)&pPairingData->secret_data, &encrypted_data_len);

        if ((SGX_SUCCESS != seStatus) || 
            (sizeof(se_secret_pairing_data_t) != encrypted_data_len) || 
            sizeof(se_plaintext_pairing_data_t) != additional_MACtext_len)
        {
            break;
        }

        status = AE_SUCCESS;

    } while (false);

    return status;
}

ae_error_t SealPairingBlob(pairing_data_t* pPairingData, pairing_blob_t* pPairingBlob)
{
    const uint32_t encrypted_data_len = sizeof(se_secret_pairing_data_t);
    const uint32_t additional_MACtext_len = sizeof(se_plaintext_pairing_data_t);

    ae_error_t status = PSE_PAIRING_BLOB_SEALING_ERROR;

    if (!pPairingData || !pPairingBlob)
    {
        return status;
    }

    do
    {
        if (!sgx_is_within_enclave(pPairingData, sizeof(pairing_data_t)))
            break;

        memset_s(pPairingBlob, sizeof(*pPairingBlob), 0, sizeof(*pPairingBlob));

        pPairingData->plaintext.seal_blob_type = PSE_SEAL_PAIRING_BLOB;
        pPairingData->plaintext.pairing_blob_version = PSE_PAIRING_BLOB_VERSION;

        UINT32 sealed_data_size = sgx_calc_sealed_data_size(additional_MACtext_len, encrypted_data_len);

        sgx_status_t seStatus = sgx_seal_data(
            additional_MACtext_len, (uint8_t*)&pPairingData->plaintext, 
            encrypted_data_len, (uint8_t*)&pPairingData->secret_data, 
            sealed_data_size, (sgx_sealed_data_t*)&pPairingBlob->sealed_pairing_data);

        if (SGX_SUCCESS != seStatus)
            break;

        status = AE_SUCCESS;

    } while (false);

    return status;
}

