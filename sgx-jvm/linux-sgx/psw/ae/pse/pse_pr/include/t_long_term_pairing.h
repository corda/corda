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
 
 
#ifndef _LONG_TERM_PAIRING_H_
#define _LONG_TERM_PAIRING_H_

#include "pse_pr_inc.h"
#include "pse_pr_types.h"
#include "sigma_crypto_layer.h"
#include "epid/common/types.h"
#include "Epid11_rl.h"
#include "pairing_blob.h"

class TEpidSigma11Verifier
{

public:
    TEpidSigma11Verifier();
    ~TEpidSigma11Verifier(void);

    enum State
    {
        STATE_GENM7,
        STATE_VERIFYM8,
        STATE_DONE,
        STATE_ERROR
    };

    static bool get_sigRL_info(const EPID11_SIG_RL* pSigRL, uint32_t& sigRL_entries, uint32_t& sigRL_size);
    static bool get_privRL_info(const EPID11_PRIV_RL* pPrivRL, uint32_t& privRL_entries, uint32_t& privRL_size);

    ae_error_t GenM7
    (
        /*in */ const SIGMA_S1_MESSAGE*      pS1,
        /*in */ const EPID11_SIG_RL*         pSigRL, 
        /*in */ const uint8_t*               pOcspResp, 
        /*in */ uint32_t  nLen_OcspResp, 
        /*in */ const uint8_t*               pVerifierCert,
        /*in */ uint32_t  nLen_VerifierCert,
        /*in */ const pairing_blob_t* pPairingBlob,
        /*in */ uint32_t  nMax_S2,
        /*out*/ SIGMA_S2_MESSAGE*  pS2, 
        /*out*/ uint32_t* pnLen_S2
    );

    ae_error_t VerifyM8
    (
        /*in */ const SIGMA_S3_MESSAGE*      pS3, 
        /*in */ uint32_t  nLen_S3,
        /*in */ const EPID11_PRIV_RL*        pPrivRL,
        /*i/o*/ pairing_blob_t* pPairingBlob,
        /*out*/ bool*     pbNewPairing
    );

private:
    SigmaCryptoLayer m_sigmaAlg;
    State       m_nextState;

    EcDsaPrivKey m_verifierPrivateKey;

    uint8_t*    m_pSigRL;
    size_t      m_nSigRL;

    uint32_t    m_nSigRLVersion;
    uint32_t    m_nPrivRLVersion;
    uint32_t    m_nDalAppletVersion;

    SAFEID_GID  m_gid;

    SIGMA_SECRET_KEY m_pairingID;  // sk used for repairing check
    Nonce128_t  m_pairingNonce;

    bool TaskInfoIsValid(const ME_TASK_INFO& taskInfo);

    ae_error_t ValidateS3DataBlock(const SIGMA_S3_MESSAGE* pS3, uint32_t nLen_S3, X509_GROUP_CERTIFICATE_VLR** X509GroupCertVlr, EPID_SIGNATURE_VLR** EpidSigVlr);

    ae_error_t AddCertificateChain(SIGMA_S2_MESSAGE* pS2, size_t& index, 
            size_t nMaxS2, const UINT8* pCertChain, size_t nCertChain);
    ae_error_t AddRevocationList(SIGMA_S2_MESSAGE* pS2, size_t& index, 
            size_t nMaxS2, const EPID11_SIG_RL* pRL, uint32_t nSigRL);
    ae_error_t AddOcspResponses(SIGMA_S2_MESSAGE* pS2, size_t& index, 
            size_t nMaxS2, const uint8_t* pOcspResp, size_t nOcspResp);

    ae_error_t ValidateSigRL(const EPID11_SIG_RL* pSigRL, uint32_t sigRL_entries, uint32_t sigRL_size, uint32_t* pVersion);

	ae_error_t ValidatePrivRL(const EPID11_PRIV_RL* pPrivRL, uint32_t privRL_entries, uint32_t privRL_size, uint32_t* pVersion);

private:

    // Disable class operations (default constructor, copy constructor, assignment operator, and address-of operator)
    //TEpidSigma11Verifier(void);                                         // default constructor
    TEpidSigma11Verifier(const TEpidSigma11Verifier& rhs);              // copy constructor
    TEpidSigma11Verifier& operator=(const TEpidSigma11Verifier& rhs);   // assignment operator
    TEpidSigma11Verifier* operator&();                                  // address-of operator
    const TEpidSigma11Verifier* operator&() const;                      // address-of operator

};

#endif

