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


#include "arch.h"
#include "PSEClass.h"
#include "pse_op_u.h"
#include "pse_op_u.c"

#include "pse_op_psda_ocall.cpp"
#include "pse_op_vmc_sqlite_ocall.cpp"
#include "sgx_tseal.h"
#include "pairing_blob.h"
#include "oal/oal.h"
#include "byte_order.h"

#include "LEClass.h"
#include "ae_ipp.h"
#include "PSDAService.h"
#include "se_wrapper.h"
#include "PSEPRClass.h"

#include "sgx_profile.h"
#include "sgx_uae_service.h"
#include "aesm_pse_status.h"
#include "interface_psda.h"

#define PSDA_CAP_PRTC               0x1
#define PSDA_CAP_RPDATA             0x8

#define CHECK_ECALL_RET(status,ret)            \
            if((status) == SGX_ERROR_ENCLAVE_LOST)  \
            {retry++;continue;}                     \
            else if ((status) == SGX_ERROR_OUT_OF_MEMORY) { \
            (ret) = AE_OUT_OF_MEMORY_ERROR; break; } \
            else if ((status) != SGX_SUCCESS) {     \
            (ret) = AE_FAILURE; break; }              \
            else if ((ret) != 0) { break; }

extern uint32_t upse_iclsInit();

ae_error_t CPSEClass::init_ps(void)
{
    // Try to establish PSDA session during startup
    PROFILE_START("PSDAService::start_service()");
    bool psda_started = PSDAService::instance().start_service();
    PROFILE_END("PSDAService::start_service()");
    if (!psda_started)
    {
        AESM_DBG_ERROR("Psda not available");
        AESM_LOG_INFO_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_START]);
        // This is logged as a WARNING here, since the system may not require PS capability
        AESM_LOG_WARN_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_FAIL_DAL]);
        // Set state to UNAVAILABLE
        m_status = PSE_STATUS_UNAVAILABLE;
        PlatformServiceStatus::instance().set_platform_service_status(PLATFORM_SERVICE_NOT_AVAILABLE);
        return AESM_PSDA_NOT_AVAILABLE;
    }

    //Logic here is that ME FW mode is used(Emulator is not running)
    //provisioning is attempted using iclsclient and the return code is not verified
    //In case of emulator the emulator provisioning tool is used to provision for epid 1.1 and if not long term pairing will return not provisioned error.
    pse_pr_interface_psda* pPSDA = new(std::nothrow) pse_pr_interface_psda();
    if (pPSDA == NULL) {
        return AE_OUT_OF_MEMORY_ERROR;
    }
    // Probe CSME provisiong status first by calling get_csme_gid()
    ae_error_t ret = pPSDA->get_csme_gid(&PSDAService::instance().csme_gid);
    if (ret != AE_SUCCESS)
    {
        // As long as get_csme_gid fails, call iclsInit to trigger provisioning
        uint32_t status_provision = upse_iclsInit();
        if (status_provision != 0)
        {
            // Provisioning failed , maybe caused by missing of iCls client, etc.
            AESM_LOG_INFO_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_START]);
            // This is logged as a WARNING here, since the system may not require PS capability
            AESM_LOG_WARN_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_FAIL_DAL]);
            delete pPSDA;
            pPSDA = NULL;
            return AESM_PSE_PR_PSDA_PROVISION_ERROR;
        }
        else
        {
            // try to get CSME GID again
            ret = pPSDA->get_csme_gid(&PSDAService::instance().csme_gid);
            if (ret != AE_SUCCESS)
            {
                // Failed to get CSME GID
                AESM_LOG_INFO_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_START]);
                // This is logged as a WARNING here, since the system may not require PS capability
                AESM_LOG_WARN_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_FAIL_DAL]);
                delete pPSDA;
                pPSDA = NULL;
                return ret;
            }
        }
    }
    delete pPSDA;
    pPSDA = NULL;

    // Set state to PROVISIONED
    m_status = PSE_STATUS_CSE_PROVISIONED;

    // Get platform service capbility
    PROFILE_START("get_ps_cap");
    ret = get_ps_cap(&m_ps_cap);
    PROFILE_END("get_ps_cap");
    if (ret != AE_SUCCESS){
        AESM_LOG_INFO_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_START]);
        // This is logged as a WARNING here, since the system may not require PS capability
        AESM_LOG_WARN_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_FAIL_DAL]);
        AESM_DBG_ERROR("get_ps_cap failed:%d",ret);
        return ret;
    }

    // Try to establish ephemeral session
    PROFILE_START("create_ephemeral_session_pse_cse");
    ret = create_ephemeral_session_pse_cse(false, false);
    PROFILE_END("create_ephemeral_session_pse_cse");
    if(ret != AE_SUCCESS)
    {
        AESM_DBG_ERROR("creatfe_ephemeral_session_pse_cse failed:%d", ret);
        if (ret == PSE_OP_LTPB_SEALING_OUT_OF_DATE) 
        {
            AESM_DBG_ERROR("TCB update casued ephemeral session failure, reseal LTP blob now");
            // Try to reseal LTP blob
            bool is_new_pairing = false;
            ae_error_t ltpStatus = CPSEPRClass::instance().long_term_pairing(&is_new_pairing);
            if (ltpStatus == AE_SUCCESS)
            {
                AESM_DBG_INFO("Reseal LTP blob succeeded. Try ephermeal session again.");
                ret = create_ephemeral_session_pse_cse(is_new_pairing, false);
                if (ret != AE_SUCCESS){
                    AESM_DBG_ERROR("creatfe_ephemeral_session_pse_cse after ltp blob resealing failed:%d", ret);
                }
            }
        }
    }
	else {
        // If this succeeds we should log PS Init start/success, simply because it won't be repeated and
        // logged later. We don't log the error flows here, because we don't consider this the "real" 
        // PS Init. That will happen the first time create_session(), etc is invoked.
        AESM_LOG_INFO_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_START]);
        AESM_LOG_INFO_ADMIN("%s", g_admin_event_string_table[SGX_ADMIN_EVENT_PS_INIT_SUCCESS]);
	}

    return ret;
}

void CPSEClass::before_enclave_load() {
    // always unload pse_pr enclave before loading pse_op enclave
    CPSEPRClass::instance().unload_enclave();
}

ae_error_t CPSEClass::create_session(
    uint32_t* session_id,
    uint8_t* se_dh_msg1, uint32_t se_dh_msg1_size
    )
{
    sgx_status_t status = SGX_SUCCESS;
    ae_error_t ret = AE_SUCCESS;
    ae_error_t ret2 = AE_SUCCESS;

    // check enclave ID
    if(!m_enclave_id)
        return AE_FAILURE;

    if(sizeof(pse_dh_msg1_t) != se_dh_msg1_size)
        return PSE_OP_PARAMETER_ERROR;

    uint64_t milliseconds = static_cast<uint64_t>(static_cast<double>(se_get_tick_count()) * 1000.0 / static_cast<double>(m_freq) + 0.5);
    status = create_session_wrapper(m_enclave_id,&ret,milliseconds,session_id,(pse_dh_msg1_t*)se_dh_msg1);
    if (status == SGX_ERROR_ENCLAVE_LOST)
    {
        // unload pse-op enclave
        unload_enclave();
        // Return PSE_OP_EPHEMERAL_SESSION_INVALID to trigger ephemeral session re-establishment
        return PSE_OP_EPHEMERAL_SESSION_INVALID;
    }
    if(AE_SUCCESS != (ret2=sgx_error_to_ae_error(status)))
        return ret2;
    return ret;
}

//if ok return 0
ae_error_t CPSEClass::exchange_report(
    uint32_t session_id,
    uint8_t* se_dh_msg2, uint32_t se_dh_msg2_size,
    uint8_t* se_dh_msg3, uint32_t se_dh_msg3_size
    )
{
    sgx_status_t status = SGX_SUCCESS;
    ae_error_t ret = AE_SUCCESS;
    ae_error_t ret2 = AE_SUCCESS;

    if(!m_enclave_id)
        return AE_FAILURE;

    if(sizeof(sgx_dh_msg2_t) != se_dh_msg2_size)
        return PSE_OP_PARAMETER_ERROR;
    if(sizeof(pse_dh_msg3_t) != se_dh_msg3_size)
        return PSE_OP_PARAMETER_ERROR;

    uint64_t sys_tick = se_get_tick_count();
    uint64_t milliseconds = static_cast<uint64_t>(static_cast<double>(sys_tick) * 1000.0 / static_cast<double>(m_freq) + 0.5);
    //ECall
    status = exchange_report_wrapper(m_enclave_id, &ret,
        milliseconds,
        session_id,
        (sgx_dh_msg2_t*)se_dh_msg2,
        (pse_dh_msg3_t*)se_dh_msg3);
    if (status == SGX_ERROR_ENCLAVE_LOST)
    {
        // unload pse-op enclave
        unload_enclave();
        // Return PSE_OP_EPHEMERAL_SESSION_INVALID to trigger ephemeral session re-establishment
        return PSE_OP_EPHEMERAL_SESSION_INVALID;
    }
    if(AE_SUCCESS != (ret2 = sgx_error_to_ae_error(status)))
        return ret2;
    return ret;
}

//if ok return 0
ae_error_t CPSEClass::close_session(uint32_t session_id)
{
    sgx_status_t status = SGX_SUCCESS;
    ae_error_t ret = AE_SUCCESS;
    ae_error_t ret2 = AE_SUCCESS;

    if(!m_enclave_id)
        return AE_FAILURE;

    // ECall
    status = close_session_wrapper(m_enclave_id,&ret,session_id);
    if (status == SGX_ERROR_ENCLAVE_LOST)
    {
        // unload pse-op enclave
        unload_enclave();
        // Return PSE_OP_EPHEMERAL_SESSION_INVALID to trigger ephemeral session re-establishment
        return PSE_OP_EPHEMERAL_SESSION_INVALID;
    }
    if(AE_SUCCESS != (ret2=sgx_error_to_ae_error(status)))
        return ret2;
    return ret;
}

//if ok return 0
ae_error_t CPSEClass::invoke_service(
    uint8_t* pse_message_req, size_t pse_message_req_size,
    uint8_t* pse_message_resp, size_t pse_message_resp_size)
{
    sgx_status_t status = SGX_SUCCESS;
    ae_error_t ret = AE_SUCCESS;
    ae_error_t ret2= AE_SUCCESS;

    if(!m_enclave_id)
        return AE_FAILURE;

    uint64_t sys_tick = se_get_tick_count();
    uint64_t milliseconds = static_cast<uint64_t>(static_cast<double>(sys_tick) * 1000.0 / static_cast<double>(m_freq) + 0.5);
    // ECall
    PROFILE_START("invoke_service_wrapper");
    status = invoke_service_wrapper(m_enclave_id,
                                    &ret,
                                    milliseconds,
                                    pse_message_req,
                                    (uint32_t)pse_message_req_size,
                                    pse_message_resp,
                                    (uint32_t)pse_message_resp_size);
    if (status == SGX_ERROR_ENCLAVE_LOST)
    {
        // unload pse-op enclave
        unload_enclave();
        // Return PSE_OP_EPHEMERAL_SESSION_INVALID to trigger ephemeral session re-establishment
        return PSE_OP_EPHEMERAL_SESSION_INVALID;
    }
    PROFILE_END("invoke_service_wrapper");
    if(AE_SUCCESS != (ret2=sgx_error_to_ae_error(status)))
        return ret2;
    return ret;
}

ae_error_t CPSEClass::get_ps_cap(uint64_t* ps_cap)
{
    if (ps_cap == NULL)
    {
        AESM_DBG_ERROR("input ps_cap is NULL");
        return AE_FAILURE;
    }

    if (m_ps_cap != PS_CAP_NOT_AVAILABLE)
    {
        AESM_DBG_TRACE("ps_cap is available:%llu", m_ps_cap);
        *ps_cap = m_ps_cap;
        return AE_SUCCESS;
    }

    psda_info_query_msg_t psda_cap_query_msg;
    psda_cap_query_msg.msg_hdr.msg_type = _htonl(PSDA_MSG_TYPE_CAP_QUERY);
    psda_cap_query_msg.msg_hdr.msg_len = 0;

    psda_cap_result_msg_t psda_cap_result_msg;
    memset(&psda_cap_result_msg, 0, sizeof(psda_cap_result_msg_t));

    JVM_COMM_BUFFER commBuf;
    commBuf.TxBuf->buffer = &psda_cap_query_msg;
    commBuf.TxBuf->length = sizeof(psda_info_query_msg_t);
    commBuf.RxBuf->buffer = &psda_cap_result_msg;
    commBuf.RxBuf->length = sizeof(psda_cap_result_msg_t);
    int response_code;

    ae_error_t ret;
    ret = PSDAService::instance().send_and_recv(
                                PSDA_COMMAND_INFO,
                                &commBuf,
                                &response_code,
                                AUTO_RETRY_ON_SESSION_LOSS);
    if (ret != AE_SUCCESS)
    {
        AESM_DBG_ERROR("JHI_SendAndRecv2 returned (ae%d)",ret);
        AESM_LOG_ERROR_UNICODE("%s", g_event_string_table[SGX_EVENT_DAL_COMM_FAILURE]);
        return ret;
    }

    if (response_code != PSDA_SUCCESS)
    {
        AESM_DBG_ERROR("JHI_SendAndRecv2 response_code is %d", response_code);
        return AE_FAILURE;
    }

    if (_ntohl(psda_cap_result_msg.msg_hdr.msg_type) != PSDA_MSG_TYPE_CAP_RESULT
        || _ntohl(psda_cap_result_msg.msg_hdr.msg_len) != PSDA_CAP_RESULT_MSG_LEN) {
        AESM_DBG_ERROR("msg_type %d, msg_len %d while expected value type %d, len %d",
        _ntohl(psda_cap_result_msg.msg_hdr.msg_type), _ntohl(psda_cap_result_msg.msg_hdr.msg_len),
        PSDA_MSG_TYPE_CAP_RESULT, PSDA_CAP_RESULT_MSG_LEN);
        return AE_FAILURE;
    }

    if (_ntohl(psda_cap_result_msg.cap_descriptor_version) != 1)
    {
        return AE_FAILURE;
    }

    m_ps_cap = 0;
    uint32_t psda_cap0 = _ntohl(psda_cap_result_msg.cap_descriptor0);
    if (psda_cap0 & PSDA_CAP_PRTC)
        m_ps_cap |= PS_CAP_TRUSTED_TIME;        // Trusted time service
    if (psda_cap0 & PSDA_CAP_RPDATA)        // RPDATA capbility is available
        m_ps_cap |= PS_CAP_MONOTONIC_COUNTER;        // Monotonic counter service

    *ps_cap = m_ps_cap;

    return AE_SUCCESS;
}

/**
 * @brief Establish an ephemeral session between PSE and CSE if not established yet.
 *
 * @param is_new_pairing
 * @param redo
 *
 * @return SGX_SUCCESS for success. Other values indicate an error.
 */
ae_error_t CPSEClass::create_ephemeral_session_pse_cse(bool is_new_pairing, bool redo)
{
    ae_error_t ret = AE_FAILURE;
    pse_cse_msg2_t msg2;
    pse_cse_msg3_t msg3;
    pse_cse_msg4_t msg4;
    uint32_t blob_size;
    uint8_t* p_sealed_buffer = NULL;
    sgx_status_t status = SGX_SUCCESS;
    sgx_status_t stat_initdb = SGX_SUCCESS;
    int retry = 0;

    if (m_status == PSE_STATUS_INIT ||
        m_status == PSE_STATUS_UNAVAILABLE)
    {
        // CSE provisioning failed during initialization.
        PlatformServiceStatus::instance().set_platform_service_status(PLATFORM_SERVICE_NOT_AVAILABLE);
        return AE_FAILURE;
    }

    if (!redo)
    {
        if (m_status == PSE_STATUS_SERVICE_READY) {
            PlatformServiceStatus::instance().set_platform_service_status(PLATFORM_SERVICE_READY);
            return AE_SUCCESS;
        }
    }
    else
    {
        // invalidate current session
        m_status = PSE_STATUS_CSE_PROVISIONED;
    }
    // Set to NOT_READY at the beginning.
    PlatformServiceStatus::instance().set_platform_service_status(PLATFORM_SERVICE_NOT_READY);

    do
    {
        AESM_DBG_INFO("PSDA started");
        // Check LT pairing blob first
        blob_size = sizeof(pairing_blob_t);
        p_sealed_buffer = (uint8_t*)malloc(blob_size);
        if (p_sealed_buffer == NULL)
        {
            return AE_FAILURE;
        }

        PROFILE_START("aesm_read_data");
        // read sealed blob from persistent storage
        ret = aesm_read_data(FT_PERSISTENT_STORAGE, PSE_PR_LT_PAIRING_FID, p_sealed_buffer, &blob_size);
        PROFILE_END("aesm_read_data");
        if (ret != AE_SUCCESS||blob_size!=sizeof(pairing_blob_t))
        {
            // Failed to load LT sealed blob
            ret = PSE_PAIRING_BLOB_INVALID_ERROR;

            // unload pse_op enclave
            unload_enclave();

            // load pse_pr enclave
            CPSEPRClass::instance().load_enclave();
            break;
        }

        AESM_DBG_INFO("LT Paring Blob read");

        // load pse-op enclave if it's not loaded yet
        if ((ret = load_enclave()) != AE_SUCCESS)
            break;

        do
        {
            // create PSDA session if not available
            if (!PSDAService::instance().start_service()) {
                PlatformServiceStatus::instance().set_platform_service_status(PLATFORM_SERVICE_NOT_AVAILABLE);
                ret = AE_FAILURE;
                break;
            }

            if(status == SGX_ERROR_ENCLAVE_LOST)
            {
                unload_enclave();
                // Reload an AE will not fail because of out of EPC, so AESM_AE_OUT_OF_EPC is not checked here
                if(AE_SUCCESS != load_enclave())
                {
                    ret = AE_FAILURE;
                    break;
                }
            }
            AESM_DBG_INFO("PSDA Start Ephemral Session");
            // PSE --- M1:StartSession ---> CSE
            memset(&msg2, 0, sizeof(msg2));
            PROFILE_START("psda_start_ephemeral_session");
            ret = psda_start_ephemeral_session(((pairing_blob_t*)p_sealed_buffer)->plaintext.pse_instance_id, &msg2);
            PROFILE_END("psda_start_ephemeral_session");
            if (ret != AE_SUCCESS) 
                break;

            AESM_DBG_INFO("Ephemral Session M2/M3");
            // PSE <--- M2 --- CSE
            memset(&msg3, 0, sizeof(msg3));
            PROFILE_START("ephemeral_session_m2m3_wrapper");
            status = ephemeral_session_m2m3_wrapper(m_enclave_id, &ret, (pairing_blob_t*)p_sealed_buffer, &msg2, &msg3);
            PROFILE_END("ephemeral_session_m2m3_wrapper");
            CHECK_ECALL_RET(status, ret)

            AESM_DBG_INFO("PSDA Finalize Session");
            // PSE --- M3 ---> CSE
            memset(&msg4, 0, sizeof(msg4));
            PROFILE_START("psda_finalize_session");
            ret = psda_finalize_session(((pairing_blob_t*)p_sealed_buffer)->plaintext.pse_instance_id, &msg3, &msg4);
            PROFILE_END("psda_finalize_session");
            if (ret == AESM_PSDA_SESSION_LOST)
            {
                retry++;
                continue;
            }
            BREAK_IF_FAILED(ret);

            AESM_DBG_INFO("Ephemeral Session M4");
            // PSE <--- M4 --- CSE
            PROFILE_START("ephemeral_session_m4_wrapper");
            status = ephemeral_session_m4_wrapper(m_enclave_id, &ret, &msg4);
            PROFILE_END("ephemeral_session_m4_wrapper");
            CHECK_ECALL_RET(status, ret)

            /* the return value of initialize_sqlite_database_file_wrapper is ignored unless it's SGX_ERROR_ENCLAVE_LOST */
            AESM_DBG_INFO("initialize vmc database");
            PROFILE_START("initialize_sqlite_database_file_wrapper");
            ae_error_t ret2;
            stat_initdb = initialize_sqlite_database_file_wrapper(m_enclave_id, &ret2, is_new_pairing);
            PROFILE_END("initialize_sqlite_database_file_wrapper");
            if (stat_initdb == SGX_ERROR_ENCLAVE_LOST) 
            {
                retry++;
                continue;
            }
            else break;

        }while(retry < AESM_RETRY_COUNT);

        if (status == SGX_ERROR_ENCLAVE_LOST)
        {
            AESM_DBG_INFO("Enclave Lost");
            // maximum retry times reached
            ret = AE_FAILURE;
            break;
        }
        else if(ret == AE_SUCCESS)
        {
            // Set status to READY
            m_status = PSE_STATUS_SERVICE_READY;
            // Successfully build the ephemeral session
            PlatformServiceStatus::instance().set_platform_service_status(PLATFORM_SERVICE_READY);
        }

    } while(0);

    free(p_sealed_buffer);
    return ret;
}

ae_error_t CPSEClass::psda_start_ephemeral_session(const uint8_t* pse_instance_id, 
                                                   pse_cse_msg2_t* cse_msg2)
{
    assert(cse_msg2 != NULL);
    AESM_DBG_INFO("Enter psda_start_ephemeral_session ...");

    eph_session_m1_t eph_session_m1;
    memcpy_s(eph_session_m1.msg_hdr.pse_instance_id, SW_INSTANCE_ID_SIZE, pse_instance_id, SW_INSTANCE_ID_SIZE);
    eph_session_m1.msg_hdr.msg_type = _htonl(PSDA_MSG_TYPE_EP_M1);
    eph_session_m1.msg_hdr.msg_len = 0;

    eph_session_m2_t eph_session_m2;
    memset(&eph_session_m2, 0, sizeof(eph_session_m2));

    JVM_COMM_BUFFER commBuf;
    commBuf.TxBuf->buffer = &eph_session_m1;
    commBuf.TxBuf->length = sizeof(eph_session_m1_t);
    commBuf.RxBuf->buffer = &eph_session_m2;
    commBuf.RxBuf->length = sizeof(eph_session_m2_t);
    int response_code;
    ae_error_t ret;

    ret = PSDAService::instance().send_and_recv(
                        PSDA_COMMAND_EP,
                        &commBuf,
                        &response_code,
                        AUTO_RETRY_ON_SESSION_LOSS);
    if (ret != AE_SUCCESS ) {
        AESM_LOG_ERROR_UNICODE("%s", g_event_string_table[SGX_EVENT_DAL_COMM_FAILURE]);
        return ret;
    }

    if (response_code == PSDA_LT_PAIRING_NOT_EXIST
        || response_code == PSDA_INTEGRITY_ERROR)
    {
        return AESM_PSDA_NEED_REPAIRING;
    }
    else if (response_code != PSDA_SUCCESS
        || _ntohl(eph_session_m2.msg_hdr.msg_type) != PSDA_MSG_TYPE_EP_M2
        || _ntohl(eph_session_m2.msg_hdr.msg_len) != sizeof(pse_cse_msg2_t)) {
        AESM_DBG_ERROR("JHI_SendAndRecv2 response_code is %d", response_code);
        return AE_FAILURE;
    }

    memcpy_s(cse_msg2, sizeof(pse_cse_msg2_t), &eph_session_m2.msg2, sizeof(pse_cse_msg2_t));

    return AE_SUCCESS;
}

ae_error_t CPSEClass::psda_finalize_session(const uint8_t* pse_instance_id, 
                                            const pse_cse_msg3_t* cse_msg3, 
                                            pse_cse_msg4_t* cse_msg4)
{
    assert(cse_msg3 != NULL && cse_msg4 != NULL);
    AESM_DBG_INFO("Enter psda_finalize_session ...");

    eph_session_m3_t eph_session_m3;
    memcpy_s(eph_session_m3.msg_hdr.pse_instance_id, SW_INSTANCE_ID_SIZE, pse_instance_id, SW_INSTANCE_ID_SIZE);
    eph_session_m3.msg_hdr.msg_type = _htonl(PSDA_MSG_TYPE_EP_M3);
    eph_session_m3.msg_hdr.msg_len = _htonl(sizeof(pse_cse_msg3_t));
    memcpy_s(&eph_session_m3.msg3, sizeof(eph_session_m3.msg3), cse_msg3, sizeof(pse_cse_msg3_t));

    eph_session_m4_t eph_session_m4;
    memset(&eph_session_m4, 0, sizeof(eph_session_m4));

    JVM_COMM_BUFFER commBuf;
    commBuf.TxBuf->buffer = &eph_session_m3;
    commBuf.TxBuf->length = sizeof(eph_session_m3_t);
    commBuf.RxBuf->buffer = &eph_session_m4;
    commBuf.RxBuf->length = sizeof(eph_session_m4_t);
    int response_code;
    ae_error_t ret;

    ret = PSDAService::instance().send_and_recv(
                        PSDA_COMMAND_EP,
                        &commBuf,
                        &response_code,
                        NO_RETRY_ON_SESSION_LOSS);
    if (ret != AE_SUCCESS)
    {
        AESM_LOG_ERROR_UNICODE("%s", g_event_string_table[SGX_EVENT_DAL_COMM_FAILURE]);
        return ret;
    }

    if (response_code == PSDA_INTEGRITY_ERROR)
    {
        return AESM_PSDA_NEED_REPAIRING;
    }
    else if (response_code != PSDA_SUCCESS
        || _ntohl(eph_session_m4.msg_hdr.msg_type) != PSDA_MSG_TYPE_EP_M4
        || _ntohl(eph_session_m4.msg_hdr.msg_len) != sizeof(pse_cse_msg4_t)) {
        AESM_DBG_ERROR("JHI_SendAndRecv2 response_code is %d", response_code);
        return AE_FAILURE;
    }

    memcpy_s(cse_msg4, sizeof(pse_cse_msg4_t), &eph_session_m4.msg4, sizeof(pse_cse_msg4_t));

    return AE_SUCCESS;
}

ae_error_t CPSEClass::psda_invoke_service(uint8_t* psda_req_msg, uint32_t psda_req_msg_size,
                        uint8_t* psda_resp_msg, uint32_t psda_resp_msg_size)
{
    AESM_DBG_INFO("Enter psda_invoke_service ...");

    JVM_COMM_BUFFER commBuf;
    commBuf.TxBuf->buffer = psda_req_msg;
    commBuf.TxBuf->length = psda_req_msg_size;
    commBuf.RxBuf->buffer = psda_resp_msg;
    commBuf.RxBuf->length = psda_resp_msg_size;
    int response_code;
    ae_error_t ret;

    PROFILE_START("JHI_SendAndRecv2");
    ret = PSDAService::instance().send_and_recv(
                        PSDA_COMMAND_SERVICE,
                        &commBuf,
                        &response_code,
                        NO_RETRY_ON_SESSION_LOSS);
    PROFILE_END("JHI_SendAndRecv2");
    if (ret != AE_SUCCESS) {
        AESM_LOG_ERROR_UNICODE("%s", g_event_string_table[SGX_EVENT_DAL_COMM_FAILURE]);
        return ret;
    }
    if (PSDA_SUCCESS != response_code) {
        AESM_LOG_ERROR_UNICODE("%s", g_event_string_table[SGX_EVENT_DAL_SERVICE_ERROR]);
    }

    AESM_DBG_INFO("JHI_SendAndRecv2 response_code is %d", response_code);

    switch (response_code)
    {
    case PSDA_SUCCESS:                // SGX Platform Service Message form PSE processed successfully
        return AE_SUCCESS;

    case PSDA_INTERNAL_ERROR:         // Internal error, possibly due to unexpected error of the system
        return AESM_PSDA_INTERNAL_ERROR;

    case PSDA_INVALID_SESSION_STATE:  // SGX Platform Service ephemeral session state is invalid
    case PSDA_SEQNO_CHECK_FAIL:       // SGX Platform Service secure channel message sequence number check failure
    case PSDA_INTEGRITY_ERROR:        // SGX Platform Service message crypto verification failure
    case PSDA_LT_PAIRING_NOT_EXIST:   // SGX Platform Service long  term pairing session doesn't exist
        return AESM_PSDA_NEED_REPAIRING;

    case PSDA_INVALID_COMMAND:        // SGX Platform Service PS_COMMAND_ID provided by the transport layer is not recognized
    case PSDA_BAD_PARAMETER:          // SGX Platform Service Message format error detected
    default:
        return AE_FAILURE;
    }
}
