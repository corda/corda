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
* File: sample_libcrypto.h
* Description:
*  Interface for generic crypto library APIs. 
*  Do NOT use this library in your actual product.
*  The purpose of this sample library is to aid the debugging of a
*  remote attestation service.
*  To achieve that goal, the sample remote attestation application
*  will use this sample library to generate reproducible messages.
*/

#ifndef SAMPLE_LIBCRYPTO_H
#define SAMPLE_LIBCRYPTO_H

#include <stdint.h>

typedef enum sample_status_t
{
    SAMPLE_SUCCESS                  = 0,

    SAMPLE_ERROR_UNEXPECTED         ,      // Unexpected error
    SAMPLE_ERROR_INVALID_PARAMETER  ,      // The parameter is incorrect
    SAMPLE_ERROR_OUT_OF_MEMORY      ,      // Not enough memory is available to complete this operation

} sample_status_t;

#define SAMPLE_SHA256_HASH_SIZE            32
#define SAMPLE_ECP256_KEY_SIZE             32
#define SAMPLE_NISTP_ECP256_KEY_SIZE       (SAMPLE_ECP256_KEY_SIZE/sizeof(uint32_t))
#define SAMPLE_AESGCM_IV_SIZE              12
#define SAMPLE_AESGCM_KEY_SIZE             16
#define SAMPLE_AESGCM_MAC_SIZE             16
#define SAMPLE_CMAC_KEY_SIZE               16
#define SAMPLE_CMAC_MAC_SIZE               16
#define SAMPLE_AESCTR_KEY_SIZE             16

typedef struct sample_ec256_dh_shared_t
{
    uint8_t s[SAMPLE_ECP256_KEY_SIZE];
} sample_ec256_dh_shared_t;

typedef struct sample_ec256_private_t
{
    uint8_t r[SAMPLE_ECP256_KEY_SIZE];
} sample_ec256_private_t;

typedef struct sample_ec256_public_t
{
    uint8_t gx[SAMPLE_ECP256_KEY_SIZE];
    uint8_t gy[SAMPLE_ECP256_KEY_SIZE];
} sample_ec256_public_t;

typedef struct sample_ec256_signature_t
{
    uint32_t x[SAMPLE_NISTP_ECP256_KEY_SIZE];
    uint32_t y[SAMPLE_NISTP_ECP256_KEY_SIZE];
} sample_ec256_signature_t;

typedef void* sample_sha_state_handle_t;
typedef void* sample_cmac_state_handle_t;
typedef void* sample_ecc_state_handle_t;

typedef uint8_t sample_sha256_hash_t[SAMPLE_SHA256_HASH_SIZE];

typedef uint8_t sample_aes_gcm_128bit_key_t[SAMPLE_AESGCM_KEY_SIZE];
typedef uint8_t sample_aes_gcm_128bit_tag_t[SAMPLE_AESGCM_MAC_SIZE];
typedef uint8_t sample_cmac_128bit_key_t[SAMPLE_CMAC_KEY_SIZE];
typedef uint8_t sample_cmac_128bit_tag_t[SAMPLE_CMAC_MAC_SIZE];
typedef uint8_t sample_aes_ctr_128bit_key_t[SAMPLE_AESCTR_KEY_SIZE];

#ifdef __cplusplus
    #define EXTERN_C extern "C"
#else
    #define EXTERN_C 
#endif

    #define SAMPLE_LIBCRYPTO_API EXTERN_C

/* Rijndael AES-GCM
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS on success, error code otherwise.
*   Inputs: sample_aes_gcm_128bit_key_t *p_key - Pointer to key used in encryption/decryption operation
*           uint8_t *p_src - Pointer to input stream to be encrypted/decrypted
*           uint32_t src_len - Length of input stream to be encrypted/decrypted
*           uint8_t *p_iv - Pointer to initialization vector to use
*           uint32_t iv_len - Length of initialization vector
*           uint8_t *p_aad - Pointer to input stream of additional authentication data
*           uint32_t aad_len - Length of additional authentication data stream
*           sample_aes_gcm_128bit_tag_t *p_in_mac - Pointer to expected MAC in decryption process
*   Output: uint8_t *p_dst - Pointer to cipher text. Size of buffer should be >= src_len.
*           sample_aes_gcm_128bit_tag_t *p_out_mac - Pointer to MAC generated from encryption process
* NOTE: Wrapper is responsible for confirming decryption tag matches encryption tag */
SAMPLE_LIBCRYPTO_API sample_status_t sample_rijndael128GCM_encrypt(const sample_aes_gcm_128bit_key_t *p_key, const uint8_t *p_src, uint32_t src_len,
                                        uint8_t *p_dst, const uint8_t *p_iv, uint32_t iv_len, const uint8_t *p_aad, uint32_t aad_len,
                                        sample_aes_gcm_128bit_tag_t *p_out_mac);

/* Message Authentication - Rijndael 128 CMAC
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS on success, error code otherwise.
*   Inputs: sample_cmac_128bit_key_t *p_key - Pointer to key used in encryption/decryption operation
*           uint8_t *p_src - Pointer to input stream to be MAC
*           uint32_t src_len - Length of input stream to be MAC
*   Output: sample_cmac_gcm_128bit_tag_t *p_mac - Pointer to resultant MAC */
SAMPLE_LIBCRYPTO_API sample_status_t sample_rijndael128_cmac_msg(const sample_cmac_128bit_key_t *p_key, const uint8_t *p_src,
                                      uint32_t src_len, sample_cmac_128bit_tag_t *p_mac);



/*
* Elliptic Curve Crytpography - Based on GF(p), 256 bit
*/
/* Allocates and initializes ecc context
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS or failure as defined SAMPLE_Error.h.
*   Output: sample_ecc_state_handle_t ecc_handle - Handle to ECC crypto system  */
SAMPLE_LIBCRYPTO_API sample_status_t sample_ecc256_open_context(sample_ecc_state_handle_t* ecc_handle);

/* Cleans up ecc context
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS or failure as defined SAMPLE_Error.h.
*   Output: sample_ecc_state_handle_t ecc_handle - Handle to ECC crypto system  */
SAMPLE_LIBCRYPTO_API sample_status_t sample_ecc256_close_context(sample_ecc_state_handle_t ecc_handle);

/* Populates private/public key pair - caller code allocates memory
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS on success, error code otherwise.
*   Inputs: sample_ecc_state_handle_t ecc_handle - Handle to ECC crypto system
*   Outputs: sample_ec256_private_t *p_private - Pointer to the private key
*            sample_ec256_public_t *p_public - Pointer to the public key  */
SAMPLE_LIBCRYPTO_API sample_status_t sample_ecc256_create_key_pair(sample_ec256_private_t *p_private,
                                        sample_ec256_public_t *p_public,
                                        sample_ecc_state_handle_t ecc_handle);

/* Computes DH shared key based on private B key (local) and remote public Ga Key
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS on success, error code otherwise.
*   Inputs: sample_ecc_state_handle_t ecc_handle - Handle to ECC crypto system
*           sample_ec256_private_t *p_private_b - Pointer to the local private key - LITTLE ENDIAN
*           sample_ec256_public_t *p_public_ga - Pointer to the remote public key - LITTLE ENDIAN
*   Output: sample_ec256_dh_shared_t *p_shared_key - Pointer to the shared DH key - LITTLE ENDIAN
x-coordinate of (privKeyB - pubKeyA) */
SAMPLE_LIBCRYPTO_API sample_status_t sample_ecc256_compute_shared_dhkey(sample_ec256_private_t *p_private_b,
                                             sample_ec256_public_t *p_public_ga,
                                             sample_ec256_dh_shared_t *p_shared_key,
                                             sample_ecc_state_handle_t ecc_handle);


/* Computes signature for data based on private key 
*
* A message digest is a fixed size number derived from the original message with
* an applied hash function over the binary code of the message. (SHA256 in this case)
* The signer's private key and the message digest are used to create a signature. 
*
* A digital signature over a message consists of a pair of large numbers, 256-bits each,
* which the given function computes. 
*
* The scheme used for computing a digital signature is of the ECDSA scheme,
* an elliptic curve of the DSA scheme. 
*
* The keys can be generated and set up by the function: sgx_ecc256_create_key_pair.
*
* The elliptic curve domain parameters must be created by function: 
*     sample_ecc256_open_context
*
* Return: If context, private key, signature or data pointer is NULL, 
*                       SAMPLE_ERROR_INVALID_PARAMETER is returned.
*         If the signature creation process fails then SAMPLE_ERROR_UNEXPECTED is returned.
*
* Parameters:
*   Return: sample_status_t - SAMPLE_SUCCESS, success, error code otherwise.
*   Inputs: sample_ecc_state_handle_t ecc_handle - Handle to the ECC crypto system
*           sample_ec256_private_t *p_private - Pointer to the private key - LITTLE ENDIAN
*           uint8_t *p_data - Pointer to the data to be signed
*           uint32_t data_size - Size of the data to be signed
*   Output: ec256_signature_t *p_signature - Pointer to the signature - LITTLE ENDIAN */
SAMPLE_LIBCRYPTO_API sample_status_t sample_ecdsa_sign(const uint8_t *p_data, 
                                        uint32_t data_size,  
                                        sample_ec256_private_t *p_private, 
                                        sample_ec256_signature_t *p_signature, 
                                        sample_ecc_state_handle_t ecc_handle);

/* Allocates and initializes sha256 state
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS on success, error code otherwise.
*   Output: sample_sha_state_handle_t sha_handle - Handle to the SHA256 state  */
SAMPLE_LIBCRYPTO_API sample_status_t sample_sha256_init(sample_sha_state_handle_t* p_sha_handle);

/* Updates sha256 has calculation based on the input message
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS or failure.
*   Input:  sample_sha_state_handle_t sha_handle - Handle to the SHA256 state
*           uint8_t *p_src - Pointer to the input stream to be hashed
*           uint32_t src_len - Length of the input stream to be hashed  */
SAMPLE_LIBCRYPTO_API sample_status_t sample_sha256_update(const uint8_t *p_src, uint32_t src_len, sample_sha_state_handle_t sha_handle);

/* Returns Hash calculation
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS on success, error code otherwise.
*   Input:  sample_sha_state_handle_t sha_handle - Handle to the SHA256 state
*   Output: sample_sha256_hash_t *p_hash - Resultant hash from operation  */
SAMPLE_LIBCRYPTO_API sample_status_t sample_sha256_get_hash(sample_sha_state_handle_t sha_handle, sample_sha256_hash_t *p_hash);

/* Cleans up sha state
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS on success, error code otherwise.
*   Input:  sample_sha_state_handle_t sha_handle - Handle to the SHA256 state  */
SAMPLE_LIBCRYPTO_API sample_status_t sample_sha256_close(sample_sha_state_handle_t sha_handle);

#endif
