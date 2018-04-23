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


// tSeal.cpp - Trusted Sealing Routines

#include "sgx_tseal.h"
#include <stdlib.h>
#include <string.h>
#include "sgx_trts.h"
#include "sgx_report.h"
#include "se_cdefs.h"
#include "sgx_utils.h"
#include "tSeal_internal.h"

extern "C" sgx_status_t sgx_seal_data(const uint32_t additional_MACtext_length,
                                      const uint8_t *p_additional_MACtext, const uint32_t text2encrypt_length,
                                      const uint8_t *p_text2encrypt, const uint32_t sealed_data_size,
                                      sgx_sealed_data_t *p_sealed_data)
{
    sgx_status_t err = SGX_ERROR_UNEXPECTED;
    sgx_attributes_t attribute_mask;
    attribute_mask.flags = TSEAL_DEFAULT_FLAGSMASK;
    attribute_mask.xfrm = 0x0;

    err = sgx_seal_data_ex(SGX_KEYPOLICY_MRSIGNER, attribute_mask, TSEAL_DEFAULT_MISCMASK, additional_MACtext_length,
        p_additional_MACtext, text2encrypt_length, p_text2encrypt, sealed_data_size, p_sealed_data);
    return err;
}

extern "C" sgx_status_t sgx_seal_data_ex(const uint16_t key_policy,
                                         const sgx_attributes_t attribute_mask,
                                         const sgx_misc_select_t misc_mask,
                                         const uint32_t additional_MACtext_length,
                                         const uint8_t *p_additional_MACtext, const uint32_t text2encrypt_length,
                                         const uint8_t *p_text2encrypt, const uint32_t sealed_data_size,
                                         sgx_sealed_data_t *p_sealed_data)
{
    sgx_status_t err = SGX_ERROR_UNEXPECTED;
    sgx_report_t report;
    sgx_key_id_t keyID;
    sgx_key_request_t tmp_key_request;
    uint8_t payload_iv[SGX_SEAL_IV_SIZE];
    memset(&payload_iv, 0, sizeof(payload_iv));


    uint32_t sealedDataSize = sgx_calc_sealed_data_size(additional_MACtext_length,text2encrypt_length);
    // Check for overflow
    if (sealedDataSize == UINT32_MAX)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    //
    // Check parameters
    //
    // check key_request->key_policy reserved bits are not set and one of policy bits are set
    if ((key_policy & ~(SGX_KEYPOLICY_MRENCLAVE | SGX_KEYPOLICY_MRSIGNER)) ||
        ((key_policy & (SGX_KEYPOLICY_MRENCLAVE | SGX_KEYPOLICY_MRSIGNER)) == 0))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    if ( !(attribute_mask.flags & SGX_FLAGS_INITTED)
      || !(attribute_mask.flags & SGX_FLAGS_DEBUG) )
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    if ((additional_MACtext_length > 0) && (p_additional_MACtext == NULL))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    if ((text2encrypt_length == 0) || (p_text2encrypt == NULL) || (!sgx_is_within_enclave(p_text2encrypt,text2encrypt_length)))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    // Ensure sealed data blob is within an enclave during the sealing process
    if ((p_sealed_data == NULL) || (!sgx_is_within_enclave(p_sealed_data,sealed_data_size)))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    // Ensure aad data does not cross enclave boundary
    if ((additional_MACtext_length > 0) &&
        (!(sgx_is_within_enclave(p_additional_MACtext,additional_MACtext_length) || sgx_is_outside_enclave(p_additional_MACtext, additional_MACtext_length))))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    if (sealedDataSize != sealed_data_size)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    memset(&report, 0, sizeof(sgx_report_t));
    memset(p_sealed_data, 0, sealedDataSize);
    memset(&keyID, 0, sizeof(sgx_key_id_t));
    memset(&tmp_key_request, 0, sizeof(sgx_key_request_t));

    // Get the report to obtain isv_svn and cpu_svn
    err = sgx_create_report(NULL, NULL, &report);
    if (err != SGX_SUCCESS)
    {
        goto clear_return;
    }

    // Get a random number to populate the key_id of the key_request
    err = sgx_read_rand(reinterpret_cast<uint8_t *>(&keyID), sizeof(sgx_key_id_t));
    if (err != SGX_SUCCESS)
    {
        goto clear_return;
    }

    memcpy(&(tmp_key_request.cpu_svn), &(report.body.cpu_svn), sizeof(sgx_cpu_svn_t));
    memcpy(&(tmp_key_request.isv_svn), &(report.body.isv_svn), sizeof(sgx_isv_svn_t));
    tmp_key_request.key_name = SGX_KEYSELECT_SEAL;
    tmp_key_request.key_policy = key_policy;
    tmp_key_request.attribute_mask.flags = attribute_mask.flags;
    tmp_key_request.attribute_mask.xfrm = attribute_mask.xfrm;
    memcpy(&(tmp_key_request.key_id), &keyID, sizeof(sgx_key_id_t));
    tmp_key_request.misc_mask = misc_mask;

    err = sgx_seal_data_iv(additional_MACtext_length, p_additional_MACtext,
        text2encrypt_length, p_text2encrypt, payload_iv, &tmp_key_request, p_sealed_data);

    if (err == SGX_SUCCESS)
    {
        // Copy data from the temporary key request buffer to the sealed data blob
        memcpy(&(p_sealed_data->key_request), &tmp_key_request, sizeof(sgx_key_request_t));
    }
clear_return:
    // Clear temp state
    memset_s(&report, sizeof(sgx_report_t), 0, sizeof(sgx_report_t));
    memset_s(&keyID, sizeof(sgx_key_id_t), 0, sizeof(sgx_key_id_t));
    return err;
}

extern "C" sgx_status_t sgx_unseal_data(const sgx_sealed_data_t *p_sealed_data, uint8_t *p_additional_MACtext,
                                        uint32_t *p_additional_MACtext_length, uint8_t *p_decrypted_text, uint32_t *p_decrypted_text_length)
{
    sgx_status_t err = SGX_ERROR_UNEXPECTED;
    // Ensure the the sgx_sealed_data_t members are all inside enclave before using them.
    if ((p_sealed_data == NULL) || (!sgx_is_within_enclave(p_sealed_data,sizeof(sgx_sealed_data_t))))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    uint32_t encrypt_text_length = sgx_get_encrypt_txt_len(p_sealed_data);
    if(encrypt_text_length == UINT32_MAX)
    {
        return SGX_ERROR_MAC_MISMATCH; // Return error indicating the blob is corrupted
    }
    uint32_t add_text_length = sgx_get_add_mac_txt_len(p_sealed_data);
    if(add_text_length == UINT32_MAX)
    {
        return SGX_ERROR_MAC_MISMATCH; // Return error indicating the blob is corrupted
    }
    uint32_t sealedDataSize = sgx_calc_sealed_data_size(add_text_length,encrypt_text_length);
    if (sealedDataSize == UINT32_MAX)
    {
        return SGX_ERROR_MAC_MISMATCH; // Return error indicating the blob is corrupted
    }

    //
    // Check parameters
    //
    // Ensure sealed data blob is within an enclave during the sealing process
    if (!sgx_is_within_enclave(p_sealed_data,sealedDataSize))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    if ((add_text_length > 0) && ((p_additional_MACtext == NULL) || (p_additional_MACtext_length == NULL)))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    if ((encrypt_text_length < 1) || (p_decrypted_text == NULL) || (p_decrypted_text_length == NULL))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    if (!sgx_is_within_enclave(p_decrypted_text,encrypt_text_length))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    if (!sgx_is_within_enclave(p_decrypted_text_length,sizeof(p_decrypted_text_length)))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    // Ensure aad data does not cross enclave boundary
    if ((add_text_length > 0) &&
        (!(sgx_is_within_enclave(p_additional_MACtext,add_text_length) || sgx_is_outside_enclave(p_additional_MACtext, add_text_length))))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    if ((*p_decrypted_text_length) < encrypt_text_length)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    uint32_t additional_MACtext_length = (NULL != p_additional_MACtext_length) ? *p_additional_MACtext_length : 0;
    if (additional_MACtext_length < add_text_length) {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    err = sgx_unseal_data_helper(p_sealed_data, p_additional_MACtext, add_text_length,
        p_decrypted_text, encrypt_text_length);
    if (err == SGX_SUCCESS)
    {
        *p_decrypted_text_length = encrypt_text_length;
        if(p_additional_MACtext_length != NULL)
            *p_additional_MACtext_length = add_text_length;
    }
    return err;
}

