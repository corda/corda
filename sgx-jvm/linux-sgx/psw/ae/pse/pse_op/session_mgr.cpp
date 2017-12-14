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


#include "utility.h"
#include "session_mgr.h"
#include "pse_op_t.h"
#include "sgx_dh.h"

// ISV enclave <-> pse-op sessions
static pse_session_t        g_session[SESSION_CONNECTION];
static uint32_t             g_session_count = 0;

// ephemeral session global variables
static uint8_t              g_nonce_r_pse[EPH_SESSION_NONCE_SIZE] = {0};      // nonce R(PSE) for ephemeral session establishment
static uint8_t              g_nonce_r_cse[EPH_SESSION_NONCE_SIZE] = {0};      // nonce R(CSE) for ephemeral session establishment
static pairing_data_t       g_pairing_data;                       // unsealed pairing data
eph_session_t               g_eph_session;                        // ephemeral session information

/**
 * @brief Check the status of the ephemeral session
 *
 * @return true if the ephemeral session is active. Otherwise false.
 */
bool is_eph_session_active()
{
    if(SESSION_ACTIVE != g_eph_session.state)
    {
        return false;
    }
    return true;
}

/**
 * @brief Get session from session ID
 *
 * @param sid Session ID
 *
 * @return Session
 */
pse_session_t* sid2session(uint32_t sid)
{
    for(int index = 0; index < SESSION_CONNECTION; index++)
    {
        if(g_session[index].state != SESSION_CLOSE &&
           g_session[index].sid == sid)
        {
            return &g_session[index];
        }
    }
    return NULL;
}

/**
 * @brief Free the memory of a session
 *
 * @param session
 */
void free_session(pse_session_t* session)
{
    if (session) {
        memset_s(session, sizeof(pse_session_t), 0, sizeof(pse_session_t));
    }
}

/**
 * @brief Check active state of a session and its sequence number
 *
 * @param session
 *
 * @return true if the session is active and sequence number doesn't overflow
 */
bool is_isv_session_valid(pse_session_t* session)
{
    //if session not exists or established, return error
    if (!session || session->state != SESSION_ACTIVE)
    {
        return false;
    }

    // check sequence number
    if(session->active.counter >= (UINT32_MAX-2))
    {
        //close session which contains AEK
        memset_s(session, sizeof(pse_session_t), 0, sizeof(pse_session_t));
        return false;
    }

    return true;
}

/**
 * @brief Get the sequence number of a session
 *
 * @param session
 *
 * @return Sequence number
 */
uint32_t get_session_seq_num(pse_session_t* session)
{
    return session->active.counter;
}

/**
 * @brief Set the sequence number of a session
 *
 * @param session
 * @param seq_num Sequence number
 */
void set_session_seq_num(pse_session_t* session, uint32_t seq_num)
{
    session->active.counter = seq_num;
}

/**
 * @brief Update the tick counter of a session
 *
 * @param session
 * @param new_tick_count
 */
void update_session_tick_count(pse_session_t* session, uint64_t new_tick_count)
{
    session->isv_attributes.tick_count = new_tick_count;
}

////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////
/**
 * @brief 
 * Here describe how to find the idle session
 * 1. find the one which exceed session quote, and if two enclave has
 * same instances number, find the one has idle for the longest time.
 * two arrays:
 * instances: record each enclave's instances number
 * time_stamp: for each enclave's instance, record the instance that
 * idle for the longest time, it's a index to g_session.
 * the two array items are corresponding to each other
 * for instance:
 * instances:
 * | 1 | 32 | 9 | 8 | 1 | 32 |....
 * ---------------------------....
 * time stamp
 * | 0 | 2 | 5 | 10 | 19 | 6 |....
 * ---------------------------....
 * [0]:
 * enclave has 1 instance, and the longest idle one is g_session[0]
 * [1]:
 * enclave has 32 instances, among them the g_session[2] is the longest
 * idle one.
 * [2]:
 * enclave has 9 instances, among them the g_session[5] is the longest
 * idle one.
 *
 * @param tick
 *
 * @return Session
 */
pse_session_t* find_idle_session(uint64_t tick)
{
    pse_session_t* session = NULL;
    uint8_t instances[SESSION_CONNECTION];
    uint8_t time_stamp[SESSION_CONNECTION];
    uint8_t end_index = 0;
    uint8_t max_instance = 0;

    memset(instances, 0, SESSION_CONNECTION);
    memset(time_stamp, 0, SESSION_CONNECTION);
    for(uint8_t i = 0; i < SESSION_CONNECTION; i++)
    {
        pse_session_t* s1 = &g_session[i];
        // if one session has not established, no need to calculate the quote
        // as no enclave information has obtain.
        if (s1->state != SESSION_ACTIVE)
        {
            continue;
        }
        uint8_t j = 0;
        for(; j < end_index; j++)
        {
            pse_session_t* s2 = &g_session[time_stamp[j]];
            if (!memcmp(&s1->isv_attributes.mr_enclave,
                        &s2->isv_attributes.mr_enclave,
                        sizeof(s1->isv_attributes.mr_enclave)) &&
                !memcmp(&s1->isv_attributes.mr_signer,
                        &s2->isv_attributes.mr_signer,
                        sizeof(s1->isv_attributes.mr_signer)) &&
                s1->isv_attributes.isv_prod_id == s2->isv_attributes.isv_prod_id)
            {
                if (s1->isv_attributes.tick_count < s2->isv_attributes.tick_count)
                {
                    time_stamp[j] = i;
                }
                ++instances[j];
                if (instances[j] > max_instance)
                {
                    max_instance = instances[j];
                }
                break;
            }
        }
        if (j == end_index)
        {
            time_stamp[end_index] = i;
            instances[end_index] = 1;
            if (instances[end_index] > max_instance)
            {
                max_instance = instances[end_index];
            }
            end_index++;
        }
    }

    if (max_instance > MAX_INST_PER_ENCLAVE)
    {
        for (uint8_t i = 0; i < end_index; i++)
        {
            if (instances[i] == max_instance)
            {
                pse_session_t* s1 = &g_session[time_stamp[i]];
                if (!session)
                {
                    session = s1;
                }
                else if (session->isv_attributes.tick_count > s1->isv_attributes.tick_count)
                {
                    session = s1;
                }
            }
        }
    }
    if(session)
    {
        return session;
    }


    //if no enclave instance exceed quote,
    //iterate all enclave to find the one no-active for long time
    uint64_t elapse = 0;

    //find the least active session
    for(uint8_t index = 0; index < SESSION_CONNECTION; index++)
    {
        uint64_t temp = tick - g_session[index].isv_attributes.tick_count;
        if(temp > tick)
        {
            // temp underflow
            return NULL;
        }
        if(temp > elapse)
        {
            elapse = temp;
            session = &g_session[index];
        }
    }
    //if the least active session is not exceed predefined time,
    // no idle session find
    if(elapse < SESSION_IDLE_TIME)
    {
        session = NULL;
    }

    return session;
}

/**
 * @brief Select a session slot based on the defined rules. 
 *
 * @param tick
 *
 * @return 
 */
pse_session_t* open_session(uint64_t tick)
{
    int index = 0;
    pse_session_t* session = NULL;
    //if the session id reached the limitation, reset all sessions
    if(g_session_count == SESSION_COUNTER_MAX)
    {
        for(index = 0; index < SESSION_CONNECTION; index++)
        {
            free_session(&g_session[index]);
        }
        g_session_count = 0;
    }

    for(index = 0; index < SESSION_CONNECTION; index++)
    {
        if(g_session[index].state == SESSION_CLOSE)
        {
            break;
        }
    }
    if(index == SESSION_CONNECTION)
    {
        //if no session slot available, find one idle session for reuse
        session = find_idle_session(tick);
        if (session)
        {
            free_session(session);
        }
    }
    else
    {
        session = &g_session[index];
    }

    return session;
}

/**
 * @brief Initialize a DH session.
 *
 * @param tick Tick counter
 * @param id Session ID
 * @param dh_msg1 DH Message1
 *
 * @return OP_SUCCESS 
 */
pse_op_error_t pse_create_session(uint64_t tick, uint32_t &id, pse_dh_msg1_t &dh_msg1)
{
    pse_op_error_t status = OP_SUCCESS;
    pse_session_t* session = NULL;
    sgx_status_t se_ret;
    sgx_dh_session_t sgx_dh_session;

    se_ret = sgx_dh_init_session(SGX_DH_SESSION_RESPONDER, &sgx_dh_session);
    if(SGX_SUCCESS != se_ret)
    {
        return OP_ERROR_INTERNAL;
    }

    session = open_session(tick);
    if(!session)
    {
        //if no available session found, return error to ISV enclave
        id = INVADE_SESSION_ID;
        return OP_ERROR_MAX_NUM_SESSION_REACHED;
    }
    else
    {
        session->state = SESSION_IN_PROGRESS;
        session->sid = g_session_count++;     // addition overflow already checked in open_session
        id = session->sid;
    }

    do
    {
        //Generate Message1
        se_ret = sgx_dh_responder_gen_msg1((sgx_dh_msg1_t*)&dh_msg1, &sgx_dh_session);
        if(SGX_SUCCESS != se_ret)
        {
            status = OP_ERROR_INTERNAL;
            break;
        }
        memcpy(&session->in_progress.dh_session, &sgx_dh_session, sizeof(sgx_dh_session_t));

        // clear secret
        memset_s(&sgx_dh_session, sizeof(sgx_dh_session_t), 0, sizeof(sgx_dh_session_t));

        //update tick count to record the last session activation tick count
        session->isv_attributes.tick_count = tick;
        session->isv_attributes_len = sizeof(isv_attributes_t);
    }while(0);

    if(status != OP_SUCCESS)
    {
        pse_close_session(id);
        id = INVADE_SESSION_ID;
    }

    return status;
}

/**
 * @brief Exchange M2 and M3
 *
 * @param tick
 * @param sid
 * @param dh_msg2
 * @param dh_msg3
 *
 * @return 
 */
pse_op_error_t pse_exchange_report(uint64_t tick,
                               uint32_t sid,
                               const sgx_dh_msg2_t &dh_msg2,
                               pse_dh_msg3_t &dh_msg3)
{
    pse_op_error_t status = OP_SUCCESS;
    sgx_dh_session_t sgx_dh_session;
    sgx_key_128bit_t aek;
    sgx_dh_session_enclave_identity_t initiator_identity;
    cse_sec_prop_t * pcse_sec = NULL;
    secu_info_t* psec_info = NULL;

    pse_session_t* session = sid2session(sid);
    if(!session || session->state != SESSION_IN_PROGRESS)
    {
        return OP_ERROR_INVALID_SESSION;
    }

    do
    {
        memcpy(&sgx_dh_session, &session->in_progress.dh_session, sizeof(sgx_dh_session_t));

        dh_msg3.additional_prop_length = sizeof(cse_sec_prop_t);
        // set secu_info_t
        pcse_sec = &dh_msg3.cse_sec_prop;
        pcse_sec->gid_cse = g_pairing_data.plaintext.cse_sec_prop.ps_hw_gid;
        pcse_sec->prvrl_version = g_pairing_data.plaintext.cse_sec_prop.ps_hw_privkey_rlversion;
        pcse_sec->sigrl_version = g_pairing_data.plaintext.cse_sec_prop.ps_hw_sig_rlversion;
        pcse_sec->sec_info_type = 0;
        memcpy(pcse_sec->ca_id_cse, g_pairing_data.plaintext.cse_sec_prop.ps_hw_CA_id, sizeof(pcse_sec->ca_id_cse));
        psec_info = (secu_info_t*)(pcse_sec->sec_info);
        psec_info->jom_task_id = 8;
        //psec_info->psda_svn = *(uint32_t*)&g_pairing_data.plaintext.cse_sec_prop.ps_hw_sec_info[12];
        psec_info->psda_svn = g_pairing_data.plaintext.cse_sec_prop.ps_hw_sec_info.psdaSvn;
        psec_info->psda_id = 1;
        psec_info->reserved = 0;
        memset_s(psec_info->reserved2, sizeof(psec_info->reserved2), 0, sizeof(psec_info->reserved2));

        sgx_status_t se_ret = sgx_dh_responder_proc_msg2(&dh_msg2,
                                                       (sgx_dh_msg3_t*)&dh_msg3,
                                                       &sgx_dh_session,
                                                       &aek,
                                                       &initiator_identity);
        // clear secret
        memset_s(&sgx_dh_session, sizeof(sgx_dh_session_t), 0, sizeof(sgx_dh_session_t));
        if (SGX_ERROR_KDF_MISMATCH == se_ret)
        {
            status = OP_ERROR_KDF_MISMATCH;
            break;
        }
        else if(SGX_SUCCESS != se_ret)
        {
            status = OP_ERROR_INTERNAL;
            break;
        }

        // clear secret before changing session status
        memset_s(&session->in_progress.dh_session, sizeof(sgx_dh_session_t), 0, sizeof(sgx_dh_session_t));

        session->state = SESSION_ACTIVE;
        memcpy(&session->active.AEK, &aek, sizeof(sgx_key_128bit_t));
        session->active.counter = 0;
        session->isv_attributes_len = sizeof(isv_attributes_t);
        memcpy(&session->isv_attributes.attribute, &initiator_identity.attributes, sizeof(sgx_attributes_t));
        memcpy(&session->isv_attributes.isv_prod_id, &initiator_identity.isv_prod_id, sizeof(sgx_prod_id_t));
        memcpy(&session->isv_attributes.isv_svn, &initiator_identity.isv_svn, sizeof(sgx_isv_svn_t));
        //record mr_signer
        memcpy(&session->isv_attributes.mr_signer, &initiator_identity.mr_signer, sizeof(sgx_measurement_t));
        //record mr_enclave
        memcpy(&session->isv_attributes.mr_enclave, &initiator_identity.mr_enclave, sizeof(sgx_measurement_t));
        //update tick count
        session->isv_attributes.tick_count = tick;
    }while(0);

    if(status != OP_SUCCESS)
    {
        pse_close_session(sid);
    }

    return status;
}

pse_op_error_t pse_close_session(uint32_t sid)
{
    if (sid != (uint32_t)INVADE_SESSION_ID)
    {
        pse_session_t* session = sid2session(sid);
        if(session)
        {
            free_session(session);
        }
        return OP_SUCCESS;
    }
    return OP_ERROR_INVALID_SESSION;
}

/**
 * @brief Exchange message2 and message3 between CSE and PSE
 *
 * @param sealed_blob Sealed long term pairing blob.
 * @param pse_cse_msg2 Message2
 * @param pse_cse_msg3 Message3
 *
 * @return OP_SUCCESS for successful message exchange. All other values indicate FAIL.
 */
pse_op_error_t ephemeral_session_m2m3(
    pairing_blob_t* sealed_blob,
    const pse_cse_msg2_t &pse_cse_msg2,
    pse_cse_msg3_t &pse_cse_msg3)
{
    sgx_status_t se_ret = SGX_SUCCESS;
    uint32_t msg_len = 0;
    pse_op_error_t op_ret = OP_SUCCESS;
    Nonce128_t zero_nonce;
    sgx_report_t report;
    sgx_isv_svn_t sealed_isv_svn;
    sgx_cpu_svn_t* p_sealed_cpu_svn = NULL;

    // decrypt sealed blob
    if (UnsealPairingBlob(sealed_blob, &g_pairing_data) != AE_SUCCESS)
    {
        // unseal error
        op_ret = OP_ERROR_UNSEAL_PAIRING_BLOB;
        goto error;
    }

    // If the pairing blob was sealed by a different ISV SVN, we need to 
    // tell AESM to redo long term pairing
    memset_s(&report, sizeof(report), 0, sizeof(report));
    se_ret = sgx_create_report(NULL, NULL, &report);
    if(SGX_SUCCESS != se_ret)
    {
        op_ret = OP_ERROR_INTERNAL;
        goto error;
    }

    sealed_isv_svn = ((sgx_sealed_data_t*)sealed_blob)->key_request.isv_svn;
    p_sealed_cpu_svn = &((sgx_sealed_data_t*)sealed_blob)->key_request.cpu_svn;
    if (sealed_isv_svn != report.body.isv_svn || memcmp(p_sealed_cpu_svn, &report.body.cpu_svn, sizeof(sgx_cpu_svn_t)) != 0)
    {
        op_ret = OP_ERROR_LTPB_SEALING_OUT_OF_DATE;
        goto error;
    }

     // Reset ephemeral session
    memset(&g_eph_session, 0 , sizeof(eph_session_t));

    // verify pairingNonce, must be non-zero.
    memset(&zero_nonce, 0, sizeof(zero_nonce));
    if(0 == memcmp(&g_pairing_data.secret_data.pairingNonce, &zero_nonce, sizeof(Nonce128_t)))
    {
        op_ret = OP_ERROR_INVALID_PAIRING_BLOB;
        goto error;
    }

    // clear secret data
    memset_s(&g_pairing_data.secret_data.VerifierPrivateKey, sizeof(EcDsaPrivKey), 0, sizeof(EcDsaPrivKey));
    memset_s(&g_pairing_data.secret_data.pairingID, sizeof(SIGMA_SECRET_KEY), 0, sizeof(SIGMA_SECRET_KEY));

    // verify IDcse
    if (memcmp(g_pairing_data.secret_data.Id_cse, pse_cse_msg2.id_cse, sizeof(pse_cse_msg2.id_cse)) != 0)
    {
        op_ret = OP_ERROR_EPH_SESSION_ESTABLISHMENT_INTEGRITY_ERROR;
        goto error;
    }

    // save nonce R_cse
    memcpy(g_nonce_r_cse, pse_cse_msg2.nonce_r_cse, sizeof(g_nonce_r_cse));

    // generate Message3
    se_ret = sgx_read_rand((uint8_t*)&g_nonce_r_pse, sizeof(g_nonce_r_pse));
    if (se_ret != SGX_SUCCESS)
    {
        op_ret = OP_ERROR_INTERNAL;
        goto error;
    }

    memcpy(pse_cse_msg3.id_cse, g_pairing_data.secret_data.Id_cse, sizeof(pse_cse_msg3.id_cse));
    memcpy(pse_cse_msg3.id_pse, g_pairing_data.secret_data.Id_pse, sizeof(pse_cse_msg3.id_pse));
    memcpy(pse_cse_msg3.nonce_r_pse, g_nonce_r_pse, sizeof(pse_cse_msg3.nonce_r_pse));
    memcpy(pse_cse_msg3.nonce_r_cse, pse_cse_msg2.nonce_r_cse, sizeof(pse_cse_msg3.nonce_r_cse));

    // compute HMAC over IDcse||IDpse||Rpse||Rcse
    msg_len = static_cast<uint32_t>(sizeof(pse_cse_msg3.id_cse) + sizeof(pse_cse_msg3.id_pse)
                    + sizeof(pse_cse_msg3.nonce_r_pse) + sizeof(pse_cse_msg3.nonce_r_cse));
    if (ippsHMAC_Message((uint8_t*)&pse_cse_msg3, msg_len, g_pairing_data.secret_data.mk, sizeof(g_pairing_data.secret_data.mk),
        pse_cse_msg3.mac, SGX_SHA256_HASH_SIZE, IPP_ALG_HASH_SHA256) != ippStsNoErr)
    {
        op_ret = OP_ERROR_INTERNAL;
        goto error;
    }

    g_eph_session.state = SESSION_IN_PROGRESS;

    return OP_SUCCESS;
error:
    memset_s(&g_pairing_data.secret_data, sizeof(se_secret_pairing_data_t), 0, sizeof(se_secret_pairing_data_t));
    return op_ret;
}

/**
 * @brief Verify message4 which is generated by CSE. If verification
 *        is passed, ephemeral session will be established.
 *
 * @param pse_cse_msg4 Message4
 *
 * @return OP_SUCCESS for successful session establishment. All other values indicate FAIL.
 */
pse_op_error_t ephemeral_session_m4(
    const pse_cse_msg4_t &pse_cse_msg4)
{
    uint32_t msg_len;
    uint8_t* msg_buf = NULL;
    uint8_t  mac_buf[SGX_SHA256_HASH_SIZE];
    pse_op_error_t op_ret = OP_SUCCESS;

    // check session state
    if (g_eph_session.state != SESSION_IN_PROGRESS)
    {
        op_ret = OP_ERROR_INVALID_EPH_SESSION;
        goto error;
    }

    // verify message4
    if (memcmp(g_nonce_r_pse, pse_cse_msg4.nonce_r_pse, sizeof(pse_cse_msg4.nonce_r_pse)) != 0
        || memcmp(g_pairing_data.secret_data.Id_cse, pse_cse_msg4.id_cse, sizeof(pse_cse_msg4.id_cse)) != 0)
    {
        op_ret = OP_ERROR_EPH_SESSION_ESTABLISHMENT_INTEGRITY_ERROR;
        goto error;
    }

    // verify MAC value
    if (!verify_hmac_sha256(g_pairing_data.secret_data.mk,
            sizeof(g_pairing_data.secret_data.mk),
            (const uint8_t*)&pse_cse_msg4,
            CSE_ID_SIZE + EPH_SESSION_NONCE_SIZE,
            pse_cse_msg4.mac))
    {
        op_ret = OP_ERROR_EPH_SESSION_ESTABLISHMENT_INTEGRITY_ERROR;
        goto error;
    }

    // Deriving Transient Session Key
    msg_len = EPH_SESSION_NONCE_SIZE * 2;
    msg_buf = (uint8_t*)malloc(msg_len);
    if (msg_buf == NULL)
    {
        op_ret = OP_ERROR_MALLOC;
        goto error;
    }

    // calculate session key
    memcpy(msg_buf, g_nonce_r_pse, EPH_SESSION_NONCE_SIZE);
    memcpy(msg_buf + EPH_SESSION_NONCE_SIZE, g_nonce_r_cse, EPH_SESSION_NONCE_SIZE);
    // TSK := HMAC-SHA256sk(Rpse || Rcse)
    if (ippsHMAC_Message(msg_buf,
            msg_len,
            g_pairing_data.secret_data.sk,
            sizeof(g_pairing_data.secret_data.sk),
            mac_buf,
            SGX_SHA256_HASH_SIZE, IPP_ALG_HASH_SHA256) != ippStsNoErr)
    {
        SAFE_FREE(msg_buf);
        op_ret = OP_ERROR_INTERNAL;
        goto error;
    }
    memcpy(g_eph_session.TSK, mac_buf, EPH_SESSION_TSK_SIZE);
    memcpy(g_eph_session.TMK, mac_buf + EPH_SESSION_TSK_SIZE, EPH_SESSION_TMK_SIZE);

    // free buffer
    SAFE_FREE(msg_buf);

    // mark ephemeral session as established
    g_eph_session.state = SESSION_ACTIVE;

    // reset global variables that will be never used anymore
    memset_s(g_nonce_r_cse, sizeof(g_nonce_r_cse), 0, sizeof(g_nonce_r_cse));
    memset_s(g_nonce_r_pse, sizeof(g_nonce_r_pse), 0, sizeof(g_nonce_r_pse));
    // keep the CSE security property information and the pairing nonce in the enclave memory
    memset_s(&g_pairing_data.secret_data.sk, sizeof(SIGMA_SECRET_KEY), 0, sizeof(SIGMA_SECRET_KEY));
    memset_s(&g_pairing_data.secret_data.mk, sizeof(SIGMA_MAC_KEY), 0, sizeof(SIGMA_MAC_KEY));
    memset_s(&g_pairing_data.secret_data.Id_pse, sizeof(SHA256_HASH), 0, sizeof(SHA256_HASH));
    memset_s(&g_pairing_data.secret_data.Id_cse, sizeof(SHA256_HASH), 0, sizeof(SHA256_HASH));
    return OP_SUCCESS;

error:
    memset_s(&g_pairing_data.secret_data, sizeof(se_secret_pairing_data_t), 0, sizeof(se_secret_pairing_data_t));
    memset(&g_eph_session, 0, sizeof(g_eph_session));
    return op_ret;
}

bool copy_global_pairing_nonce(uint8_t* target_buffer)
{
    // g_pairing_data availability is ensured by checking ephemeral session state at the ECALL layer
    // but we check again here
    if (!is_eph_session_active())
    {
        return false;
    }

    memcpy(target_buffer, g_pairing_data.secret_data.pairingNonce, sizeof(Nonce128_t));

    return true;
}

void copy_pse_instance_id(uint8_t* pse_instance_id)
{
    memcpy(pse_instance_id, g_pairing_data.plaintext.pse_instance_id, SW_INSTANCE_ID_SIZE);
}
