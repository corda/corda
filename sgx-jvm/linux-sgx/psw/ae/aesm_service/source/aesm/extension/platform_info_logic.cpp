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

#include "util.h"
#include "platform_info_logic.h"
#include "sgx_quote.h"
#include "aesm_encode.h"
#include "pve_logic.h"
#include "aesm_logic.h"
#include "PSEClass.h"
#include "PSEPRClass.h"
#include "PSDAService.h"
#include "upse/helper.h"
#include "interface_psda.h"
#include "aesm_pse_status.h"
#include <assert.h>
#include "sgx_profile.h"
#include "aesm_long_lived_thread.h"

ae_error_t PlatformInfoLogic::check_ltp_thread_func(bool& is_new_pairing)
{

    AESM_DBG_TRACE("enter fun");
    ae_error_t psStatus = AE_SUCCESS;
    //platform_info_blob_wrapper_t platform_info_blob;
    is_new_pairing = false;
    //
    // if long-term pairing fails, we may run cert provisioning,
    // but long-term pairing may not fail when current pse is
    // newer than pse that was current last time provisioning ran
    // so check that here
    //

    // Put the PS init log in this method so it doesn't get logged multiple times in the invoking function
    // in the BUSY thread case
    AESM_LOG_INFO_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_START]);

    ae_error_t npcStatus = need_pse_cert_provisioning();
    switch (npcStatus)
    {
    default:
        {
            assert(false); break;
        }
    case AESM_NPC_DONT_NEED_PSEP:
        {
            break;
        }
    case AESM_NPC_NO_PSE_CERT:
        {
            ae_error_t pcphStatus = pse_cert_provisioning_helper(NULL);
            switch (pcphStatus)
            {
            case AE_SUCCESS:
                {
                    AESM_DBG_INFO("pcphStatus AE_SUCCESS");
                    break;
                }
            case OAL_NETWORK_UNAVAILABLE_ERROR:
            case OAL_PROXY_SETTING_ASSIST:
            case PSW_UPDATE_REQUIRED:
            case AESM_AE_OUT_OF_EPC:
            case AESM_PCP_NEED_PSE_UPDATE:
            case AESM_PCP_PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_NEED_EPID_UPDATE:
            case AESM_PCP_PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_MIGHT_NEED_EPID_UPDATE:
            case AESM_PCP_SIMPLE_PSE_CERT_PROVISIONING_ERROR:
            case AESM_PCP_SIMPLE_EPID_PROVISION_ERROR:
            case OAL_THREAD_TIMEOUT_ERROR:
                {
#ifdef DBG_LOG
                    ae_error_t& p = pcphStatus;
                    AESM_DBG_ERROR("pcphStatus %s",
                        p==AESM_PCP_NEED_PSE_UPDATE ? "AESM_PCP_NEED_PSE_UPDATE" :
                        p==AESM_PCP_PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_NEED_EPID_UPDATE ? "AESM_PCP_PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_NEED_EPID_UPDATE" :
                        p==AESM_PCP_PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_MIGHT_NEED_EPID_UPDATE ? "AESM_PCP_PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_MIGHT_NEED_EPID_UPDATE" :
                        p==AESM_PCP_SIMPLE_PSE_CERT_PROVISIONING_ERROR ? "AESM_PCP_SIMPLE_PSE_CERT_PROVISIONING_ERROR" :
                        p==AESM_PCP_SIMPLE_EPID_PROVISION_ERROR ? "AESM_PCP_SIMPLE_EPID_PROVISION_ERROR" : "OAL_THREAD_TIMEOUT_ERROR");
#endif
                    return pcphStatus;
                }
            default:
                {
                    assert(false); break;
                }
            }
            break;
        }
    }
    ae_error_t nltpStatus = need_long_term_pairing(NULL);
    switch (nltpStatus)
    {
    case AE_SUCCESS:
    case AESM_NPC_NO_PSE_CERT:
    case AE_FAILURE:
        {
            break;
        }
    case AESM_NLTP_NO_LTP_BLOB:
    case AESM_NLTP_DONT_NEED_UPDATE_PAIR_LTP:
    case AESM_NLTP_MAY_NEED_UPDATE_LTP:
        //case AESM_NLTP_OLD_EPID11_RLS:              // not possible since no info blob
        {
            ae_error_t ltpStatus = CPSEPRClass::instance().long_term_pairing(&is_new_pairing);
            //
            // what do we do if new pairing?
            //
            SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" ltpStatus = ", ltpStatus, __LINE__);
            switch(ltpStatus)
            {
            case AE_SUCCESS:
                {
                    break;
                }
            case OAL_PROXY_SETTING_ASSIST:
                {
                    return OAL_PROXY_SETTING_ASSIST;
                }
            case AESM_AE_OUT_OF_EPC:
                {
                    return AESM_AE_OUT_OF_EPC;
                }
            case AESM_NPC_NO_PSE_CERT:
            case AESM_LTP_PSE_CERT_REVOKED:
            case PSE_PAIRING_BLOB_UNSEALING_ERROR:
            case PSE_PAIRING_BLOB_INVALID_ERROR:
            case AESM_PSDA_LT_SESSION_INTEGRITY_ERROR:
                {
                    ae_error_t pcphStatus = pse_cert_provisioning_helper(NULL);
                    switch(pcphStatus)
                    {
                    case OAL_NETWORK_UNAVAILABLE_ERROR:
                    case OAL_PROXY_SETTING_ASSIST:
                    case PSW_UPDATE_REQUIRED:
                    case AESM_AE_OUT_OF_EPC:
                    case AESM_PCP_PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_MIGHT_NEED_EPID_UPDATE:
                    case AESM_PCP_SIMPLE_PSE_CERT_PROVISIONING_ERROR:
                    case AESM_PCP_SIMPLE_EPID_PROVISION_ERROR:
                    case AESM_PCP_PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_NEED_EPID_UPDATE:
                    case AESM_PCP_NEED_PSE_UPDATE:
                        {
                            AESM_DBG_ERROR("pcphStatus: (ae%d)", pcphStatus);
                            psStatus = pcphStatus;
                            break;
                        }
                    case AE_SUCCESS:
                        {
                            //
                            // retry one time
                            //
                            ltpStatus = CPSEPRClass::instance().long_term_pairing(&is_new_pairing);
                            SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" ltpStatus = ", ltpStatus, __LINE__);
                            switch (ltpStatus)
                            {
                            case AE_SUCCESS:
                                {
                                    break;
                                }
                            case OAL_PROXY_SETTING_ASSIST:
                            case AESM_AE_OUT_OF_EPC:
                            case OAL_THREAD_TIMEOUT_ERROR:
                                {
                                    AESM_DBG_ERROR("long_term_pairing Return: (ae0x%X)", ltpStatus);
                                    psStatus = ltpStatus;
                                    break;
                                }
                            case AESM_NPC_NO_PSE_CERT:
                            case AESM_LTP_PSE_CERT_REVOKED:
                                {
                                    AESM_DBG_ERROR("long_term_pairing Return: (ae0x%X)", ltpStatus);
                                    AESM_LOG_ERROR("%s",g_event_string_table[SGX_EVENT_LTP_FAILURE]);
                                    psStatus = AESM_LTP_SIMPLE_LTP_ERROR;
                                    break;
                                }
                            default:
                                {
                                    psStatus = AESM_LTP_SIMPLE_LTP_ERROR;
                                    break;
                                }
                            }
                            break;
                        }
                    default:
                        {
                            assert(false); break;
                        }
                    }
                    break;
                }
            default:
                {
                    psStatus = AESM_LTP_SIMPLE_LTP_ERROR;
                    break;
                }

            }
            break;
        }
    default:
        {
            assert(false); break;
        }
    }
    return psStatus;
}


//AESM_NPC_DONT_NEED_PSEP: don't pse provisioning or long term pairing
//AE_SUCCESS: pse provisioning and long term pairing success
//other error code: failure
static ae_error_t check_ltp(bool* is_new_pairing)
{
    AESM_DBG_TRACE("enter fun");
    ae_error_t ae_ret = PlatformInfoLogic::need_pse_cert_provisioning();
    if(AESM_NPC_DONT_NEED_PSEP == ae_ret &&
        AE_SUCCESS == PlatformInfoLogic::need_long_term_pairing(NULL))
    {
        AESM_DBG_TRACE("dont need psep");
        *is_new_pairing = false;
        return AESM_NPC_DONT_NEED_PSEP;
    }

    return start_check_ltp_thread(*is_new_pairing);
}


//
// call at beginning of create_session()
//
ae_error_t PlatformInfoLogic::create_session_pre_internal(void)
{
    AESM_DBG_TRACE("enter fun");
    bool is_new_pairing = false;
    ae_error_t psStatus = check_ltp(&is_new_pairing);
    if (psStatus == AE_SUCCESS)
    {

        // long term pairing will load pse-pr enclave , which will unload pse-op enclave and
        // break ephemeral session
        ae_error_t ret = CPSEClass::instance().create_ephemeral_session_pse_cse(is_new_pairing, true);
        if (ret != AE_SUCCESS)
        {
            AESM_DBG_ERROR("Long term pairing succeeded but ephemeral session failed(ae%d)",ret);
            if(ret == AESM_AE_OUT_OF_EPC)
            {
                // Return AESM_AE_OUT_OF_EPC
                psStatus = AESM_AE_OUT_OF_EPC;
            }
            // ignore other return value
        }
    }
    else if (AESM_NPC_DONT_NEED_PSEP == psStatus)
    {
        psStatus = AE_SUCCESS;
    }
    else {
        PlatformServiceStatus::instance().set_platform_service_status(PLATFORM_SERVICE_NOT_READY);
    }

    // Log result to the Admin log
    switch (psStatus)
    {
    case AE_SUCCESS:
    case OAL_THREAD_TIMEOUT_ERROR:
        // no logging needed
        break;
    case OAL_PROXY_SETTING_ASSIST:
        // don't log an error here
        break;
    case PSW_UPDATE_REQUIRED:
        AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_FAIL_PSWVER]);
        break;
    case AESM_AE_OUT_OF_EPC:
        AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_FAIL]);
        break;
    case AESM_PCP_NEED_PSE_UPDATE:
    case AESM_PCP_PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_NEED_EPID_UPDATE:
    case AESM_PCP_PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_MIGHT_NEED_EPID_UPDATE:
    case AESM_PCP_SIMPLE_PSE_CERT_PROVISIONING_ERROR:
    case AESM_PCP_SIMPLE_EPID_PROVISION_ERROR:
    default:
        AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_FAIL_LTP]);
        break;
    }

    return psStatus;
}

ae_error_t PlatformInfoLogic::update_pse_thread_func(const platform_info_blob_wrapper_t* p_platform_info, uint32_t attestation_status)
{
    AESM_DBG_TRACE("enter fun");
    ae_error_t ltpStatus = AE_SUCCESS;
    ae_error_t retVal = AESM_LTP_SIMPLE_LTP_ERROR;
    bool is_new_pairing = false;

    // Put the PS init log in this method so it doesn't get logged multiple times in the invoking function
    // in the BUSY thread case
    // Note: Are there cases where we call into here but don't actually reinit PS!? If so, need to conditionally log here
    // What about AESM_NLTP_DONT_NEED_UPDATE_PAIR_LTP?
    AESM_LOG_INFO_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_START]);

    ae_error_t pcphStatus = AE_SUCCESS;
    if (Helper::noLtpBlob() || Helper::noPseCert())
    {
        pcphStatus = pse_cert_provisioning_helper(p_platform_info);
        AESM_DBG_TRACE("pse_cert_provisioning_helper Return: (ae%d)", pcphStatus);
        if (AE_SUCCESS == pcphStatus)
        {
            pcphStatus = CPSEPRClass::instance().long_term_pairing(&is_new_pairing);
            AESM_DBG_TRACE("long_term_pairing Return: (ae%d)", pcphStatus);
            if(OAL_PROXY_SETTING_ASSIST == pcphStatus){
                return OAL_PROXY_SETTING_ASSIST;
            }
            else if (AESM_AE_OUT_OF_EPC == pcphStatus)
                return AESM_AE_OUT_OF_EPC;
        }
        else if (AESM_AE_OUT_OF_EPC == pcphStatus)
            return AESM_AE_OUT_OF_EPC;
    }

    ae_error_t nltpStatus = need_long_term_pairing(p_platform_info);
    AESM_DBG_TRACE("need_long_term_pairing result (ae%d)", nltpStatus);

    switch (nltpStatus)
    {
    case AE_SUCCESS:
    case AESM_NLTP_MAY_NEED_UPDATE_LTP:       // get this case in create_session
        {
            retVal = AE_SUCCESS;
            break;
        }

    case AESM_NLTP_NO_LTP_BLOB:  // maybe we should only handle this case in create_session to be consistent with epid
    case AESM_NLTP_DONT_NEED_UPDATE_PAIR_LTP:
    case AESM_NLTP_OLD_EPID11_RLS:
        {
            //
            // long-term pairing won't catch all cases where cert is out of date
            // so could check here, but we opt to only do this in create_session
            // only do this if app tells us its attestation failed (attestation_status != 0)
            //
            if (attestation_status || ((AESM_NLTP_NO_LTP_BLOB == nltpStatus) || (AESM_NLTP_DONT_NEED_UPDATE_PAIR_LTP == nltpStatus)))
            {
                ltpStatus = CPSEPRClass::instance().long_term_pairing(&is_new_pairing);
                switch(ltpStatus)
                {
                case AE_SUCCESS:
                    {
                        retVal = AE_SUCCESS;
                        break;
                    }
                case OAL_PROXY_SETTING_ASSIST:
                case PSW_UPDATE_REQUIRED:
                case AESM_AE_OUT_OF_EPC:
                    {
                        AESM_DBG_TRACE("long_term_pairing Return: (ae%d)", ltpStatus);
                        retVal = ltpStatus;
                        break;
                    }
                case AESM_NPC_NO_PSE_CERT:
                case AESM_LTP_PSE_CERT_REVOKED:
                case PSE_PAIRING_BLOB_UNSEALING_ERROR:
                case PSE_PAIRING_BLOB_INVALID_ERROR:
                case AESM_PSDA_LT_SESSION_INTEGRITY_ERROR:
                    {
                        AESM_DBG_TRACE("long_term_pairing Return: (ae%d)", ltpStatus);
                        pcphStatus = pse_cert_provisioning_helper(p_platform_info);
                        switch(pcphStatus)
                        {
                        case OAL_NETWORK_UNAVAILABLE_ERROR:
                        case OAL_PROXY_SETTING_ASSIST:
                        case PSW_UPDATE_REQUIRED:
                        case AESM_AE_OUT_OF_EPC:
                            {
                                AESM_DBG_TRACE("pse_cert_provisioning_helper Return: (ae%d)", pcphStatus);
                                return pcphStatus;
                            }
                        case AESM_PCP_PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_MIGHT_NEED_EPID_UPDATE:
                        case AESM_PCP_SIMPLE_PSE_CERT_PROVISIONING_ERROR:
                        case AESM_PCP_SIMPLE_EPID_PROVISION_ERROR:
                        case AESM_PCP_PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_NEED_EPID_UPDATE:
                        case AESM_PCP_NEED_PSE_UPDATE:
                            {
                                AESM_DBG_TRACE("pse_cert_provisioning_helper Return: (ae%d)", pcphStatus);
                                break;
                            }
                        case AE_SUCCESS:
                            {
                                //
                                // retry one time
                                //
                                ltpStatus = CPSEPRClass::instance().long_term_pairing(&is_new_pairing);
                                switch (ltpStatus)
                                {
                                case AE_SUCCESS:
                                    {
                                        retVal = AE_SUCCESS;
                                        break;
                                    }
                                case OAL_PROXY_SETTING_ASSIST:
                                        return OAL_PROXY_SETTING_ASSIST;
                                case AESM_AE_OUT_OF_EPC:
                                        return AESM_AE_OUT_OF_EPC;
                                case AESM_NPC_NO_PSE_CERT:
                                case AESM_LTP_PSE_CERT_REVOKED:
                                    {
                                        AESM_DBG_ERROR("long_term_pairing Return: (ae%d)", ltpStatus);
                                        AESM_LOG_ERROR("%s",g_event_string_table[SGX_EVENT_LTP_FAILURE]);
                                        break;
                                    }
                                default:
                                    {
                                        break;
                                    }
                                }
                                break;
                            }
                        default:
                            {
                                break;
                            }
                    }
                        break;
                    }
                default:
                    {
                        break;
                    }

            }
            }

            break;
        }
    default:
        {
            break;
        }
    }
    return retVal;
}

aesm_error_t PlatformInfoLogic::report_attestation_status(
    uint8_t* platform_info, uint32_t platform_info_size,
    uint32_t attestation_status,
    uint8_t* update_info, uint32_t update_info_size)
{

    AESM_DBG_TRACE("enter fun");
    //
    // we don't do anything without platform info
    //
    if (NULL == platform_info) {
        return AESM_PARAMETER_ERROR;
    }

    platform_info_blob_wrapper_t pibw;


    //
    // presence of platform info is conditional, on whether we're up to date
    // if we're up to date, no platform info and no need for update info
    //
    if (((sizeof(pibw.platform_info_blob) > platform_info_size)) || ((NULL != update_info) && (sizeof(sgx_update_info_bit_t) > update_info_size))) {
        return AESM_PARAMETER_ERROR;
    }

    pibw.valid_info_blob = false;
    memcpy_s(&pibw.platform_info_blob, sizeof(pibw.platform_info_blob), platform_info, platform_info_size);

    aesm_error_t status = AESM_SUCCESS;       // status only tells app to look at updateInfo

    //
    // we want to know what ias based its decision on; ie, some ltp blob
    // so it's important that we take a snapshot of the ltp blob before
    // we potentially trigger ltp and it's better, in general, to
    // read it asap since other threads could be triggering ltp (our
    // service locks help with this, but there's no harm in reading it
    // early especially since it's conditional).
    //
    pairing_blob_t pairing_blob;
    ae_error_t readLtpBlobStatus = AE_FAILURE;
    //
    // only need to read ltp blob (know what was reported to ias) if attestation
    // being reported on failed and we have an update info structure to fill in
    //
    if ((0 != attestation_status) && (NULL != update_info)) {
        readLtpBlobStatus = Helper::read_ltp_blob(pairing_blob);
        if (AE_FAILED(readLtpBlobStatus))
        {
            AESM_DBG_ERROR("read_ltp_blob Return: (ae%d)", readLtpBlobStatus);
        }
    }
    //
    // contents of input platform info can get stale, but not by virtue of anything we do
    // (the latest/current versions can change)
    // therefore, we'll use the same platform info the whole time
    //
    bool pibSigGood = (AE_SUCCESS == pib_verify_signature(pibw));
    //
    // invalid pib is an error whenever it's provided
    //
    if (!pibSigGood) {
        AESM_DBG_ERROR("pib verify signature failed");
        return AESM_PLATFORM_INFO_BLOB_INVALID_SIG;
    }
    if(pibw.platform_info_blob.xeid != AESMLogic::get_active_extended_epid_group_id()){
        return AESM_UNEXPECTED_ERROR;
    }
    uint32_t gid_mt_result = AESMLogic::is_gid_matching_result_in_epid_blob( pibw.platform_info_blob.gid);
    if(AESMLogic::GIDMT_UNMATCHED == gid_mt_result||
        AESMLogic::GIDMT_UNEXPECTED_ERROR == gid_mt_result){
            return AESM_UNEXPECTED_ERROR;
    }
    else if (AESMLogic::GIDMT_NOT_AVAILABLE == gid_mt_result) {
            return AESM_EPIDBLOB_ERROR;
    }

    ae_error_t nepStatus = need_epid_provisioning(&pibw);
    AESM_DBG_TRACE("need_epid_provisioning return (ae%d)",nepStatus);
    switch (nepStatus)
    {
    case AESM_NEP_DONT_NEED_EPID_PROVISIONING:
        {
            break;
        }
    case AESM_NEP_DONT_NEED_UPDATE_PVEQE:       // sure thing
        {
            AESMLogicLock lock(AESMLogic::_qe_pve_mutex);
            if(!query_pve_thread_status()){//If another thread is busy on Epid Provisioning
                status = AESM_SUCCESS;
                break;
            }
            bool perfRekey = false;
            status = PvEAESMLogic::provision(perfRekey, THREAD_TIMEOUT);
            if (AESM_BUSY == status || //thread timeout
                AESM_PROXY_SETTING_ASSIST == status || //uae service need to set up proxy info and retry
                AESM_UPDATE_AVAILABLE == status || //PSW need be updated
                AESM_UNRECOGNIZED_PLATFORM == status || //Platform not recognized by Provisioning backend
                AESM_OUT_OF_EPC == status) // out of EPC
            {
                return status;//We should return to uae serivce directly
            }
            if (AESM_SUCCESS != status &&
                AESM_OUT_OF_MEMORY_ERROR != status &&
                AESM_BACKEND_SERVER_BUSY != status &&
                AESM_NETWORK_ERROR != status &&
                AESM_NETWORK_BUSY_ERROR != status)
            {
                status = AESM_SGX_PROVISION_FAILED;
            }
            break;
        }
    case AESM_NEP_PERFORMANCE_REKEY:
        {
            if (0 == attestation_status)           // pr only if we succeeded (also we'll never get pr unless gid up-to-date)
            {
                bool perfRekey = true;
                AESMLogicLock lock(AESMLogic::_qe_pve_mutex);
                if(!query_pve_thread_status()){//If another thread is busy on Epid Provisioning
                    status = AESM_SUCCESS;
                    break;
                }
                status = PvEAESMLogic::provision(perfRekey, THREAD_TIMEOUT);
                if (AESM_BUSY == status ||//thread timeout
                    AESM_PROXY_SETTING_ASSIST == status ||//uae service need to set up proxy info and retry
                    AESM_UPDATE_AVAILABLE == status ||
                    AESM_UNRECOGNIZED_PLATFORM == status ||
                    AESM_OUT_OF_EPC == status)
                {
                    return status;//We should return to uae serivce directly
                }
                if (AESM_SUCCESS != status &&
                    AESM_OUT_OF_MEMORY_ERROR != status &&
                    AESM_BACKEND_SERVER_BUSY != status &&
                    AESM_NETWORK_ERROR != status &&
                    AESM_NETWORK_BUSY_ERROR != status)
                {
                    status = AESM_SGX_PROVISION_FAILED;
                }
            }
            break;
        }
    default:
        {
            status = AESM_UNEXPECTED_ERROR;
            break;
        }
    }

    // don't worry about pairing unless indication that PS being used
    if (ps_collectively_not_uptodate(&pibw) && pibw.platform_info_blob.xeid == AESMLogic::get_active_extended_epid_group_id())
    {
        ae_error_t ae_ret = start_update_pse_thread(&pibw, attestation_status);
        switch (ae_ret)
        {
        case AE_SUCCESS:
            break;
        case OAL_THREAD_TIMEOUT_ERROR:
            return AESM_BUSY;
        case PVE_PROV_ATTEST_KEY_NOT_FOUND:
            return AESM_UNRECOGNIZED_PLATFORM;
        case OAL_PROXY_SETTING_ASSIST:
            // don't log an error here
            return AESM_PROXY_SETTING_ASSIST;
        case PSW_UPDATE_REQUIRED:
            AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_FAIL_PSWVER]);
            return AESM_UPDATE_AVAILABLE;
        case AESM_AE_OUT_OF_EPC:
            AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_FAIL_LTP]);
            return AESM_OUT_OF_EPC;
        case AESM_LTP_SIMPLE_LTP_ERROR:
        default:
            AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_FAIL_LTP]);
            break;
        }
    }
    //
    // don't nag happy app about updates
    //
    if ((0 != attestation_status) && (NULL != update_info))
    {
        sgx_update_info_bit_t* p_update_info = (sgx_update_info_bit_t*)update_info;
        memset(p_update_info, 0, sizeof(*p_update_info));

        //
        // here, we treat values that get reported live - cpusvn, qe.isvsvn,
        // in normal flow, live values reported to attestation server will be the same as current values now so
        // we just look at out-of-date bits corresponding to these values.
        // the alternative would be to compare current with latest as reported by IAS. this
        // isn't an option for cpusvn since what we get from IAS is equivalent cpusvn.
        //
        
        
        if (cpu_svn_out_of_date(&pibw))
        {
            p_update_info->ucodeUpdate = 1;
            status = AESM_UPDATE_AVAILABLE;
        }
        if (qe_svn_out_of_date(&pibw) ||
            pce_svn_out_of_date(&pibw)
          ||pse_svn_out_of_date(&pibw))
        {
            p_update_info->pswUpdate = 1;
            status = AESM_UPDATE_AVAILABLE;
        }
        else if (psda_svn_out_of_date(&pibw)) {
            //
            // the psda svn value in quote is from ltp blob -> possibly stale
            // better to determine if update is required by comparing current
            // psda svn to latest as reported by ias in platform info
            //
            // if current is equal to latest, it means code above will have triggered ltp
            //
            if (latest_psda_svn(&pibw) != PSDAService::instance().psda_svn) {
                p_update_info->pswUpdate = 1;
                status = AESM_UPDATE_AVAILABLE;
            }
        }

        if (cse_gid_out_of_date(&pibw)) {

            pse_pr_interface_psda* pPSDA = NULL;

            pPSDA = new(std::nothrow) pse_pr_interface_psda();
            if(pPSDA == NULL){
                 return AESM_OUT_OF_MEMORY_ERROR;
            }
            EPID_GID meGid;

            //
            // compare current CSME GID to one reported to IAS, in LTP blob
            // if same, need update
            // if different, assume subsequent attestation will succeed (basically
            // assume CSME GID is now up-to-date)
            //
            if ((AE_SUCCESS == pPSDA->get_csme_gid(&meGid)) && (AE_SUCCESS == readLtpBlobStatus)) {
                if (Helper::ltpBlobCseGid(pairing_blob) == meGid) {
                    p_update_info->csmeFwUpdate = 1;
                    status = AESM_UPDATE_AVAILABLE;
                }

            }
            else {
                p_update_info->csmeFwUpdate = 1;
                status = AESM_UPDATE_AVAILABLE;
            }
            delete pPSDA;
            pPSDA = NULL;
        }
        //
        // IAS will provide latest PSDA SVN value => avoid ambiguity like one above
        // we may not be able to get current PSDA SVN (and we can know that we didn't get it),
        // for several reasons (no applet file present, no heci, no jhi)
        // i don't really want to further complicate this code, but
        // if we can't get the value in the case here, we should return that
        // "Intel Platform SW" may need to be re-installed
        //

        //
        // what if MEI/HECI, JHI, iCLS isn't present/installed?
        // none of these are in our TCB, but they are necessary to
        // get properties of our TCB, when PS being used =>
        // at least need to document this dependency
        //
    }
    return status;
}




ae_error_t PlatformInfoLogic::attestation_failure_in_pse_cert_provisioning(const platform_info_blob_wrapper_t* p_platform_info_blob)
{
    ae_error_t status = AE_SUCCESS;
    AESM_DBG_TRACE("enter fun");

    if ((NULL != p_platform_info_blob) && (p_platform_info_blob->valid_info_blob)) {
        status = AESM_PCP_NEED_PSE_UPDATE;
        ae_error_t nepStatus = need_epid_provisioning(p_platform_info_blob);
        AESM_DBG_TRACE("need_epid_provisioning return %d",nepStatus);

        switch (nepStatus)
        {
        default:
            {
                assert(false); break;
            }
        case AESM_NEP_DONT_NEED_EPID_PROVISIONING:
            {
                status = AESM_PCP_PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_NEED_EPID_UPDATE;
                break;
            }
        case AESM_NEP_DONT_NEED_UPDATE_PVEQE:
        case AESM_NEP_PERFORMANCE_REKEY:
            {
                aesm_error_t pvStatus = PvEAESMLogic::provision(nepStatus == AESM_NEP_PERFORMANCE_REKEY, AESM_THREAD_INFINITE);
                SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION("pvStatus = ", pvStatus, pvStatus);

                switch (pvStatus)
                {
                case AESM_BUSY:
                    {
                        status = OAL_THREAD_TIMEOUT_ERROR;
                        break;
                    }
                case AESM_UNRECOGNIZED_PLATFORM:
                    {
                        status = PVE_PROV_ATTEST_KEY_NOT_FOUND;
                        break;
                    }
                case AESM_UPDATE_AVAILABLE:
                    {
                        status = PSW_UPDATE_REQUIRED;
                        break;
                    }
                case AESM_OUT_OF_EPC:
                    {
                        status = AESM_AE_OUT_OF_EPC;
                        break;
                    }
                case AESM_SUCCESS:
                    {
                        //gLastEpidProvisioningTuple.blob = epidBlobEquivPsvn();
                        //gLastEpidProvisioningTuple.current = currentPsvn();
                        //
                        // retry one time
                        //
                        AESM_DBG_INFO("attestation; redo certificate provisioning");
                        platform_info_blob_wrapper_t new_platform_info_blob;
                        new_platform_info_blob.valid_info_blob = false;

                        ae_error_t cpStatus = CPSEPRClass::instance().certificate_provisioning(&new_platform_info_blob);
                        SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION("cpStatus = ", cpStatus, cpStatus);
                        switch (cpStatus)
                        {
                        case AE_SUCCESS:
                            {
                                status = AE_SUCCESS; break;
                            }
                        case AESM_CP_ATTESTATION_FAILURE:
                            {
                                status = AESM_PCP_PSE_CERT_PROVISIONING_ATTESTATION_FAILURE_MIGHT_NEED_EPID_UPDATE; break;
                            }
                        case PSW_UPDATE_REQUIRED:
                            {
                                status = PSW_UPDATE_REQUIRED; break;
                            }
                        case AESM_AE_OUT_OF_EPC:
                            {
                                status = AESM_AE_OUT_OF_EPC; break;
                            }
                        default:
                            {
                                status = AESM_PCP_SIMPLE_PSE_CERT_PROVISIONING_ERROR;

                                break;
                            }
                        }
                        break;
                    }
                default:
                    {
                        status = AESM_PCP_SIMPLE_EPID_PROVISION_ERROR;

                        break;
                    }
                }
                break;
            }
        }
    }

    SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" returning ", status, status);
    return status;
}

