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


#include "X509Cert.h"
#include <cstddef>
#include <assert.h>

#define X509_FOR_PSE_PR  1


#ifdef X509_FOR_PSE_PR
#include "pse_pr_support.h"
// used to eliminate `unused variable' warning
#define UNUSED(val) (void)(val)
#endif

#ifndef X509_FOR_PSE_PR
#ifdef WIN_TEST
#include <crypt_data_gen.h>
#include "special_defs.h"
#include <openssl/rsa.h>
#include <openssl/sha.h>
#include <openssl/hmac.h>
#include <openssl/aes.h>
#include <openssl/pem.h>
#include <openssl/x509.h>
#include <openssl/objects.h>

extern HANDLE hConsole;
#endif

#ifndef WIN_TEST
#include "MeTypes.h"
#include "SessMgrCommonDefs.h"
#include "le2be_macros.h"
#include "CryptoDefs.h"
#include "romapi/romapi_rsa.h"
#include "TimeSrv.h"
#endif
#endif  // #ifndef X509_FOR_PSE_PR

#ifdef X509_FOR_PSE_PR
STATUS CreateSha1Hash
    (
    /*in */  SessMgrDataBuffer            *pSrcBuffer,
    /*out*/  SessMgrDataBuffer            *pDigest
    )
{
    PrepareHashSHA1 hash;
    hash.Update(pSrcBuffer->buffer, pSrcBuffer->length);
    if (!hash.Finalize((SHA1_HASH*)pDigest->buffer))
        return X509_GENERAL_ERROR;

    return STATUS_SUCCESS;
};


crypto_status_t EcDsa_VerifySignature
    (
    /*in */ const UINT8* pMsg,
    /*in */ uint32_t nMsg,
    /*in */ const EcDsaPubKey* pPublicKey,
    /*in */ const EcDsaSig* pSignature,
    /*out*/ bool* fValid
    )
{
    crypto_status_t status = CRYPTO_STATUS_INTERNAL_ERROR;
    sgx_ecc_state_handle_t ecc_handle = NULL;
    *fValid = false;

    do
    {

        if (SGX_SUCCESS != sgx_ecc256_open_context(&ecc_handle)) break;

        uint8_t result;

        if ((SGX_SUCCESS == sgx_ecdsa_verify(pMsg, nMsg,
            (sgx_ec256_public_t *)pPublicKey,
            (sgx_ec256_signature_t *)pSignature,
            &result,
            ecc_handle)) && (result == SGX_EC_VALID ))
            *fValid = true;

        status = CRYPTO_STATUS_SUCCESS;

    } while (0);
    if (ecc_handle != NULL) sgx_ecc256_close_context(ecc_handle);
    return status;
}

#endif

//*******************************************************************************************************************************************
//*******************************************************************************************************************************************
//*******************************************************************************************************************************************

static STATUS VerifyBasicCertificateAttributes(const Uint8* certificateDerEncoded, const Uint8* workBuffer, const SessMgrCertificateFields* certificateFields,
                                               const ISSUER_INFO *IssuerInfo, CertificateType CertType, CertificateLevel  CertLevel , BOOL UseFacsimileEpid);

#ifndef X509_FOR_PSE_PR
static STATUS VerifyOcspRevocationStatus(SessMgrCertificateFields* certificateFields,
                                         UINT8 NumberofSingleResponses, OCSP_CERT_STATUS_TABLE * OcspCertStatusTable, ISSUER_INFO *IssuerInfo);
#endif

static STATUS VerifySignature(const ISSUER_INFO *IssuerInfo, const SessMgrDataBuffer *MsgBuffer, const SessMgrDataBuffer *SignBuffer, BOOL UseFacsimileEpid);

#ifndef X509_FOR_PSE_PR
static void GetPublicKeyDataBuf(SessMgrDataBuffer *PubKeyBuffer, ISSUER_INFO *IssuerInfo);
static STATUS VerifyOcspCachedResponseValidity(SessMgrOcspResponseFields *OcspResponseFields);
static STATUS VerifyValidity(SessMgrDateTime notValidBeforeTime, SessMgrDateTime NotValidAfter);
static STATUS ConvertTimeToNtp(SessMgrDateTime Time, NTP_TIMESTAMP *NtpTime);
static STATUS StoreTrustedTime(SessMgrDateTime TrustedTime);
#endif

#ifndef X509_FOR_PSE_PR
static STATUS VerifyOcspResponseAttributes(Uint8* OcspRespBuffer, SessMgrOcspResponseFields *ocspResponseFields, ISSUER_INFO* OcspCertRootPublicKey,
                                           SessMgrDataBuffer Nonce, OCSP_REQ_TYPE OcspReqType, BOOL UseFacsimileEpid);
static BOOL VerifySha1Hash(SessMgrDataBuffer *HashData, UINT8 *Expectedhash, UINT32 ExpectedHashLength);
#endif

static STATUS sessMgrParseDerCert(IN  X509_PROTOCOL* X509Protocol, IN  Uint8* certificateDerEncoded,
                                  IN  Uint8* pCertEnd, IN  Uint8* workBuffer, IN UINT32 workBufferSize,
                                  OUT SessMgrCertificateFields* certificateFields, IN  ISSUER_INFO *IssuerInfo,
                                  IN  BOOL UseFacsimileEpid);

#ifndef X509_FOR_PSE_PR
static STATUS sessMgrParseOcspResponse(IN X509_PROTOCOL* X509Protocol, IN  Uint8* OcspResponse,
                                       IN  Uint8* OcspResponseEnd, IN  Uint8* workBuffer, IN  UINT32 workBufferSize,
                                       OUT SessMgrOcspResponseFields* OcspResponseFields);
static STATUS ParseBoolean(UINT8 **ppCurrent, UINT8 *pEnd, BOOL* Value, BOOL optional);
#endif

static STATUS ParseInteger(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrDataBuffer* DataBuf, BOOL isOptional, BOOL MustBePositive, UINT32 *PaddingLen);

#ifndef X509_FOR_PSE_PR
static STATUS ParseOcspExtensions(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrOcspResponseFields* OcspResponseFields);
static STATUS ParseCertExtensions(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrCertificateFields* certificateFields);
static STATUS ParseCertificatePolicy(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrDataBuffer *CertificatePolicy);
#endif

static STATUS ParseSubjectPublicKeyInfo(UINT8 **ppCurrent, UINT8 *pEnd, UINT8 **pworkbuffer, SessMgrCertificateFields* certificateFields);
static STATUS ParseRsaPublicKey(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrRsaKey * RsaKey);
static STATUS ParseEpidPublicKey(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrEpidGroupPublicKey * EpidKey);
static STATUS ParseEcdsaPublicKey(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrEcdsaPublicKey * EcDsaKey, SessMgrEllipticCurveParameter params);
static STATUS ParseOID(UINT8 **ppCurrent, UINT8 *pEnd, UINT32 *EnumVal, const UINT8 *OidList, UINT32 Max_Entries, UINT32 EntrySize );
static STATUS ParseSignatureValue(UINT8 **ppCurrent, UINT8 *pEnd, UINT8 **pworkbuffer, UINT32 WorkBufferSize, SessMgrDataBuffer *SignatureValueBuf, UINT8 SignatureAlgoId);
static STATUS ParseAlgoIdentifier(UINT8 **ppCurrent, UINT8 *pEnd, UINT32* algoId, AlgorithmTypes Type, SessMgrEllipticCurveParameter *params);
static STATUS ParseAlgoParameters(UINT8 **ppCurrent, UINT8 *pEnd, UINT32* param);
static STATUS ParseName(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrX509Name* Name);
static STATUS ParseTime(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrDateTime* DateTime);
static STATUS DecodeLength(UINT8* Buffer, UINT8* BufferEnd, UINT32* Length, UINT8* EncodingBytes);
static void SwapEndian(UINT8* ptr, int length);
static STATUS swapendian_memcpy(UINT8 *DestPtr, UINT32 DestLen, UINT8 *SrcPtr, UINT32 SrcLen);
static STATUS ParseIdAndLength(UINT8 **ppCurrent, UINT8 *pEnd, UINT8 ExpectedId, UINT32* Length, UINT8* EncodingBytes, BOOL Optional);

#ifndef X509_FOR_PSE_PR
static int Pow(int num, int exp);
#endif


/* This list should always be synced up with the SessMgrAlgorithmOid enum */
const UINT8 HardCodedSignatureAlgorithmOid[][9] =
{
    {0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x02},
    {0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x03},
    {0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x04},
    {0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x05},
    {0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x07},
    {0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x08},
    {0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x09},
    {0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x0a},
    {0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x0b},
    {0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x0c},
    {0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x0d},
    {0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x0e},

    {0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x04, 0x01},
    {0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x04, 0x03, 0x02},
};

const UINT8 HardCodedPublicKeyAlgorithmOid[][10] =
{
    {0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x01},
    {0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x02, 0x01},
    {0x2A, 0x86, 0x48, 0x86, 0xf8, 0x4d, 0x01, 0x09, 0x04, 0x01},
    {0x2A, 0x86, 0x48, 0x86, 0xf8, 0x4d, 0x01, 0x09, 0x04, 0x02},
    {0x2A, 0x86, 0x48, 0x86, 0xf8, 0x4d, 0x01, 0x09, 0x04, 0x03},
};

const UINT8 HashAlgorithmOid[][9] =
{
    {0x2B, 0x0E, 0x03, 0x02, 0x1A},
    {0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01},
};

/* This list should always be synced up with the NameStruct enum */
const UINT8 HardCodedNameOid[][10] =
{
    {0x55, 0x04, 0x03},
    {0x55, 0x04, 0x0a},
    {0x55, 0x04, 0x06},
    {0x55, 0x04, 0x07},
    {0x55, 0x04, 0x08},
    {0x55, 0x04, 0x0b},
    {0x09, 0x92, 0x26, 0x89, 0x93, 0xF2, 0x2C, 0x64, 0x01, 0x01},
};

#ifndef X509_FOR_PSE_PR
const UINT8 CertExtensionOid[][9] =
{
    {0x55, 0x1d, 0x23},
    {0x55, 0x1d, 0x0E},
    {0x55, 0x1d, 0x0F},
    {0x55, 0x1d, 0x13},
    {0x55, 0x1d, 0x20},
    {0x55, 0x1d, 0x25},
    {0x2A, 0x86, 0x48, 0x86, 0xF8, 0x4D, 0x01, 0x09, 0x02},
};

const UINT8 OcspExtensionOid[][9] =
{
    {0x2B, 0x06, 0x01, 0x05, 0x05, 0x07, 0x30, 0x01, 0x02},  // 1.3.6.1.5.5.7.48.1.2
};
#endif

const UINT8 EllipticCurveOid[][8] =
{
    {0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x03, 0x01, 0x07}
};

#ifndef X509_FOR_PSE_PR
const UINT8 CertificatePolicyOid[][9] =
{
    {0x2A, 0x86, 0x48, 0x86, 0xF8, 0x4d, 0x01, 0x09, 0x01}
};

const UINT8 CertificatePolicyQualifierIdOid[][8] =
{
    {0x2B, 0x06, 0x01, 0x05, 0x05, 0x07, 0x02, 0x01}
};

const UINT8 OcspResponseTypeOid[][9] =
{
    {0x2B, 0x06, 0x01, 0x05, 0x05, 0x07, 0x30, 0x01, 0x01}
};

const UINT8 ExtendedKeyUsageOcspSignOid[][8] =
{
    0x2b, 0x06, 0x01, 0x05, 0x05, 0x07, 0x03, 0x09
};
#endif

#ifndef X509_FOR_PSE_PR
/*
ParseOcspResponseChain           - Decodes a DER encoded X.509 OCSP response and makes a list of the serial numbers and hashes from the OCSP response.

@param  OcspRespBuffer        - If not NULL, contains the OCSP response. OCSP response contains a list of certificates with their current status (good/revoked)
@param  OcspRespBufferLength  - Total Length of the OCSP response
@param  OcspCertRootPublicKey -  Public key used to sign the first certificate in the chain. This is the root of trust. If NULL, Intel public key is used.
@param  OcspCertStatusTable -  Table containing interesting fields in the OCSP response which will be used to compare against the verifier certificate.
@param  NumberOfSingleResponses -  Number of single responses that the OCSP response has returned to us.
@param  Nonce -  Contains the Nonce that was sent. If nonce Exists, the OCSP response should have a nonce extension that contains the same values.


@retval X509_STATUS_SUCCESS        - The operation completed successfully.
@retval STATUS_INVALID_VERSION
@retval STATUS_UNSUPPORTED_ALGORITHM
@retval STATUS_ENCODING_ERROR
@retval STATUS_INVALID_ARGS
@retval STATUS_UNSUPPORTED_CRITICAL_EXTENSION
@retval STATUS_UNSUPPORTED_TYPE
*/
STATUS ParseOcspResponseChain( UINT8* OcspRespBuffer,
                              UINT32 OcspRespBufferLength,
                              UINT8* workBuffer,
                              UINT32 workBufferSize,
                              ISSUER_INFO* OcspCertRootPublicKey,
                              OCSP_CERT_STATUS_TABLE *OcspCertStatusTable,
                              UINT8* NumberOfSingleResponses,
                              SessMgrDataBuffer Nonce,
                              OCSP_REQ_TYPE OcspReqType,
                              BOOL UseFacsimileEpid)
{
    STATUS Status;
    UINT32 Length = 0;
    UINT32 OcspResponseLength = 0;
    UINT8 TableIndex = 0;
    SessMgrOcspSingleResponse *SingleResponse;
    int i;
    SessMgrOcspResponseFields OcspResponseFields;
    UINT8 EncodingBytes;   // number of bytes used for encoding into ASN DER format
    UINT8 *current_ptr = OcspRespBuffer;
    UINT8 *end_of_ocsp_response_chain = OcspRespBuffer + OcspRespBufferLength;

    // Workaround for Windows OCSP responder shortcoming. Loop through OCSP response
    // We will loop through each of the OCSP responses, verify the OCSP responder certificate and

    while(current_ptr < end_of_ocsp_response_chain){

        Status = DecodeLength(current_ptr + 1, end_of_ocsp_response_chain, &Length, &EncodingBytes);
        if(Status != X509_STATUS_SUCCESS){
            DBG_ASSERT(0);
            return X509_STATUS_ENCODING_ERROR;
        }
        OcspResponseLength = Length + EncodingBytes + 1;
        memset(workBuffer, 0, workBufferSize);
        memset(&OcspResponseFields, 0, sizeof(OcspResponseFields));

        Status = sessMgrParseOcspResponse( NULL, current_ptr, current_ptr + OcspResponseLength, workBuffer, workBufferSize, &OcspResponseFields);
        if(Status != X509_STATUS_SUCCESS){
            DBG_ASSERT(0);
            return X509_STATUS_ENCODING_ERROR;
        }

        Status = VerifyOcspResponseAttributes(NULL, &OcspResponseFields, NULL, Nonce, OcspReqType, UseFacsimileEpid);
        if(Status != X509_STATUS_SUCCESS){
            DBG_ASSERT(0);
            return X509_STATUS_OCSP_VERIFICATION_FAILED;
        }

        // copy the interesting data
        for(i=0;i<OcspResponseFields.numberOfSingleReponses;i++){
            SingleResponse = (SessMgrOcspSingleResponse *)(OcspResponseFields.allResponses) + i;

            Status = VerifyValidity(SingleResponse->thisUpdate, SingleResponse->nextUpdate);
            if(Status != X509_STATUS_SUCCESS){
                DBG_ASSERT(0);
                return X509_STATUS_OCSP_VERIFICATION_FAILED;
            }

            if(SingleResponse->ocspCertificateStatus == good){

                if(TableIndex == MAX_CERT_CHAIN_LENGTH){
                    DBG_ASSERT(0);
                    return X509_STATUS_OCSP_VERIFICATION_FAILED;
                }

                SESSMGR_MEMCPY_S(OcspCertStatusTable[TableIndex].serialNumber, sizeof(OcspCertStatusTable[TableIndex].serialNumber),
                    SingleResponse->serialNumber.buffer, SingleResponse->serialNumber.length);
                DBG_ASSERT(SingleResponse->serialNumber.length <= 255);
                OcspCertStatusTable[TableIndex].SerialNumberSize = (UINT8)SingleResponse->serialNumber.length;

                SESSMGR_MEMCPY_S(OcspCertStatusTable[TableIndex].issuerKeyHash, sizeof(OcspCertStatusTable[TableIndex].issuerKeyHash),
                    SingleResponse->issuerKeyHash.buffer, SingleResponse->issuerKeyHash.length);
                DBG_ASSERT(SingleResponse->issuerKeyHash.length <= 255);
                OcspCertStatusTable[TableIndex].issuerKeyHashSize = (UINT8)SingleResponse->issuerKeyHash.length;

                SESSMGR_MEMCPY_S(OcspCertStatusTable[TableIndex].issuerNameHash, sizeof(OcspCertStatusTable[TableIndex].issuerNameHash),
                    SingleResponse->issuerNameHash.buffer, SingleResponse->issuerNameHash.length);
                DBG_ASSERT(SingleResponse->issuerNameHash.length <= 255);
                OcspCertStatusTable[TableIndex].issuerNameHashSize = (UINT8)SingleResponse->issuerNameHash.length;

                OcspCertStatusTable[TableIndex].HashAlgo = SingleResponse->issuerIdentifierHashType;
                TableIndex++;
            }
        }

        current_ptr += OcspResponseLength;
    }

    if (current_ptr != end_of_ocsp_response_chain){
        DBG_ASSERT(0);
        return X509_STATUS_INVALID_ARGS;
    }

    *NumberOfSingleResponses = TableIndex;
    return STATUS_SUCCESS;
}
#endif


/*
ParseCertificateChain          - This function can
- parse a certificate chain and return the CertificateFields of all the last certificate (usually the certificate of interest)
- optionally take in root public key that was used to sign first certificate in the chain. If NULL, Intel public Key is used.
- optionally take in ocspRespBuffer. If ocspRespBuffer is not NULL, this function will parse the ocsp response cert, make a list of
certificates authenticated by OCSP responder, and use this list to verify if each certificate in the chain has been authenticated.
- optionally takes in the root public key used to sign the first certifcate  in the OCSP responders certificate.


@param  pCertChain - Pointer to the certificate chain. The first certificate in the chain is assumed to be Intel signed if root public key (arg 4) is NULL
@param  CertChainLength       - Length of the certificate chain
@param  certificateFields     - Data structure containing parsed output of the last certificate in the chain (usually the certificate of interest))
@param  RootPublicKey         - Public key used to sign the first certificate in the chain. This is the root of trust. If NULL, Intel public key is used.
@param  NumberOfSingleResponses - Number of single responses that the OCSP response has returned to us.
@param  OcspCertStatusTable   - Table containing interesting fields in the OCSP response which will be used to compare against the verifier certificate.
@param  VerifierCert          - Contains pointer and length of the VerifierCertificate. If non NULL, the final certificate in the chain is populated with this Data.
@param  CertType              - Indicates what type of certificate we are processing.Some checks are specific to certain types of certs.


@retval X509_STATUS_SUCCESS        - The operation completed successfully.
*/

/* Input:
pointer to buffer containing a chain of certificates
Total Length

Assumes the first certificate in the chain is signed by Intel
*/
STATUS ParseCertificateChain(UINT8 *pCertChain,
                             UINT32 CertChainLength,
                             SessMgrCertificateFields *certificateFields,
                             UINT8                    *CertWorkBuffer,
                             UINT32                   CertWorkBufferLength,
                             ISSUER_INFO              *RootPublicKey,
                             UINT8                    NumberOfSingleResponses,
                             OCSP_CERT_STATUS_TABLE   *OcspCertStatusTable,
                             CertificateType          CertType,
                             BOOL                     UseFacsimileEpid
                             )
{
    STATUS Status;
    UINT8 CertCount = 0;
    SessMgrEcdsaPublicKey ecdsa_pub_key;

    // This is the temp buffer used to store the issuer signing key to verify the next certificate in the chain.
    // This size of this buffer should be equal to the Max possible key size
    UINT8 TempSignKeyBuffer[200];
    SessMgrDataBuffer TempDataBuffer;
    UINT8 *pCert;
    UINT8 *pCertChainEnd;
    UINT32 CertLength = 0;
    UINT8 EncodingBytes;   // number of bytes used for encoding into ASN DER format
    int MaxChainLengthAllowed = 0xFF; // This is to enforce the PathLen Basic Constraints.
    UINT8 HashOut[SHA1_HASH_LEN] = {0};
    UINT8 *KeyBufPtr;
    CertificateLevel       CertLevel;

    ISSUER_INFO IssuerInfo;

#ifdef X509_FOR_PSE_PR
    UNUSED(NumberOfSingleResponses);
#endif

    if (pCertChain == NULL ||
        pCertChain + CertChainLength <= pCertChain ||
        CertWorkBuffer == NULL ||
        CertWorkBuffer + CertWorkBufferLength <= CertWorkBuffer)
    {
        return X509_STATUS_INVALID_ARGS;
    }
    memset(&IssuerInfo, 0 , sizeof(ISSUER_INFO));

    pCert = pCertChain;
    pCertChainEnd = pCertChain + CertChainLength;

    CertLevel = root;

    if(!RootPublicKey){
        // always use debug keys except for EPID group certs
#ifdef X509_FOR_PSE_PR
        ecdsa_pub_key.px = SerializedPublicKey;
        ecdsa_pub_key.py = SerializedPublicKey + 32;
#else
        if(gSessmgrCtx.FuseGidZero == FALSE){
            ecdsa_pub_key.px = INTEL_ECDSA_PUBKEY_PROD_BE;
            ecdsa_pub_key.py = INTEL_ECDSA_PUBKEY_PROD_BE + 32;
        }else{
            ecdsa_pub_key.px = INTEL_ECDSA_PUBKEY_DBG_BE;
            ecdsa_pub_key.py = INTEL_ECDSA_PUBKEY_DBG_BE + 32;
        }
#endif

        ecdsa_pub_key.eccParameter = curvePrime256v1;

        IssuerInfo.buffer = (UINT8 *)&ecdsa_pub_key;
        IssuerInfo.length = sizeof(ecdsa_pub_key);
        IssuerInfo.AlgoType = X509_ecdsa_with_SHA256;

    }else{
        // Not allowing user to pass their own root key.
        DBG_ASSERT(0);
        return X509_STATUS_INVALID_ARGS;
        // memcpy(&IssuerInfo, RootPublicKey, sizeof(IssuerInfo));
    }

    // Set this up for the hash.
    IssuerInfo.EncodedPublicKeyHashBuffer.buffer = HashOut;
    IssuerInfo.EncodedPublicKeyHashBuffer.length = SHA1_HASH_LEN;

    // For root key, populate the ISSUER_INFO data structure. Refer to data strucutre definition for more details.
    // use the temp sign key buffer for this purpose.
    memset(TempSignKeyBuffer, 0 , sizeof(TempSignKeyBuffer));
    TempDataBuffer.buffer = TempSignKeyBuffer;

    KeyBufPtr = TempSignKeyBuffer;
    *KeyBufPtr = 0x04;
    KeyBufPtr++;
    SESSMGR_MEMCPY_S(KeyBufPtr, sizeof(TempSignKeyBuffer) - 1, ecdsa_pub_key.px, ECDSA_KEY_ELEMENT_SIZE);
    KeyBufPtr += ECDSA_KEY_ELEMENT_SIZE;
    SESSMGR_MEMCPY_S(KeyBufPtr, sizeof(TempSignKeyBuffer) - 1 - ECDSA_KEY_ELEMENT_SIZE, ecdsa_pub_key.py, ECDSA_KEY_ELEMENT_SIZE);

    TempDataBuffer.length = ECDSA_KEY_SIZE + 1;   // +1 to reflect the addition of 0x04 in the beginning of the buffer.

    Status = CryptoCreateHash(CRYPTO_HASH_TYPE_SHA1,
        &TempDataBuffer,
        &IssuerInfo.EncodedPublicKeyHashBuffer,
        NULL,
        NULL,
        SINGLE_BLOCK);

    if(Status != STATUS_SUCCESS){
        DBG_ASSERT(0);
        return X509_STATUS_INTERNAL_ERROR;
    }

    while(pCert < pCertChainEnd)
    {

        /* certificate always starts with a sequence followed by length at offset 1. */
        CHECK_ID(*pCert, DER_ENCODING_SEQUENCE_ID);
        if ((pCert + 1) == pCertChainEnd) {
            break;
        }
        Status = DecodeLength(pCert + 1, pCertChainEnd, &CertLength, &EncodingBytes);
        if(Status != X509_STATUS_SUCCESS){
            DBG_ASSERT(0);
            return Status;
        }

        CertLength = CertLength + EncodingBytes + 1;

        if( (pCert + CertLength) >= pCertChainEnd){
            // if this is the last certificate in the chain, it is the leaf
            CertLevel = leaf;
        }

#ifdef WIN_TEST
        printf(" \n Max Chain Length %d \n",MaxChainLengthAllowed);
#endif

        // Check Basic Constraints Compliance
        if(MaxChainLengthAllowed <= 0 && CertLevel != leaf){
            // We have one more CA which is violating the basic constraints set by somebody. return error
            DBG_ASSERT(0);
            return X509_STATUS_BASIC_CONSTRAINTS_VIOLATION;
        }

        memset(certificateFields, 0, sizeof(SessMgrCertificateFields));
        memset(CertWorkBuffer, 0, CertWorkBufferLength);

        certificateFields->productType = invalidProductType;

        Status = sessMgrParseDerCert(NULL, pCert, pCert + CertLength, CertWorkBuffer, CertWorkBufferLength, certificateFields, &IssuerInfo, UseFacsimileEpid);
        if(Status != X509_STATUS_SUCCESS){
            DBG_ASSERT(0);
            return Status;
        }

        // First Certificate is always intel signed
        Status = VerifyBasicCertificateAttributes(pCert, CertWorkBuffer, certificateFields, &IssuerInfo, CertType, CertLevel, UseFacsimileEpid);
        if(Status != X509_STATUS_SUCCESS){
            DBG_ASSERT(0);
            return Status;
        }

        // Verifiation is required if OCSP table exists (even if empty), make sure the certificate has not been revoked
        if(OcspCertStatusTable){
            BOOL IntelSelfSignedRoot = false;
#ifdef X509_FOR_PSE_PR
            if(CertLevel == root && memcmp(certificateFields->EncodedSubjectPublicKey.buffer+1, SerializedPublicKey, sizeof(SerializedPublicKey)) == 0)
                IntelSelfSignedRoot = true;
#else
            if(CertLevel == root && memcmp(certificateFields->EncodedSubjectPublicKey.buffer+1, INTEL_ECDSA_PUBKEY_PROD_BE, sizeof(INTEL_ECDSA_PUBKEY_PROD_BE)) == 0)
                IntelSelfSignedRoot = true;
            else if(gSessmgrCtx.FuseGidZero && CertLevel == root && memcmp(certificateFields->EncodedSubjectPublicKey.buffer+1, INTEL_ECDSA_PUBKEY_DBG_BE, sizeof(INTEL_ECDSA_PUBKEY_DBG_BE)) == 0)
                IntelSelfSignedRoot = true;
#endif

            // Skip revocation status check for Intel self-signed root certificate
            if(!IntelSelfSignedRoot) {
#ifndef X509_FOR_PSE_PR
                Status = VerifyOcspRevocationStatus(certificateFields, NumberOfSingleResponses, OcspCertStatusTable, &IssuerInfo);
                if(Status != X509_STATUS_SUCCESS){
                    DBG_ASSERT(0);
                    return X509_STATUS_OCSP_FAILURE;
                }
#endif
            }
        }

        // Certificate has been verified. Everything is good.
        // if this is not the leaf, store the public key and algorithm type to use in next certificate signature verification

        if(CertLevel != leaf){
            memset(TempSignKeyBuffer, 0, sizeof(TempSignKeyBuffer));
            SESSMGR_MEMCPY_S(TempSignKeyBuffer, sizeof(TempSignKeyBuffer), certificateFields->subjectPublicKey.buffer, certificateFields->subjectPublicKey.length);

            // Clear the issuer info structure.
            memset(&IssuerInfo, 0 , sizeof(ISSUER_INFO));

            // This key is the issuer key for the next certificate. Populate the IssuerInfo
            IssuerInfo.buffer   = TempSignKeyBuffer;
            IssuerInfo.length   = certificateFields->subjectPublicKey.length;
            IssuerInfo.AlgoType = certificateFields->algorithmIdentifierForSignature;

            // Set this up for the hash.
            IssuerInfo.EncodedPublicKeyHashBuffer.buffer = HashOut;
            IssuerInfo.EncodedPublicKeyHashBuffer.length = SHA1_HASH_LEN;

            Status = CryptoCreateHash(CRYPTO_HASH_TYPE_SHA1,
                &certificateFields->EncodedSubjectPublicKey,
                &IssuerInfo.EncodedPublicKeyHashBuffer,
                NULL,
                NULL,
                SINGLE_BLOCK);

            if(Status != STATUS_SUCCESS){
                DBG_ASSERT(0);
                return X509_STATUS_INTERNAL_ERROR;
            }

            // We might have zero or more intermediate certificates
            CertLevel = intermediate;

            // Record and Verify Basic Path Len Constraints. Refer to RFC for details on Basic constrains path len extensions.
            // If PathLen constraint set by this CA is more constrained than the one enforced by the previous CA, update MaxChainLength
            if(certificateFields->basicConstraint.isBasicConstraintPresent && certificateFields->basicConstraint.pathLenConstraint < (UINT32)MaxChainLengthAllowed)
                MaxChainLengthAllowed = certificateFields->basicConstraint.pathLenConstraint;
            else
                MaxChainLengthAllowed--;
            IssuerInfo.CommonNameBuf.buffer = (UINT8 *)certificateFields->subject.commonName;
            IssuerInfo.CommonNameBuf.length = certificateFields->subject.commonNameSize;

            IssuerInfo.productType = certificateFields->productType;
        }

        pCert += CertLength;
        CertCount++;
    }

    if (pCert != pCertChainEnd) {
        DBG_ASSERT(0);
        return X509_STATUS_INVALID_ARGS;
    }

    return X509_STATUS_SUCCESS;

}

#ifndef X509_FOR_PSE_PR
/*
This function compares the serial number of the certificate with the list of certificates that the OCSP responder sent  us.
If found, Make sure the status of the certificate is not revoked.
*/
STATUS VerifyOcspRevocationStatus(SessMgrCertificateFields* certificateFields,
                                  UINT8 NumberofSingleResponses,
                                  OCSP_CERT_STATUS_TABLE* OcspCertStatusTable,
                                  ISSUER_INFO *IssuerInfo)
{

    UINT32 i;
    STATUS Status;
    STATUS VerificationStatus = X509_STATUS_OCSP_VERIFICATION_FAILED;
    SessMgrDataBuffer HashBuf;

    SessMgrDataBuffer  IssuerNameBuf;
    UINT8 HashOut[SHA1_HASH_LEN] = {0};

    HashBuf.buffer = HashOut;
    HashBuf.length = SHA1_HASH_LEN;

    IssuerNameBuf.buffer = (UINT8 *)certificateFields->issuer.DistinguishedName;
    IssuerNameBuf.length = certificateFields->issuer.DistinguishedNameSize;

    for (i=0;i<NumberofSingleResponses;i++){
        // Check serial number
        if(certificateFields->serialNumber.length != OcspCertStatusTable[i].SerialNumberSize ||
            memcmp(certificateFields->serialNumber.buffer, OcspCertStatusTable[i].serialNumber, OcspCertStatusTable[i].SerialNumberSize) != 0) {
                continue;
        }

        // Check hash key
        if(IssuerInfo->EncodedPublicKeyHashBuffer.length != OcspCertStatusTable[i].issuerKeyHashSize ||
            memcmp(IssuerInfo->EncodedPublicKeyHashBuffer.buffer, OcspCertStatusTable[i].issuerKeyHash, OcspCertStatusTable[i].issuerKeyHashSize) != 0){
                continue;
        }

        memset(HashBuf.buffer, 0, SHA1_HASH_LEN);

        Status = CryptoCreateHash(CRYPTO_HASH_TYPE_SHA1,
            &IssuerNameBuf,
            &HashBuf,
            NULL,
            NULL,
            SINGLE_BLOCK);
        if(Status != STATUS_SUCCESS){
            DBG_ASSERT(0);
            return X509_STATUS_INTERNAL_ERROR;
        }

        // Check issuer name
        if(SHA1_HASH_LEN != OcspCertStatusTable[i].issuerNameHashSize ||
            memcmp(HashBuf.buffer, OcspCertStatusTable[i].issuerNameHash, OcspCertStatusTable[i].issuerNameHashSize) != 0){
                continue;
        }

        // The certificate has been found in the OCSP response, break and return success
        VerificationStatus = X509_STATUS_SUCCESS;
        break;
    }

#ifdef WIN_TEST
    if(VerificationStatus == X509_STATUS_SUCCESS)
        printf("Ocsp revocation check passed ");
    else
        printf("\n OCSP revocation check failed ");
#endif

    DBG_ASSERT(VerificationStatus == X509_STATUS_SUCCESS);

    return VerificationStatus;

}
#endif

#ifndef X509_FOR_PSE_PR
/*
This function will accept the algorithm and the keys as a void pointer and will verify the keys accordingly.
*/

#ifndef WIN_TEST
#define SESSMGR_RSA_WORK_BUFFER_SIZE (ROM_RSA_WIN_EXP_1_BUFFER_SIZE + 2*(RSA_KEY_SIZE_2048_BYTES))
UINT8 RsaWorkBuffer[SESSMGR_RSA_WORK_BUFFER_SIZE];
#endif
#endif  // #ifndef X509_FOR_PSE_PR



STATUS VerifySignature(const ISSUER_INFO *IssuerInfo, const SessMgrDataBuffer *MsgBuffer, const SessMgrDataBuffer *SignBuffer, BOOL UseFacsimileEpid)
{
#ifdef X509_FOR_PSE_PR
    BOOL VerifRes = FALSE;
#else
#ifdef WIN_TEST
    CdgStatus Cstatus;
    CdgResult CResult;
    RSA *RsaKey;
    UINT8 Hash[32];
    int HashType;
#else
    BOOL VerifRes = FALSE;
    SessMgrDataBuffer LocalSignBuffer;
    SessMgrDataBuffer LocalMsgBuffer;
    ROM_RSA_DATA_BUFFER workBuffer;
    ROM_RSA_VERIFY_PARAMS RsaVerifyParams;
#endif

    UINT8 RsaEBuffer[RSA_E_SIZE] = {0, 0 ,0 ,0};
    UINT8 RsaNBuffer[RSA_KEY_SIZE_2048_BYTES] = {0};
#endif

    SessMgrEcdsaPublicKey *PublicKeyFromCert;
    PseEcdsaPublicKey EcdsaKey;
    G3Point* g3point;
#ifndef X509_FOR_PSE_PR
    UINT32 hashSize = 0;
    BOOL IsSignatureValid = FALSE;
    SessMgrRsaKey *RsaKeyFromCert;
#endif
    STATUS Status = X509_INVALID_SIGNATURE;

#ifdef X509_FOR_PSE_PR
    UNUSED(UseFacsimileEpid);
#endif

    do{
        switch(IssuerInfo->AlgoType){
        case X509_ecdsa_with_SHA1:
            Status = X509_STATUS_UNSUPPORTED_ALGORITHM;
            break;
        case X509_ecdsa_with_SHA256:

            PublicKeyFromCert = (SessMgrEcdsaPublicKey *)(IssuerInfo->buffer);
            SESSMGR_MEMCPY_S(EcdsaKey.px, sizeof(EcdsaKey.px), PublicKeyFromCert->px, 32);
            SESSMGR_MEMCPY_S(EcdsaKey.py, sizeof(EcdsaKey.py), PublicKeyFromCert->py, 32);

#ifndef WIN_TEST

            // Allocate DWORD aligned local buffers for Signature and Msg.

            // Swap Key and signature Convert Intel signature of the parameters certificate prior verifying it
            g3point = reinterpret_cast<G3Point*>(EcdsaKey.px);
            SwapEndian_32B(g3point->x);
            SwapEndian_32B(g3point->y);
            SwapEndian_32B(reinterpret_cast<G3Point*>(SignBuffer->buffer)->x);
            SwapEndian_32B(reinterpret_cast<G3Point*>(SignBuffer->buffer)->y);

#ifndef X509_FOR_PSE_PR
            void* pCtx = NULL;

            pCtx = UseFacsimileEpid ? gSessmgrCtx.KeysCtxFacsimile : gSessmgrCtx.KeysCtx;
#endif
            Status = SafeIdSigmaEcDsaVerifyPriv( pCtx,
                MsgBuffer->buffer,
                MsgBuffer->length,
                (unsigned char *)&EcdsaKey,
                (unsigned char *)SignBuffer->buffer,
                CRYPTO_HASH_TYPE_SHA256,
                0,
                32,
                &VerifRes);


            if(Status != STATUS_SUCCESS){
                DBG_ASSERT(0);
                Status = SESSMGR_STATUS_INTERNAL_ERROR;
                break;
            }

#ifndef X509_FOR_PSE_PR
            // Testing workaround: always allow production signed on SSKU part
            if(VerifRes == FALSE) {
                if(gSessmgrCtx.FuseGidZero)
                {
                    Status = SafeIdSigmaEcDsaVerifyPriv(UseFacsimileEpid ? gSessmgrCtx.KeysCtxFacsimile : gSessmgrCtx.KeysCtx,
                        MsgBuffer->buffer,
                        MsgBuffer->length,
                        INTEL_ECDSA_PUBKEY_PROD_LE,
                        (unsigned char *)SignBuffer->buffer,
                        CRYPTO_HASH_TYPE_SHA256,
                        0,
                        32,
                        &VerifRes);
                    DBG_ASSERT(Status == STATUS_SUCCESS);
                }
                if(VerifRes == FALSE) {
                    DBG_ASSERT(0);
                    return X509_INVALID_SIGNATURE;
                }
            }
#else
            if(VerifRes == FALSE) {
                return X509_INVALID_SIGNATURE;
            }
#endif

            // convert back
            g3point = reinterpret_cast<G3Point*>(EcdsaKey.px);
            SwapEndian_32B(g3point->x);
            SwapEndian_32B(g3point->y);
            SwapEndian_32B((reinterpret_cast<G3Point*>(SignBuffer->buffer))->x);
            SwapEndian_32B((reinterpret_cast<G3Point*>(SignBuffer->buffer))->y);
#else
            Cstatus =  MessageVerify( (unsigned char *)&EcdsaKey,
                sizeof(EcdsaKey),
                MsgBuffer->buffer,
                MsgBuffer->length,
                SignBuffer->buffer,
                SignBuffer->length,
                &CResult);
#ifdef TEMP_DISABLE_ECDSA_CHECK
            Status = X509_STATUS_SUCCESS;
#else

            if(CResult != CdgValid) {
                DBG_ASSERT(0);
                return X509_INVALID_SIGNATURE;
            }
            Status = X509_STATUS_SUCCESS;
#endif

            SetConsoleTextAttribute(hConsole, 11);
            printf("\n Signature Verified ");
            SetConsoleTextAttribute(hConsole, 8);
#endif
            break;

        case X509_sha1withRSAEncryption:
        case X509_sha256WithRSAEncryption:
#ifdef X509_FOR_PSE_PR
            Status = X509_STATUS_UNSUPPORTED_ALGORITHM;
#else
            RsaKeyFromCert = (SessMgrRsaKey *)(IssuerInfo->buffer);
            DBG_ASSERT(RsaKeyFromCert->n.length == RSA_KEY_SIZE_2048_BYTES);

            SESSMGR_MEMCPY_S(RsaEBuffer + sizeof(RsaEBuffer) - RsaKeyFromCert->e.length, RsaKeyFromCert->e.length, RsaKeyFromCert->e.buffer, RsaKeyFromCert->e.length);
            RsaKeyFromCert->e.buffer = RsaEBuffer;
            RsaKeyFromCert->e.length = RSA_E_SIZE;

            SESSMGR_MEMCPY_S(RsaNBuffer + RSA_KEY_SIZE_2048_BYTES - RsaKeyFromCert->n.length, RsaKeyFromCert->n.length, RsaKeyFromCert->n.buffer, RsaKeyFromCert->n.length);
            RsaKeyFromCert->n.buffer = RsaNBuffer;
            RsaKeyFromCert->n.length = RSA_KEY_SIZE_2048_BYTES;

#ifndef WIN_TEST
            workBuffer.length = SESSMGR_RSA_WORK_BUFFER_SIZE;
            workBuffer.buffer = RsaWorkBuffer;

            SESSMGR_MEM_ALLOC_BUFFER(LocalSignBuffer.buffer, TRUSTED_MEM, sizeof(UINT32), SignBuffer->length, TX_WAIT_FOREVER);
            SESSMGR_MEM_ALLOC_BUFFER(LocalMsgBuffer.buffer, TRUSTED_MEM, sizeof(UINT32), MsgBuffer->length, TX_WAIT_FOREVER);

            SESSMGR_MEMCPY_S(LocalSignBuffer.buffer, SignBuffer->length, SignBuffer->buffer, SignBuffer->length);
            LocalSignBuffer.length = SignBuffer->length;
            SESSMGR_MEMCPY_S(LocalMsgBuffer.buffer, MsgBuffer->length, MsgBuffer->buffer, MsgBuffer->length);
            LocalMsgBuffer.length = MsgBuffer->length;

            // Swap the Key and signature buffers. These are local buffers so no swapping back is necessary.
            SwapEndian(RsaKeyFromCert->e.buffer, RsaKeyFromCert->e.length);
            SwapEndian(RsaKeyFromCert->n.buffer, RsaKeyFromCert->n.length);
            SwapEndian(LocalSignBuffer.buffer, LocalSignBuffer.length);

            RsaVerifyParams.pMsgBuffer = (ROM_RSA_DATA_BUFFER*)&LocalMsgBuffer;
            RsaVerifyParams.pSignatureBuffer = (ROM_RSA_DATA_BUFFER*)&LocalSignBuffer;
            RsaVerifyParams.pbIsValid = &IsSignatureValid;
            RsaVerifyParams.CallbackAbortNow = NULL;
            RsaVerifyParams.pWorkBuffer = &workBuffer;
            RsaVerifyParams.HashFunc = (X509_sha1withRSAEncryption == IssuerInfo->AlgoType ? ROM_RSA_SCHEME_HASH_SHA1 : ROM_RSA_SCHEME_HASH_SHA256);

            Status = CryptoRsaPkcsVerify((RSA_IPP_KEY*)RsaKeyFromCert,
                FALSE,
                (RSA_VERIFY_PARAMS*)&RsaVerifyParams);


            if(LocalSignBuffer.buffer){
                SESSMGR_MEM_FREE(LocalSignBuffer.buffer)
                    LocalSignBuffer.length = 0;
            }

            if(LocalMsgBuffer.buffer){
                SESSMGR_MEM_FREE(LocalMsgBuffer.buffer)
                    LocalMsgBuffer.length = 0;
            }


            if ((Status != STATUS_SUCCESS) || (IsSignatureValid != TRUE)){
                DBG_ASSERT(0);
                return X509_INVALID_SIGNATURE;
            }

#else


            if(IssuerInfo->AlgoType == sha1withRSAEncryption){
                HashType = NID_sha1;
                hashSize = 20;
            }
            else if(IssuerInfo->AlgoType == sha256WithRSAEncryption){
                HashType = NID_sha256;
                hashSize = 32;
            }
            else{
                DBG_ASSERT(0);
                return X509_STATUS_UNSUPPORTED_ALGORITHM;
            }

            if(HashType == NID_sha1){
                SHA1(MsgBuffer->buffer, MsgBuffer->length, Hash);
            }else if(HashType == NID_sha256){
                SHA256(MsgBuffer->buffer, MsgBuffer->length, Hash);
            }else{
                DBG_ASSERT(0);
                return X509_STATUS_UNSUPPORTED_ALGORITHM;
            }

            /* copy key from cert to a local buffer */
            //       memcpy(RsaKey.Ebuffer, RsaKeyFromCert->e.buffer, RsaKeyFromCert->e.length);
            //       memcpy(RsaKey.Nbuffer, RsaKeyFromCert->n.buffer, RsaKeyFromCert->n.length);

            //       RsaKeyBuffers.e.buffer = RsaKey.Ebuffer;
            //       RsaKeyBuffers.e.length = RSA_E_SIZE;

            //       RsaKeyBuffers.n.buffer = RsaKey.Nbuffer;
            //       RsaKeyBuffers.n.length = RSA_KEY_SIZE_2048_BYTES;

            RsaKey = RSA_new();
            RsaKey->e= BN_bin2bn((UINT8*)RsaKeyFromCert->e.buffer,RsaKeyFromCert->e.length,RsaKey->e);
            RsaKey->n= BN_bin2bn((UINT8*)RsaKeyFromCert->n.buffer,RsaKeyFromCert->n.length, RsaKey->n);

            Status = RSA_verify(HashType,
                Hash, hashSize,
                SignBuffer->buffer, SignBuffer->length,
                RsaKey);
            if(Status != 1){
                DBG_ASSERT(0);
                return X509_INVALID_SIGNATURE;
            }

            Status = X509_STATUS_SUCCESS;
#endif
#endif  // #ifdef X509_FOR_PSE_PR

            break;
        default:
            assert(0);
        }

    }while(0);

    return Status;


}



#define CRYPTO_SIZE_SHA256  32

#ifndef X509_FOR_PSE_PR

STATUS VerifyOcspResponseAttributes(Uint8* OcspRespBuffer, SessMgrOcspResponseFields *ocspResponseFields, ISSUER_INFO* OcspCertRootPublicKey,
                                    SessMgrDataBuffer Nonce, OCSP_REQ_TYPE OcspReqType, BOOL UseFacsimileEpid)
{
    SessMgrCertificateFields certificateFields;
    STATUS Status;
    UINT32 workBufferSize = 1000;
    UINT8 *workBuffer = NULL;

    ISSUER_INFO IssuerInfo;
    SessMgrDataBuffer PubKeyHashBuf;
    UINT8 HashOut[SHA1_HASH_LEN] = {0};

    PubKeyHashBuf.buffer = HashOut;
    PubKeyHashBuf.length = SHA1_HASH_LEN;

    if(ocspResponseFields->ocspResponseStatus != successful){
        return X509_STATUS_OCSP_FAILURE;
    }

    // parse the ocsp certificate
    SESSMGR_MEM_ALLOC_BUFFER(workBuffer, MM_DATA_HEAP_SHARED_RW, sizeof(UINT32), workBufferSize, TX_WAIT_FOREVER);

    do {
        Status = ParseCertificateChain(ocspResponseFields->responderCertificate.buffer,
            ocspResponseFields->responderCertificate.length,
            &certificateFields,
            workBuffer,
            workBufferSize,
            NULL,
            0,
            NULL,
            OcspResponderCertificate,
            UseFacsimileEpid);
        if(Status != X509_STATUS_SUCCESS){
            DBG_ASSERT(0);
            break;
        }


        // verify OCSP response signature.
        IssuerInfo.AlgoType = ocspResponseFields->algorithmIdentifierForSignature;
        IssuerInfo.buffer = certificateFields.subjectPublicKey.buffer;
        IssuerInfo.length = certificateFields.subjectPublicKey.length;

        Status = VerifySignature(&IssuerInfo, &ocspResponseFields->tbsResponseData, &ocspResponseFields->signature, UseFacsimileEpid);
        if(Status != X509_STATUS_SUCCESS){
            DBG_ASSERT(0);
            Status = X509_INVALID_SIGNATURE;
            break;
        }

        // Verify Nonce only on Signed FW.
#ifndef _WIN32_DEVPLATFORM
#ifndef WIN_TEST
#ifndef X509_FOR_PSE_PR
        if(!(gManifestDataPtr->ManifestHeader.manifestFlags.r.debugManifest)){
#endif
            // Verify Nonce only for Non-cached Response.
            if(OcspReqType == NON_CACHED){
                // Make sure we have a nonce in the OCSP response
                if(!ocspResponseFields->nonce.buffer || Nonce.length <= 0){
                    DBG_ASSERT(0);
                    Status = X509_STATUS_OCSP_FAILURE;
                    break;
                }

                if( memcmp(ocspResponseFields->nonce.buffer, Nonce.buffer, Nonce.length) != 0 || (ocspResponseFields->nonce.length != Nonce.length) )
                {
                    DBG_ASSERT(0);
                    Status = X509_STATUS_OCSP_FAILURE;
                    break;
                }
            }
#ifndef X509_FOR_PSE_PR
        }
#endif
#endif
#endif

        // If ResponderId is a KeyHash, then verify its a Hash of the responders public key.
        if(ocspResponseFields->ocspResponderIdKeyHash.buffer){
            Status = CryptoCreateHash(CRYPTO_HASH_TYPE_SHA1,
                &certificateFields.EncodedSubjectPublicKey,
                &PubKeyHashBuf,
                NULL,
                NULL,
                SINGLE_BLOCK);

            if(Status != STATUS_SUCCESS){
                DBG_ASSERT(0);
                Status = X509_STATUS_INTERNAL_ERROR;
                break;
            }

            if(SHA1_HASH_LEN != ocspResponseFields->ocspResponderIdKeyHash.length ||
                memcmp(HashOut,ocspResponseFields->ocspResponderIdKeyHash.buffer, ocspResponseFields->ocspResponderIdKeyHash.length) != 0){
                    DBG_ASSERT(0);
                    Status = X509_STATUS_OCSP_FAILURE;
                    break;
            }
        }else{
            // If there is no Hash, There has to be a name. In the name structure, Common Name has to exist.
            if(!ocspResponseFields->ocspResponderIdName.commonName){
                DBG_ASSERT(0);
                Status = X509_STATUS_OCSP_FAILURE;
                break;
            }

            // if Responder Id is a Name, make sure the value matches the value in the certificate.
            if(ocspResponseFields->ocspResponderIdName.commonNameSize != certificateFields.subject.commonNameSize ||
                memcmp(ocspResponseFields->ocspResponderIdName.commonName, certificateFields.subject.commonName, certificateFields.subject.commonNameSize) != 0){
                    DBG_ASSERT(0);
                    Status = X509_STATUS_OCSP_FAILURE;
                    break;
            }
        }

#ifndef WIN_TEST
        // We have verified the OCSP response. See if we have trusted time. Else provision it.
        if(OcspReqType == NON_CACHED){
            Status = StoreTrustedTime(ocspResponseFields->producedAt);
            if(Status == STATUS_SUCCESS)
                gSessmgrCtx.TrustedTimeProvisioned = TRUE;
        }


        // If we have cached response, Make sure produced at was not more than a day
        if(OcspReqType == CACHED){
            Status = VerifyOcspCachedResponseValidity(ocspResponseFields);
            if(Status != X509_STATUS_SUCCESS){
                DBG_ASSERT(0);
                Status = X509_STATUS_OCSP_FAILURE;
                break;
            }
        }
#endif

#ifdef PRINT
        printf("\nOcsp response signature verified ");
        SetConsoleTextAttribute(hConsole, 2);
        printf("\n \n *************** VerifyOcspResponseAttributes complete ***************** \n \n");
        SetConsoleTextAttribute(hConsole, 8);
#endif

        Status = X509_STATUS_SUCCESS;
    } while(0);

    SESSMGR_MEM_FREE(workBuffer);

    return Status;
}
#endif // #ifndef X509_FOR_PSE_PR

STATUS VerifyBasicCertificateAttributes(const Uint8* certificateDerEncoded, const Uint8* workBuffer, const SessMgrCertificateFields* certificateFields,
                                        const ISSUER_INFO *IssuerInfo, const CertificateType CertType, CertificateLevel CertLevel, BOOL UseFacsimileEpid)
{

    SessMgrEcdsaPublicKey *EcdsaKey;

#ifdef X509_FOR_PSE_PR
    UNUSED(certificateDerEncoded);
    UNUSED(workBuffer);
    UNUSED(EcdsaKey);
    UNUSED(CertType);
    UNUSED(CertLevel);
    UNUSED(UseFacsimileEpid);
#endif

    // Make sure the signature Algorithms for issuer is the same as the one used in the TbsCertificate
    if(certificateFields->TbsCertSignAlgoId != certificateFields->algorithmIdentifierForSignature){
        DBG_ASSERT(0);
        return X509_STATUS_ENCODING_ERROR;
    }

#ifndef X509_FOR_PSE_PR  //PSE Usage doesn't need to check Cert expiration
    STATUS Status = X509_GENERAL_ERROR;
    // Verify if the certificate time is valid. At this point we expect trusted time to be set.
    /* Chicken and Egg problem: we get trusted time from OCSP. How do we check validity of OCSP responder certificate?
    Solution : Intel signs the OCSP responder cert and EPID group certs. its valid for a really long time. So for these
    certs, dont check validity.*/
#ifndef WIN_TEST
    if((CertType !=  EpidGroupCertificate) && (CertType != OcspResponderCertificate)){
        Status = VerifyValidity(certificateFields->notValidBeforeTime, certificateFields->notValidAfterTime);
        if(Status != X509_STATUS_SUCCESS){
            DBG_ASSERT(0);
            return X509_STATUS_EXPIRED_CERTIFICATE;
        }
    }
#endif

    Status = VerifySignature(IssuerInfo, &certificateFields->messageBuffer, &certificateFields->signatureBuffer, UseFacsimileEpid);
    if(Status != X509_STATUS_SUCCESS){
        DBG_ASSERT(0);
        return X509_INVALID_SIGNATURE;
    }
#endif

    // Common name and OrgName should be present.
    if(!certificateFields->issuer.commonName || !certificateFields->subject.commonName || !certificateFields->subject.organization){
        DBG_ASSERT(0);
        return X509_STATUS_ENCODING_ERROR;
    }

#if 0
    // The issuer of the root certificate has to be intel irrespective of the certificate type.
    if(CertLevel == root){
        if(certificateFields->issuer.commonNameSize != strlen("www.intel.com") ||
            memcmp(certificateFields->issuer.commonName, "www.intel.com", strlen("www.intel.com")) !=0){
                DBG_ASSERT(0);
                return X509_STATUS_ENCODING_ERROR;
        }
    }
#endif

    // Make sure the subject of the prev certificate is the issuer of the current certificate
    if(IssuerInfo->CommonNameBuf.buffer &&  IssuerInfo->CommonNameBuf.length > 0){
        if(certificateFields->issuer.commonNameSize != IssuerInfo->CommonNameBuf.length ||
            memcmp(certificateFields->issuer.commonName, IssuerInfo->CommonNameBuf.buffer, IssuerInfo->CommonNameBuf.length) != 0){
                DBG_ASSERT(0);
                return X509_STATUS_ENCODING_ERROR;
        }
    }

#ifndef X509_FOR_PSE_PR
    switch(CertType){

    case OcspResponderCertificate:

        if(certificateFields->keyUsage.value == 0){
            DBG_ASSERT(0);
            return X509_STATUS_ENCODING_ERROR;
        }

        // Make sure only the non-repudiation and digitalSignature are set.
        if(certificateFields->keyUsage.value != (X509_BIT0 | X509_BIT1)){
            DBG_ASSERT(0);
            return X509_STATUS_ENCODING_ERROR;
        }

        // ExtendedKeyUsage must be specified.
        if(certificateFields->ExtendedKeyUsage.value == 0){
            DBG_ASSERT(0);
            return X509_STATUS_ENCODING_ERROR;
        }

        // Basic constraints extension should be present.
        if(!certificateFields->basicConstraint.isBasicConstraintPresent){
            DBG_ASSERT(0);
            return X509_STATUS_ENCODING_ERROR;
        }

        // isCa should be deasserted. We will not delegate OCSP signing authority to anybody else.
        if(certificateFields->basicConstraint.isCa == DER_ENCODING_TRUE){
            DBG_ASSERT(0);
            return X509_STATUS_ENCODING_ERROR;
        }
        break;

    case VerifierCertificate:
        // Basic Constraint should be present.
        if(!certificateFields->basicConstraint.isBasicConstraintPresent){
            DBG_ASSERT(0);
            return X509_STATUS_ENCODING_ERROR;
        }

        // Make sure leaf certs dont have isCA set and intermediate Certs have isCa deasserted.
        if( (CertLevel == leaf && certificateFields->basicConstraint.isCa == DER_ENCODING_TRUE) ||
            (CertLevel != leaf && certificateFields->basicConstraint.isCa == DER_ENCODING_FALSE)){
                DBG_ASSERT(0);
                return X509_STATUS_ENCODING_ERROR;
        }

        if(certificateFields->algorithmIdentifierForSubjectPublicKey == X509_ecdsaPublicKey){
            // For ECDSA public key, we expect curvve to be prime256v1
            EcdsaKey = (SessMgrEcdsaPublicKey *)certificateFields->subjectPublicKey.buffer;
            if(EcdsaKey->eccParameter != curvePrime256v1){
                DBG_ASSERT(0);
                return X509_STATUS_ENCODING_ERROR;
            }
        }

        // For root and intermediate certificates, BIT5 (KeyCertSign) must be asserted
        if(CertLevel != leaf){
            if(certificateFields->keyUsage.value != X509_BIT5){
                DBG_ASSERT(0);
                return X509_STATUS_ENCODING_ERROR;
            }
        }else{
            if(certificateFields->keyUsage.value != (X509_BIT0 | X509_BIT1)){
                DBG_ASSERT(0);
                return X509_STATUS_ENCODING_ERROR;
            }
        }

        // Check for Subject Key Identifier. Per spec, all certificate except leaf should have this and should be equal to SHA1 of the public key.
        if(CertLevel != leaf){
            if(!certificateFields->SubjectKeyId.buffer || certificateFields->SubjectKeyId.length != SHA1_HASH_LEN ){
                DBG_ASSERT(0);
                return X509_STATUS_ENCODING_ERROR;
            }

            if(VerifySha1Hash(&certificateFields->EncodedSubjectPublicKey, certificateFields->SubjectKeyId.buffer , certificateFields->SubjectKeyId.length) == FALSE){
                DBG_ASSERT(0);
                return X509_STATUS_ENCODING_ERROR;
            }
        }

        // Every verifier cert should have an authority key id
        if(!certificateFields->AuthorityKeyId.buffer || (certificateFields->AuthorityKeyId.length != IssuerInfo->EncodedPublicKeyHashBuffer.length) ){
            DBG_ASSERT(0);
            return X509_STATUS_ENCODING_ERROR;
        }

        // Verify Authority Key Id. Spec says Authority Key ID of current cert should be equal to the SubjectKeyId of the upper cert. SubjectKeyId is nothing but the hash of the upper certs public key.
        // we have that available in this function. So compare Authority Key with that.
        if(certificateFields->AuthorityKeyId.length != IssuerInfo->EncodedPublicKeyHashBuffer.length ||
            memcmp(certificateFields->AuthorityKeyId.buffer, IssuerInfo->EncodedPublicKeyHashBuffer.buffer, IssuerInfo->EncodedPublicKeyHashBuffer.length) != 0){
                // if the first cert is signed by the prod Intel IVK & GID is 0, try again with hash of the prod Intel IVK

#ifndef X509_FOR_PSE_PR
                if (gSessmgrCtx.FuseGidZero){
#endif
                    UINT8 TempSignKeyBuffer[ECDSA_KEY_SIZE + 1] = {0};
                    SessMgrDataBuffer TempDataBuffer = {sizeof(TempSignKeyBuffer), TempSignKeyBuffer};
                    TempSignKeyBuffer[0] = 0x04;
                    SESSMGR_MEMCPY_S(TempSignKeyBuffer + 1, ECDSA_KEY_SIZE, INTEL_ECDSA_PUBKEY_PROD_BE, sizeof(INTEL_ECDSA_PUBKEY_PROD_BE));

                    Status = CryptoCreateHash(CRYPTO_HASH_TYPE_SHA1,
                        &TempDataBuffer,
                        &IssuerInfo->EncodedPublicKeyHashBuffer,
                        NULL,
                        NULL,
                        SINGLE_BLOCK);
                    if (Status != STATUS_SUCCESS) {
                        DBG_ASSERT(0);
                        return X509_STATUS_INTERNAL_ERROR;
                    }
                    if (certificateFields->AuthorityKeyId.length != IssuerInfo->EncodedPublicKeyHashBuffer.length ||
                        memcmp(certificateFields->AuthorityKeyId.buffer, IssuerInfo->EncodedPublicKeyHashBuffer.buffer, IssuerInfo->EncodedPublicKeyHashBuffer.length) != 0){
                            DBG_ASSERT(0);
                            return X509_STATUS_ENCODING_ERROR;
                    }
#ifndef X509_FOR_PSE_PR
                }
                else{
                    DBG_ASSERT(0);
                    return X509_STATUS_ENCODING_ERROR;
                }
#endif  // #ifdef X509_FOR_PSE_PR
        }

        // Product type must be present
        if(certificateFields->productType == invalidProductType || certificateFields->productType >= Max_ProductType){
            DBG_ASSERT(0);
            return X509_STATUS_ENCODING_ERROR;
        }

        // If issuer certificate product type exists, ensure it matches current cert
        if ((IssuerInfo->productType != invalidProductType) && (IssuerInfo->productType != certificateFields->productType)) {
            DBG_ASSERT(0);
            return X509_STATUS_ENCODING_ERROR;
        }

        break;
    }
#endif

#ifdef PRINT
    SetConsoleTextAttribute(hConsole, 4);
    printf("\n \n *************** VerifyBasicCertificateAttributes complete ***************** \n \n");
    SetConsoleTextAttribute(hConsole, 8);
#endif

    return X509_STATUS_SUCCESS;
}

#ifndef X509_FOR_PSE_PR
static BOOL VerifySha1Hash(SessMgrDataBuffer *HashData, UINT8 *Expectedhash, UINT32 ExpectedHashLength)
{

    UINT8 HashOut[SHA1_HASH_LEN] = {0};
    SessMgrDataBuffer HashBuf;
    STATUS Status;

    if(!HashData->buffer || HashData->length == 0)
        return FALSE;

    HashBuf.buffer = HashOut;
    HashBuf.length = SHA1_HASH_LEN;

    memset(HashBuf.buffer, 0 ,  HashBuf.length);

    Status = CryptoCreateHash(CRYPTO_HASH_TYPE_SHA1,
        HashData,
        &HashBuf,
        NULL,
        NULL,
        SINGLE_BLOCK);

    if(Status != STATUS_SUCCESS){
        DBG_ASSERT(0);
        return FALSE;
    }

    if(HashBuf.length != ExpectedHashLength ||
        memcmp(HashBuf.buffer, Expectedhash, ExpectedHashLength) != 0){
            return FALSE;
    }

    return TRUE;
}
#endif

#ifndef X509_FOR_PSE_PR  //NRG: not validating time in enclave

#ifndef WIN_TEST
STATUS VerifyValidity(SessMgrDateTime notValidBeforeTime, SessMgrDateTime NotValidAfterTime)
{

    STATUS Status;
    NTP_TIMESTAMP CurrentTime, NotValidBefore, NotValidAfter;

    // At this point, we expect trusted time to be provisioned. Else Error
    if(!gSessmgrCtx.TrustedTimeProvisioned){
        DBG_ASSERT(0);
        return X509_STATUS_INTERNAL_ERROR;
    }

    Status = PrtcGetTime(&CurrentTime, gSessmgrCtx.TrustedTime.RtcOffset);
    if(Status != STATUS_SUCCESS){
        DBG_ASSERT(0);
        return X509_STATUS_INTERNAL_ERROR;
    }

    // extract time from the certs and convert it into NTP. Get NotValidBefore  and NotValidAfter.
    // There is no day 0. We use that to find if the field has been populated. If not, ignore this check.
    if(notValidBeforeTime.date.yearMonthDay.day != 0){
        Status = ConvertTimeToNtp(notValidBeforeTime, &NotValidBefore);
        if(Status != STATUS_SUCCESS){
            DBG_ASSERT(0);
            return X509_STATUS_INTERNAL_ERROR;
        }

        if( CurrentTime.Seconds < NotValidBefore.Seconds - 120){
            DBG_ASSERT(0);
            return X509_STATUS_EXPIRED_CERTIFICATE;
        }
    }

    if(NotValidAfterTime.date.yearMonthDay.day != 0){
        Status = ConvertTimeToNtp(NotValidAfterTime, &NotValidAfter);
        if(Status != STATUS_SUCCESS){
            DBG_ASSERT(0);
            return X509_STATUS_INTERNAL_ERROR;
        }

        if(CurrentTime.Seconds >  NotValidAfter.Seconds){
            DBG_ASSERT(0);
            return X509_STATUS_EXPIRED_CERTIFICATE;
        }

    }

    return X509_STATUS_SUCCESS;
}

STATUS VerifyOcspCachedResponseValidity(SessMgrOcspResponseFields *OcspResponseFields)
{

    STATUS Status;
    NTP_TIMESTAMP CurrentTime = {0, 0};
    NTP_TIMESTAMP ProducedAt = {0, 0};

    // At this point, we expect trusted time to be provisioned. Else Error
    if(!gSessmgrCtx.TrustedTimeProvisioned){
        DBG_ASSERT(0);
        return X509_STATUS_INTERNAL_ERROR;
    }

    Status = PrtcGetTime(&CurrentTime, gSessmgrCtx.TrustedTime.RtcOffset);
    if(Status != STATUS_SUCCESS){
        DBG_ASSERT(0);
        return X509_STATUS_INTERNAL_ERROR;
    }

    // extract time from the certs and convert it into NTP. Get NotValidBefore  and NotValidAfter
    Status = ConvertTimeToNtp(OcspResponseFields->producedAt, &ProducedAt);
    if(Status != STATUS_SUCCESS){
        DBG_ASSERT(0);
        return X509_STATUS_INTERNAL_ERROR;
    }

#ifndef _WIN32_DEVPLATFORM // don't validate certificate time on DevPlatform
    // assuming no rollover

    if ((ProducedAt.Seconds > CurrentTime.Seconds) &&
        ((ProducedAt.Seconds - CurrentTime.Seconds) > OCSP_DELAY_TOLERANCE_SECONDS )){
            DBG_ASSERT(0);
            return X509_STATUS_INTERNAL_ERROR;
    }

    if ((CurrentTime.Seconds > ProducedAt.Seconds) &&
        ((CurrentTime.Seconds - ProducedAt.Seconds) > SECONDS_IN_DAY )){
            DBG_ASSERT(0);
            return X509_STATUS_EXPIRED_CERTIFICATE;
    }
#endif // _WIN32_DEVPLATFORM

    return X509_STATUS_SUCCESS;
}

#endif

#endif // #ifndef X509_FOR_PSE_PR

/*
This function will parse the certificate, will extract out interesting data (defined in the "Fields" structure) and return them to the caller.
*/
static STATUS sessMgrParseDerCert
    (
    IN  X509_PROTOCOL*            X509Protocol,
    IN  Uint8*                    certificateDerEncoded,
    IN  Uint8*                    pCertEnd,
    IN  Uint8*                    workBuffer,
    IN  UINT32                    workBufferSize,
    OUT SessMgrCertificateFields* certificateFields,
    IN  ISSUER_INFO *IssuerInfo,
    IN  BOOL UseFacsimileEpid
    )
{
    UINT32 Status;
    /* Refer to certificate structure for definition */
    Uint8* workBufferStart = workBuffer;
    UINT8* current_ptr = certificateDerEncoded;
    UINT8* pTemp;
    UINT32 length;
    UINT32 i = 0;
    SessMgrEllipticCurveParameter params = unknownParameter;
    UINT8 EncodingBytes;
    UINT32 padding;

#ifdef PRINT
    printf("\n **************** Parsing a Certificate ******************** ");
    for(i=0;i<certificateFields->serialNumber.length;i++)
        printf("%hhx ",*(certificateFields->serialNumber.buffer + i));
#endif

#ifdef X509_FOR_PSE_PR
    UNUSED(X509Protocol);
    UNUSED(i);
#endif

    do{
        Status = ParseIdAndLength(&current_ptr, pCertEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;

        certificateFields->messageBuffer.buffer = current_ptr;

        /* Start of TBS Certificate : Starts with a sequence */

        Status = ParseIdAndLength(&current_ptr, pCertEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;

        // Total signed buffer length = certificate Length + Encoding Bytes ( +1 is for the Type identifier)
        certificateFields->messageBuffer.length = length + EncodingBytes + 1;

        /*** Early Signature Verification ****/

        // Try to verify signature first. That way we resolve all corruption problems.
        pTemp = certificateFields->messageBuffer.buffer + certificateFields->messageBuffer.length;

        // Current pointer is at SignatureAlgorithm which is a algorithm identifier
        Status = ParseAlgoIdentifier(&pTemp, pCertEnd, (UINT32*)&certificateFields->TbsCertSignAlgoId, signature_algo, &params);
        if(Status != X509_STATUS_SUCCESS){
            DBG_ASSERT(0);
            break;
        }

        // Next field : SignatureValue
        Status = ParseSignatureValue(&pTemp, pCertEnd, &workBuffer, workBufferSize - (int)(workBuffer-workBufferStart), &certificateFields->signatureBuffer, certificateFields->TbsCertSignAlgoId);
        if(Status != X509_STATUS_SUCCESS){
            DBG_ASSERT(0);
            break;
        }

        Status = VerifySignature(IssuerInfo, &certificateFields->messageBuffer, &certificateFields->signatureBuffer, UseFacsimileEpid);
        if(Status != X509_STATUS_SUCCESS){
            DBG_ASSERT(0);
            return X509_INVALID_SIGNATURE;
        }

        /**** End Early Signature Verification *****/

        //  Next Field : Version (Optional field with explicit tagging)

        Status = ParseIdAndLength(&current_ptr, pCertEnd, EXPLICIT_TAG_0_ID_VALUE, &length, &EncodingBytes, TRUE);
        if( (Status != X509_STATUS_SUCCESS) && Status != X509_STATUS_NOT_FOUND)
            break;

        if(Status != X509_STATUS_NOT_FOUND){
            // We have a version number. Note: Not using ParseInteger function because certificateVersion is defined as a UINT32 and not a DataBuffer
            Status = ParseIdAndLength(&current_ptr, pCertEnd, DER_ENCODING_INTEGER_ID, &length, &EncodingBytes, FALSE);
            if(Status != X509_STATUS_SUCCESS || length > MAX_VERSION_LENGTH_SIZE_BYTES){
                Status = X509_STATUS_ENCODING_ERROR;
                break;
            }
            SESSMGR_MEMCPY_S((UINT8*)&certificateFields->certificateVersion + sizeof(UINT32) - length, length, current_ptr, length);
            // The certificates are big endian.
            SwapEndian((UINT8 *)&certificateFields->certificateVersion, sizeof(UINT32));

            if(certificateFields->certificateVersion != v3){
                Status = X509_STATUS_INVALID_VERSION;
                break;
            }

            current_ptr+= length;
        }else{
            /* Default is v1 according to spec . But we dont support onyl v3. Return error. */
            Status = X509_STATUS_INVALID_VERSION;
            break;
        }

        //  Next Field : Certificate Serial Number   Format : Integer Identifier + Length + Value (Max 20 bytes)
        Status = ParseInteger(&current_ptr, pCertEnd, &certificateFields->serialNumber, FALSE, TRUE, &padding);
        if ((Status != X509_STATUS_SUCCESS) || (certificateFields->serialNumber.length + padding > MAX_HASH_LEN)) {
            DBG_ASSERT(0);
            Status = X509_STATUS_ENCODING_ERROR;
            break;
        }

#ifdef PRINT
        printf("\n Serial Number ");
        for(i=0;i<certificateFields->serialNumber.length;i++)
            printf("%hhx ",*(certificateFields->serialNumber.buffer + i));
#endif

        // Next Field : Algorithm Identifier (signature)  The OID is expected to be one of the HardCodedSignatureAlgorithmOid array values.

        Status = ParseAlgoIdentifier(&current_ptr, pCertEnd, (UINT32*)&certificateFields->algorithmIdentifierForSignature,signature_algo, &params);
        if(Status != X509_STATUS_SUCCESS){
            Status = X509_STATUS_ENCODING_ERROR;
            break;
        }

        // Next Field : issuer
        Status = ParseName(&current_ptr, pCertEnd, &certificateFields->issuer);
        if(Status != X509_STATUS_SUCCESS){
            DBG_ASSERT(0);
            break;
        }

        //  Next Field : Validity  Format : sequence notBefore notAfter

        Status = ParseIdAndLength(&current_ptr, pCertEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;

        // Parse notBefore, notAfter, Subject, SubjectPublic key info

        Status = ParseTime(&current_ptr, pCertEnd, &certificateFields->notValidBeforeTime);
        if(Status != X509_STATUS_SUCCESS)
            break;

        Status = ParseTime(&current_ptr, pCertEnd, &certificateFields->notValidAfterTime);
        if(Status != X509_STATUS_SUCCESS)
            break;

        Status = ParseName(&current_ptr, pCertEnd, &certificateFields->subject);
        if(Status != X509_STATUS_SUCCESS)
            break;

        Status = ParseSubjectPublicKeyInfo(&current_ptr, pCertEnd, &workBuffer, certificateFields);
        if(Status != X509_STATUS_SUCCESS)
            break;

        // Next Field: IssuerUniqueId [optional] Implicit TAG Number is 1        Type : UniqueIdentifier (BIT STRING)
        Status = ParseIdAndLength(&current_ptr, pCertEnd, IMPLICIT_TAG_ID + TAG_NUMBER_ISSUER_UNIQUE_ID, &length, &EncodingBytes, TRUE);
        if(Status != X509_STATUS_SUCCESS && Status != X509_STATUS_NOT_FOUND)
            break;

        if(Status != X509_STATUS_NOT_FOUND){
            certificateFields->IssuerUniqueId.buffer = current_ptr;
            certificateFields->IssuerUniqueId.length = length;
            current_ptr += length;
        }

        // Next Field: SubjectUniqueId [optional] Implicit TAG Number is 2  Type : UniqueIdentifier (BIT STRING)
        Status = ParseIdAndLength(&current_ptr, pCertEnd, IMPLICIT_TAG_ID + TAG_NUMBER_SUBJECT_UNIQUE_ID, &length, &EncodingBytes, TRUE);
        if(Status != X509_STATUS_SUCCESS && Status != X509_STATUS_NOT_FOUND)
            break;

        if(Status != X509_STATUS_NOT_FOUND){
            certificateFields->SubjectUniqueId.buffer = current_ptr;
            certificateFields->SubjectUniqueId.length = length;
            current_ptr += length;
        }

        // Next Field: Extensions [optional]
        Status = ParseIdAndLength(&current_ptr, pCertEnd, EXPLICIT_TAG_ID + TAG_NUMBER_EXTENSIONS, &length, &EncodingBytes, TRUE);
        if(Status != X509_STATUS_SUCCESS && Status != X509_STATUS_NOT_FOUND)
            break;


        if(Status != X509_STATUS_NOT_FOUND){
#ifndef X509_FOR_PSE_PR
            Status = ParseCertExtensions(&current_ptr, current_ptr + length, certificateFields);
            if(Status != X509_STATUS_SUCCESS){
                DBG_ASSERT(0);
                break;
            }
#else
            current_ptr += length;
#endif
        }

        /* End of TbsCErtificate */

        // Current pointer is at SignatureAlgorithm which is a algorithm identifier
        Status = ParseAlgoIdentifier(&current_ptr, pCertEnd, (UINT32*)&certificateFields->TbsCertSignAlgoId, signature_algo, &params);
        if(Status != X509_STATUS_SUCCESS){
            DBG_ASSERT(0);
            break;
        }

        // Next field : SignatureValue
        Status = ParseSignatureValue(&current_ptr, pCertEnd, &workBuffer, workBufferSize - (int)(workBuffer - workBufferStart), &certificateFields->signatureBuffer, certificateFields->algorithmIdentifierForSignature);
        if(Status != X509_STATUS_SUCCESS){
            DBG_ASSERT(0);
            break;
        }
    }while(0);

    DBG_ASSERT((int)(workBuffer - workBufferStart) < workBufferSize)
        DBG_ASSERT(Status == X509_STATUS_SUCCESS);

    if (current_ptr != pCertEnd) {
        DBG_ASSERT(0);
        return X509_STATUS_ENCODING_ERROR;
    }

    return Status;
}

#ifndef X509_FOR_PSE_PR
STATUS sessMgrParseOcspResponse
    (
    IN  X509_PROTOCOL*            X509Protocol,
    IN  Uint8*                    OcspResponse,
    IN  Uint8*                    OcspResponseEnd,
    IN  Uint8*                    workBuffer,
    IN  UINT32                    workBufferSize,
    OUT SessMgrOcspResponseFields* OcspResponseFields
    )
{
    STATUS Status;
    Uint8* workBufferStart = workBuffer;
    UINT8* current_ptr = OcspResponse;
    UINT32 length;
    UINT32 temp;
    SessMgrOcspSingleResponse *SingleResponse;
    UINT8 *end_of_single_responses;
    SessMgrEllipticCurveParameter params;
    UINT8 EncodingBytes;
    UINT32 padding;

#ifdef WIN_TEST
    SetConsoleTextAttribute(hConsole, 4);
    printf("\n \n *************** Parsing OCSP response ***************** \n \n");
    SetConsoleTextAttribute(hConsole, 8);
#endif

    /* Ocsp response is a SEQ { responseStatus, response bytes }*/
    do{

        Status = ParseIdAndLength(&current_ptr, OcspResponseEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;

        // Next Field: response status  response status is a enumerated type
        Status = ParseIdAndLength(&current_ptr, OcspResponseEnd, DER_ENCODING_ENUMERATED_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS || length != 1){
            // Note: currently we support only 7 bits. So error out if length is more than 1 byte
            Status = X509_STATUS_ENCODING_ERROR;
            break;
        }

        OcspResponseFields->ocspResponseStatus = (ResponseStatus)*current_ptr;
        current_ptr += length;

        //  Next Field: response bytes Reponsne Bytes is a SEQ { response type , response }. response bytes is optional
        Status = ParseIdAndLength(&current_ptr, OcspResponseEnd, EXPLICIT_TAG_ID + TAG_NUMBER_RESPONSE_BYTES, &length, &EncodingBytes, TRUE);
        if(Status != X509_STATUS_SUCCESS && Status != X509_STATUS_NOT_FOUND)
            break;

        if(Status == X509_STATUS_NOT_FOUND){
            // No response bytes. Nothing more to parse.
            Status = X509_STATUS_SUCCESS;
            break;
        }

        /* WE HAVE A RESPONSE !!!! */

        Status = ParseIdAndLength(&current_ptr, OcspResponseEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;

        //  Next Field: responseType   Type : Object ID
        Status = ParseOID(&current_ptr, OcspResponseEnd, &temp, &OcspResponseTypeOid[0][0],
            sizeof(OcspResponseTypeOid)/sizeof(OcspResponseTypeOid[0]),
            sizeof(OcspResponseTypeOid[0]));
        if(Status != X509_STATUS_SUCCESS || temp != 0){
            DBG_ASSERT(0);
            Status = X509_STATUS_ENCODING_ERROR;
            break;
        }

        //  Next Field: response      Type : OCTET STRING
        Status = ParseIdAndLength(&current_ptr, OcspResponseEnd, DER_ENCODING_OCTET_STRING_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;

        // The only response type we support is basicOcspResponse. BasicOcspResponse is SEQ {tbsResponseData, SignatureAlgo, Signature, Certs [optional] }
        Status = ParseIdAndLength(&current_ptr, OcspResponseEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;

        // Next Field : tbsResponseData          Format : Seq { version [optional], ResponderId, ProducesdAt, responses, responseExtensions [optional]
        OcspResponseFields->tbsResponseData.buffer = current_ptr;

        Status = ParseIdAndLength(&current_ptr, OcspResponseEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;

        OcspResponseFields->tbsResponseData.length = length + EncodingBytes + 1;

        // We dont store the version anywhere. Just parse if present and move pointer to next type
        Status = ParseIdAndLength(&current_ptr, OcspResponseEnd, EXPLICIT_TAG_ID + TAG_NUMBER_VERSION, &length, &EncodingBytes, TRUE);
        if(Status != X509_STATUS_SUCCESS && Status != X509_STATUS_NOT_FOUND)
            break;

        if(Status != X509_STATUS_NOT_FOUND){
            // we have version. Just parse over it
            current_ptr += length;
        }

        // Next Field : ResponderId    ResponderId can be a choice of either Name or KeyHash. We distinguish them using the tags
        Status = ParseIdAndLength(&current_ptr, OcspResponseEnd, EXPLICIT_TAG_ID + TAG_NUMBER_RESPONDER_NAME, &length, &EncodingBytes, TRUE);
        if(Status != X509_STATUS_SUCCESS && Status != X509_STATUS_NOT_FOUND)
            break;

        if(Status != X509_STATUS_NOT_FOUND){
            Status = ParseName(&current_ptr, OcspResponseEnd, &OcspResponseFields->ocspResponderIdName);
            if(Status != X509_STATUS_SUCCESS)
                break;
        }else{
            Status = ParseIdAndLength(&current_ptr, OcspResponseEnd, EXPLICIT_TAG_ID + TAG_NUMBER_RESPONDER_KEYHASH, &length, &EncodingBytes, FALSE);
            if(Status != X509_STATUS_SUCCESS){
                /* We either need to have a Id or a Keyhash */
                Status = X509_STATUS_ENCODING_ERROR;
                break;
            }

            // KeyHash is a octet String
            Status = ParseIdAndLength(&current_ptr, OcspResponseEnd, DER_ENCODING_OCTET_STRING_ID, &length, &EncodingBytes, FALSE);
            if(Status != X509_STATUS_SUCCESS)
                break;
            OcspResponseFields->ocspResponderIdKeyHash.buffer = current_ptr;
            OcspResponseFields->ocspResponderIdKeyHash.length = length;
            current_ptr +=length;
        }

        /* Next Field : ProducedAt */
        Status = ParseTime(&current_ptr, OcspResponseEnd, &OcspResponseFields->producedAt);
        if(Status != X509_STATUS_SUCCESS)
            break;

#ifdef TIME_PRINT
        printf("\n Produced At ");
        PrintValidity(&OcspResponseFields->producedAt);
#endif

        // Next Field : response   Format : responses is a SEQ { single Responses }
        Status = ParseIdAndLength(&current_ptr, OcspResponseEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;

        end_of_single_responses = current_ptr + length;

        // Each single response is parsed and put into the SessMgrOcspSingleResponse structure. This structure is declared out of the work buffer
        SingleResponse = (SessMgrOcspSingleResponse *)workBuffer;
        OcspResponseFields->allResponses = (SessMgrOcspSingleResponse*) workBuffer;
        OcspResponseFields->numberOfSingleReponses = 0;

        while(current_ptr < end_of_single_responses){

            // update workbuffer pointer for future use
            workBuffer += sizeof(SessMgrOcspSingleResponse);

            //  Each single response is a SEQ { CertID, CertStatus, Thisupdate, nextUpdate [optional], SingleExtensions [optional]  */
            Status = ParseIdAndLength(&current_ptr, end_of_single_responses, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
            if(Status != X509_STATUS_SUCCESS)
                break;


            // current pointer is over CertId      CertId is a Seq {hash algorithm, issuername hash, issuerKeyHash, serialNumber}
            Status = ParseIdAndLength(&current_ptr, end_of_single_responses, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
            if(Status != X509_STATUS_SUCCESS)
                break;

            // current pointer over Hash algo        Hash algorithm is a Algorithm identifier
            Status = ParseAlgoIdentifier(&current_ptr, end_of_single_responses, (UINT32*)&SingleResponse->issuerIdentifierHashType,Hash_algo, &params);
            if(Status != X509_STATUS_SUCCESS)
                break;

            // Next Field: IssueNameHash           Type : octet string */
            Status = ParseIdAndLength(&current_ptr, end_of_single_responses, DER_ENCODING_OCTET_STRING_ID, &length, &EncodingBytes, FALSE);
            if(Status != X509_STATUS_SUCCESS)
                break;

            SingleResponse->issuerNameHash.buffer = current_ptr;
            SingleResponse->issuerNameHash.length = length;
            current_ptr+=length;

            // Next Field: IssueKeyHash   Type : octet string
            Status = ParseIdAndLength(&current_ptr, end_of_single_responses, DER_ENCODING_OCTET_STRING_ID, &length, &EncodingBytes, FALSE);
            if(Status != X509_STATUS_SUCCESS)
                break;

            SingleResponse->issuerKeyHash.buffer = current_ptr;
            SingleResponse->issuerKeyHash.length = length;
            current_ptr+=length;

            // Next Field: SerialNumber  Type : Integer */
            Status = ParseInteger(&current_ptr, end_of_single_responses, &SingleResponse->serialNumber, FALSE, TRUE, &padding);
            if(Status != X509_STATUS_SUCCESS || (SingleResponse->serialNumber.length + padding > MAX_HASH_LEN)){
                DBG_ASSERT(0);
                Status = X509_STATUS_ENCODING_ERROR;
                break;
            }

            // Next field:  CertStatus   certStatus can be a choice of { good [type : NULL], revoked [type : revoked info], unknown [type : NULL]
            switch(*current_ptr)
            {
            case IMPLICIT_TAG_ID + TAG_NUMBER_GOOD:
                SingleResponse->ocspCertificateStatus = good;

                current_ptr++;

                /* NULL id is always followed by length 0x00 */
                if(*current_ptr != 0)
                {
                    DBG_ASSERT(0);
                    return X509_STATUS_ENCODING_ERROR;
                }
                current_ptr++;
                break;

            case IMPLICIT_TAG_ID + TAG_NUMBER_UNKNOWN:

                SingleResponse->ocspCertificateStatus = unknown;
                current_ptr++;

                /* NULL id is always followed by length 0x00 */
                if(*current_ptr != 0)
                {
                    DBG_ASSERT(0);
                    return X509_STATUS_ENCODING_ERROR;
                }
                current_ptr++;
                break;

            case IMPLICIT_TAG_STRUCTURED_TYPE_ID + TAG_NUMBER_REVOKED:
                SingleResponse->ocspCertificateStatus = revoked;

                /* This will be followed by a revoked time and reason.
                We parse but ignore those fields
                */

                current_ptr++;

                /* Get length */
                Status = DecodeLength(current_ptr, end_of_single_responses, &length, &EncodingBytes);
                if(Status != X509_STATUS_SUCCESS)
                    break;

                current_ptr += EncodingBytes;



                /* Move current_ptr beyond the revoked data */
                current_ptr +=length;
                break;

            default:
                DBG_ASSERT(0);
                return X509_STATUS_ENCODING_ERROR;

            }

            if(Status != X509_STATUS_SUCCESS)
                break;

            // Next Field : ThisUpdate        type: GeneralizedTime
            Status = ParseTime(&current_ptr, end_of_single_responses, &SingleResponse->thisUpdate);
            if(Status != X509_STATUS_SUCCESS)
                break;

            /* Next Field : NextUpdate [optional] */

            Status = ParseIdAndLength(&current_ptr, end_of_single_responses, EXPLICIT_TAG_ID + TAG_NUMBER_NEXT_UPDATE, &length, &EncodingBytes, TRUE);
            if(Status != X509_STATUS_SUCCESS && Status != X509_STATUS_NOT_FOUND)
                break;

            if(Status != X509_STATUS_NOT_FOUND){
                Status = ParseTime(&current_ptr, end_of_single_responses, &SingleResponse->nextUpdate);
                if(Status != X509_STATUS_SUCCESS)
                    break;
            }


            /*   INTERESTING CASE: Both singleExtensions and responseExtensions use the tag [1]. So at this point, there is no way of finding out if "A1" means Single or response Extentions.
            We check the end of sequence pointer to resolve this.
            */
            if(current_ptr < end_of_single_responses){

                // Next Field : Single Extensions [optional]             we parse this but ignore the contents
                Status = ParseIdAndLength(&current_ptr, end_of_single_responses, EXPLICIT_TAG_ID + TAG_NUMBER_SINGLE_EXTENSIONS, &length, &EncodingBytes, TRUE);
                if(Status != X509_STATUS_SUCCESS && Status != X509_STATUS_NOT_FOUND)
                    break;

                if(Status != X509_STATUS_NOT_FOUND){
                    /* Move pointer past the extensions */
                    current_ptr +=length;
                }
            }

            SingleResponse = (SessMgrOcspSingleResponse *)((UINT8 *)SingleResponse + sizeof(SessMgrOcspSingleResponse));
            OcspResponseFields->numberOfSingleReponses++;
            Status = X509_STATUS_SUCCESS;
        }

        if(Status != X509_STATUS_SUCCESS){
            DBG_ASSERT(0);
            Status = X509_STATUS_ENCODING_ERROR;
            break;
        }

#ifdef PRINT
        int i;
        printf("\n Number of single responses %d", OcspResponseFields->numberOfSingleReponses);
        for (i=0;i<OcspResponseFields->numberOfSingleReponses;i++){
            printf("\n\n Response Number %d", i);
            SingleResponse = (SessMgrOcspSingleResponse *)(OcspResponseFields->allResponses) + i;
            printf("\n serial number ");
            PrintDataBuffer(&SingleResponse->serialNumber);
#ifdef TIME_PRINT
            printf("\n This Update ");
            PrintValidity(&SingleResponse->thisUpdate);
#endif
        }
#endif


        // Next Field : Response Extensions [optional]
        Status = ParseIdAndLength(&current_ptr, OcspResponseEnd, EXPLICIT_TAG_ID + TAG_NUMBER_RESPONSE_EXTENSIONS, &length, &EncodingBytes, TRUE);
        if(Status != X509_STATUS_SUCCESS && Status != X509_STATUS_NOT_FOUND)
            break;

        if(Status != X509_STATUS_NOT_FOUND){
            // Move pointer past the extensions
            Status = ParseOcspExtensions(&current_ptr, current_ptr + length, OcspResponseFields);
            if(Status != X509_STATUS_SUCCESS){
                break;
            }
        }

        // Next field : Signature Algorithm Id*/
        Status = ParseAlgoIdentifier(&current_ptr, OcspResponseEnd, (UINT32 *)&OcspResponseFields->algorithmIdentifierForSignature ,signature_algo, &params);
        if(Status != X509_STATUS_SUCCESS)
            break;

        Status = ParseSignatureValue(&current_ptr, OcspResponseEnd, &workBuffer, workBufferSize - (int)(workBuffer - workBufferStart), &OcspResponseFields->signature, OcspResponseFields->algorithmIdentifierForSignature);
        if(Status != X509_STATUS_SUCCESS)
            break;

        // Next field : Certificate of the OCSP responder. This can be a chain */
        // ??? why is this optional

        Status = ParseIdAndLength(&current_ptr, OcspResponseEnd, EXPLICIT_TAG_ID + TAG_NUMBER_CERTS, &length, &EncodingBytes, TRUE);
        if(Status != X509_STATUS_SUCCESS && Status != X509_STATUS_NOT_FOUND)
            break;

        if(Status != X509_STATUS_NOT_FOUND){

            /* This is a sequence of certificates.  */
            Status = ParseIdAndLength(&current_ptr, OcspResponseEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
            if(Status != X509_STATUS_SUCCESS)
                break;

            OcspResponseFields->responderCertificate.buffer = current_ptr;
            OcspResponseFields->responderCertificate.length = length;

            current_ptr += length;
        }
    }while(0);

    DBG_ASSERT(Status == X509_STATUS_SUCCESS);

    if (current_ptr != OcspResponseEnd) {
        DBG_ASSERT(0)
            return X509_STATUS_ENCODING_ERROR;
    }

    return Status;
}
#endif

/* Common functions */

#ifndef X509_FOR_PSE_PR
static STATUS ParseBoolean(UINT8 **ppCurrent, UINT8 *pEnd, BOOL* Value, BOOL optional)
{
    STATUS Status;
    UINT8 *current_ptr = *ppCurrent;
    UINT8 EncodingBytes;
    UINT32 length;

    Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_BOOLEAN_ID, &length, &EncodingBytes, optional);
    if(Status == X509_STATUS_NOT_FOUND && optional == TRUE)
        return X509_STATUS_NOT_FOUND;

    if(Status != X509_STATUS_SUCCESS || length != 1){
        Status = X509_STATUS_ENCODING_ERROR;
        return Status;
    }

    *Value = *current_ptr;
    current_ptr++;

    /* we expect Status to be 0xFF or 0x00 */
    if( (*Value != DER_ENCODING_TRUE) && (*Value != DER_ENCODING_FALSE))
        return X509_STATUS_ENCODING_ERROR;

    *ppCurrent = current_ptr;

    return X509_STATUS_SUCCESS;
}
#endif

/* ParseInteger will strip out the integer encoding part. If parsing is successful, ParseInteger
will update the current pointer and length

Returns X509_ENCODING_ERROR on failure

*/
static STATUS ParseInteger(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrDataBuffer* DataBuf, BOOL isOptional, BOOL MustBePositive, UINT32 *PaddingLen)
{
    STATUS Status;
    UINT8 *current_ptr = *ppCurrent;
    UINT32 Integer_Length;
    UINT8 EncodingBytes;
    UINT32 PaddingCounter = 0;

    Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_INTEGER_ID, &Integer_Length, &EncodingBytes, isOptional);

    if(isOptional == TRUE && Status == X509_STATUS_NOT_FOUND)
        return X509_STATUS_NOT_FOUND;

    if(Status != X509_STATUS_SUCCESS)
        return X509_STATUS_ENCODING_ERROR;

    // MSB must be zero for positive integer. Error if MSB is not zero and we expect positive
    if(MustBePositive && (*current_ptr & 0x80))
        return X509_STATUS_ENCODING_ERROR;

    // Note : DER Integer Encoding adds 0x00 if MSB of the actual integer is one. Ignore the first byte if it is 0x00
    if ((*current_ptr == 0) && (Integer_Length > 1)){
        current_ptr++;     // skip padding
        Integer_Length--;  // remove padding from length
        PaddingCounter++;
    }

    DataBuf->buffer = current_ptr;
    DataBuf->length = Integer_Length;
    *ppCurrent = current_ptr + Integer_Length;
    if (PaddingLen)
        *PaddingLen = PaddingCounter;

    return X509_STATUS_SUCCESS;
}

#ifndef X509_FOR_PSE_PR
STATUS ParseOcspExtensions(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrOcspResponseFields* OcspResponseFields)
{
    UINT8 *current_ptr = *ppCurrent;
    UINT32 ExtensionType;
    BOOL critical;
    UINT32 length;
    STATUS Status;
    UINT8 EncodingBytes;

    do{
        //  Extensions = SEQ { extension }      Each extension is associated with a Object ID. We support the extensions listed in ExtensionOID array.

        Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;

        while(current_ptr < pEnd){

            // Each extension is a SEQ { extid (OID) , critical (BOOLEAN), extnValue (OCTET STRING) }
            Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
            if(Status != X509_STATUS_SUCCESS)
                break;

            /* Next Field : Extension OID */
            Status = ParseOID(&current_ptr, pEnd, &ExtensionType, &OcspExtensionOid[0][0],
                sizeof(OcspExtensionOid)/sizeof(OcspExtensionOid[0]),
                sizeof(OcspExtensionOid[0]));

            // Note : We might have unsupported extensions.
            if(Status != X509_STATUS_SUCCESS && Status != X509_STATUS_UNKNOWN_OID){
                DBG_ASSERT(0);
                break;
            }

            // Next field is "Critical". This indicates whether it is mandatory for the parser to understand the extension. This  is an optinal field. default is false
            Status = ParseBoolean(&current_ptr, pEnd, &critical, TRUE);
            if(Status != X509_STATUS_SUCCESS && Status != X509_STATUS_NOT_FOUND)
                break;

            if(Status == X509_STATUS_NOT_FOUND)
                critical = DER_ENCODING_FALSE;

            // Next Field: Extn Value      This is a OCTET STRING
            Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_OCTET_STRING_ID, &length, &EncodingBytes, FALSE);
            if(Status != X509_STATUS_SUCCESS)
                break;

            switch(ExtensionType)
            {
            case Nonce:
                // Some OCSP responders have an additional encoding of OCTET string for the nonce.
                // We know the Nonce is 32 bytes (Per Sigma Spec. If given length is more than that, then check if this extra encoding was included.

                if(length > SIGMA_NONCE_LENGTH){
                    Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_OCTET_STRING_ID, &length, &EncodingBytes, FALSE);
                    if(Status != X509_STATUS_SUCCESS)
                        break;
                }

                DBG_ASSERT(length == SIGMA_NONCE_LENGTH);
                OcspResponseFields->nonce.buffer = current_ptr;
                OcspResponseFields->nonce.length = length;
                current_ptr+=length;
                break;

            default:
                /* unsupported extension.
                Check if it marked as critical. If so, return error else ignore this extension
                */

                if(critical == DER_ENCODING_TRUE){
                    DBG_ASSERT(0);
                    Status = X509_STATUS_UNSUPPORTED_CRITICAL_EXTENSION;
                    break;
                }
                else{
                    /* Extension is non-criticial. So it is ok if we dont support it .
                    Move current pointer to the next extension */
                    current_ptr += length;
                    continue;
                }
            }  // switch

            if(Status != X509_STATUS_SUCCESS){
                DBG_ASSERT(0);
                break;
            }

            Status = X509_STATUS_SUCCESS;

        }  // WHILE end of extensions
    }while(0); // do_while

    *ppCurrent = current_ptr;
    return Status;
}
#endif


#ifndef X509_FOR_PSE_PR
STATUS ParseCertExtensions(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrCertificateFields* certificateFields)
{
    UINT8 *current_ptr = *ppCurrent;
    UINT32 ExtensionType;
    BOOL critical;
    UINT32 length;
    STATUS Status;
    TAG_TYPE TagType;
    UINT8 EncodingBytes;
    UINT8 PaddedBits;
    SessMgrDataBuffer DataBuf;
    UINT32 ExtendedKeyUsageOcspType;

    do{

        //  Extensions = SEQ { extension }      Each extension is associated with a Object ID. We support the extensions listed in ExtensionOID array.

        Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;

        while(current_ptr < pEnd){

            // Each extension is a SEQ { extid (OID) , critical (BOOLEAN), extnValue (OCTET STRING) }
            Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
            if(Status != X509_STATUS_SUCCESS)
                break;

            /* Next Field : Extension OID */
            Status = ParseOID(&current_ptr, pEnd, &ExtensionType, &CertExtensionOid[0][0],
                sizeof(CertExtensionOid)/sizeof(CertExtensionOid[0]),
                sizeof(CertExtensionOid[0]));

            // Note : We might have unsupported extensions.
            if(Status != X509_STATUS_SUCCESS && Status != X509_STATUS_UNKNOWN_OID){
                DBG_ASSERT(0);
                break;
            }

            // Next field is "Critical". This indicates whether it is mandatory for the parser to understand the extension. This  is an optinal field. default is false
            Status = ParseBoolean(&current_ptr, pEnd, &critical, TRUE);
            if(Status != X509_STATUS_SUCCESS && Status != X509_STATUS_NOT_FOUND)
                break;

            if(Status == X509_STATUS_NOT_FOUND)
                critical = DER_ENCODING_FALSE;

            // Next Field: Extn Value      This is a OCTET STRING
            Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_OCTET_STRING_ID, &length, &EncodingBytes, FALSE);
            if(Status != X509_STATUS_SUCCESS)
                break;

            switch(ExtensionType)
            {
            case AuthorityKeyId:
                /* Authority ID is a SEQ of {KeyIdentifier [optional TAG 0] , General Names [optional Tag 1], Certificate Serial number [optional Tag 2] */
                /* Currently supported Intel and CA certs have general names and Certificate serial numbers as NULL */

                Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
                if(Status != X509_STATUS_SUCCESS)
                    break;

                /* See if optional arguments are present */

                // Note: InvalidTag means either the tag was not found (not an error if the field is optional) or the tag was not recognized.
                FIND_TAG_TYPE(current_ptr, TAG_NUMBER_AUTHORITY_KEY_ID, TagType);

                if(TagType != invalid_tag){
                    /* We have an implicit or explicit tag */
                    current_ptr++;

                    /* Get Length of sequence */
                    Status = DecodeLength(current_ptr, pEnd, &length, &EncodingBytes);
                    if(Status != X509_STATUS_SUCCESS)
                        break;
                    current_ptr += EncodingBytes;

                    if(TagType == explicit_tag){
                        Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_OCTET_STRING_ID, &length, &EncodingBytes, FALSE);
                        if(Status != X509_STATUS_SUCCESS)
                            break;
                    }

                    /* Store the Authority Key ID into the data buffer */
                    certificateFields->AuthorityKeyId.buffer = current_ptr;
                    certificateFields->AuthorityKeyId.length = length;
                    current_ptr+=length;
                }

                FIND_TAG_TYPE(current_ptr, TAG_NUMBER_AUTHORITY_CERT_ISSUER_ID, TagType);

                if(TagType != invalid_tag){
                    current_ptr++;

                    Status = DecodeLength(current_ptr, pEnd, &length, &EncodingBytes);
                    if(Status != X509_STATUS_SUCCESS)
                        break;
                    current_ptr += EncodingBytes;

                    if(TagType == explicit_tag){
                        /* Expecting a NULL. Encoding for NULL is 0x05 0x00 */
                        Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_NULL_ID, &length, &EncodingBytes, FALSE);
                        if(Status != X509_STATUS_SUCCESS || length !=0)
                            break;
                    }
                }

                FIND_TAG_TYPE(current_ptr, TAG_NUMBER_AUTHORITY_CERT_SERIAL_NUMBER_ID, TagType);

                if(TagType != invalid_tag){
                    current_ptr++;
                    Status = DecodeLength(current_ptr, pEnd, &length, &EncodingBytes);
                    if(Status != X509_STATUS_SUCCESS)
                        break;
                    current_ptr += EncodingBytes;

                    if(TagType == explicit_tag){
                        /* Expecting a NULL. Encoding for NULL is 0x05 0x00 */
                        Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_NULL_ID, &length, &EncodingBytes, FALSE);
                        if(Status != X509_STATUS_SUCCESS || length !=0)
                            break;
                    }
                }

                break;
            case SubjectKeyId:

                // Next Field: Extn Value      This is a OCTET STRING
                Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_OCTET_STRING_ID, &length, &EncodingBytes, FALSE);
                if(Status != X509_STATUS_SUCCESS)
                    break;

                /* Store the Subject Key ID into the data buffer */
                certificateFields->SubjectKeyId.buffer = current_ptr;
                certificateFields->SubjectKeyId.length = length;
                current_ptr+=length;
                break;

            case KeyUsage:
                /* KeyUsage is a bit string */
                Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_BIT_STRING_ID, &length, &EncodingBytes, FALSE);
                if(Status != X509_STATUS_SUCCESS)
                    break;

                /* KeyUsage is defined as a 16 bit value.So we expect the length to be 1 or 2. Add 1 to the check to account for the byte containing the number of bits paddd  */
                if(length != 2 && length != 3){
                    DBG_ASSERT(0);
                    Status = X509_STATUS_ENCODING_ERROR;
                    break;
                }

                /* current_ptr is pointing to the padded bits */
                PaddedBits = *current_ptr;
                current_ptr++;

                /* decrementing length by one because length included the padded bits octet that we have already parsed*/
                length--;

                if(length == 2){
                    certificateFields->keyUsage.value = *current_ptr;
                    /* Shift by 8 */
                    certificateFields->keyUsage.value = certificateFields->keyUsage.value << 8;
                    current_ptr++;
                }

                certificateFields->keyUsage.value = *current_ptr;
                current_ptr++;
                break;

            case ExtendedKeyUsage:
                /* ExtendedKeyUsage is a sequence of KeyPurposeId's  */

                Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
                if(Status != X509_STATUS_SUCCESS)
                    break;

                // KeyPurposeId is a object identifier
                Status = ParseOID(&current_ptr, pEnd, &ExtendedKeyUsageOcspType, &ExtendedKeyUsageOcspSignOid[0][0],
                    sizeof(ExtendedKeyUsageOcspSignOid)/sizeof(ExtendedKeyUsageOcspSignOid[0]),
                    sizeof(ExtendedKeyUsageOcspSignOid[0]));
                if(Status != X509_STATUS_SUCCESS)
                    break;

                /* we only support OcspSign in Extended key usage */
                if(ExtendedKeyUsageOcspType != 0){
                    DBG_ASSERT(0);
                    Status = X509_STATUS_UNSUPPORTED_TYPE;
                    break;
                }else{
                    certificateFields->ExtendedKeyUsage.usage.OCSPSign = 1;
                }

                break;

            case BasicConstraint:
                /* Basic constraint is a SEQ { BOOLEAN (default false) , Integer [optional] */

                certificateFields->basicConstraint.isBasicConstraintPresent = TRUE;

                Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
                if(Status != X509_STATUS_SUCCESS)
                    break;

                /* Next is a optional field for boolean */
                Status = ParseBoolean(&current_ptr, pEnd, &certificateFields->basicConstraint.isCa, TRUE);
                if(Status != X509_STATUS_SUCCESS && Status != X509_STATUS_NOT_FOUND)
                    break;

                if(Status == X509_STATUS_NOT_FOUND)
                    certificateFields->basicConstraint.isCa = DER_ENCODING_FALSE;

                // Next field is PathLenConstraint [Integer optional]
                Status = ParseInteger(&current_ptr, pEnd, &DataBuf, TRUE, FALSE, NULL);
                if(Status != X509_STATUS_SUCCESS && Status != X509_STATUS_NOT_FOUND )
                    break;

                if(Status == X509_STATUS_NOT_FOUND)
                    certificateFields->basicConstraint.pathLenConstraint = 0xFF;
                else{
                    Status = swapendian_memcpy((UINT8 *)&certificateFields->basicConstraint.pathLenConstraint,
                        sizeof(UINT32),
                        DataBuf.buffer, DataBuf.length);

                    if(Status != X509_STATUS_SUCCESS)
                        break;
                }

                Status = X509_STATUS_SUCCESS;

                break;

            case CertificatePolicy:
                Status = ParseCertificatePolicy(&current_ptr, pEnd, &certificateFields->CertificatePolicy);
                break;

            case ProductType:
                /* Product type is a enumerated type */
                Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_ENUMERATED_ID, &length, &EncodingBytes, FALSE);
                if(Status != X509_STATUS_SUCCESS || length != 1)
                {
                    Status = X509_STATUS_ENCODING_ERROR;
                    break;
                }

                certificateFields->productType = (SessMgrProductType)*current_ptr;
                current_ptr++;
                break;
            default:
                /* unsupported extension.
                Check if it marked as critical. If so, return error else ignore this extension
                */

                if(critical == DER_ENCODING_TRUE){
                    DBG_ASSERT(0);
                    Status = X509_STATUS_UNSUPPORTED_CRITICAL_EXTENSION;
                    break;
                }
                else{
                    /* Extension is non-criticial. So it is ok if we dont support it .
                    Move current pointer to the next extension */
                    current_ptr += length;
                    continue;
                }
            }  // switch

            if(Status != X509_STATUS_SUCCESS){
                DBG_ASSERT(0);
                break;
            }

            Status = X509_STATUS_SUCCESS;

        }  // WHILE end of extensions
    }while(0);

    DBG_ASSERT(Status == X509_STATUS_SUCCESS);

    *ppCurrent = current_ptr;
    return Status;
}


/* Certificate Policy is a SEQ of Policy Information.
Policy information is Seq of {Policy Identifier, Policy Qualifier [optional] }

Policy Identifier is a Object ID
Policy Qualifier Info is Seq {PolicyqualifierId, qualifier }

PolicyqualifierId is a known Object id
Qualifier is a CHOICE{cPSuri, UserNotice}

we only support cpSuri which is a IA5string
*/
static STATUS ParseCertificatePolicy(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrDataBuffer *CertificatePolicy)
{
    UINT8 *current_ptr = *ppCurrent;
    UINT32 length;
    UINT8* end_of_PolicyInformation = NULL;
    UINT32 temp;
    STATUS Status;
    UINT8 EncodingBytes;

    do{

        /* Certificate Policy is a SEQ of Policy Information */
        Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;

        end_of_PolicyInformation = current_ptr + length;

        while(current_ptr < end_of_PolicyInformation){

            /* Policy information is Seq of {Policy Identifier, Policy Qualifier [optional] */
            Status = ParseIdAndLength(&current_ptr, end_of_PolicyInformation, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
            if(Status != X509_STATUS_SUCCESS)
                break;

            // Policy Identifier is a Object ID
            Status = ParseOID(&current_ptr, end_of_PolicyInformation, &temp, &CertificatePolicyOid[0][0],
                sizeof(CertificatePolicyOid)/sizeof(CertificatePolicyOid[0]),
                sizeof(CertificatePolicyOid[0]));
            if(Status != X509_STATUS_SUCCESS)
                break;

            if(temp >= Max_Certificatepolicy){
                /* this is a unsupported policy. Ignore */
                current_ptr+= length;
                continue;
            }

            /* We have a supported policy !!!! */

            // Next Field: PolicyQualifiers    PolicyQualifiers is a SEQ of Policy Qualifier Info. However, we only support one Policy Qualifier Info
            Status = ParseIdAndLength(&current_ptr, end_of_PolicyInformation, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
            if(Status != X509_STATUS_SUCCESS)
                break;

            Status = ParseIdAndLength(&current_ptr, end_of_PolicyInformation, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
            if(Status != X509_STATUS_SUCCESS)
                break;

            Status = ParseOID(&current_ptr, end_of_PolicyInformation, &temp, &CertificatePolicyQualifierIdOid[0][0],
                sizeof(CertificatePolicyQualifierIdOid)/sizeof(CertificatePolicyQualifierIdOid[0]),
                sizeof(CertificatePolicyQualifierIdOid[0]));
            if(Status != X509_STATUS_SUCCESS)
                break;

            if(temp >= Max_Certificatepolicy){
                Status = X509_STATUS_ENCODING_ERROR;
                break;
            }

            // we have a supported policy qualifier id. Qualifier can be a CHOICE{cPSuri, UserNotice}.   Note : Parser only supports  cPSuri
            Status = ParseIdAndLength(&current_ptr, end_of_PolicyInformation, DER_ENCODING_IA5_STRING_ID, &length, &EncodingBytes, FALSE);
            if(Status != X509_STATUS_SUCCESS)
                break;

            CertificatePolicy->buffer = current_ptr;
            CertificatePolicy->length = length;
            current_ptr +=length;
        }
    }while(0);

    DBG_ASSERT(Status == X509_STATUS_SUCCESS);

    *ppCurrent = current_ptr;
    return Status;
}
#endif

static STATUS ParseSubjectPublicKeyInfo(UINT8 **ppCurrent, UINT8 *pEnd, UINT8 **pworkbuffer, SessMgrCertificateFields* certificateFields)
{
    UINT8 *current_ptr = *ppCurrent;
    UINT8 *workbuffer_ptr = *pworkbuffer;
    UINT32 length;
    SessMgrEllipticCurveParameter params = unknownParameter;
    STATUS Status;
    UINT8 EncodingBytes;
    SessMgrDataBuffer* Key;
    SessMgrPublicKeyAlgoType* KeyType;

    /* Subject public key consist of a Algo Identifier and a BIT String containing the actual key */

    Key = &certificateFields->subjectPublicKey;
    KeyType = &certificateFields->algorithmIdentifierForSubjectPublicKey;

    do{

        Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;

        /* We first get the algo identifier */
        Status = ParseAlgoIdentifier(&current_ptr, pEnd, (UINT32*)KeyType, PublicKey_algo, &params);
        if(Status != X509_STATUS_SUCCESS)
            break;

        /* Next is the Subject Public Key. It is a BIT string. */
        Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_BIT_STRING_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;


        /* current_ptr is over the number of padding bits for the BIT string . We expect this to always be zero since we are not dealing with bit level data */
        if(*current_ptr != 0x00){
            DBG_ASSERT(0);
            return X509_STATUS_ENCODING_ERROR;
        }

        current_ptr++;

        certificateFields->EncodedSubjectPublicKey.buffer = current_ptr;
        certificateFields->EncodedSubjectPublicKey.length = length - 1;

        switch(*KeyType)
        {

        case X509_ecdsaPublicKey:

            // for ecdsa we know the length should be 66 bytes.
            if(length != 66){
                Status = X509_STATUS_ENCODING_ERROR;
                break;
            }

            Key->buffer = workbuffer_ptr;
            Key->length = sizeof(SessMgrEcdsaPublicKey);
            workbuffer_ptr += sizeof(SessMgrEcdsaPublicKey);
            Status = ParseEcdsaPublicKey(&current_ptr, pEnd, (SessMgrEcdsaPublicKey * )(Key->buffer), (SessMgrEllipticCurveParameter)params);
            break;

        case X509_intel_sigma_epidGroupPublicKey_epid11:
            Key->buffer = workbuffer_ptr;
            Key->length = sizeof(SessMgrEpidGroupPublicKey);
            workbuffer_ptr += sizeof(SessMgrEpidGroupPublicKey);
            Status = ParseEpidPublicKey(&current_ptr, pEnd, (SessMgrEpidGroupPublicKey * )(Key->buffer));
#ifdef PRINT
            PrintEpidKey((void *)Key->buffer);
#endif
            break;

        case X509_rsaPublicKey:
            Key->buffer = workbuffer_ptr;
            Key->length = sizeof(SessMgrRsaKey);
            workbuffer_ptr += sizeof(SessMgrRsaKey);
            Status = ParseRsaPublicKey(&current_ptr, pEnd, (SessMgrRsaKey * )(Key->buffer));
            break;

        default:
            DBG_ASSERT(0);
        }
    }while(0);


    if(Status == X509_STATUS_SUCCESS){
        *pworkbuffer = workbuffer_ptr;
        *ppCurrent = current_ptr;
    }

    DBG_ASSERT(Status == X509_STATUS_SUCCESS);

    return Status;
}


static STATUS ParseRsaPublicKey(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrRsaKey * RsaKey)
{
    UINT8 *current_ptr = *ppCurrent;
    UINT32 length;
    STATUS Status;
    UINT8 EncodingBytes;

    /* The data portion of the BIT string contains a sequence of Seq { n , e} where n and e are integers */
    Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
    if(Status != X509_STATUS_SUCCESS){
        DBG_ASSERT(0);
        return X509_STATUS_ENCODING_ERROR;
    }

    Status = ParseInteger(&current_ptr, pEnd, &RsaKey->n, FALSE, FALSE, NULL);
    if(Status != X509_STATUS_SUCCESS)
        return X509_STATUS_ENCODING_ERROR;

    Status = ParseInteger(&current_ptr, pEnd, &RsaKey->e, FALSE, FALSE, NULL);
    if(Status != X509_STATUS_SUCCESS)
        return X509_STATUS_ENCODING_ERROR;

    *ppCurrent = current_ptr;
    return X509_STATUS_SUCCESS;
}


static STATUS ParseEpidPublicKey(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrEpidGroupPublicKey * EpidKey)
{
    UINT8 *current_ptr = *ppCurrent;
    UINT32 length;
    SessMgrDataBuffer DataBuf;
    STATUS Status;
    UINT8 EncodingBytes;

    do{

        /* We are expecting a sequence of {groupId [Integer], h1 [ECPoint], h2 [ECPoint], w [G2ECPoint] */
        Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;

        /* Parse Integer */
        Status = ParseInteger(&current_ptr, pEnd, &DataBuf, FALSE, FALSE, NULL);
        if(Status != X509_STATUS_SUCCESS)
            break;

        Status = swapendian_memcpy((UINT8 *)&EpidKey->groupId, sizeof(UINT32), DataBuf.buffer, DataBuf.length);
        if(Status != X509_STATUS_SUCCESS)
            break;

        // Next Field : h1 [octet string]    This is a ECPoint which is a octet string
        Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_OCTET_STRING_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;

        /* Make sure it has the first bype as 0x04 indicating key is uncompressed  */
        if(*current_ptr != 0x04){
            Status = X509_STATUS_ENCODING_ERROR;
            break;
        }
        current_ptr++;

        EpidKey->h1x = current_ptr;
        EpidKey->h1y = current_ptr + 32;
        current_ptr += 64;

        // Next Field : h2 [octet string]  This is a ECPoint which is a octet string
        Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_OCTET_STRING_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;

        /* Make sure it has the first bype as 0x04 indicating key is uncompressed  */
        if(*current_ptr != 0x04){
            Status = X509_STATUS_ENCODING_ERROR;
            break;
        }
        current_ptr++;

        EpidKey->h2x = current_ptr;
        EpidKey->h2y = current_ptr + 32;
        current_ptr += 64;

        // Next Field : w [octet string]  This is a ECPoint which is a octet string
        Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_OCTET_STRING_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;

        /* Make sure it has the first bype as 0x04 indicating key is uncompressed  */
        if(*current_ptr != 0x04){
            Status = X509_STATUS_ENCODING_ERROR;
            break;
        }
        current_ptr++;

        EpidKey->wx0 = current_ptr;
        EpidKey->wx1 = current_ptr + 32;
        EpidKey->wx2 = current_ptr + 64;
        EpidKey->wy0 = current_ptr + 96;
        EpidKey->wy1 = current_ptr + 128;
        EpidKey->wy2 = current_ptr + 160;
        current_ptr += 192;
    }while(0);

    *ppCurrent = current_ptr;
    return Status;
}


static STATUS ParseEcdsaPublicKey(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrEcdsaPublicKey * EcDsaKey, SessMgrEllipticCurveParameter params)
{
    UINT8 *current_ptr = *ppCurrent;
    STATUS Status = X509_STATUS_SUCCESS;

#ifdef X509_FOR_PSE_PR
    UNUSED(pEnd);
#endif

    do{

        /* Ecdsa Public Key is a Octet string. The SubjectPublicKey in the X509 definitiion is a bit string.
        We use the following logic to convert the Octet String as a bit string as per the Verifier Cert Spec defined by Xiaoyu. */


        /* First octet of bit string is 0x04 indicating key is uncompressed.
        Note: The Key is in OCTET String form and we convert it to BIT STRING.        Format : 0x04 | 64-bytes of keys    */

        if(*current_ptr != 0x04){
            Status = X509_STATUS_ENCODING_ERROR;
            break;
        }
        current_ptr++;

        EcDsaKey->px = current_ptr;
        EcDsaKey->py = current_ptr + ECDSA_KEY_ELEMENT_SIZE;
        current_ptr += ECDSA_KEY_SIZE;
        EcDsaKey->eccParameter = params;
    }while(0);

    DBG_ASSERT(Status == X509_STATUS_SUCCESS);

    *ppCurrent = current_ptr;
    return Status;
}


/* We will never send an OID to the caller. All the OID are matched to a corresponding enumerated type and sent to the caller. */
static STATUS ParseOID(UINT8 **ppCurrent, UINT8 *pEnd, UINT32 *EnumVal, const UINT8 *OidList, UINT32 Max_Entries, UINT32 EntrySize )
{
    UINT8 *current_ptr = *ppCurrent;
    UINT32 length;
    UINT32 i;
    STATUS Status;
    UINT8 EncodingBytes;

    do{
        Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_OBJECT_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;

        /* We have a encoded list of hard coded OIDs that we support in an array. Just compare the OID with that array. */
        for(i = 0;i< Max_Entries; i++)
        {
            if(memcmp(current_ptr, OidList, length) == 0){
                /* We found a match. i is the algorithm number that  the caller is looking for */
                *EnumVal= i;
                Status = X509_STATUS_SUCCESS;
                break;
            }
            OidList+=EntrySize;
        }

        if(i >= Max_Entries) {
            /* Never found a match */
            *EnumVal= i;
            Status = X509_STATUS_UNKNOWN_OID;
        }

        current_ptr += length;
    }while(0);

    DBG_ASSERT(Status == X509_STATUS_SUCCESS || Status == X509_STATUS_UNKNOWN_OID);
    *ppCurrent = current_ptr;
    return Status;
}

static STATUS ParseSignatureValue(UINT8 **ppCurrent, UINT8 *pEnd, UINT8 **pworkbuffer, UINT32 WorkBufferSize, SessMgrDataBuffer *SignatureValueBuf, UINT8 SignatureAlgoId)
{
    UINT8 *current_ptr = *ppCurrent;
    UINT32 length;
    UINT8 *workbuffer_ptr;
    STATUS Status;
    SessMgrDataBuffer DataBuf;
    UINT8 EncodingBytes;

#ifdef X509_FOR_PSE_PR
    UNUSED(WorkBufferSize);
#endif

    workbuffer_ptr = *pworkbuffer;

    do{
        /* SignatureValue is a BIT string. The content within the BIT string is based on the signature algorithm */
        Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_BIT_STRING_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS )
            break;

        /* Next Byte is the number of padded. Because this is a signature, we expect the resulting value to be a multiple of 8 bits meaning no padding will be necessery    */

        if(*current_ptr != 0){
            Status = X509_STATUS_ENCODING_ERROR;
            break;
        }

        //
        // the inc below requires this (i'm choosing to care about **read** buffer overflows)
        //
        if ((current_ptr + 1) >= pEnd) {
            Status = X509_STATUS_ENCODING_ERROR;
            break;
        }

        /* increment current pointer. decrement length to reflect size of actual bit string excluding the padding data*/
        current_ptr++;
        length--;

        /* Rest of the buffer is parsed based on the algo */

        switch(SignatureAlgoId)
        {
            /* ECDSA based sign algorithm */
        case X509_ecdsa_with_SHA1:
        case X509_ecdsa_with_SHA256:
            /* For ECDSA, Signature value is represented as a SEQ {r, s} where r and s are integers. We use the workbuffer to store this */

            /* First memset the buffers where keys will be copied to */
            memset(workbuffer_ptr, 0, ECDSA_SIGNATURE_SIZE);

            Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
            if(Status != X509_STATUS_SUCCESS)
                break;

            /* Parse R and S */
            Status = ParseInteger(&current_ptr, pEnd, &DataBuf, FALSE, FALSE, NULL);
            if(Status != X509_STATUS_SUCCESS || DataBuf.length > ECDSA_SIGNATURE_MAX_SIZE_R ){
                Status = X509_STATUS_ENCODING_ERROR;
                break;
            }
            SESSMGR_MEMCPY_S(workbuffer_ptr + (ECDSA_SIGNATURE_MAX_SIZE_R - DataBuf.length), WorkBufferSize - (workbuffer_ptr - *pworkbuffer), DataBuf.buffer, DataBuf.length);
            workbuffer_ptr += ECDSA_SIGNATURE_MAX_SIZE_R;

            Status = ParseInteger(&current_ptr, pEnd, &DataBuf, FALSE, FALSE, NULL);
            if(Status != X509_STATUS_SUCCESS  || DataBuf.length > ECDSA_SIGNATURE_MAX_SIZE_S){
                Status = X509_STATUS_ENCODING_ERROR;
                break;
            }
            SESSMGR_MEMCPY_S(workbuffer_ptr + (ECDSA_SIGNATURE_MAX_SIZE_S - DataBuf.length), WorkBufferSize - (workbuffer_ptr - *pworkbuffer), DataBuf.buffer, DataBuf.length);
            workbuffer_ptr += ECDSA_SIGNATURE_MAX_SIZE_S;
            break;

            /* All the RSA algorithms */
        case X509_sha1withRSAEncryption:
        case X509_sha256WithRSAEncryption:
            /* Assuming here that all RSA algorithms just have a bit string signaure   */

            if(length > RSA_SIGNATURE_SIZE){
                Status = X509_STATUS_ENCODING_ERROR;
                break;
            }
            memset(workbuffer_ptr, 0 , length);
            SESSMGR_MEMCPY_S(workbuffer_ptr, WorkBufferSize - (workbuffer_ptr - *pworkbuffer), current_ptr, length);
            workbuffer_ptr += length;
            current_ptr +=length;
            break;

        default:
            DBG_ASSERT(0);
            Status = X509_STATUS_INVALID_ARGS;
        }

    }while(0);



    if(Status == X509_STATUS_SUCCESS){
        // Signature value has been parsed successfully. Store the offset in the workbuffer in the DataBuffer that will be passed back to the caller
        SignatureValueBuf->buffer = *pworkbuffer;
        // length is the amount of work buffer we have used up
        SignatureValueBuf->length = static_cast<Uint32>(workbuffer_ptr - *pworkbuffer);
    }else{
        DBG_ASSERT(0);
    }

    *pworkbuffer = workbuffer_ptr;
    *ppCurrent = current_ptr;
    return Status;
}


static STATUS ParseAlgoIdentifier(UINT8 **ppCurrent, UINT8 *pEnd, UINT32* algoId, AlgorithmTypes Type, SessMgrEllipticCurveParameter *params)
{
    /*****
    Format : Sequence Id + length + Object Id + length + OID value + parameters (optional)
    *****/

    UINT8 *current_ptr = *ppCurrent;
    UINT32 length;
    UINT8  *end_of_sequence;
    STATUS Status;
    UINT8 EncodingBytes;

    Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
    if(Status != X509_STATUS_SUCCESS){
        DBG_ASSERT(0);
        return X509_STATUS_ENCODING_ERROR;
    }

    end_of_sequence =  current_ptr + length;

    switch(Type){
    case signature_algo:
        Status = ParseOID(&current_ptr, pEnd, algoId, &HardCodedSignatureAlgorithmOid[0][0],
            static_cast<uint32_t>(sizeof(HardCodedSignatureAlgorithmOid)/sizeof(HardCodedSignatureAlgorithmOid[0])),
            sizeof(HardCodedSignatureAlgorithmOid[0]));
        break;

    case PublicKey_algo:
        Status = ParseOID(&current_ptr, pEnd, algoId, &HardCodedPublicKeyAlgorithmOid[0][0],
            static_cast<uint32_t>(sizeof(HardCodedPublicKeyAlgorithmOid)/sizeof(HardCodedPublicKeyAlgorithmOid[0])),
            sizeof(HardCodedPublicKeyAlgorithmOid[0]));
        break;

    case Hash_algo:
        Status = ParseOID(&current_ptr, pEnd, algoId, &HashAlgorithmOid[0][0],
            static_cast<uint32_t>(sizeof(HashAlgorithmOid)/sizeof(HashAlgorithmOid[0])),
            sizeof(HashAlgorithmOid[0]));
        break;

    default:
        DBG_ASSERT(0);
    }

    if(Status != X509_STATUS_SUCCESS){
        DBG_ASSERT(0);
        return X509_STATUS_ENCODING_ERROR;
    }

    if(current_ptr < end_of_sequence){
        /* We have some parameters  */
        Status = ParseAlgoParameters(&current_ptr, end_of_sequence, (UINT32 *)params);
        if(Status == X509_STATUS_UNSUPPORTED_PARAMETER){
            // As per spec, we  just move on.
            current_ptr = end_of_sequence;
            *params = unknownParameter;
            Status = X509_STATUS_SUCCESS;
        }
        if(Status != X509_STATUS_SUCCESS)
            Status = X509_STATUS_ENCODING_ERROR;
    }else{
        *params=unknownParameter;
    }

    *ppCurrent = current_ptr;
    return Status;
}

static STATUS ParseAlgoParameters(UINT8 **ppCurrent, UINT8 *pEnd, UINT32* param)
{
    UINT8 *current_ptr = *ppCurrent;
    UINT32 length;
    UINT32 i;
    STATUS Status = X509_STATUS_SUCCESS;
    UINT8 EncodingBytes;

    if ((!param) || ((current_ptr + 1) >= pEnd))
    {
        DBG_ASSERT(0);
        return X509_STATUS_INVALID_ARGS;
    }

    /* current_ptr is over the parameters. We currently support a NULL parameter or an object ID specifiying some algo specific data */

    switch(*current_ptr)
    {
    case DER_ENCODING_NULL_ID:
        current_ptr++;

        /* NULL id is always followed by length 0x00 */
        if(*current_ptr != 0)
        {
            DBG_ASSERT(0);
            return X509_STATUS_ENCODING_ERROR;
        }
        current_ptr++;
        *param = unknownParameter;
        break;

    case DER_ENCODING_OBJECT_ID:
        current_ptr++;

        /* Get Length of object identifier */
        Status = DecodeLength(current_ptr, pEnd, &length, &EncodingBytes);
        if(Status != X509_STATUS_SUCCESS)
            break;

        current_ptr += EncodingBytes;


        /* This can describe the curve */
        for(i = 0;i< MaxElipticCurveOidSupported; i++)
        {
            if(memcmp(current_ptr, EllipticCurveOid[i], length) == 0){

                /* We found a match. "i+1" is the algorithm number that  the caller is looking for */
                *param = i;
                break;
            }
        }

        if(i >= MaxElipticCurveOidSupported) {
            /* Never found a match */
            DBG_ASSERT(0);
            *param = unknownParameter;
            Status = X509_STATUS_ENCODING_ERROR;
        }
        current_ptr+=length;
        break;

    default:
        Status = X509_STATUS_UNSUPPORTED_PARAMETER;
        break;
        // unknown parameter. We ignore this.
    }

    *ppCurrent = current_ptr;
    return Status;
}


static STATUS ParseName(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrX509Name* Name)
{
    UINT8* end_of_sequence = NULL;
    UINT8 *current_ptr = *ppCurrent;
    UINT32 length;
    STATUS Status;
    UINT8 EncodingBytes;
    UINT32 NameType;

    memset(Name, 0, sizeof(SessMgrX509Name));

    do{

        Name->DistinguishedName = (char *)current_ptr;

        /* Format : Sequence [ Set [ Sequence [ OID Value] ] ] */
        Status = ParseIdAndLength(&current_ptr, pEnd, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
        if(Status != X509_STATUS_SUCCESS)
            break;


        Name->DistinguishedNameSize = length + EncodingBytes + 1;

        end_of_sequence = current_ptr + length;

        /* Because Set can have variable number of data underneath it, we need to cap it using the length of the sequence
        We will use variable End_of_set and loop through until end is reached. */
        while(current_ptr < end_of_sequence){

            Status = ParseIdAndLength(&current_ptr, end_of_sequence, DER_ENCODING_SET_ID, &length, &EncodingBytes, FALSE);
            if(Status != X509_STATUS_SUCCESS)
                break;

            Status = ParseIdAndLength(&current_ptr, end_of_sequence, DER_ENCODING_SEQUENCE_ID, &length, &EncodingBytes, FALSE);
            if(Status != X509_STATUS_SUCCESS)
                break;

            /* Expected value : Attribute type (OID) and value (Can be anything) */
            Status = ParseOID(&current_ptr, end_of_sequence, &NameType, &HardCodedNameOid[0][0],
                static_cast<uint32_t>(sizeof(HardCodedNameOid)/sizeof(HardCodedNameOid[0])),
                sizeof(HardCodedNameOid[0]));

            // We might have some cases where we are getting an Unknown OID which we just ignore.
            if(Status != X509_STATUS_SUCCESS && Status != X509_STATUS_UNKNOWN_OID){
                DBG_ASSERT(0);
                break;
            }

            /* Value can be any type. Mostly some form of ascii string */
            Status = ParseIdAndLength(&current_ptr, end_of_sequence, DER_ENCODING_UTF8_ID, &length, &EncodingBytes, TRUE);
            if(Status != X509_STATUS_SUCCESS && Status != X509_STATUS_NOT_FOUND)
                break;

            if(Status == X509_STATUS_NOT_FOUND){
                // Has to be one of UTF8 or PRINTABLE_STRING or IA5
                Status = ParseIdAndLength(&current_ptr, end_of_sequence, DER_ENCODING_PRINTABLE_STRING_ID, &length, &EncodingBytes, TRUE);
                if(Status != X509_STATUS_SUCCESS && Status != X509_STATUS_NOT_FOUND)
                    break;

                if(Status == X509_STATUS_NOT_FOUND){
                    Status = ParseIdAndLength(&current_ptr, end_of_sequence, DER_ENCODING_IA5_STRING_ID, &length, &EncodingBytes, FALSE);
                    if(Status != X509_STATUS_SUCCESS){
                        DBG_ASSERT(0);
                        break;
                    }
                }
            }

            switch(NameType)
            {
            case commonName:
                Name->commonName = (char *)current_ptr;
                Name->commonNameSize = length;
                break;

            case organization:
                Name->organization = (char *)current_ptr;
                Name->organizationSize = length;
                break;
            case country:
                Name->country = (char *)current_ptr;
                Name->countrySize = length;
                break;
            case locality:
                Name->locality = (char *)current_ptr;
                Name->localitySize = length;
                break;
            case state:
                Name->state = (char *)current_ptr;
                Name->stateSize = length;
                break;
            case organizationUnit:
                Name->organizationUnit = (char *)current_ptr;
                Name->organizationUnitSize = length;
                break;
            case UserId:
                Name->UserId = (char *)current_ptr;
                Name->UserIdSize = length;
                break;

            default:
                // Dont support this NameType. Just continue.
                break;
            }
            current_ptr += length;
        }
    }while(0);

    *ppCurrent = current_ptr;
    return Status;
}

#define DecodeTime_FourBytes(current_ptr)  ((1000*( (*current_ptr) - 0x30)) +    \
    (100*( *(current_ptr+1) - 0x30)) +    \
    (10*( *(current_ptr+2) - 0x30)) +    \
    (*(current_ptr + 3) - 0x30)) \

#define DecodeTime_TwoBytes(current_ptr)  ((10*( (*current_ptr) - 0x30)) +    \
    (*(current_ptr + 1) - 0x30)) \


#if 0
UINT32 DecodeTime(UINT8 *current_ptr, UINT8 length)
{
    UINT32 value = 0;
    UINT32 digit;
    int i;

    for(i=0; i<length - 1;i++)
    {
        digit = *current_ptr - 0x30;
        digit = digit * Pow(10, length - i - 1);
        value += digit;
        current_ptr++;
    }

    return value;
}
#endif


static STATUS ParseTime(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrDateTime* DateTime)
{
    /* Id : Either UTC Time or Generalized Time  */
    /*****
    Supported UTC formats : YYMMDDhhmmZ
    YYMMDDhhmm+hhmm
    YYMMDDhhmm-hhmm
    YYMMDDhhmmssZ
    YYMMDDhhmmss+hhmm
    YYMMDDhhmmss-hhmm
    *****/

    UINT8 *current_ptr = *ppCurrent;
    UINT32 length;
    BOOL    isUTC;
    STATUS Status;
    UINT8 EncodingBytes;
    UINT8 *date_start_ptr;

    DateTime->date.data = 0;
    DateTime->time.data = 0;

    /* Check whether current_ptr is pointing to UTC time or Generalized time*/

    if(*current_ptr ==  DER_ENCODING_UTC_TIME_ID){
        /* Year format is YY */
        isUTC = TRUE;
    }
    else if(*current_ptr ==  DER_ENCODING_GENERALIZED_TIME_ID){
        /* Year format is YYYY */
        isUTC = FALSE;
    }else{
        DBG_ASSERT(0);
        return X509_STATUS_ENCODING_ERROR;
    }

    current_ptr++;

    /* get Length of the time */
    Status = DecodeLength(current_ptr, pEnd, &length, &EncodingBytes);
    if(Status != X509_STATUS_SUCCESS){
        DBG_ASSERT(0);
        return X509_STATUS_ENCODING_ERROR;
    }

    current_ptr += EncodingBytes;
    date_start_ptr = current_ptr;

    /* Only difference between generalized and UTC is number of digits for the years.
    UTC has 2 digits and Generalized has 4 digits    */
    if(!isUTC){
        DateTime->date.yearMonthDay.year = static_cast<short unsigned int>(DecodeTime_FourBytes(current_ptr));
        current_ptr += 4;
    }else{
        // per rfc3280, if XX >= 50 then 19XX, otherwise 20XX. However, here we always use 20XX.
        DateTime->date.yearMonthDay.year = static_cast<short unsigned int>(DecodeTime_TwoBytes(current_ptr));
        current_ptr += 2;
        DateTime->date.yearMonthDay.year = static_cast<short unsigned int>(DateTime->date.yearMonthDay.year + 2000);
    }

    /* The next 8 bytes are common for both UTC and Generalized time */
    DateTime->date.yearMonthDay.month = DecodeTime_TwoBytes(current_ptr) & 0xF;
    current_ptr += 2;
    DateTime->date.yearMonthDay.day = DecodeTime_TwoBytes(current_ptr) & 0x3F;
    current_ptr += 2;
    DateTime->time.hourMinuteSecond.hour = DecodeTime_TwoBytes(current_ptr) & 0x3F;
    current_ptr += 2;
    DateTime->time.hourMinuteSecond.minute = DecodeTime_TwoBytes(current_ptr) & 0x3F;
    current_ptr += 2;

    /* Next character can be a numeral(incase we have seconds),  +, - or Z */

    if(isdigit(*current_ptr)){
        /* we have second. Get the next two bytes as seconds. */
        DateTime->time.hourMinuteSecond.second = DecodeTime_TwoBytes(current_ptr) & 0x3F;
        current_ptr += 2;
    }

    /* Next character has to be +, - or Z */
    switch(*current_ptr)
    {
    case '-':
        DateTime->time.hourMinuteSecond.timezone_is_neg = true;
        /* fallthrough */
    case '+':
        current_ptr++;
        DateTime->time.hourMinuteSecond.timezone_hour = DecodeTime_TwoBytes(current_ptr) & 0x3F;
        current_ptr += 2;
        DateTime->time.hourMinuteSecond.timezone_minute = DecodeTime_TwoBytes(current_ptr) & 0x3F;
        current_ptr += 2;
        break;

    case 'Z':
        /* End of time */
        current_ptr++;
        break;

    default:
        DBG_ASSERT(0);
        return X509_STATUS_ENCODING_ERROR;
    }

    // ensure the length parsed is equal to the specified length
    if ((current_ptr - date_start_ptr) != length)
    {
        DBG_ASSERT(0);
        return X509_STATUS_ENCODING_ERROR;
    }

    // date sanity check
    if (DateTime->date.yearMonthDay.year < 2000 || DateTime->date.yearMonthDay.year >= 2137 ||
        DateTime->date.yearMonthDay.month < 1 || DateTime->date.yearMonthDay.month > 12 ||
        DateTime->date.yearMonthDay.day < 1 || DateTime->date.yearMonthDay.day > 31 ||
        DateTime->time.hourMinuteSecond.hour > 24 ||
        DateTime->time.hourMinuteSecond.minute > 60 ||
        DateTime->time.hourMinuteSecond.second > 60 ||
        DateTime->time.hourMinuteSecond.timezone_hour > 24 ||
        DateTime->time.hourMinuteSecond.timezone_minute > 60)
    {
        DBG_ASSERT(0);
        return X509_STATUS_ENCODING_ERROR;
    }

    *ppCurrent = current_ptr;
    return X509_STATUS_SUCCESS;
}

/**

\param[in]      Buffer              Buffer which is pointing to a length field in Asn DER encoded form
\param[out]     Length              Length of the Asn DER encoded type in bytes
\param[out]     EncodingBytes       Number of bytes used for the encoding of the length

\retval  X509_STATUS_SUCCESS
\retval STATUS_INVALID_PARAMS     THere was some error in parsing the ASN Der encoded buffer.

@brief Return length of the ASN DER encoded type


*/
static STATUS DecodeLength(UINT8* Buffer, UINT8* BufferEnd, UINT32* Length, UINT8* EncodingBytes)
{
    if(Buffer[0] < 0x81){
        /* length is only one byte */
        *Length = Buffer[0];
        *EncodingBytes = 1;
    } else if ((Buffer[0] == 0x81) && (&Buffer[1] < BufferEnd)) {
        /* length is two bytes */
        *Length = Buffer[1];
        *EncodingBytes = 2;
    } else if ((Buffer[0] == 0x82) && (&Buffer[2] < BufferEnd)) {
        /* length is 3 bytes */
        *Length = (Buffer[1] << 8) + Buffer[2];
        *EncodingBytes = 3;
    }else{
        return X509_STATUS_ENCODING_ERROR;
    }

    //
    // check for unsigned integer overflow
    //
    UINT32 tempLength = *EncodingBytes + *Length;
    if (((size_t) Buffer + tempLength) >= tempLength) {
        if ((Buffer + tempLength) > BufferEnd) {
            return X509_STATUS_ENCODING_ERROR;
        }
    }
    else {
        return X509_STATUS_ENCODING_ERROR;
    }

    return X509_STATUS_SUCCESS;
}

static void SwapEndian(UINT8* ptr, int length)
{
    UINT8 temp;
    int i;

    for(i=0;i<length/2;i++)
    {
        temp = *(ptr + i);
        *(ptr + i) = *(ptr + length - i - 1);
        *(ptr + length - i - 1) = temp;
    }

}

#ifndef X509_FOR_PSE_PR
static int Pow(int num, int exp)
{
    int i;
    for(i=0;i<(exp -1);i++)
        num *=num;

    return num;
}
#endif

/*
This function is used to copy a variable length Big-Endian buffer to a   little-endian static length buffer. the buffer is copied over and endianness changed.
Does not affect Source Buffer. Copies dest buffer uses some ptr arithmatic and converts endianness.
*/
static STATUS swapendian_memcpy(UINT8 *DestPtr, UINT32 DestLen, UINT8 *SrcPtr, UINT32 SrcLen)
{
    if( (!DestPtr) || (!SrcPtr) || (DestLen < SrcLen))
        return STATUS_INVALID_PARAMS;

    memset(DestPtr, 0, DestLen);
    SESSMGR_MEMCPY_S(DestPtr + DestLen - SrcLen, DestLen, SrcPtr, SrcLen);

    SwapEndian(DestPtr, DestLen);

    return STATUS_SUCCESS;
}



/**

@brief ASN DER encoding follows the TLV format : Type Identifier || Length || Value
1. This function will parse the Type ID and Length, validate it with Expected Id and some encoding rules.
2. If no errors, function will move the current ptr to the value.

\param[in]      pCurrentPtr     This is a pointer to the current pointer. The function will move the current pointer after parsing the ID and length
\param[in]      ExpectedId      Expected Type identifier
\param[out]     Length          Length in Bytes of the "Value"
\param[out]     EncodingBytes   Number of Bytes used to encode the length field.
\param[in]      Optional        Is the ExpectedIdOptional

@retval  X509_STATUS_SUCCESS         ID and length were parsed and verified successfully.
@retval  X509_STATUS_NOT_FOUND       This value is only returned
@retval  STATUS_ENCODING_ERROR  Some error in the encoding of the certificate.
*/

static STATUS ParseIdAndLength(UINT8 **ppCurrent, UINT8 *pEnd, UINT8 ExpectedId, UINT32* Length, UINT8* EncodingBytes, BOOL Optional)
{
    UINT8*  current_ptr = *ppCurrent;
    STATUS  Status;

    if(*current_ptr != ExpectedId){
        if(Optional)
            return X509_STATUS_NOT_FOUND;
        else{
            DBG_ASSERT(0);
            return X509_STATUS_ENCODING_ERROR;
        }
    }
    current_ptr++;

    if (current_ptr != pEnd) {
        Status = DecodeLength(current_ptr, pEnd, Length, EncodingBytes);
        if(Status != X509_STATUS_SUCCESS){
            DBG_ASSERT(0);
            return X509_STATUS_ENCODING_ERROR;
        }
        current_ptr += *EncodingBytes;

        *ppCurrent = current_ptr;
        return X509_STATUS_SUCCESS;
    }
    else {
        return X509_STATUS_ENCODING_ERROR;
    }
}

#ifndef X509_FOR_PSE_PR
#ifndef WIN_TEST
/*
* This function expects the trusted time to be available from the OCSP parsing and stored in the SessMgrCtx.
* This call is protected by the Mutex we acquire when we get the ProcessS2GetS3 call.
*/

STATUS StoreTrustedTime(SessMgrDateTime TrustedTime)
{
    STATUS Status;
    STORAGE_FILE_ATTRIB Blob;
    STORAGE_OPERATION_FLAGS StorageFlags = {0};
    NTP_TIMESTAMP NtpTime = {0,0};

    Status = ConvertTimeToNtp(TrustedTime, &NtpTime );
    if(Status != STATUS_SUCCESS){
        DBG_ASSERT(0);
        return Status;
    }

    // We have the time in NTP. Call PrtcSetTime. This will give us an offset which we can use to find out current time.
    Status = PrtcSetTime(&NtpTime, &gSessmgrCtx.TrustedTime.RtcOffset);
    if(Status != STATUS_SUCCESS){
        DBG_ASSERT(0);
        return Status;
    }

    gSessmgrCtx.TrustedTime.Seconds = NtpTime.Seconds;

    // Store RTC offset in the Blob
    SESSMGR_INIT_BLOB_ATTRIB(Blob);
    Blob.Attributes.BlobType = AR_BLOB;
    StorageFlags.Data = 0;
    Status = gSessmgrCtx.StorageProtocol->Write(gSessmgrCtx.StorageProtocol,
        SESS_MGR_TRUSTED_TIME_NVAR_NAME,
        &Blob, &StorageFlags,
        sizeof(gSessmgrCtx.TrustedTime),
        &gSessmgrCtx.TrustedTime, 0);
    if(Status != STATUS_SUCCESS){
        DBG_ASSERT(0);
        return Status;
    }

    return STATUS_SUCCESS;
}

STATUS ConvertTimeToNtp(SessMgrDateTime Time, NTP_TIMESTAMP *NtpTime)
{
    STATUS Status;
    RTC_TIME RtcTime;
    TZ_DST   TimeZoneAndDST;

    memset(&RtcTime, 0, sizeof(RTC_TIME));
    memset(NtpTime, 0, sizeof(NTP_TIMESTAMP));

    if (Time.date.yearMonthDay.year < 2000 || Time.date.yearMonthDay.year >= 2137)
        return STATUS_FAILURE;

    RtcTime.Year  = (UINT8)(Time.date.yearMonthDay.year - 2000);
    RtcTime.Month = (UINT8)Time.date.yearMonthDay.month;
    RtcTime.Day   = (UINT8)Time.date.yearMonthDay.day;
    RtcTime.Hour  = (UINT8)Time.time.hourMinuteSecond.hour;
    RtcTime.Min   = (UINT8)Time.time.hourMinuteSecond.minute;
    RtcTime.Sec   = (UINT8)Time.time.hourMinuteSecond.second;

    RtcTime.Status.DM = 1;         // DM = 1 => Binary data representation.
    RtcTime.Status.HOURFORM = 1;   // HOURFORM = 1 => 24 hour

    TimeZoneAndDST.DST            = 0;
    TimeZoneAndDST.HalfHourAdjust = Time.time.hourMinuteSecond.timezone_minute ? 1 : 0;
    TimeZoneAndDST.RSVD           = 0;
    TimeZoneAndDST.TimeZone       = (Time.time.hourMinuteSecond.timezone_is_neg ? TIME_ZONE_OFFSET_SIGN_MASK : 0) |
        Time.time.hourMinuteSecond.timezone_hour;

    Status = PrtcLocalToNtp(&RtcTime, TimeZoneAndDST, NtpTime);
    if(Status != STATUS_SUCCESS){
        DBG_ASSERT(0);
        return Status;
    }

    // PrtcLocalToNtp has a starting offset of year 1900 and will overflow in year 2037
    // Shift the offset to start at year 2000, so that we can operate in years 2000-2137
    NtpTime->Seconds -= SECONDS_FROM_1900_TO_2000;

    return STATUS_SUCCESS;
}
#endif
#endif  // #ifndef X509_FOR_PSE_PR

