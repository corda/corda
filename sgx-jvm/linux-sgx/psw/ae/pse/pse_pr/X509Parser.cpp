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
#include "X509Parser.h"
#include <cstddef>
#include "X509_Parser/X509_Interface.h"
#include <string.h>
#include <stdlib.h>
#include "byte_order.h"
#include "le2be_macros.h"

#ifdef DUMP_OCTETS
void OutputOctets(const char* pMsg, const void* pData, size_t nData);
#endif

const int certWorkBufferLength = 8196;

X509Parser::~X509Parser(void)
{
}


UINT32 X509Parser::ParseGroupCertificate
    (
    /*in */ const EcDsaPubKey* pSerializedPublicKey,
    /*in */ const X509_GROUP_CERTIFICATE_VLR* pGroupCertVlr,
    /*out*/ UINT32* pGID,
    /*out*/ Epid11GroupPubKey* pGroupPubKey
    )
{
    STATUS status = X509_GENERAL_ERROR;

    SessMgrCertificateFields* certificateFields = NULL;
    UINT8*                    certWorkBuffer = NULL;
    CertificateType certType = EpidGroupCertificate;

    do
    {
        if (NULL == pSerializedPublicKey ||
            NULL == pGroupCertVlr ||
            NULL == pGID || NULL == pGroupPubKey)
            break;

        certificateFields = (SessMgrCertificateFields*)calloc(1, sizeof(*certificateFields));
        certWorkBuffer = (UINT8*)calloc(1, certWorkBufferLength);
        if (NULL == certificateFields ||
            NULL == certWorkBuffer)
            break;

        // Inject the Public Key to X509_Parser through the global variable SerializedPublicKey
        SetPublicEcDsaKey(pSerializedPublicKey);

        UINT8* X509GroupCertificate = const_cast<UINT8*>(pGroupCertVlr->X509GroupCertData);
        if (
            //
                // this is "functional", not buffer overflow check
                    //
                        (pGroupCertVlr->VlrHeader.PaddedBytes > 3) ||
                        //
                        // buffer overflow check: Length can be anything, sizeof is a constant...
                        //
                        (pGroupCertVlr->VlrHeader.Length <= (sizeof(pGroupCertVlr->VlrHeader) + pGroupCertVlr->VlrHeader.PaddedBytes))
                        ) {
                            break;
        }
        UINT32 X509GroupCertificateSize = static_cast<UINT32>(pGroupCertVlr->VlrHeader.Length -
            sizeof(pGroupCertVlr->VlrHeader) - pGroupCertVlr->VlrHeader.PaddedBytes);

#ifdef DUMP_OCTETS
        OutputOctets("X509GroupCertificate", X509GroupCertificate, X509GroupCertificateSize);
#endif

        //typedef enum{
        //    EpidGroupCertificate = 0,
        //    VerifierCertificate,
        //    OcspResponderCertificate,
        //    Others, // OMA DRM
        //}CertificateType;
        ISSUER_INFO* pRootPublicKey = NULL;

        status = ParseCertificateChain(
            X509GroupCertificate, X509GroupCertificateSize,
            certificateFields, certWorkBuffer, certWorkBufferLength, pRootPublicKey, 0,
            NULL, certType, FALSE);
        if (X509_STATUS_SUCCESS != status)
            break;

        uint8_t gidArray[sizeof(GroupId)] = {0};
        if (certificateFields->serialNumber.length > sizeof(gidArray))
            break;

        int index = static_cast<int>(sizeof(gidArray)-certificateFields->serialNumber.length);
        memcpy(&gidArray[index], certificateFields->serialNumber.buffer, certificateFields->serialNumber.length);

        if(certificateFields->algorithmIdentifierForSubjectPublicKey != X509_intel_sigma_epidGroupPublicKey_epid11)//Only Epid Group Public Key Epid1.1 are permitted to be subject key
            break;

        SessMgrEpidGroupPublicKey* EpidKey = (SessMgrEpidGroupPublicKey*)certificateFields->subjectPublicKey.buffer;

        memset(pGroupPubKey, 0, sizeof(Epid11GroupPubKey));
        memcpy(&pGroupPubKey->gid, gidArray, sizeof(pGroupPubKey->gid));
        memcpy(&pGroupPubKey->h1.x, EpidKey->h1x, sizeof(pGroupPubKey->h1.x));
        memcpy(&pGroupPubKey->h1.y, EpidKey->h1y, sizeof(pGroupPubKey->h1.y));
        memcpy(&pGroupPubKey->h2.x, EpidKey->h2x, sizeof(pGroupPubKey->h2.x));
        memcpy(&pGroupPubKey->h2.y, EpidKey->h2y, sizeof(pGroupPubKey->h2.y));
        memcpy(&pGroupPubKey->w.x[0], EpidKey->wx0, sizeof(pGroupPubKey->w.x[0]));
        memcpy(&pGroupPubKey->w.x[1], EpidKey->wx1, sizeof(pGroupPubKey->w.x[1]));
        memcpy(&pGroupPubKey->w.x[2], EpidKey->wx2, sizeof(pGroupPubKey->w.x[2]));
        memcpy(&pGroupPubKey->w.y[0], EpidKey->wy0, sizeof(pGroupPubKey->w.y[0]));
        memcpy(&pGroupPubKey->w.y[1], EpidKey->wy1, sizeof(pGroupPubKey->w.y[1]));
        memcpy(&pGroupPubKey->w.y[2], EpidKey->wy2, sizeof(pGroupPubKey->w.y[2]));

        *pGID = lv_htonl(gidArray);

        status = X509_STATUS_SUCCESS;
    } while (false);

    if (NULL != certificateFields)
        free(certificateFields);
    if (NULL != certWorkBuffer)
        free(certWorkBuffer);

    return status;
}
