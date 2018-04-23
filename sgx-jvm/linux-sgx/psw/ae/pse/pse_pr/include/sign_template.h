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

#ifndef _SIGN_TEMPLATE_H_
#define _SIGN_TEMPLATE_H_

#include "pse_pr_inc.h"
#include "pse_pr_types.h"
#include "signing_template_info.h"

class SignTemplate
{
public:
    SignTemplate(tSigningTemplateInfo& info);
    virtual ~SignTemplate(void);

    virtual size_t GetSize();

    virtual ae_error_t GetOriginalTemplate(Ipp8u* pTemplate, size_t* nTemplate);

    virtual ae_error_t GetPublicEcDsaKey(
        /*in */ const EcDsaPrivKey* pPrivateKey,
        /*out*/ EcDsaPubKey* pPublicKey);

    // Note: The keys returned are Big-endian
    virtual ae_error_t GenEcDsaKeyPair(
        /*out*/ EcDsaPrivKey* pPrivateKey,
        /*out*/ EcDsaPubKey* pPublicKey);

    // Note: The keys input are Big-endian
    virtual ae_error_t GetSignedTemplate(
        /*in */ EcDsaPrivKey* pPrivateKey,
        /*in */ EcDsaPubKey* pPublicKey,
        /*out*/ Ipp8u* pSignedTemplate, 
        /*i/o*/ size_t* pnBytes); 

private:

    bool VerifySignature(
        /*in */ Ipp8u* pSignedTemplate, 
        /*in */ size_t nBytes);

    IppsECCPState* newStd_256_ECP(void);
    IppsECCPPointState* newECP_256_Point(void);
    IppsPRNGState* newPRNG(void);
    IppsBigNumState* newBN(int len, const Ipp32u* pData);

    tSigningTemplateInfo& m_info;
};

#endif //#ifndef _SIGN_TEMPLATE_H_
