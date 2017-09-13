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


#include "pse_op_logic.h"
#include "aesm_logic.h"
#include "PSEClass.h"
#include "PSEPRClass.h"
#include "aesm_long_lived_thread.h"
#include "platform_info_logic.h"
#include <assert.h>

static aesm_error_t pse_ret_to_aesm_ret(ae_error_t ret_pse)
{
    switch(ret_pse)
    {
    case AE_SUCCESS:
        return AESM_SUCCESS;
    case PSE_OP_PARAMETER_ERROR:
        return AESM_PARAMETER_ERROR;
    case PSE_OP_MAX_NUM_SESSION_REACHED:
        return AESM_MAX_NUM_SESSION_REACHED;
    case PSE_OP_SESSION_INVALID:
        return AESM_SESSION_INVALID;
    case PSE_OP_SERVICE_MSG_ERROR:
        return AESM_MSG_ERROR;
    case AESM_PSDA_NOT_AVAILABLE:
        return AESM_PSDA_UNAVAILABLE;
    case PSE_OP_ERROR_KDF_MISMATCH:
        return AESM_KDF_MISMATCH;
    default:
        return AESM_UNEXPECTED_ERROR;
    }
}

// Local helper function to log to the admin log based on error code
// Note: In some cases, PSEOP functions also log PS errors directly
static void log_admin_ps_ae(ae_error_t ae_error_code)
{
    switch (ae_error_code)
    {
    case AE_SUCCESS:
        break;
    case PSE_OP_MAX_NUM_SESSION_REACHED:
        AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_RESOURCE_ERROR]);
        break;
    case PSE_OP_PARAMETER_ERROR:
    case PSE_OP_SESSION_INVALID:
    case PSE_OP_SERVICE_MSG_ERROR:
    case AESM_PSDA_NOT_AVAILABLE:
    default:
        AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_ERROR]);
        break;
    }
}

aesm_error_t PSEOPAESMLogic::get_ps_cap(
    uint64_t* ps_cap)
{
    AESM_DBG_INFO("PSEOPAESMLogic::get_ps_cap");

    ae_error_t ret_pse = CPSEClass::instance().get_ps_cap(ps_cap);

    return pse_ret_to_aesm_ret(ret_pse);
}

// Get ready for PS service. Establish ephemeral session or long term pairing according to current
// status. Update status accordingly.
aesm_error_t PSEOPAESMLogic::prepare_for_ps_request(void)
{
    AESM_DBG_INFO("PSEOPAESMLogic::prepare_for_ps_request");
    pse_status_t status = CPSEClass::instance().get_status();

    switch(status)
    {
    case PSE_STATUS_INIT:
        AESM_DBG_ERROR("unexpeted status PSE_STATUS_INIT");
        AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_CERT_ERROR]);
        return AESM_UNEXPECTED_ERROR;
    case PSE_STATUS_UNAVAILABLE:
        AESM_DBG_ERROR("status PSE_STATUS_UNAVAILABLE");
        AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_DAL_ERROR]);
        return AESM_PSDA_UNAVAILABLE;
    case PSE_STATUS_CSE_PROVISIONED:
        {
        AESM_DBG_TRACE("status PSE_STATUS_CSE_PROVISIONED");
        aesm_error_t ret = establish_ephemeral_session(false);
        // If PS is still not ready after trying to establish the ephemeral session, log a general
        // error. The PS_INIT_FAIL log will have more details, so we don't have to log them here.
        if (CPSEClass::instance().get_status() != PSE_STATUS_SERVICE_READY)
            AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_ERROR]);
        return ret;
        }
    case PSE_STATUS_SERVICE_READY:
        return AESM_SUCCESS;
    default:
        AESM_DBG_ERROR("unexpeted status %d", (int)status);
        return AESM_UNEXPECTED_ERROR;
    }
}

aesm_error_t PSEOPAESMLogic::create_session(
    uint32_t* session_id,
    uint8_t* se_dh_msg1, uint32_t se_dh_msg1_size)
{
    aesm_error_t result = AESM_UNEXPECTED_ERROR;

    // prepare for service request
    result = prepare_for_ps_request();
    if (result != AESM_SUCCESS)
        return result;

    ae_error_t ret_pse = CPSEClass::instance().create_session(
        session_id,
        se_dh_msg1,
        se_dh_msg1_size);

    if (ret_pse == PSE_OP_EPHEMERAL_SESSION_INVALID)
    {
        AESM_DBG_ERROR("Ephemeral session is broken");
        // Ephemeral session is broken , re-establish ephemeral session and retry create_session.
        if ((result = establish_ephemeral_session(true)) != AESM_SUCCESS) {
            AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_ERROR]);
            return result;
        }

        AESM_DBG_INFO("create session again");
        ret_pse = CPSEClass::instance().create_session(
            session_id,
            se_dh_msg1,
            se_dh_msg1_size); 
    }

    log_admin_ps_ae(ret_pse);

    return pse_ret_to_aesm_ret(ret_pse);
}

ae_error_t PSEOPAESMLogic::certificate_provisioning_and_long_term_pairing_func(bool& is_new_pairing)
{
    ae_error_t psStatus = AE_SUCCESS;

    AESM_DBG_INFO("certificate_provisioning_and_long_term_pairing_func()");

    is_new_pairing = false;
    ae_error_t ltpStatus = CPSEPRClass::instance().long_term_pairing(&is_new_pairing);

    SGX_DBGPRINT_ONE_STRING_TWO_INTS_CREATE_SESSION(__FUNCTION__" ltpStatus = ", ltpStatus, __LINE__);
    switch (ltpStatus)
    {
    case AE_SUCCESS:
    case OAL_PROXY_SETTING_ASSIST:
    case AESM_AE_OUT_OF_EPC:
        return ltpStatus;
    // for below errors need to check cert status
    case AESM_NPC_NO_PSE_CERT:
    case AESM_LTP_PSE_CERT_REVOKED:
    case PSE_PAIRING_BLOB_UNSEALING_ERROR:
    case PSE_PAIRING_BLOB_INVALID_ERROR:
    case AESM_PSDA_LT_SESSION_INTEGRITY_ERROR:
        {
            ae_error_t pcphStatus = PlatformInfoLogic::pse_cert_provisioning_helper(NULL);
            switch (pcphStatus)
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
                return pcphStatus;
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
                    case OAL_PROXY_SETTING_ASSIST:
                    case AESM_AE_OUT_OF_EPC:
                    case OAL_THREAD_TIMEOUT_ERROR:
                        psStatus = ltpStatus;
                        break;

                    case AESM_NPC_NO_PSE_CERT:
                    case AESM_LTP_PSE_CERT_REVOKED:
                        {
                            AESM_DBG_ERROR("long_term_pairing Return: 0x%X", ltpStatus);
                            AESM_LOG_ERROR_UNICODE("%s", g_event_string_table[SGX_EVENT_LTP_FAILURE]);
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

    return psStatus;

}

static aesm_error_t redo_long_term_pairing(
    bool* is_new_pairing)
{

    ae_error_t ae_ret = AE_FAILURE;

    ae_ret = start_long_term_pairing_thread(*is_new_pairing);

    switch (ae_ret)
    {
    case AE_SUCCESS:
        return AESM_SUCCESS;
    case OAL_THREAD_TIMEOUT_ERROR:
        return AESM_BUSY;
    case PVE_PROV_ATTEST_KEY_NOT_FOUND:
        return AESM_UNRECOGNIZED_PLATFORM;
    case OAL_PROXY_SETTING_ASSIST:
        return AESM_PROXY_SETTING_ASSIST;
    case PSW_UPDATE_REQUIRED:
        return AESM_UPDATE_AVAILABLE;
    case AESM_AE_OUT_OF_EPC:
        return AESM_OUT_OF_EPC;
    default:
        return AESM_LONG_TERM_PAIRING_FAILED;
        break;
    }
}


aesm_error_t PSEOPAESMLogic::establish_ephemeral_session(bool force_redo)
{
    AESM_DBG_INFO("PSEOPAESMLogic::establish_ephemeral_session");
    ae_error_t ret = AE_SUCCESS;

    // If session already exists and force_redo is false, session is already ready
    if (force_redo == false && CPSEClass::instance().get_status() == PSE_STATUS_SERVICE_READY) 
        return AESM_SUCCESS;

    AESM_LOG_INFO_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_START]);

    // establish ephemeral session
    // Note: Admin Logging of success/failure after this point is owned by create_ephemeral_session_pse_cse()
    ret = CPSEClass::instance().create_ephemeral_session_pse_cse(false, force_redo);

    // Attempt retry/recovery, where appropriate
    switch (ret) 
    {
    case PSE_PAIRING_BLOB_UNSEALING_ERROR:
    case PSE_PAIRING_BLOB_INVALID_ERROR:
    case AESM_PSDA_NEED_REPAIRING:
    case PSE_OP_ERROR_EPH_SESSION_ESTABLISHMENT_INTEGRITY_ERROR:
    case PSE_OP_LTPB_SEALING_OUT_OF_DATE:
    {
        // Pairing blob doesn't exist or blob size is wrong
        // or pse-op fails to unseal pairing blob
        // or PSDA reports session integrity error
        // or pse-op reports session integrity error
        bool is_new_pairing = false;//out
        aesm_error_t retValue = AESM_SUCCESS;
        retValue = redo_long_term_pairing(&is_new_pairing);
        if (AESM_SUCCESS == retValue)
        {
            // retry ephemeral session
            ret = CPSEClass::instance().create_ephemeral_session_pse_cse(is_new_pairing, true);
            goto exit; // handle non retry results for both create_ephemeral_session_pse_cse() calls below
        }

        // else log failure, since we're returning here
        switch (retValue)
        {
        case AESM_SUCCESS:  // handled above
        case AESM_BUSY:     // don't log an error here
            break;
        case AESM_PROXY_SETTING_ASSIST: // don't log an error here
            break;
        case AESM_UPDATE_AVAILABLE:
            AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_FAIL_PSWVER]);
            break;
        case AESM_OUT_OF_EPC:
            AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_FAIL]);
            break;
        case AESM_LONG_TERM_PAIRING_FAILED:
        default:
            AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_FAIL_LTP]);
            break;
        }

        AESM_DBG_ERROR("Ephemeral session failed");
        return retValue;
    }
    default:
        goto exit; // handle non retry cases for both create_ephemeral_session_pse_cse() calls below
    }
    //All error code handled

exit:
    // Log result of create_ephemeral_session_pse_cse() and map return value
    switch (ret)
    {
    case AE_SUCCESS:
        AESM_LOG_INFO_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_SUCCESS]);
        AESM_DBG_INFO("PSEOPAESMLogic::establish_ephemeral_session success");
        return AESM_SUCCESS;
    case AESM_AE_OUT_OF_EPC:
        AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_FAIL]);
        AESM_DBG_ERROR("Ephemeral session failed");
        return AESM_OUT_OF_EPC;
    case PSE_PAIRING_BLOB_UNSEALING_ERROR:
    case PSE_PAIRING_BLOB_INVALID_ERROR:
    case PSE_OP_ERROR_EPH_SESSION_ESTABLISHMENT_INTEGRITY_ERROR:
    case PSE_OP_LTPB_SEALING_OUT_OF_DATE:
        AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_FAIL_LTP]);
        AESM_DBG_ERROR("Ephemeral session failed");
        return AESM_EPH_SESSION_FAILED;
    case AESM_PSDA_NEED_REPAIRING:
    case AESM_PSDA_INTERNAL_ERROR:
    case AESM_PSDA_SESSION_LOST:
        // This is logged as an ERROR here, since we know the system is expecting PS capability at this point
        AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_FAIL_DAL]);
        AESM_DBG_ERROR("Ephemeral session failed");
        return AESM_EPH_SESSION_FAILED;
    case AE_FAILURE:
    case AE_OUT_OF_MEMORY_ERROR:
    default:
        AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_FAIL]);
        AESM_DBG_ERROR("Ephemeral session failed");
        return AESM_EPH_SESSION_FAILED;
    }
}

aesm_error_t PSEOPAESMLogic::exchange_report(
    uint32_t session_id,
    const uint8_t* se_dh_msg2, uint32_t se_dh_msg2_size,
    uint8_t* se_dh_msg3, uint32_t se_dh_msg3_size)
{
    aesm_error_t ret = AESM_UNEXPECTED_ERROR;

    // prepare for service request
    ret = prepare_for_ps_request();
    if (ret != AESM_SUCCESS)
        return ret;

    ae_error_t ret_pse = CPSEClass::instance().exchange_report(
        session_id,
        const_cast<uint8_t *>(se_dh_msg2),se_dh_msg2_size,
        se_dh_msg3,se_dh_msg3_size);

    if (ret_pse == PSE_OP_EPHEMERAL_SESSION_INVALID)
    {
        AESM_DBG_ERROR("Ephemeral session is broken");
        // Ephemeral session is broken , re-establish ephemeral session and retry exchange_report.
        aesm_error_t result = AESM_UNEXPECTED_ERROR;
        if ((result = establish_ephemeral_session(true)) != AESM_SUCCESS) {
            AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_ERROR]);
            return result;
        }

        AESM_DBG_INFO("Exchange report again");
        // If CPSECLass:exchange_report() returned PSE_OP_EPHMERAL_SESSIOn_INVALID because PSE-Op loss, 
        // the retry here will fail too, as the session is also lost when enclave is lost.
        ret_pse = CPSEClass::instance().exchange_report(
            session_id,
            const_cast<uint8_t *>(se_dh_msg2),se_dh_msg2_size,
            se_dh_msg3,se_dh_msg3_size);
    }

    log_admin_ps_ae(ret_pse);

    return pse_ret_to_aesm_ret(ret_pse);
}

aesm_error_t PSEOPAESMLogic::invoke_service(
    const uint8_t* pse_message_req, uint32_t pse_message_req_size,
    uint8_t* pse_message_resp, uint32_t pse_message_resp_size)
{
    aesm_error_t result;

    // prepare for service request
    result = prepare_for_ps_request();
    if (result != AESM_SUCCESS)
        return result;

    ae_error_t ret_pse = CPSEClass::instance().invoke_service(
        const_cast<uint8_t *>(pse_message_req),pse_message_req_size,
        pse_message_resp,pse_message_resp_size);

    if (ret_pse == PSE_OP_EPHEMERAL_SESSION_INVALID || ret_pse == AESM_PSDA_SESSION_LOST)
    {
        AESM_DBG_ERROR("Ephemeral session is broken");
        // Ephemeral session is broken , re-establish ephemeral session and retry invoke_service.
        if ((result = establish_ephemeral_session(true)) != AESM_SUCCESS) {
            AESM_LOG_ERROR_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_ERROR]);
            return result;
        }

        AESM_DBG_INFO("Invoke service again");
        // If CPSECLass:invoke_service() returned PSE_OP_EPHEMERAL_SESSION_INVALID because PSE-Op loss, 
        // the retry here will fail too, as the session is also lost when enclave is lost.
        ret_pse = CPSEClass::instance().invoke_service(
            const_cast<uint8_t *>(pse_message_req),pse_message_req_size,
            pse_message_resp,pse_message_resp_size);
    }

    log_admin_ps_ae(ret_pse);

    return pse_ret_to_aesm_ret(ret_pse);
}

aesm_error_t PSEOPAESMLogic::close_session(
    uint32_t session_id)
{
    ae_error_t ret_pse = CPSEClass::instance().close_session(
        session_id);

    if (ret_pse == PSE_OP_EPHEMERAL_SESSION_INVALID)
    {
        AESM_DBG_ERROR("Ephemeral session is broken");
        // Ephemeral session is broken , re-establish ephemeral session
        aesm_error_t result = AESM_UNEXPECTED_ERROR;
        if ((result = establish_ephemeral_session(true)) != AESM_SUCCESS)
            return result;

        // Here PSE_OP_EPHEMERAL_SESSION_INVALID is returned only when power event occurs,
        // and the session is also lost when enclave is lost, so always return SUCCESS
        ret_pse = AE_SUCCESS;
    }

    return pse_ret_to_aesm_ret(ret_pse);
}
