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
@file   X509Cert.h
@author Kapil Anantharaman
@brief  This file contains the data structures for the X509 certificates for verifier and EPID group . 
*/

#ifndef _X509CERT_H_
#define _X509CERT_H_

#ifndef WIN_TEST
#include "typedef.h"
#include "X509AlgoType.h"
#include "pse_pr_sigma_1_1_defs.h"
#else
#include "special_defs.h"
#endif

typedef UINT32 Uint32 ;
typedef UINT8  Uint8;
typedef UINT16 Uint16;
typedef void   X509_PROTOCOL;
typedef UINT32 STATUS;

#define DER_ENCODING_BOOLEAN_ID             0x01
#define DER_ENCODING_INTEGER_ID             0x02
#define DER_ENCODING_BIT_STRING_ID          0x03
#define DER_ENCODING_OCTET_STRING_ID        0x04
#define DER_ENCODING_NULL_ID                0x05 
#define DER_ENCODING_OBJECT_ID              0x06
#define DER_ENCODING_ENUMERATED_ID          0x0A
#define DER_ENCODING_UTF8_ID                0x0C
#define DER_ENCODING_PRINTABLE_STRING_ID    0x13
#define DER_ENCODING_IA5_STRING_ID          0x16
#define DER_ENCODING_UTC_TIME_ID            0x17
#define DER_ENCODING_GENERALIZED_TIME_ID    0x18
#define DER_ENCODING_SEQUENCE_ID            0x30
#define DER_ENCODING_SET_ID                 0x31

#define X509_BIT0 128
#define X509_BIT1 64
#define X509_BIT2 32
#define X509_BIT3 16
#define X509_BIT4 8
#define X509_BIT5 4
#define X509_BIT6 2
#define X509_BIT7 1

#define MAX_SUPPORTED_VERSION       0x02
#define MAX_HASH_LEN                20

#define ECDSA_KEY_ELEMENT_SIZE      32  // applies for px and py
#define ECDSA_KEY_SIZE              64 

#define IMPLICIT_TAG_ID                     0x80
#define EXPLICIT_TAG_ID                     0xA0
#define IMPLICIT_TAG_STRUCTURED_TYPE_ID     0xA0

#define TAG_NUMBER_ISSUER_UNIQUE_ID  1
#define TAG_NUMBER_SUBJECT_UNIQUE_ID 2
#define TAG_NUMBER_EXTENSIONS        3

#define TAG_NUMBER_AUTHORITY_KEY_ID                 0
#define TAG_NUMBER_AUTHORITY_CERT_ISSUER_ID         1
#define TAG_NUMBER_AUTHORITY_CERT_SERIAL_NUMBER_ID  2

#define DER_ENCODING_TRUE  0xFF
#define DER_ENCODING_FALSE 0x00

/* To supress thr warning on duplicate ECDSA_SIGANTURE_SIZE definition */
#undef  ECDSA_SIGNATURE_SIZE 

#define ECDSA_SIGNATURE_SIZE         64
#define ECDSA_SIGNATURE_MAX_SIZE_R   32
#define ECDSA_SIGNATURE_MAX_SIZE_S   32

#define RSA_SIGNATURE_SIZE           256
#define RSA_KEY_SIZE_2048_BYTES    256
#define RSA_E_SIZE                 4

#define MAX_VERSION_LENGTH_SIZE_BYTES 4
#define MAX_CERT_CHAIN_LENGTH         5

#define SECONDS_IN_DAY 86400
#define OCSP_DELAY_TOLERANCE_SECONDS 120

typedef enum{
    v1 = 0,
    v2,
    v3,
}CERTIFICATE_VERSIONS;

/* CmlaOmaDataBuffer */
typedef struct{
   Uint32  length;
   Uint8*  buffer;
} SessMgrDataBuffer;


typedef enum{
    signature_algo,
    PublicKey_algo,
    Hash_algo
}AlgorithmTypes;

typedef X509SignAlgoType SessMgrSignAlgoType;
typedef X509PublicKeyAlgoType SessMgrPublicKeyAlgoType;



typedef enum{
    AuthorityKeyId = 0,
    SubjectKeyId,
    KeyUsage,
    BasicConstraint,
    CertificatePolicy,
    ExtendedKeyUsage,
    ProductType,
    Max_supported_CertExtensions
}CertExtensions;

typedef enum{
    Nonce = 0,
    Max_supported_OcspExtensions
}OcspExtensions;

/* name struct for issuer and subject */
typedef enum{
   commonName = 0, 
   organization,
   country, 
   locality,
   state, 
   organizationUnit,
   UserId,
   Max_NameId_Supported
}  NameStruct;


/* name struct for issuer and subject */
typedef struct{
   char *DistinguishedName;
   Uint32 DistinguishedNameSize;
   char* commonName; /* OID 2 5 4 3 */
   Uint32 commonNameSize;
   char* organization; /* OID 2 5 4 10 */
   Uint32 organizationSize;
   char* country; /* OID 2 5 4 6 */
   Uint32 countrySize;
   char* locality; /* OID 2 5 4 7 */
   Uint32 localitySize; 
   char* state; /* OID 2 5 4 8 */
   Uint32 stateSize;
   char* organizationUnit; /* OID 2 5 4 11 */
   Uint32 organizationUnitSize;      
   char* UserId;   /* 0x09, 0x92, 0x26, 0x89, 0x93, 0xF2, 0x2C, 0x64, 0x01, 0x01 */
   Uint32 UserIdSize;    
} SessMgrX509Name;

/* time */
typedef union{
   Uint32 data;
   struct{
      Uint32 hour    : 6; /* 0-23 */
      Uint32 minute  : 6; /* 0-59 */
      Uint32 second  : 6; /* 0-59 */
      Uint32 timezone_is_neg : 2;
      Uint32 timezone_hour   : 6;
      Uint32 timezone_minute : 6;
   } hourMinuteSecond;       
} SessMgrTime;

/* date */
typedef union{
   Uint32 data;
   struct{
      Uint32 year    : 16;/* 2000-2137 */
      Uint32 month   : 4; /* 1-12 */
      Uint32 day     : 6; /* 1-31 */
      Uint32 reserve : 6;
   } yearMonthDay;       
} SessMgrDate;

typedef struct{
   SessMgrTime time;
   SessMgrDate date;
} SessMgrDateTime;


/*
This enum is used so that caller can pass this argument to the ParseCertificateChain function. Based on this, we can do extra validation on each 
certificate 
*/
typedef enum{
    EpidGroupCertificate = 0,
    VerifierCertificate,
    OcspResponderCertificate,
    Others, // OMA DRM 
}CertificateType;


typedef enum{
    root = 0,
    intermediate,
    leaf,
}CertificateLevel;


typedef enum{
   /* OID 1 2 840 10045 3 1 1 7 */
   curvePrime256v1 = 0,
   MaxElipticCurveOidSupported,
   unknownParameter = MaxElipticCurveOidSupported,
} SessMgrEllipticCurveParameter;

/* Definition of ECDSA public key */
typedef struct{
   Uint8* px; /* always 32 bytes */
   Uint8* py; /* always 32 bytes */
   SessMgrEllipticCurveParameter eccParameter;
} SessMgrEcdsaPublicKey;

/* Definition of RSA key */
typedef struct{
   SessMgrDataBuffer n;
   SessMgrDataBuffer p;
   SessMgrDataBuffer e;    
} SessMgrRsaKey;

/* Definition of EPID group public key */
typedef struct{
   Uint32 groupId;
   Uint8* h1x; /* always 32 bytes */
   Uint8* h1y; /* always 32 bytes */
   Uint8* h2x; /* always 32 bytes */
   Uint8* h2y; /* always 32 bytes */ 
   Uint8* wx0; /* always 32 bytes */
   Uint8* wx1; /* always 32 bytes */ 
   Uint8* wx2; /* always 32 bytes */
   Uint8* wy0; /* always 32 bytes */ 
   Uint8* wy1; /* always 32 bytes */ 
   Uint8* wy2; /* always 32 bytes */
} SessMgrEpidGroupPublicKey;



/* SessMgrKeyUsage */
typedef union {
   Uint32  value;
   struct {
      Uint32 OCSPSign    : 1;
      Uint32 reserved    : 31;
   } usage;
} SessMgrExtendedKeyUsage;

/* SessMgrKeyUsage */
typedef union {
   Uint16  value;
   struct {
      Uint16 digitalSignature: 1;
      Uint16 nonRepudiation:   1;
      Uint16 keyEncipherment:  1;
      Uint16 dataEncipherment: 1;
      Uint16 keyAgreement:     1;
      Uint16 keyCertSign:      1;
      Uint16 cRLSign:          1;
      Uint16 encipherOnly:     1;
      Uint16 decipherOnly:     1;
      Uint16 reserved:         7;
   } usage;
} SessMgrKeyUsage;


typedef struct _RsaPublicKey
{
   /**
    * @brief Buffer for Key.e
    */
   UINT8          Ebuffer[RSA_E_SIZE];
   /**
    * @brief Buffer for Key.n
    */
   UINT8          Nbuffer[RSA_KEY_SIZE_2048_BYTES];
} RsaPublicKey;

typedef struct _PseEcdsaPublicKey
{
    /**
    * @brief Buffer for px
    */
   UINT8          px[32];
   /**
    * @brief Buffer for py
    */
   UINT8          py[32];

}PseEcdsaPublicKey;

/* SessMgrProductType */
typedef enum{
   reserved = 0,
   invalidProductType = reserved,
   mediaVault,
   identityProtectionTechnology,
   capabilityLicensingServices,
   intelDAtestCertificate,
   Max_ProductType
} SessMgrProductType; 

/* SessMgrCertificatePolicy */
typedef enum{
   intel_sigma_cert_policy = 0,
   Max_Certificatepolicy
} SessMgrCertificatePolicyId; 

/* SessMgrCertificatePolicyQualifierId */
typedef enum{
   internet_policy_qualifier = 0,
   Max_CertificatepolicyQualifierid
} SessMgrCertificatePolicyQualifierId; 

/* SessMgrBasicConstraint */
typedef struct{
   BOOL isBasicConstraintPresent;
   BOOL isCa; /* is subject a CA? */
   Uint32 pathLenConstraint; /* applicable only if isCa is TRUE */
} SessMgrBasicConstraint;

/* information extracted from certificate */
/* !!!! if you change this, you MUST change the corresponding stucture in container.h !!!!*/
typedef struct{
   Uint32                   certificateVersion;
   SessMgrDataBuffer        serialNumber;
   SessMgrPublicKeyAlgoType algorithmIdentifierForSubjectPublicKey;
   SessMgrSignAlgoType      algorithmIdentifierForSignature;
   SessMgrX509Name          issuer;
   SessMgrX509Name          subject;
   SessMgrDateTime          notValidBeforeTime;
   SessMgrDateTime          notValidAfterTime;
   SessMgrDataBuffer        subjectPublicKey; 
   SessMgrDataBuffer        EncodedSubjectPublicKey;   // ptr to the encoding. This will be used to calculate the hash.
   SessMgrDataBuffer        IssuerUniqueId;
   SessMgrDataBuffer        SubjectUniqueId;
   SessMgrDataBuffer        AuthorityKeyId;
   SessMgrDataBuffer        SubjectKeyId;
   SessMgrKeyUsage          keyUsage; /* not applicable to group cert */
   SessMgrExtendedKeyUsage  ExtendedKeyUsage;
   SessMgrProductType       productType; /* only applicable to Intel-signed cert OID 1 2 840 113741 1 9 2 */
   SessMgrDataBuffer        CertificatePolicy;
   SessMgrBasicConstraint   basicConstraint; /* only applicable to OCSP responder cert */
   SessMgrSignAlgoType		TbsCertSignAlgoId;
   SessMgrDataBuffer       signatureBuffer;
   SessMgrDataBuffer       messageBuffer; /* aka tbsCertificate.  everything that is signed */
} SessMgrCertificateFields;

/* 
   The ISSUER_INFO is a data structure that is used to store interesting information about the issuer of the certificate. Because in a chain, the current certificate is the issuer 
   of the next certificate in the chain, In most cases, this data structure will contain data (like public key, signature algo, hash etc) of the parent. 
   For the root certificate, we have to calculate the hash of the issuer's public key from the hard coded value. The Hash of the key will be compared against the IssuerKeyHash in the OCSP response.
   */
typedef struct{
   Uint32                   length;
   Uint8*                   buffer;
   SessMgrSignAlgoType      AlgoType; 
   SessMgrDataBuffer 		EncodedPublicKeyHashBuffer;
   SessMgrDataBuffer 		CommonNameBuf;
   SessMgrProductType       productType;
} ISSUER_INFO;


typedef enum{
	EXPLICIT_TAG_0_ID_VALUE = 0xa0,
}DER_EXPLICIT_TAG_ID;

typedef enum{
    X509_STATUS_SUCCESS                  = 0,
    X509_GENERAL_ERROR,
    X509_STATUS_INVALID_VERSION,
    X509_STATUS_UNSUPPORTED_ALGORITHM,
    X509_STATUS_ENCODING_ERROR,
    X509_STATUS_INVALID_ARGS,
    X509_STATUS_UNSUPPORTED_CRITICAL_EXTENSION,
    X509_STATUS_UNSUPPORTED_TYPE,
    X509_STATUS_OCSP_FAILURE,
    X509_INVALID_SIGNATURE,
    X509_STATUS_UNKNOWN_OID,
    X509_STATUS_NOT_FOUND,
    X509_STATUS_OCSP_VERIFICATION_FAILED,
    X509_STATUS_UNSUPPORTED_PARAMETER,
    X509_STATUS_EXPIRED_CERTIFICATE,
    X509_STATUS_INTERNAL_ERROR,
    X509_STATUS_BASIC_CONSTRAINTS_VIOLATION,
    X509_STATUS_MEMORY_ALLOCATION_ERROR,
    X509_STATUS_INVALID_PARAMS,
}X509_Parser_Error_codes;

typedef enum{
    explicit_tag = 0,
    implicit_tag,
    invalid_tag,
}TAG_TYPE;

/* only supports SHA1 and SHA256 */
typedef enum{
   sessMgrHashSha1 = 0,
   sessMgrHashSha256
} SessMgrHashTypes;

typedef struct _OCSP_CERT_STATUS_TABLE{
UINT8               serialNumber[20];
UINT8				SerialNumberSize;
SessMgrHashTypes    HashAlgo;
UINT8               issuerKeyHash[20];
UINT8               issuerKeyHashSize;
UINT8               issuerNameHash[20];
UINT8               issuerNameHashSize;
}OCSP_CERT_STATUS_TABLE;


/* Macros */
#define CHECK_ID(value, ExpectedId) { if(value != ExpectedId) { \
                                       DBG_ASSERT(0);  \
                                       return X509_STATUS_ENCODING_ERROR; \
                                    } }

#define CHECK_VALUE(value, ExpectedValue) { if(value != ExpectedValue) { \
                                       DBG_ASSERT(0);  \
                                       return X509_STATUS_ENCODING_ERROR; \
                                    } }

#define CHECK_ID_OPTIONAL(value, ExpectedId) (value == ExpectedId) 


#define FIND_TAG_TYPE(ptr, TagId, TagType)   \
                   { if(((*ptr & 0xF0) == 0xA0) && ((*ptr & 0x0F) == TagId))  \
                        TagType = explicit_tag; \
                   else if (((*ptr & 0xF0) == 0x80) && ((*ptr & 0x0F) == TagId)) \
                            TagType =  implicit_tag; \
                        else \
                            TagType = invalid_tag; }

//STATUS ParseTime(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrDateTime* DataTime);
//STATUS ParseName(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrX509Name* Name);
//STATUS ParseAlgoIdentifier(UINT8 **ppCurrent, UINT8 *pEnd, UINT32* algoId, AlgorithmTypes Type, SessMgrEllipticCurveParameter* params);
//STATUS ParseOID(UINT8 **ppCurrent, UINT8 *pEnd, UINT32 *EnumVal, const UINT8 *OidList, UINT32 Max_Entries, UINT32 EntrySize);
//STATUS ParseAlgoParameters(UINT8 **ppCurrent, UINT8 *pEnd, UINT32 *param);
//STATUS ParseSubjectPublicKeyInfo(UINT8 **ppCurrent, UINT8 *pEnd, UINT8 **pworkbuffer, SessMgrCertificateFields* certificateFields);
//STATUS ParseEcdsaPublicKey(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrEcdsaPublicKey * EcDsaKey, SessMgrEllipticCurveParameter params);
//STATUS ParseEpidPublicKey(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrEpidGroupPublicKey * EpidKey);
//STATUS ParseCertExtensions(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrCertificateFields* certificateFields);
//STATUS ParseCertificatePolicy(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrDataBuffer *CertificatePolicy);
//STATUS ParseSignatureValue(UINT8 **ppCurrent, UINT8 *pEnd, UINT8 **pworkbuffer, UINT32 WorkBufferSize, SessMgrDataBuffer *SignatureValueBuf, UINT8 SignatureAlgoId);
//STATUS ParseRsaPublicKey(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrRsaKey * RsaKey);
//STATUS ParseInteger(UINT8 **ppCurrent, UINT8 *pEnd, SessMgrDataBuffer* DataBuf, BOOL isOptional, BOOL MustBePositive, UINT32 *PaddingLen);
//STATUS ParseIdAndLength(UINT8 **ppCurrent, UINT8 *pEnd, UINT8 ExpectedId, UINT32* Length, UINT8* EncodingBytes, BOOL Optional);
//STATUS ParseBoolean(UINT8 **ppCurrent, UINT8 *pEnd, BOOL* Value, BOOL optional);

//STATUS sessMgrParseDerCert
//(
//   IN  X509_PROTOCOL*            X509Protocol,
//   IN  Uint8*                    certificateDerEncoded,
//   IN  UINT8*                    pCertificateEnd,
//   IN  Uint8*                    workBuffer,
//   IN  UINT32                    workBufferSize,
//   OUT SessMgrCertificateFields* certificateFields,
//   IN  ISSUER_INFO *IssuerInfo,
//   IN  BOOL UseFacsimileEpid
// );



void PrintName(SessMgrX509Name *Name);
void PrintValidity(SessMgrDateTime *Time);
void PrintEcdsaPublicKey(SessMgrEcdsaPublicKey *Key);
void PrintAlgo(UINT8 *AlgoId);
void PrintDataBuffer(SessMgrDataBuffer *data);
void PrintEpidKey(SessMgrEpidGroupPublicKey *Key);

STATUS ParseCertificateChain(UINT8 *pCertChain,
                             UINT32 CertChainLength,
                             SessMgrCertificateFields *certificateFields,
                             UINT8                    *CertWorkBuffer,
                             UINT32                   CertWorkBufferLength,
                             ISSUER_INFO 			  *RootPublicKey,
                             UINT8   				  NumberOfSingleResponses,
                             OCSP_CERT_STATUS_TABLE   *OcspCertStatusTable,
                             CertificateType CertType,
                             BOOL UseFacsimileEpid);

//STATUS ParseOcspResponseChain(UINT8* OcspRespBuffer,
//                              UINT32 OcspRespBufferLength,
//                              UINT8* workBuffer,
//                             UINT32 workBufferSize,
//                              ISSUER_INFO* OcspCertRootPublicKey,
//                              OCSP_CERT_STATUS_TABLE *OcspCertStatusTable,
//                              UINT8* NumberOfSingleResponses,
//                              SessMgrDataBuffer Nonce,
//                              OCSP_REQ_TYPE OcspReqType,
//                              BOOL UseFacsimileEpid);

//STATUS DecodeLength(UINT8* Buffer, UINT8* BufferEnd, UINT32* Length, UINT8* EncodingBytes);
//UINT32 DecodeTime(UINT8 *current_ptr, UINT8 length);
//void SwapEndian(UINT8* Ptr, int length);
//int Pow(int num, int exp);
//STATUS swapendian_memcpy(UINT8 *DestPtr, UINT32 DestLen, UINT8 *SrcPtr, UINT32 SrcLen);
//BOOL VerifySha1Hash(SessMgrDataBuffer *HashData, UINT8 *Expectedhash, UINT32 ExpectedHashLength);




#endif
