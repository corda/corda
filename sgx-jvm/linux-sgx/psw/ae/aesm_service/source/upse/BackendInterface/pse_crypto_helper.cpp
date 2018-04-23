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

#include "CertificateProvisioningProtocol.h"
#include <cstddef>
#include "ipp_wrapper.h"
#include "sgx_tcrypto.h"

#include "../../aesm_service/source/aesm/application/aesm_rand.h"

#include <ippcp.h>
#include "sgx_memset_s.h"
#include "se_wrapper.h"
#include "oal/error_report.h"

//#define USE_HARDCODED_PEK

#if defined(USE_HARDCODED_PEK)
public_key_t s_public_key =
{
    // little-endian
    {   // modulus
        0x1B, 0x7D, 0xB4, 0x5C, 0x89, 0x72, 0xFB, 0x51, 0xF3, 0x39, 0xB2, 0x07, 0x1F, 0xF2, 0x8A, 0xD1,
        0xAC, 0x55, 0xE7, 0x38, 0x7D, 0xEA, 0x42, 0xFA, 0x20, 0xD5, 0x31, 0xFA, 0x07, 0xB4, 0x5D, 0x64,
        0xC4, 0xF6, 0x9E, 0xD8, 0x34, 0xE2, 0xE3, 0x8D, 0xEA, 0x1B, 0xA6, 0xD2, 0xB8, 0xD2, 0xA4, 0xD2,
        0xA9, 0xF6, 0xFB, 0xA1, 0xA4, 0x9B, 0xC5, 0x25, 0xCE, 0xF1, 0xD3, 0xAD, 0xB9, 0xBF, 0xD9, 0x92,
        0xDF, 0x04, 0x83, 0xD0, 0x46, 0x9C, 0x66, 0xC0, 0x91, 0x4C, 0xEF, 0x43, 0x19, 0xD8, 0x54, 0xE6,
        0x32, 0xE7, 0x4F, 0x0E, 0xC5, 0x45, 0x74, 0x17, 0x38, 0xE6, 0xA9, 0x01, 0xED, 0xF8, 0xF6, 0x08,
        0xFD, 0x25, 0x0A, 0x1D, 0x8B, 0xED, 0x99, 0xB1, 0xFE, 0x52, 0x13, 0x30, 0x89, 0x5E, 0xE9, 0xD9,
        0x6A, 0xA7, 0x88, 0xD3, 0xBA, 0x1C, 0x78, 0xAD, 0xAB, 0xE1, 0x3C, 0xCB, 0xEF, 0x72, 0x41, 0x0B,
        0xB6, 0x8F, 0xDC, 0x66, 0x59, 0xF7, 0xCB, 0x0D, 0xDB, 0xAA, 0x37, 0x21, 0x8F, 0x80, 0x2D, 0x26,
        0xA2, 0xD0, 0x71, 0x96, 0xC1, 0x13, 0xCE, 0x26, 0xF5, 0x2F, 0x17, 0xCA, 0x99, 0x9B, 0x4B, 0x76,
        0x31, 0x98, 0x82, 0xCD, 0x18, 0x6A, 0x0E, 0x9C, 0xCE, 0x31, 0xA6, 0x71, 0x68, 0x4E, 0xE4, 0x02,
        0x89, 0x1C, 0xB1, 0x67, 0xAA, 0x6A, 0xE2, 0x9D, 0x62, 0xF4, 0x49, 0x2F, 0x86, 0x12, 0xE6, 0x00,
        0x7C, 0xD3, 0xD5, 0xCD, 0xC9, 0x93, 0x49, 0xAB, 0x70, 0x8A, 0xCC, 0xB7, 0xF0, 0x02, 0x21, 0x3F,
        0xA6, 0xE7, 0xB5, 0xFC, 0xB4, 0x94, 0x61, 0x6E, 0xF7, 0x45, 0x35, 0x24, 0xA6, 0x6F, 0x07, 0xBF,
        0x4A, 0x56, 0x81, 0xCD, 0xAF, 0xA1, 0x49, 0x66, 0xCF, 0xCF, 0xDB, 0x63, 0xCB, 0xB4, 0x75, 0x7C,
        0x20, 0xC7, 0x98, 0xA5, 0x02, 0xF6, 0xDD, 0x97, 0xFB, 0xAE, 0x61, 0x7A, 0x3C, 0x11, 0x53, 0x14
    },
    0x00000011  // exponent
};
#endif


const public_key_t& CertificateProvisioningProtocol::get_intel_pek()
{
#if defined(USE_HARDCODED_PEK)
    return s_public_key;
#else
    return m_publicKey;
#endif
}



//Function to get the rsa public key of intel backend server for IPP functions
//The output rsa_pub_key should be released by function free_rsa_key
static IppStatus get_intel_rsa_pub_key_in_ipp_format(const public_key_t& publicKey, IppsRSAPublicKeyState **rsa_pub_key)
{
    if (sizeof(publicKey.n) != RSA_3072_KEY_BYTES)
        return ippStsSizeErr;

    if (NULL == rsa_pub_key)
        return ippStsNullPtrErr;

    IppStatus status = create_rsa_pub_key(
                       sizeof(publicKey.n),
                       sizeof(publicKey.e),
                       (const Ipp32u*)publicKey.n,
                       (const Ipp32u*)&publicKey.e,
                       rsa_pub_key);

    return status;
}


int CertificateProvisioningProtocol::get_intel_pek_cipher_text_size()
{
    return sizeof(m_publicKey.n);
}

void CertificateProvisioningProtocol::free_intel_ipp_rsa_pub_key(IppsRSAPublicKeyState* rsa_pub_key)
{
    if (NULL == rsa_pub_key)
        return;

    secure_free_rsa_pub_key(sizeof(m_publicKey.n), sizeof(m_publicKey.e), rsa_pub_key);
}


ae_error_t CertificateProvisioningProtocol::get_random_value(uint32_t size, upse::Buffer& randomValue)
{
    ae_error_t status = AE_FAILURE;

    do
    {
        status = randomValue.Alloc(size);
        if (AE_FAILED(status))
            break;

        uint8_t* p;
        upse::BufferWriter bw(randomValue);
        status = bw.reserve(size, &p);
        if (AE_FAILED(status))
            break;

        status = aesm_read_rand(p, size);

    } while (0);

    return status;
}


ae_error_t CertificateProvisioningProtocol::aesGCMEncrypt(const upse::Buffer& iv, const upse::Buffer& key, const upse::Buffer& plainText,
                            const upse::Buffer& aad, upse::Buffer& encryptedText, upse::Buffer& mac)
{
    ae_error_t status = AE_FAILURE;

    do
    {
        if (key.getSize() != sizeof(sgx_aes_gcm_128bit_key_t))
            break;

        status = encryptedText.Alloc(plainText.getSize());
        if (AE_FAILED(status))
            break;

        uint8_t* pEncryptedText;
        status = upse::BufferWriter(encryptedText).reserve(encryptedText.getSize(), &pEncryptedText);
        if (AE_FAILED(status))
            break;

        status = mac.Alloc(sizeof(sgx_aes_gcm_128bit_tag_t));
        if (AE_FAILED(status))
            break;

        uint8_t* pMAC;
        status = upse::BufferWriter(mac).reserve(mac.getSize(), &pMAC);
        if (AE_FAILED(status))
            break;

        sgx_status_t sgx_status;
        sgx_status = sgx_rijndael128GCM_encrypt(reinterpret_cast<const sgx_aes_gcm_128bit_key_t *>(key.getData()),
                        plainText.getData(), plainText.getSize(), pEncryptedText, iv.getData(), IV_SIZE, aad.getData(), aad.getSize(),
                        reinterpret_cast<sgx_aes_gcm_128bit_tag_t *>(pMAC));
        if (SGX_SUCCESS != sgx_status)
        {
            status = AE_FAILURE;
            break;
        }

        status = AE_SUCCESS;
    } while (0);

    return status;
}


ae_error_t CertificateProvisioningProtocol::aesGCMDecrypt(const upse::Buffer& iv, const upse::Buffer& key, const upse::Buffer& cipherText,
                            const upse::Buffer& aad, const upse::Buffer& mac, upse::Buffer& plainText)
{
    ae_error_t status = AE_FAILURE;

    do
    {
        if (key.getSize() != sizeof(sgx_aes_gcm_128bit_key_t))
            break;

        status = plainText.Alloc(cipherText.getSize());
        if (AE_FAILED(status))
            break;

        uint8_t* pPlainText = NULL;
        status = upse::BufferWriter(plainText).reserve(plainText.getSize(), &pPlainText);
        if (AE_FAILED(status))
            break;

        sgx_status_t sgx_status;
        sgx_status = sgx_rijndael128GCM_decrypt(reinterpret_cast<const sgx_aes_gcm_128bit_key_t *>(key.getData()),
                        cipherText.getData(), cipherText.getSize(), pPlainText, iv.getData(), IV_SIZE, aad.getData(), aad.getSize(),
                        reinterpret_cast<const sgx_aes_gcm_128bit_tag_t *>(mac.getData()));
        if (SGX_SUCCESS != sgx_status)
        {
            AESM_LOG_ERROR("%s", g_event_string_table[SGX_EVENT_PSE_CERT_PROV_INTEGRITY_ERROR]);
            status = AE_FAILURE;
            break;
        }

        status = AE_SUCCESS;
    } while (0);

    return status;
}

ae_error_t CertificateProvisioningProtocol::aesCMAC(const upse::Buffer& key, const upse::Buffer& message, upse::Buffer& cmac)
{
    ae_error_t status = AE_FAILURE;

    do
    {
        if (key.getSize() != sizeof(sgx_aes_gcm_128bit_key_t))
            break;

        status = cmac.Alloc(sizeof(sgx_cmac_128bit_tag_t));
        if (AE_FAILED(status))
            break;

        uint8_t* pCMAC;
        status = upse::BufferWriter(cmac).reserve(cmac.getSize(), &pCMAC);
        if (AE_FAILED(status))
            break;

        sgx_status_t sgx_status;
        sgx_status = sgx_rijndael128_cmac_msg(reinterpret_cast<const sgx_aes_gcm_128bit_key_t *>(key.getData()),
            message.getData(), message.getSize(), reinterpret_cast<sgx_cmac_128bit_tag_t *>(pCMAC));
        if (SGX_SUCCESS != sgx_status)
        {
            status = AE_FAILURE;
            break;
        }

        status = AE_SUCCESS;

    } while (0);

    return status;
}


ae_error_t CertificateProvisioningProtocol::encryptRSA_OAEP_SHA256(const public_key_t& publicKey, upse::BufferReader& plainTextReader, upse::Buffer& cipherText)
{
    ae_error_t status = AE_FAILURE;
    IppStatus ippReturnStatus;

    IppsRSAPublicKeyState* rsa_pub_key = NULL;
    uint8_t* pub_key_buffer = NULL;

    do
    {
        ippReturnStatus = get_intel_rsa_pub_key_in_ipp_format(publicKey, &rsa_pub_key);
        if (ippStsNoErr != ippReturnStatus)
            break;

        uint8_t seed[IPP_SHA256_DIGEST_BITSIZE / 8];
        ae_error_t rand_status = aesm_read_rand(seed, sizeof(seed));
        BREAK_IF_TRUE(AE_FAILED(rand_status), status, AE_FAILURE);

        int pub_key_size;
        ippReturnStatus = ippsRSA_GetBufferSizePublicKey(&pub_key_size, rsa_pub_key);
        if (ippStsNoErr != ippReturnStatus)
            break;

        pub_key_buffer = (uint8_t*)malloc(pub_key_size);
        if (NULL == pub_key_buffer)
            break;

        int plainTextSize = plainTextReader.getRemainingSize();
        const uint8_t* pPlainText = NULL;
        if (AE_FAILED(plainTextReader.readRaw(&pPlainText)))
            break;

        int cipherTextSize = get_intel_pek_cipher_text_size();
        if (AE_FAILED(cipherText.Alloc(cipherTextSize)))
            break;

        upse::BufferWriter cipherTextWriter(cipherText);
        uint8_t* pCipherText;
        if (AE_FAILED(cipherTextWriter.reserve(cipherText.getSize(), &pCipherText)))
            break;

        ippReturnStatus = ippsRSAEncrypt_OAEP(pPlainText, plainTextSize,
                                        NULL, 0, seed,
                                        pCipherText,
                                        rsa_pub_key, IPP_ALG_HASH_SHA256,
                                        pub_key_buffer);

        if (ippStsNoErr != ippReturnStatus)
            break;

        status = AE_SUCCESS;

    } while (0);

    if (NULL != pub_key_buffer)
        free(pub_key_buffer);

    free_intel_ipp_rsa_pub_key(rsa_pub_key);

    return status;
}
