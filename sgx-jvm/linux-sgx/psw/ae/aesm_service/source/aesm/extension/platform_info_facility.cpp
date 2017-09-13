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

#include "platform_info_logic.h"
#include "PSEPRClass.h"
#include "byte_order.h"
#include "PSDAService.h"
#include "upse/helper.h"
#include <assert.h>
#include "sgx_profile.h"
#include "interface_psda.h"

static bool newer_psda_svn(uint32_t psdasvn1, uint32_t psdasvn2)
{
    // return true if psdasvn1 is newer than psdasvn2
    bool retval = static_cast<bool>(psdasvn1 > psdasvn2);
    SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" [psdasvn1,psdasvn2] = ", psdasvn1, psdasvn2);
    return retval;
}


ae_error_t PlatformInfoLogic::get_sgx_epid_group_flags(const platform_info_blob_wrapper_t* p_platform_info_blob, uint8_t* pflags)
{
    ae_error_t retval = AE_SUCCESS;
    if (NULL != pflags && NULL != p_platform_info_blob && p_platform_info_blob->valid_info_blob) {
        *pflags = p_platform_info_blob->platform_info_blob.sgx_epid_group_flags;
    }
    else {
        retval = AE_INVALID_PARAMETER;
    }
    return retval;
}

ae_error_t PlatformInfoLogic::get_sgx_tcb_evaluation_flags(const platform_info_blob_wrapper_t* p_platform_info_blob, uint16_t* pflags)
{
    ae_error_t retval = AE_SUCCESS;
    if (NULL != pflags && NULL != p_platform_info_blob && p_platform_info_blob->valid_info_blob) {
        const uint16_t* p = reinterpret_cast<const uint16_t*>(p_platform_info_blob->platform_info_blob.sgx_tcb_evaluation_flags);
        *pflags = lv_ntohs(*p);
    }
    else {
        retval = AE_INVALID_PARAMETER;
    }
    return retval;
}

ae_error_t PlatformInfoLogic::get_pse_evaluation_flags(const platform_info_blob_wrapper_t* p_platform_info_blob, uint16_t* pflags)
{
    ae_error_t retval = AE_SUCCESS;
    if (NULL != pflags && NULL != p_platform_info_blob && p_platform_info_blob->valid_info_blob) {
        const uint16_t* p = reinterpret_cast<const uint16_t*>(p_platform_info_blob->platform_info_blob.pse_evaluation_flags);
        *pflags = lv_ntohs(*p);
    }
    else {
        retval = AE_INVALID_PARAMETER;
    }
    return retval;
}
bool PlatformInfoLogic::sgx_gid_out_of_date(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
    uint8_t flags = 0;
    bool retVal = false;
    ae_error_t getflagsError = get_sgx_epid_group_flags(p_platform_info_blob, &flags);
    if (AE_SUCCESS == getflagsError) {
        retVal = (0 != (QE_EPID_GROUP_OUT_OF_DATE & flags));
    }
    SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", retVal, retVal);

    return retVal;
}
bool PlatformInfoLogic::cse_gid_out_of_date(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
    uint16_t flags = 0;
    bool retVal = false;
    ae_error_t getflagsError = PlatformInfoLogic::get_pse_evaluation_flags(p_platform_info_blob, &flags);
    if (AE_SUCCESS == getflagsError) {
        retVal = static_cast<bool>(flags & EPID_GROUP_ID_BY_PS_HW_GID_REVOKED);
    }
    SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", retVal, flags);

    return retVal;
}

uint32_t PlatformInfoLogic::latest_psda_svn(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
    uint32_t psda_svn = 0;
    // value of latest psda svn in platform info blob
    if (NULL != p_platform_info_blob && p_platform_info_blob->valid_info_blob)
    {
        //psda_svn = *((uint32_t*)p_platform_info_blob->platform_info_blob.latest_psda_svn);
        const uint32_t* p = reinterpret_cast<const uint32_t*>(p_platform_info_blob->platform_info_blob.latest_psda_svn);
        psda_svn = lv_ntohl(*p);
    }

    SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", psda_svn, psda_svn);
    return psda_svn;
}

uint16_t PlatformInfoLogic::latest_pse_svn(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
    uint16_t pse_svn = 0;
    // value of latest psda svn in platform info blob
    if (NULL != p_platform_info_blob && p_platform_info_blob->valid_info_blob)
    {
        //pse_svn = *((uint16_t*)p_platform_info_blob->platform_info_blob.latest_pse_isvsvn);
        const uint16_t* p = reinterpret_cast<const uint16_t*>(p_platform_info_blob->platform_info_blob.latest_pse_isvsvn);
        pse_svn = lv_ntohs(*p);
    }

    SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", pse_svn, pse_svn);
    return pse_svn;
}

bool PlatformInfoLogic::performance_rekey_available(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
    //
    // return whether platform info blob says PR is available
    // the group associated with PR that's returned corresponds to the group
    // that we'll be in **after** executing PR
    //
    bool retVal = false;
    uint8_t flags;
    ae_error_t getflagsError = get_sgx_epid_group_flags(p_platform_info_blob, &flags);
    if (AE_SUCCESS == getflagsError) {
        retVal = static_cast<bool>(flags & PERF_REKEY_FOR_QE_EPID_GROUP_AVAILABLE);
    }
    SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", retVal, retVal);
    return retVal;
}
bool PlatformInfoLogic::old_epid11_rls(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
    bool retval = false;
    // would it ever be important/necessary/desirable to only get one of the RLs: either Priv or Sig?
    // check bit and return true if set
    uint16_t pse_eval_flags = 0;
    ae_error_t getflagsError = get_pse_evaluation_flags(p_platform_info_blob, &pse_eval_flags);
    if (AE_SUCCESS == getflagsError) {
        retval = static_cast<bool>(pse_eval_flags & (SIGRL_VER_FROM_PS_HW_SIG_RLVER_OUT_OF_DATE | PRIVRL_VER_FROM_PS_HW_PRV_KEY_RLVER_OUT_OF_DATE));
    }

    SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", retval, retval);
    return retval;
}

bool PlatformInfoLogic::ps_collectively_not_uptodate(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
    uint16_t pse_eval_flags = 0;
    ae_error_t getflagsError = get_pse_evaluation_flags(p_platform_info_blob, &pse_eval_flags);
    if (AE_SUCCESS == getflagsError) {
        return (pse_eval_flags != 0);
    }

    return false;
}
bool PlatformInfoLogic::qe_svn_out_of_date(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
    uint16_t flags = 0;
    bool retVal = true;
    ae_error_t getflagsError = get_sgx_tcb_evaluation_flags(p_platform_info_blob, &flags);
    if (AE_SUCCESS == getflagsError) {
        retVal = (0 != (QUOTE_ISVSVN_QE_OUT_OF_DATE & flags));
    }
    SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", retVal, retVal);
    return retVal;
}

bool PlatformInfoLogic::pce_svn_out_of_date(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
    uint16_t flags = 0;
    bool retVal = true;
    ae_error_t getflagsError = get_sgx_tcb_evaluation_flags(p_platform_info_blob, &flags);
    if (AE_SUCCESS == getflagsError) {
        retVal = (0 != (QUOTE_ISVSVN_PCE_OUT_OF_DATE & flags));
    }
    SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", retVal, retVal);
    return retVal;
}

bool PlatformInfoLogic::cpu_svn_out_of_date(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
    uint16_t flags = 0;
    bool retVal = false;
    ae_error_t getflagsError = get_sgx_tcb_evaluation_flags(p_platform_info_blob, &flags);
    if (AE_SUCCESS == getflagsError) {
        retVal = (0 != (QUOTE_CPUSVN_OUT_OF_DATE & flags));
    }
    SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", retVal, retVal);

    return retVal;
}
bool PlatformInfoLogic::pse_svn_out_of_date(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
    uint16_t flags = 0;
    //
    // default to true since easy to update PSE
    //
    bool retVal = true;
    ae_error_t getflagsError = get_pse_evaluation_flags(p_platform_info_blob, &flags);
    if (AE_SUCCESS == getflagsError) {
        retVal = (0 != (PSE_ISVSVN_OUT_OF_DATE & flags));
    }

    SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", retVal, retVal);

    return retVal;
}


bool PlatformInfoLogic::psda_svn_out_of_date(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
    uint16_t flags = 0;
    ae_error_t getflagsError = PlatformInfoLogic::get_pse_evaluation_flags(p_platform_info_blob, &flags);
    //
    // default to true since easy to update PSDA
    //
    bool retval = true;
    if (AE_SUCCESS == getflagsError) {
        retval = static_cast<bool>(flags & SVN_FROM_PS_HW_SEC_INFO_OUT_OF_DATE);
    }

    SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", retval, flags);

    return retval;
}
ae_error_t PlatformInfoLogic::need_epid_provisioning(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
    ae_error_t status = AESM_NEP_DONT_NEED_EPID_PROVISIONING;
    if (sgx_gid_out_of_date(p_platform_info_blob) &&
        !qe_svn_out_of_date(p_platform_info_blob) &&
        !cpu_svn_out_of_date(p_platform_info_blob) &&
        !pce_svn_out_of_date(p_platform_info_blob))
    {
        status = AESM_NEP_DONT_NEED_UPDATE_PVEQE;      // don't need update, but need epid provisioning
    }
    else if (!sgx_gid_out_of_date(p_platform_info_blob) && performance_rekey_available(p_platform_info_blob))
    {
        status = AESM_NEP_PERFORMANCE_REKEY;
    }
    SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", status, status);
    return status;
}
//
// return values
// AESM_NPC_DONT_NEED_PSEP: cert present, ltp blob present and current pse version at least matches
//                 pse version in cert - may also be latest; default
// AESM_NPC_NO_PSE_CERT: no cert or no ltp blob
ae_error_t PlatformInfoLogic::need_pse_cert_provisioning()
{
    AESM_DBG_TRACE("enter fun");
    ae_error_t status = AESM_NPC_DONT_NEED_PSEP;

    if (Helper::noPseCert() ||
        Helper::noLtpBlob())                        // long-term pairing blob holds verifier/pse private key
    {
        status = AESM_NPC_NO_PSE_CERT;               // break this up in order to distinguish between no cert and no ltp blob?
    }

    SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", status, status);
    return status;
}


//
// return values
// SUCCESS:
// NO_LONGTERM_PAIRING_BLOB:
// DONT_NEED_UPDATE_PAIR_LTP: psda svn now up to date
// MAY_NEED_UPDATE_LTP: psda updated, but may not be up to date
// OLD_EPID11_RLS:
//
ae_error_t PlatformInfoLogic::need_long_term_pairing(const platform_info_blob_wrapper_t* platformInfoBlobWrapper)
{
    AESM_DBG_TRACE("enter fun");
    ae_error_t status = AE_SUCCESS;

    pairing_blob_t pairing_blob;

    if (AE_FAILED(Helper::read_ltp_blob(pairing_blob)))
    {
        status = AESM_NLTP_NO_LTP_BLOB;
    }
    else if (Helper::noPseCert())
    {
        status = AESM_NPC_NO_PSE_CERT;
    }
    else
    {
        uint32_t current_psda_svn = PSDAService::instance().psda_svn;

        pse_pr_interface_psda* pPSDA = NULL;

        pPSDA = new(std::nothrow) pse_pr_interface_psda();
        if (pPSDA == NULL) {
            return AE_OUT_OF_MEMORY_ERROR;
        }
        EPID_GID meGid;

        if (NULL != platformInfoBlobWrapper)
        {

            //
            // psda svn was bad, cse gid was good and now psda svn is good
            // fact that we may not be able to get current PSDA SVN doesn't matter
            // here as long as expression involving it evaluates to false
            //
            uint32_t pib_psda_svn;
            pib_psda_svn = latest_psda_svn(platformInfoBlobWrapper);

            //pairing_blob_t pairing_blob;
            //Helper::read_ltp_blob(pairing_blob);

            //
            // psda svn was bad, cse gid was good and now psda svn is good
            // fact that we may not be able to get current PSDA SVN doesn't matter
            // here as long as expression involving it evaluates to false
            //
            if (((psda_svn_out_of_date(platformInfoBlobWrapper)) &&
                (current_psda_svn == pib_psda_svn)) || 0)
                //(!psda_svn_out_of_date(platformInfoBlobWrapper) && cse_gid_out_of_date(platformInfoBlobWrapper)))
            {
                status = AESM_NLTP_DONT_NEED_UPDATE_PAIR_LTP;      // don't need update, but need pairing
            }
            else if (cse_gid_out_of_date(platformInfoBlobWrapper)) {
                if (AE_SUCCESS == pPSDA->get_csme_gid(&meGid)) {
                    if (Helper::ltpBlobCseGid(pairing_blob) != meGid) {
                        status = AESM_NLTP_DONT_NEED_UPDATE_PAIR_LTP;
                    }
                }
                else {
                    status = AESM_NLTP_DONT_NEED_UPDATE_PAIR_LTP;
                }

            }
            //
            // we need to handle cases where current psda svn or psda svn in ltp
            // blob is unavailable
            // no ltp blob is handled above
            // not being able to get current psda svn is handled elsewhere
            // we just need to make sure we don't return something misleading
            //
            else if (newer_psda_svn(current_psda_svn, Helper::ltpBlobPsdaSvn(pairing_blob)))
                //(currentCse_gid() != ltpBlobCse_gid()))                       // assume no rollback
            {
                status = AESM_NLTP_MAY_NEED_UPDATE_LTP;       // may need update and need pairing
            }
            else if (old_epid11_rls(platformInfoBlobWrapper))
            {
                status = AESM_NLTP_OLD_EPID11_RLS;
            }
        }
        else
        {
            if ((AE_SUCCESS == pPSDA->get_csme_gid(&meGid)) && (Helper::ltpBlobCseGid(pairing_blob) != meGid)) {
                status = AESM_NLTP_DONT_NEED_UPDATE_PAIR_LTP;

            }
            //
            // see comment above about what happens when no ltp blob
            // or we can't get current psda svn
            //
            else if (newer_psda_svn(current_psda_svn, Helper::ltpBlobPsdaSvn(pairing_blob)))
                //(currentCse_gid() != ltpBlobCse_gid()))
            {
                status = AESM_NLTP_MAY_NEED_UPDATE_LTP;
            }
        }

        delete pPSDA;
        pPSDA = NULL;
    }
    SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", status, status);
    return status;
}

//
// return values
// NEED_PSE_UPDATE: pse out of date, cert matches pse
// SUCCESS: have new cert
// PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_NEED_EPID_UPDATE:
// PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_MIGHT_NEED_EPID_UPDATE:
// SIMPLE_PSE_CERT_PROVISIONING_ERROR: internal error during cert provisioning
// SIMPLE_EPID_PROVISION_ERROR: internal error during epid provisioning during cert provisioning
//
ae_error_t PlatformInfoLogic::pse_cert_provisioning_helper(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
    AESM_DBG_TRACE("enter fun");
    ae_error_t status = AESM_NPC_DONT_NEED_PSEP;

    ae_error_t npcStatus = need_pse_cert_provisioning();
    switch (npcStatus)
    {
    default:
    {
        assert(false); break;
    }
    case AESM_NPC_DONT_NEED_PSEP:
    {
        status = AESM_PCP_NEED_PSE_UPDATE;
        break;
    }
    case AESM_NPC_NO_PSE_CERT:
    {
        platform_info_blob_wrapper_t new_platform_info_blob;
        new_platform_info_blob.valid_info_blob = 0;
        AESM_DBG_INFO("helper; redo certificate provisioning");
        ae_error_t cpStatus = CPSEPRClass::instance().certificate_provisioning(&new_platform_info_blob);
        SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION("cpStatus = ", cpStatus, cpStatus);

        switch (cpStatus)
        {
        case AE_SUCCESS:
        case OAL_PROXY_SETTING_ASSIST:
        case PSW_UPDATE_REQUIRED:
        case AESM_AE_OUT_OF_EPC:
        case OAL_NETWORK_UNAVAILABLE_ERROR:
        {
            status = cpStatus;
            break;
        }
        case AESM_CP_ATTESTATION_FAILURE:
        {
            status = attestation_failure_in_pse_cert_provisioning(p_platform_info_blob);
            break;
        }

        default:
        {
            status = AESM_PCP_SIMPLE_PSE_CERT_PROVISIONING_ERROR;
            break;
        }
        }
        break;
    }
    }

    SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", status, status);
    return status;
}

