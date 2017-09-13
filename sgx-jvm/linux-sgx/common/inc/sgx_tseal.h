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



#ifndef _SGX_TSEAL_H_
#define _SGX_TSEAL_H_

#include <stddef.h>
#include <stdint.h>
#include "sgx_key.h"
#include "sgx_error.h"
#include "sgx_defs.h"
#include "sgx_attributes.h"
#include "sgx_tcrypto.h"

#define SGX_SEAL_TAG_SIZE       SGX_AESGCM_MAC_SIZE
#define SGX_SEAL_IV_SIZE        12

typedef struct _aes_gcm_data_t
{
    uint32_t  payload_size;                   /*  0: Size of the payload which includes both the encrypted data and the optional additional MAC text */
    uint8_t   reserved[12];                   /*  4: Reserved bits */
    uint8_t   payload_tag[SGX_SEAL_TAG_SIZE]; /* 16: AES-GMAC of the plain text, payload, and the sizes */
    uint8_t   payload[];                      /* 32: The payload data which includes the encrypted data followed by the optional additional MAC text */
} sgx_aes_gcm_data_t;

typedef struct _sealed_data_t
{
    sgx_key_request_t  key_request;       /* 00: The key request used to obtain the sealing key */
    uint32_t           plain_text_offset; /* 64: Offset within aes_data.playload to the start of the optional additional MAC text */
    uint8_t            reserved[12];      /* 68: Reserved bits */
    sgx_aes_gcm_data_t aes_data;          /* 80: Data structure holding the AES/GCM related data */
} sgx_sealed_data_t;

#ifdef __cplusplus
extern "C" {
#endif
    /* sgx_calc_sealed_data_size
     * Purpose: This function is used to determine how much memory to allocate for sgx_sealed_data_t structure.
     *
     * Paramters:
     *      add_mac_txt_size - [IN] Length of the optional additional data stream in bytes
     *      txt_encrypt_size - [IN] Length of the data stream to be encrypted in bytes
     *
     * Return Value:
     *      uint32_t - The minimum number of bytes that need to be allocated for the sgx_sealed_data_t structure
     *      If the function fails, the return value is UINT32_MAX
    */
    uint32_t sgx_calc_sealed_data_size(const uint32_t add_mac_txt_size, const uint32_t txt_encrypt_size);

    /* sgx_get_add_mac_txt_len
     * Purpose: This function is used to determine how much memory to allocate for the additional_MAC_text buffer
     *
     * Parameter:
     *      p_sealed_data - [IN] Pointer to the sgx_sealed_data_t structure which was populated by the sgx_seal_data function
     *
     * Return Value:
     *      uint32_t - The number of bytes in the optional additional MAC buffer
     *      If the function fails, the return value is UINT32_MAX
    */
    uint32_t sgx_get_add_mac_txt_len(const sgx_sealed_data_t* p_sealed_data);

    /* sgx_get_encrypt_txt_len
     * Purpose: This function is used to determine how much memory to allocate for the decrypted data returned by the sgx_unseal_data function
     *
     * Parameter:
     *      p_sealed_data - [IN] Pointer to the sgx_sealed_data_t structure which was populated by the sgx_seal_data function
     *
     * Return Value:
     *      uint32_t - The number of bytes in the encrypted data buffer
     *      If the function fails, the return value is UINT32_MAX
    */
    uint32_t sgx_get_encrypt_txt_len(const sgx_sealed_data_t* p_sealed_data);


    /* sgx_seal_data
     * Purpose: This algorithm is used to AES-GCM encrypt the input data.  Specifically,
     *          two input data sets can be provided, one is the text to encrypt (p_text2encrypt)
     *          the second being optional additional text that should not be encrypted but will
     *          be part of the GCM MAC calculation.
     *          The sgx_sealed_data_t structure should be allocated prior to the API call and
     *          should include buffer storage for the MAC text and encrypted text.
     *          The sgx_sealed_data_t structure contains the data required to unseal the data on
     *          the same system it was sealed.
     *
     * Parameters:
     *      additional_MACtext_length - [IN] length of the plaintext data stream in bytes
     *                                  The additional data is optional and thus the length
     *                                  can be zero if no data is provided
     *      p_additional_MACtext - [IN] pointer to the plaintext data stream to be GCM protected
     *                             The additional data is optional. You may pass a NULL pointer
     *                             but additional_MACtext_length must be zero in that case
     *      text2encrypt_length - [IN] length of the data stream to encrypt in bytes
     *      p_text2encrypt - [IN] pointer to data stream to encrypt
     *      sealed_data_size - [IN] Size of the sealed data buffer passed in
     *      p_sealed_data - [OUT] pointer to the sealed data structure containing protected data
     *
     * Return Value:
     *      sgx_status_t - SGX Error code
    */
    sgx_status_t SGXAPI sgx_seal_data(const uint32_t additional_MACtext_length,
        const uint8_t *p_additional_MACtext,
        const uint32_t text2encrypt_length,
        const uint8_t *p_text2encrypt,
        const uint32_t sealed_data_size,
        sgx_sealed_data_t *p_sealed_data);

    /* sgx_seal_data_ex
     * Purpose: Expert version of sgx_seal_data which is used if the key_policy/attribute_mask/misc_mask
     *          need to be modified from the default values.
     *
     * Parameters:
     *      key_policy - [IN] Specifies the measurement to use in key derivation
     *      attribute_mask - [IN] Identifies which platform/enclave attributes to use in key derivation
     *      misc_mask - [IN] The mask for MISC_SELECT
     *      additional_MACtext_length - [IN] length of the plaintext data stream in bytes
     *                                  The additional data is optional and thus the length
     *                                  can be zero if no data is provided
     *      p_additional_MACtext - [IN] pointer to the plaintext data stream to be GCM protected
     *                             The additional data is optional. You may pass a NULL pointer
     *                             but additional_MACtext_length must be zero in that case
     *      text2encrypt_length - [IN] length of the data stream to encrypt in bytes
     *      p_text2encrypt - [IN] pointer to data stream to encrypt
     *      sealed_data_size - [IN] Size of the sealed data buffer passed in
     *      p_sealed_data - [OUT] pointer to the sealed data structure containing protected data
     *
     * Return Value:
     *      sgx_status_t - SGX Error code
    */
    sgx_status_t SGXAPI sgx_seal_data_ex(const uint16_t key_policy,
        const sgx_attributes_t attribute_mask,
        const sgx_misc_select_t misc_mask,
        const uint32_t additional_MACtext_length,
        const uint8_t *p_additional_MACtext,
        const uint32_t text2encrypt_length,
        const uint8_t *p_text2encrypt,
        const uint32_t sealed_data_size,
        sgx_sealed_data_t *p_sealed_data);

    /* sgx_unseal_data
     * Purpose: Unseal the sealed data structure passed in and populate the MAC text and decrypted text
     *          buffers with the appropriate data from the sealed data structure.
     *
     * Parameters:
     *      p_sealed_data - [IN] pointer to the sealed data structure containing protected data
     *      p_additional_MACtext - [OUT] pointer to the plaintext data stream which was GCM protected
     *                             The additiona data is optional. You may pass a NULL pointer but
     *                             p_additional_MACtext_length must be zero in that case
     *      p_additional_MACtext_length - [IN/OUT] pointer to length of the plaintext data stream in bytes
     *                             If there is not additional data, this parameter should be zero.
     *      p_decrypted_text - [OUT] pointer to decrypted data stream
     *      p_decrypted_text_length - [IN/OUT] pointer to length of the decrypted data stream to encrypt in bytes
     *
     * Return Value:
     *      sgx_status_t - SGX Error code
    */
    sgx_status_t SGXAPI sgx_unseal_data(const sgx_sealed_data_t *p_sealed_data,
        uint8_t *p_additional_MACtext,
        uint32_t *p_additional_MACtext_length,
        uint8_t *p_decrypted_text,
        uint32_t *p_decrypted_text_length);

    /* sgx_mac_aadata
    * Purpose: Use AES-GCM algorithm to generate a sealed data structure with integrity protection.  
    *          Specifically, the input data set is ONLY the plaintext data stream, or 
    *          additional authenticated data(AAD), no encrypt data.
    *          The sgx_sealed_data_t structure should be allocated prior to the API call and
    *          should include buffer storage for the plaintext data.
    *          The sgx_sealed_data_t structure contains the data required to unseal the data on
    *          the same system it was sealed.
    *
    * Parameters:
    *      additional_MACtext_length - [IN] length of the plaintext data stream in bytes
    *      p_additional_MACtext - [IN] pointer to the plaintext data stream to be GCM protected
    *      sealed_data_size - [IN] Size of the sealed data buffer passed in
    *      p_sealed_data - [OUT] pointer to the sealed data structure containing protected data
    *
    * Return Value:
    *      sgx_status_t - SGX Error code
    */
    sgx_status_t sgx_mac_aadata(const uint32_t additional_MACtext_length,
        const uint8_t *p_additional_MACtext,
        const uint32_t sealed_data_size,
        sgx_sealed_data_t *p_sealed_data);

    /* sgx_mac_aadata_ex
    * Purpose: Expert version of sgx_mac_aadata which is used if the key_policy/attribute_mask/misc_mask
    *          need to be modified from the default values.
    *
    * Parameters:
    *      key_policy - [IN] Specifies the measurement to use in key derivation
    *      attribute_mask - [IN] Identifies which platform/enclave attributes to use in key derivation
    *      misc_mask - [IN] The mask for MISC_SELECT
    *      additional_MACtext_length - [IN] length of the plaintext data stream in bytes
    *      p_additional_MACtext - [IN] pointer to the plaintext data stream to be GCM protected
    *      sealed_data_size - [IN] Size of the sealed data buffer passed in
    *      p_sealed_data - [OUT] pointer to the sealed data structure containing protected data
    *
    * Return Value:
    *      sgx_status_t - SGX Error code
    */
    sgx_status_t sgx_mac_aadata_ex(const uint16_t key_policy,
        const sgx_attributes_t attribute_mask,
        const sgx_misc_select_t misc_mask,
        const uint32_t additional_MACtext_length,
        const uint8_t *p_additional_MACtext,
        const uint32_t sealed_data_size,
        sgx_sealed_data_t *p_sealed_data);

    /* sgx_unmac_aadata
    * Purpose: Unseal the sealed data structure passed in and populate the plaintext data stream 
    *          with the appropriate data from the sealed data structure.
    *
    * Parameters:
    *      p_sealed_data - [IN] pointer to the sealed data structure containing protected data
    *      p_additional_MACtext - [OUT] pointer to the plaintext data stream which was GCM protected
    *      p_additional_MACtext_length - [IN/OUT] pointer to length of the plaintext data stream in bytes
    *
    * Return Value:
    *      sgx_status_t - SGX Error code
    */
    sgx_status_t sgx_unmac_aadata(const sgx_sealed_data_t *p_sealed_data,
        uint8_t *p_additional_MACtext,
        uint32_t *p_additional_MACtext_length);

#ifdef __cplusplus
}
#endif

#endif
