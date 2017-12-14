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


#include "sigma_crypto_layer.h"
#include "sgx_ecc256_internal.h"
#include "pse_pr_inc.h"
#include "pse_pr_types.h"
#include "safe_id.h"
#include <stddef.h>
#include <time.h>
#include <cstring>
#include "le2be_macros.h"
#include "prepare_hmac_sha256.h"
#include "prepare_hash_sha256.h"
#include "epid/verifier/1.1/api.h"
#include "epid/common/1.1/types.h"
#include "sgx_trts.h"
#include "ae_ipp.h"
#include "util.h"

#include "Keys.h"
#include "pairing_blob.h"

static ae_error_t MapEpidResultToAEError(EpidStatus epid_result)
{
    ae_error_t status = PSE_PR_PCH_EPID_UNKNOWN_ERROR;

    switch (epid_result)
    {
    case kEpidNoErr:                            status = AE_SUCCESS; break;
    case kEpidSigInvalid:                       status = PSE_PR_PCH_EPID_SIG_INVALID; break;
    case kEpidSigRevokedInGroupRl:              status = PSE_PR_PCH_EPID_SIG_REVOKED_IN_GROUPRL; break;
    case kEpidSigRevokedInPrivRl:               status = PSE_PR_PCH_EPID_SIG_REVOKED_IN_PRIVRL; break;
    case kEpidSigRevokedInSigRl:                status = PSE_PR_PCH_EPID_SIG_REVOKED_IN_SIGRL; break;
    case kEpidSigRevokedInVerifierRl:           status = PSE_PR_PCH_EPID_SIG_REVOKED_IN_VERIFIERRL; break;
    case kEpidErr:                              status = PSE_PR_PCH_EPID_UNKNOWN_ERROR; break;
    case kEpidNotImpl:                          status = PSE_PR_PCH_EPID_NOT_IMPLEMENTED; break;
    case kEpidBadArgErr:                        status = PSE_PR_PCH_EPID_BAD_ARG_ERR; break;
    case kEpidNoMemErr:                         status = PSE_PR_PCH_EPID_NO_MEMORY_ERR; break;
    case kEpidMemAllocErr:                      status = PSE_PR_PCH_EPID_NO_MEMORY_ERR; break;
    case kEpidMathErr:                          status = PSE_PR_PCH_EPID_MATH_ERR; break;
    case kEpidDivByZeroErr:                     status = PSE_PR_PCH_EPID_DIVIDED_BY_ZERO_ERR; break;
    case kEpidUnderflowErr:                     status = PSE_PR_PCH_EPID_UNDERFLOW_ERR; break;
    case kEpidHashAlgorithmNotSupported:        status = PSE_PR_PCH_EPID_HASH_ALGORITHM_NOT_SUPPORTED; break;
    case kEpidRandMaxIterErr:                   status = PSE_PR_PCH_EPID_RAND_MAX_ITER_ERR; break;
    case kEpidDuplicateErr:                     status = PSE_PR_PCH_EPID_DUPLICATE_ERR; break;
    case kEpidInconsistentBasenameSetErr:       status = PSE_PR_PCH_EPID_INCONSISTENT_BASENAME_SET_ERR; break;
    case kEpidMathQuadraticNonResidueError:     status = PSE_PR_PCH_EPID_MATH_ERR; break;
    default:                                    status = PSE_PR_PCH_EPID_UNKNOWN_ERROR; break;
    }

    return status;
}


SigmaCryptoLayer::SigmaCryptoLayer()
{
}


SigmaCryptoLayer::~SigmaCryptoLayer(void)
{
    memset_s(m_local_private_key_b_little_endian, SIGMA_SESSION_PRIVKEY_LENGTH, 0, SIGMA_SESSION_PRIVKEY_LENGTH);
    memset_s(m_SMK, SIGMA_SMK_LENGTH, 0, SIGMA_SMK_LENGTH);
    memset_s(m_SK, sizeof(m_SK), 0, sizeof(m_SK));
    memset_s(m_MK, sizeof(m_MK), 0, sizeof(m_MK));
}


ae_error_t SigmaCryptoLayer::DeriveSkMk(/* In  */ sgx_ecc_state_handle_t ecc_handle)
{
    ae_error_t ae_status = PSE_PR_DERIVE_SMK_ERROR;
    IppStatus    Status;
    Ipp8u Gab[SGX_ECP256_KEY_SIZE*2] = {0};
    Ipp8u Gab_Wth_00[SGX_ECP256_KEY_SIZE*2+1] = {0};
    Ipp8u Gab_Wth_01[SGX_ECP256_KEY_SIZE*2+1] = {0};
    Ipp8u GabHMACSha256[SGX_SHA256_HASH_SIZE] = { 0 };

    /* convert m_remotePublicKey_ga_big_endian to little endian format */
    uint8_t public_key_little_endian[SIGMA_SESSION_PUBKEY_LENGTH];
    memcpy(public_key_little_endian, m_remote_public_key_ga_big_endian, SIGMA_SESSION_PUBKEY_LENGTH);
    SwapEndian_32B(&(public_key_little_endian[0]));
    SwapEndian_32B(&(public_key_little_endian[32]));

    do
    {
        // Watch for null pointers
        if (ecc_handle == NULL)
        {
            ae_status = PSE_PR_PARAMETER_ERROR;
            break;
        }

        sgx_status_t sgx_status = sgx_ecc256_compute_shared_point((sgx_ec256_private_t *)m_local_private_key_b_little_endian,
                                           (sgx_ec256_public_t *)public_key_little_endian,
                                           (sgx_ec256_shared_point_t *)Gab,
                                           ecc_handle);
        if (SGX_SUCCESS != sgx_status)
        {
            if (SGX_ERROR_OUT_OF_MEMORY == sgx_status)
                ae_status = PSE_PR_INSUFFICIENT_MEMORY_ERROR;
            break;
        }

        //Initialize Variables required to get SK, SMK, MK
        memcpy(Gab_Wth_00, Gab, sizeof(Gab));
        Gab_Wth_00[sizeof(Gab)] = 0;

        memcpy(Gab_Wth_01, Gab, sizeof(Gab));
        Gab_Wth_01[sizeof(Gab)] = 1;
        Ipp8u HMAC_Key[SIGMA_HMAC_LENGTH] = {0};

        //Compute SMK
        Status = ippsHMAC_Message(Gab_Wth_00, sizeof(Gab_Wth_00), HMAC_Key, sizeof(HMAC_Key),
                                                m_SMK, sizeof(m_SMK), IPP_ALG_HASH_SHA256);
        if (Status != ippStsNoErr)
        {
            if (Status == ippStsNoMemErr || Status == ippStsMemAllocErr)
                ae_status = PSE_PR_INSUFFICIENT_MEMORY_ERROR;
            break;
        }

        // Compute SK and MK
        Status = ippsHMAC_Message(Gab_Wth_01, sizeof(Gab_Wth_01), HMAC_Key, sizeof(HMAC_Key),
                                                GabHMACSha256, sizeof(GabHMACSha256), IPP_ALG_HASH_SHA256);
        if (Status != ippStsNoErr)
        {
            if (Status == ippStsNoMemErr || Status == ippStsMemAllocErr)
                ae_status = PSE_PR_INSUFFICIENT_MEMORY_ERROR;
            break;
        }

        // Derive SK and MK from SHA256(g^ab)
        memcpy(m_SK, (GabHMACSha256), SIGMA_SK_LENGTH);                // SK: bits   0-127
        memcpy(m_MK, (GabHMACSha256 + SIGMA_SK_LENGTH), SIGMA_MK_LENGTH);            // MK: bits 128-255

        ae_status = AE_SUCCESS;

    } while (false);

    // Defense-in-depth: clear secrets in stack before return
    memset_s(Gab, sizeof(Gab), 0, sizeof(Gab));
    memset_s(Gab_Wth_00, sizeof(Gab_Wth_00), 0, sizeof(Gab_Wth_00));
    memset_s(Gab_Wth_01, sizeof(Gab_Wth_00), 0, sizeof(Gab_Wth_00));
    memset_s(GabHMACSha256, sizeof(GabHMACSha256), 0, sizeof(GabHMACSha256));

    return ae_status;
}



ae_error_t SigmaCryptoLayer::calc_s2_hmac(
    SIGMA_HMAC* hmac, const SIGMA_S2_MESSAGE* s2, size_t nS2VLDataLen)
{
    PrepareHMACSHA256 p(m_SMK, sizeof(m_SMK));

    p.Update(s2->Gb, sizeof(s2->Gb));
    p.Update(s2->Basename, sizeof(s2->Basename));
    p.Update(&s2->OcspReq, sizeof(s2->OcspReq));
    p.Update(s2->Data, nS2VLDataLen);

    //NRG:  SIGMA_HMAC - HMAC_SHA256 of [Gb || Basename || OCSP Req ||
    //          Verifier Cert ||  Sig-RL List ], using SMK

    return p.Finalize(hmac);
}

ae_error_t SigmaCryptoLayer::calc_s3_hmac(
    SIGMA_HMAC* hmac, const SIGMA_S3_MESSAGE* s3, size_t nS3VLDataLen)
{
    PrepareHMACSHA256 p(m_SMK, sizeof(m_SMK));

    p.Update(&s3->TaskInfo, sizeof(s3->TaskInfo));
    p.Update(s3->Ga, sizeof(s3->Ga));
    p.Update(s3->Data, nS3VLDataLen);

    //NRG:  SIGMA_HMAC -- HMAC_SHA256 of [TaskInfo || g^a ||
    //          EPIDCertprvr || EPIDSig(g^a || g^b)], using SMK

    return p.Finalize(hmac);
}

ae_error_t SigmaCryptoLayer::ComputePR(SIGMA_SECRET_KEY* oldSK, Ipp8u byteToAdd, SIGMA_HMAC* hmac)
{
    Ipp8u Sk_Wth_Added_Byte[sizeof(SIGMA_SIGN_KEY)+1];

    ae_error_t ae_status = PSE_PR_PR_CALC_ERROR;

    memset(hmac, 0, sizeof(*hmac));

    do
    {
        memcpy(Sk_Wth_Added_Byte, oldSK, SIGMA_SK_LENGTH);
        Sk_Wth_Added_Byte[SIGMA_SK_LENGTH] = byteToAdd;

        //Compute hmac
        IppStatus ippstatus = ippsHMAC_Message(Sk_Wth_Added_Byte,
            SIGMA_SK_LENGTH+1, (Ipp8u*)m_MK, SIGMA_MK_LENGTH,
            (Ipp8u*)hmac, SIGMA_HMAC_LENGTH, IPP_ALG_HASH_SHA256);

        // defense-in-depth, clear secret data
        memset_s(Sk_Wth_Added_Byte, sizeof(Sk_Wth_Added_Byte), 0, sizeof(Sk_Wth_Added_Byte));

        if (ippStsNoErr != ippstatus)
        {
            if (ippStsNoMemErr == ippstatus || ippStsMemAllocErr == ippstatus)
                ae_status = PSE_PR_INSUFFICIENT_MEMORY_ERROR;
            break;
        }

        ae_status = AE_SUCCESS;

    } while (0);

    return ae_status;
}


ae_error_t SigmaCryptoLayer::ComputeId(Ipp8u byteToAdd,
                                 SHA256_HASH* hash)
{
    memset(hash, 0, sizeof(*hash));

    PrepareHashSHA256 p;

    p.Update(m_SK, sizeof(SIGMA_SIGN_KEY));
    p.Update(m_MK, sizeof(SIGMA_MAC_KEY));
    p.Update(&byteToAdd, sizeof(Ipp8u));

    return p.Finalize(hash);
}

ae_error_t SigmaCryptoLayer::MsgVerifyPch(Ipp8u* PubKeyPch, int PubKeyPchLen,
                                    Ipp8u* EpidParamsCert,  Ipp8u* Msg, int MsgLen,
                                    Ipp8u* Bsn, int BsnLen, Ipp8u* Signature,
                                    int SignatureLen,
                                    Ipp8u* PrivRevList, int PrivRL_Len, Ipp8u* SigRevList, int SigRL_Len,
                                    Ipp8u* GrpRevList, int GrpRL_Len)
{
    ae_error_t status = AE_FAILURE;
    EpidStatus SafeIdRes = kEpidNoErr;
    Epid11Signature Epid11Sig;
    Epid11Signature *SigPointer = NULL;
    memset_s(&Epid11Sig, sizeof(Epid11Sig), 0, sizeof(Epid11Sig));

    UNUSED(EpidParamsCert);
    UNUSED(Bsn);
    UNUSED(BsnLen);

    Epid11VerifierCtx* ctx = NULL;
    do
    {
        // Watch for null pointers
        if ((PubKeyPch == NULL) || (Msg == NULL) || (Signature == NULL))
        {
            status = PSE_PR_PARAMETER_ERROR;
            break;
        }

        // Verify the length of public key and signature buffers
        if (((size_t)PubKeyPchLen < (SAFEID_CERT_LEN - ECDSA_SIGNATURE_LEN)) ||
                                (SignatureLen < SAFEID_SIG_LEN))
        {
            status = PSE_PR_PARAMETER_ERROR;
            break;
        }

        SafeIdRes = Epid11VerifierCreate(
                (Epid11GroupPubKey* )(PubKeyPch),
                NULL, &ctx);
        status = MapEpidResultToAEError(SafeIdRes);
        if (AE_FAILED(status)){
            break;
        }
        if(PrivRevList != NULL){
            SafeIdRes = Epid11VerifierSetPrivRl(ctx, (Epid11PrivRl *)(PrivRevList), PrivRL_Len);
            status = MapEpidResultToAEError(SafeIdRes);
            if(AE_FAILED(status)) {break;}
        }
        if(SigRevList != NULL){
            SafeIdRes = Epid11VerifierSetSigRl(ctx, (Epid11SigRl *)(SigRevList), SigRL_Len);
            status = MapEpidResultToAEError(SafeIdRes);
            if(AE_FAILED(status)) {break;}
        }

        if(GrpRevList != NULL){
            SafeIdRes = Epid11VerifierSetGroupRl(ctx, (Epid11GroupRl *)(GrpRevList), GrpRL_Len);
            status = MapEpidResultToAEError(SafeIdRes);
            if(AE_FAILED(status)) {break;}
        }

        //verify signature with Pub Key in ctx
        //For epid-sdk-3.0, when the sigRL is null, the signature size includes "rl_ver" and "n2" fields
        //(See structure definition of Epid11Signature)
        //So we must use bigger buffer add 8 bytes to the length
        if(SignatureLen == sizeof(Epid11BasicSignature)){
            memcpy(&Epid11Sig, Signature, SignatureLen);
            SignatureLen = static_cast<int>(SignatureLen + sizeof(Epid11Sig.rl_ver) + sizeof(Epid11Sig.n2));
            SigPointer = &Epid11Sig;
        }
        else
        {
            SigPointer = (Epid11Signature *)Signature;
        }


        SafeIdRes = Epid11Verify(ctx,
                SigPointer, SignatureLen,
                Msg, MsgLen);
        status = MapEpidResultToAEError(SafeIdRes);
        if (AE_FAILED(status)){
            break;
         }

        status = AE_SUCCESS;

    } while (false);

    if (NULL != ctx)
    {
        Epid11VerifierDelete(&ctx);
    }

    return status;
}
