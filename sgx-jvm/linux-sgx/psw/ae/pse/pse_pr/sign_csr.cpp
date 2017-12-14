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

#include <cstddef>
#include "pse_pr_inc.h"
#include "sign_csr.h"
#include "le2be_macros.h"
#include "sgx_trts.h"
#include <cstring>

#include "ae_ipp.h"

#define BREAK_IF_NULL(a)   { if (NULL     == (a)) break; }
#define BREAK_IF_IPPERR(a) { if (ippStsNoErr != (a)) break; }

#define LEN_ECDSA_SIG_COMP   32

#include "pse_product_type.hh"

static uint8_t CertificateSigningRequestTemplate[] =
{
    /*0000h:*/ 0x30, 0x82, 0x01, 0xB9,
    /* BEGIN -- Certificate Request Info (to be signed)                                                        */
                                       0x30, 0x82, 0x01, 0x5E,
                                                               0x02, 0x01, 0x00, 0x30, 0x81, 0xB7, 0x31, 0x0B,
    /*0010h:*/ 0x30, 0x09, 0x06, 0x03, 0x55, 0x04, 0x06, 0x0C, 0x02, 0x55, 0x53, 0x31, 0x0B, 0x30, 0x09, 0x06,
    /*0020h:*/ 0x03, 0x55, 0x04, 0x08, 0x0C, 0x02, 0x43, 0x41, 0x31, 0x14, 0x30, 0x12, 0x06, 0x03, 0x55, 0x04,
    /*0030h:*/ 0x07, 0x0C, 0x0B, 0x53, 0x61, 0x6E, 0x74, 0x61, 0x20, 0x43, 0x6C, 0x61, 0x72, 0x61, 0x31, 0x1A,
    /*0040h:*/ 0x30, 0x18, 0x06, 0x03, 0x55, 0x04, 0x0A, 0x0C, 0x11, 0x49, 0x6E, 0x74, 0x65, 0x6C, 0x20, 0x43,
    /*0050h:*/ 0x6F, 0x72, 0x70, 0x6F, 0x72, 0x61, 0x74, 0x69, 0x6F, 0x6E, 0x31, 0x37, 0x30, 0x35, 0x06, 0x03,
    /*0060h:*/ 0x55, 0x04, 0x0B, 0x0C, 0x2E, 0x49, 0x6E, 0x74, 0x65, 0x6C, 0x20, 0x50, 0x53, 0x45, 0x20,
        /* BEGIN -- organizationalUnitName GUID                                                                */
                                                                                                         0x65,
    /*0070h:*/ 0x66, 0x65, 0x66, 0x65, 0x66, 0x65, 0x66, 0x2D, 0x65, 0x66, 0x65, 0x66, 0x2D, 0x65, 0x66, 0x65,
    /*0080h:*/ 0x66, 0x2D, 0x65, 0x66, 0x65, 0x66, 0x2D, 0x65, 0x66, 0x65, 0x66, 0x65, 0x66, 0x65, 0x66, 0x65,
    /*0090h:*/ 0x66, 0x65, 0x66,
        /* END -- organizationalUnitName GUID                                                                  */
                                 0x31, 0x16, 0x30, 0x14, 0x06, 0x03, 0x55, 0x04, 0x03, 0x0C, 0x0D, 0x77, 0x77,
    /*00A0h:*/ 0x77, 0x2E, 0x69, 0x6E, 0x74, 0x65, 0x6C, 0x2E, 0x63, 0x6F, 0x6D, 0x31, 0x18, 0x30, 0x16, 0x06,
    /*00B0h:*/ 0x0A, 0x09, 0x92, 0x26, 0x89, 0x93, 0xF2, 0x2C, 0x64, 0x01, 0x01, 0x0C, 0x08, 0x46, 0x46, 0x46,
    /*00C0h:*/ 0x46, 0x46, 0x46, 0x46, 0x46, 0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2A, 0x86, 0x48, 0xCE, 0x3D,
    /*00D0h:*/ 0x02, 0x01, 0x06, 0x08, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00, 0x04,
    /*     BEGIN -- Public Key (64 bytes) - (public key Px || public key Py)                                   */
    /*00E0h:*/ 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
    /*00F0h:*/ 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
    /*0100h:*/ 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
    /*0110h:*/ 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
    /*     END -- Public Key (64 bytes)                                                                        */
    /*0120h:*/ 0xA0, 0x44, 0x30, 0x42, 0x06, 0x09, 0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x09, 0x0E, 0x31,
    /*0130h:*/ 0x35, 0x30, 0x33, 0x30, 0x0E, 0x06, 0x03, 0x55, 0x1D, 0x0F, 0x01, 0x01, 0xFF, 0x04, 0x04, 0x03,
    /*0140h:*/ 0x02, 0x06, 0xC0, 0x30, 0x0C, 0x06, 0x03, 0x55, 0x1D, 0x13, 0x01, 0x01, 0xFF, 0x04, 0x02, 0x30,
    /*0150h:*/ 0x00, 0x30, 0x13, 0x06, 0x09, 0x2A, 0x86, 0x48, 0x86, 0xF8, 0x4D, 0x01, 0x09, 0x02, 0x01, 0x01,
    /*0160h:*/ 0xFF, 0x04, 0x03, 0x0A, 0x01,

#if !defined(PRODUCT_TYPE)
#error PRODUCT_TYPE not #defined
#endif
                                             PRODUCT_TYPE,  /* product ID */
    /* END -- Certificate Request Info (to be signed)                                                          */

    /* ecdsaWithSHA256 (1.2.840.10045.4.3.2)                                                                   */
                                                   0x30, 0x0A, 0x06, 0x08, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x04,
    /*0170h:*/ 0x03, 0x02,

    /* BEGIN -- Signature data (max 75 bytes)                                                                  */
    /* 0x03 || MM || 0x00 || 0x30 || NN || 0x02 || XX || sigX || 0x02 || YY || sigY                            */
                           0x03, 0x49, 0x00,
                                             0x30, 0x46,
    /*          Signature X ( 0x02 || XX || Sx (max 33bytes, See X.690 8.3 Encoding of an integer value)       */
                                                         0x02, 0x21,
                                                                     0x00, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
    /*0180h:*/ 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
    /*0190h:*/ 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
    /*          Signature Y ( 0x02 || YY || Sy (max 33bytes, See X.690 8.3 Encoding of an integer value)       */
                                                                           0x02, 0x21,
                                                                                       0x00, 0xaa, 0xaa, 0xaa,
    /*01A0h:*/ 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
    /*01B0h:*/ 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa
    /* END -- Signature data                                                                                   */
};

const int nOffset_CSRSize     = 0x0002;             // Location of CSR length (length excludes 4 bytes header)
//const int nSize_CSRSize       = 2;

const int nOffset_CSRInfo     = 0x0004;             // Offset to start of CSR Info block (Signature is computed using SHA256 Message Digest of CSR Info field)
const int nSize_CSRInfo       = 4 + 350;            // Length of CSR Info block to sign

const int nOffset_GUID        = 0x006F;
const int nSize_GUID          = 36;                 // fefefefe-fefe-fefe-fefe-fefefefefefe (8-4-4-4-12)

const int nOffset_PublicKey   = 0x00E0;             // Offset to start of Public Key
const int nOffset_SigSize1    = 0x0173;             // Offset to length for signature block (1 byte)
const int nOffset_SigSize2    = 0x0176;             // Offset to length for signature block (1 byte)

const uint16_t nOffset_SigX        = 0x0177;             // Offset to start of signature (size will vary from 68 to 70 bytes)



    /* Steps:
        1) The public key will be stuffed in the CSR record
        2) A new GUID will be created and placed in the organizationalUnitName field
        2) The CSR record info signature will be computed using the ECDSA private key
        3) The signature will be separated into X and Y components
        4) Prepare X component of signature
                if Byte 0 of X component has high bit set then prepare X component
                    0x02 || 0x21 || 0x00 || X
                else
                    0x02 || 0x20 || X
        5) Prepare Y component of signature
                if Byte 0 of Y component has high bit set then prepare Y component
                    0x02 || 0x21 || 0x00 || Y
                else
                    0x02 || 0x20 || Y
        6) Update CSR length (307, 308, or 309 bytes)
        7) Update DER signature length (68, 69, or 70 bytes)
        8) Copy prepared signature components immediately after DER signature length byte
        9) Adjust size of CSR reported
    */


SignCSR::SignCSR(void)
{
}


SignCSR::~SignCSR(void)
{
}


static inline char ConvertValueToAscii(const uint8_t in)
{
    if(in <= 0x09)
    {
        return (uint8_t)(in + 48);
    }
    else if(in <= 0x0F)
    {
        return (uint8_t)(in + 55);
    }

    return 0;
}


static bool getFormatedGUID(uint8_t* pGUID, uint32_t nGUID)
{
    uint32_t i;
    uint8_t randBuffer[16];
    uint8_t asciiBuffer[32];

    if (nGUID < 36)
        return false;

    if (0 != sgx_read_rand(randBuffer, sizeof(randBuffer)))
        return false;

    uint8_t* p = randBuffer;

    for (i = 0; i < sizeof(asciiBuffer); i += 2)
    {
        asciiBuffer[i]   = ConvertValueToAscii((uint8_t)(*p >> 4));
        asciiBuffer[i+1] = ConvertValueToAscii(*p & 0xf);
        ++p;
    }

    // 01234567-9012-4567-9012-546890123456
    p = asciiBuffer;
    for (i = 0; i < nGUID; i++)
    {
        if (i == 8 || i == 13 || i == 18 || i == 23)
            pGUID[i] = '-';
        else
        {
            pGUID[i] = *p;
            ++p;
        }
    }
    return true;
}



size_t SignCSR::GetMaxSize()
{
    // May be 2 bytes larger than needed depending on whether X/Y fields of signature need a leading 0x00.
    return sizeof(CertificateSigningRequestTemplate);
}


// Note: The keys input are little-endian
ae_error_t SignCSR::GetSignedTemplate(
    /*in */ EcDsaPrivKey* pPrivateKey,
    /*in */ EcDsaPubKey* pPublicKey,
    /*in */ sgx_ecc_state_handle_t csr_ecc_handle,
    /*out*/ Ipp8u* pSignedTemplate,
    /*i/o*/ uint16_t* pnBytes)
{
    ae_error_t aeStatus = PSE_PR_INSUFFICIENT_MEMORY_ERROR;

	uint8_t SerializedSignature[64];

    if (NULL == pSignedTemplate || NULL == pnBytes ||
        NULL == pPrivateKey || NULL == pPublicKey || NULL == csr_ecc_handle)
        return PSE_PR_BAD_POINTER_ERROR;

    if (GetMaxSize() > *pnBytes)
        return PSE_PR_BUFFER_TOO_SMALL_ERROR;

    memset_s(pSignedTemplate, *pnBytes, 0, *pnBytes);
    memcpy(pSignedTemplate, CertificateSigningRequestTemplate, sizeof(CertificateSigningRequestTemplate));

    // Write serialized public key to template (public key Px || public key Py)
    memcpy(&pSignedTemplate[nOffset_PublicKey], pPublicKey, sizeof(EcDsaPubKey));
    // convert the public key field to big endian
    SwapEndian_32B(&pSignedTemplate[nOffset_PublicKey] +  0);
    SwapEndian_32B(&pSignedTemplate[nOffset_PublicKey] + 32);


    do
    {
        if (!getFormatedGUID(&pSignedTemplate[nOffset_GUID], nSize_GUID))
        {
            aeStatus = PSE_PR_INTERNAL_ERROR;
            break;
        }
        if (SGX_SUCCESS == sgx_ecdsa_sign(&pSignedTemplate[nOffset_CSRInfo], nSize_CSRInfo,
                          (sgx_ec256_private_t *)pPrivateKey,
                          (sgx_ec256_signature_t *)SerializedSignature,
                          csr_ecc_handle))
        {
            /* Convert the signature to big endian format */
            SwapEndian_32B(SerializedSignature);
            SwapEndian_32B(&(SerializedSignature[32]));
        }
        else
        {
            break;
        }

        // SigBuffer = 0x02 || length of component X || Component X of Sigature || 0x02 || length of component Y || Component Y of Sigature
        uint8_t SigBuffer[70] = {0};
        uint16_t i = 0;

        // Place signature X,Y component into buffer
        // SerializedSignature[0]~ is X component and SerializedSignature[32]~ is Y component
        for (int offset = 0; offset <= LEN_ECDSA_SIG_COMP; offset += LEN_ECDSA_SIG_COMP)
        {
            SigBuffer[i++] = 0x02;
            if ((SerializedSignature[offset] & 0x80))
            {
                // Add a leading zero if the bit 8 of the first byte is 1
                SigBuffer[i++] = LEN_ECDSA_SIG_COMP + 1;
                SigBuffer[i++] = 0x00;
                memcpy(&SigBuffer[i], &SerializedSignature[offset], LEN_ECDSA_SIG_COMP);
                i = static_cast<uint16_t>(i + LEN_ECDSA_SIG_COMP);
            }
            else
            {
                // The leading j bytes need to be removed if those bytes are all 0s and the bit 8 of the j+1 byte is also 0
                int j = 0;
                while (j < LEN_ECDSA_SIG_COMP - 1 && SerializedSignature[offset + j] == 0 && (SerializedSignature[offset + j + 1] & 0x80) == 0)
                    j++;
                if (j == LEN_ECDSA_SIG_COMP - 1 && SerializedSignature[offset + j] == 0)
                {
                    // The component buffer is all 0s, which should not happen
                    aeStatus = PSE_PR_INTERNAL_ERROR;
                    goto exit;
                }

                SigBuffer[i++] = static_cast<uint8_t>(LEN_ECDSA_SIG_COMP - j);
                memcpy(&SigBuffer[i], &SerializedSignature[offset + j], LEN_ECDSA_SIG_COMP - j);
                i = static_cast<uint16_t>(i + LEN_ECDSA_SIG_COMP - j);
            }
        }

        pSignedTemplate[nOffset_SigSize1] = (Ipp8u)(i + 3);
        pSignedTemplate[nOffset_SigSize2] = (Ipp8u)(i);

        memcpy(&pSignedTemplate[nOffset_SigX], SigBuffer, i);

        *pnBytes = (uint16_t)(nOffset_SigX + i);


        uint16_t csrLength = (uint16_t)(*pnBytes - 4);
        pSignedTemplate[nOffset_CSRSize+0] = (Ipp8u)(csrLength >> 8);
        pSignedTemplate[nOffset_CSRSize+1] = (Ipp8u)(csrLength & 0xff);

        aeStatus = AE_SUCCESS;

    } while (0);

exit:
    // If we weren't successful, don't let any data out
    if (AE_FAILED(aeStatus))
    {
        if (NULL != pSignedTemplate && NULL != pnBytes)
            memset_s(pSignedTemplate, *pnBytes, 0, *pnBytes);

        aeStatus = PSE_PR_SIGNING_CSR_ERROR;
    }
#if 0 //for debugging to verify signature was generated correctly
    else
    {
        uint8_t result;
        // Convert the signature back to little endian, for verification API
        SwapEndian_32B(SerializedSignature);
        SwapEndian_32B(&(SerializedSignature[32]));

        if ((SGX_SUCCESS != sgx_ecdsa_verify(&pSignedTemplate[nOffset_CSRInfo], nSize_CSRInfo,
                          (sgx_ec256_public_t *)pPublicKey, (sgx_ec256_signature_t *)SerializedSignature,
                          &result, csr_ecc_handle)) || (result != SGX_EC_VALID ))
        {
            aeStatus = PSE_PR_SIGNING_CSR_ERROR;
        }
    }
#endif

    return aeStatus;
}


