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

#include <limits.h>
#include "stdlib.h"
#include "string.h"
#include "sgx.h"
#include "sgx_defs.h"
#include "sgx_utils.h"
#include "sgx_ecp_types.h"
#include "sgx_key.h"
#include "sgx_report.h"
#include "sgx_attributes.h"
#include "sgx_trts.h"
#include "ecp_interface.h"
#include "sgx_dh_internal.h"

#define NONCE_SIZE              16
#define MSG_BUF_LEN             (static_cast<uint32_t>(sizeof(sgx_ec256_public_t)*2))
#define MSG_HASH_SZ             32

#ifndef SAFE_FREE
#define SAFE_FREE(ptr) {if (NULL != (ptr)) {free(ptr); (ptr)=NULL;}}
#endif

static sgx_status_t verify_cmac128(
    const sgx_ec_key_128bit_t mac_key,
    const uint8_t* data_buf,
    uint32_t buf_size,
    const uint8_t* mac_buf)
{
    uint8_t data_mac[SGX_CMAC_MAC_SIZE];
    sgx_status_t se_ret = SGX_SUCCESS;

    if(!data_buf || !mac_buf || !mac_key)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    se_ret = sgx_rijndael128_cmac_msg((const sgx_cmac_128bit_key_t*)mac_key,
                                      data_buf,
                                      buf_size,
                                      (sgx_cmac_128bit_tag_t *)data_mac);
    if(SGX_SUCCESS != se_ret)
    {
        return se_ret;
    }
    if(consttime_memequal(mac_buf, data_mac, SGX_CMAC_MAC_SIZE) == 0)
    {
        return SGX_ERROR_MAC_MISMATCH;
    }

    return se_ret;
}

static sgx_status_t dh_generate_message1(sgx_dh_msg1_t *msg1, sgx_internal_dh_session_t *context)
{
    sgx_report_t temp_report;
    sgx_report_data_t report_data = {{0}};
    sgx_target_info_t target;
    sgx_status_t se_ret;
    sgx_ecc_state_handle_t ecc_state = NULL;

    if(!msg1 || !context)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    memset(&temp_report, 0, sizeof(temp_report));
    memset(&target,      0, sizeof(target));

    //Create Report to get target info which targeted towards the initiator of the session
    se_ret = sgx_create_report(&target, &report_data,&temp_report);
    if(se_ret != SGX_SUCCESS)
    {
        return se_ret;
    }

    memcpy(&msg1->target.mr_enclave, 
           &temp_report.body.mr_enclave, 
           sizeof(sgx_measurement_t)); 
    memcpy(&msg1->target.attributes, 
           &temp_report.body.attributes, 
           sizeof(sgx_attributes_t));
    msg1->target.misc_select = temp_report.body.misc_select;

    //Initialize ECC context to prepare for creating key pair
    se_ret = sgx_ecc256_open_context(&ecc_state);
    if(se_ret != SGX_SUCCESS)
    {
        return se_ret;
    }
    //Generate the public key private key pair for Session Responder
    se_ret = sgx_ecc256_create_key_pair((sgx_ec256_private_t*)&context->responder.prv_key, 
                                       (sgx_ec256_public_t*)&context->responder.pub_key, 
                                       ecc_state);
    if(se_ret != SGX_SUCCESS)
    {
         sgx_ecc256_close_context(ecc_state);
         return se_ret;
    }

    //Copying public key to g^a
    memcpy(&msg1->g_a, 
           &context->responder.pub_key, 
           sizeof(sgx_ec256_public_t));

    se_ret = sgx_ecc256_close_context(ecc_state);
    if(SGX_SUCCESS != se_ret)
    {
        return se_ret;
    }

    return SGX_SUCCESS;
}

static sgx_status_t dh_generate_message2(const sgx_dh_msg1_t *msg1,
                                      const sgx_ec256_public_t *g_b, 
                                      const sgx_key_128bit_t *dh_smk,
                                      sgx_dh_msg2_t *msg2)
{
    sgx_report_t temp_report; 
    sgx_report_data_t report_data; 

    sgx_status_t se_ret;

    uint8_t msg_buf[MSG_BUF_LEN] = {0};
    uint8_t msg_hash[MSG_HASH_SZ] = {0};

    if(!msg1 || !g_b || !dh_smk || !msg2)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    memset(msg2, 0, sizeof(sgx_dh_msg2_t));
    memcpy(&msg2->g_b, g_b, sizeof(sgx_ec256_public_t));

    memcpy(msg_buf, 
           &msg1->g_a, 
           sizeof(sgx_ec256_public_t));
    memcpy(msg_buf + sizeof(sgx_ec256_public_t), 
           &msg2->g_b, 
           sizeof(sgx_ec256_public_t));

    se_ret = sgx_sha256_msg(msg_buf, 
                            MSG_BUF_LEN, 
                            (sgx_sha256_hash_t *)msg_hash);
    if(SGX_SUCCESS != se_ret)
    {
        return se_ret;
    }

    // Get REPORT with sha256(msg1->g_a | msg2->g_b) || kdf_id as user data
    // 2-byte little-endian KDF-ID: 0x0001 AES-CMAC Entropy Extraction and Key Derivation 
    memset(&report_data, 0, sizeof(sgx_report_data_t));
    memcpy(&report_data, &msg_hash, sizeof(msg_hash)); 

    uint16_t *kdf_id = (uint16_t *)&report_data.d[sizeof(msg_hash)];
    *kdf_id = AES_CMAC_KDF_ID;

    // Generate Report targeted towards Session Responder
    se_ret = sgx_create_report(&msg1->target, &report_data, &temp_report); 
    if(SGX_SUCCESS != se_ret)
    {
        return se_ret;
    }

    memcpy(&msg2->report, &temp_report, sizeof(sgx_report_t));

    //Calculate the MAC for Message 2
    se_ret = sgx_rijndael128_cmac_msg(dh_smk, 
                                      (uint8_t *)(&msg2->report), 
                                      sizeof(sgx_report_t), 
                                      (sgx_cmac_128bit_tag_t *)msg2->cmac);
    if(SGX_SUCCESS != se_ret)
    {
        return se_ret;
    }

    return SGX_SUCCESS;
}

static sgx_status_t dh_verify_message2(const sgx_dh_msg2_t *msg2, 
                                    const sgx_ec256_public_t *g_a, 
                                    const sgx_key_128bit_t *dh_smk)
{
    sgx_report_t temp_report;
    sgx_status_t se_ret;

    uint8_t msg_buf[MSG_BUF_LEN] = {0};
    uint8_t msg_hash[MSG_HASH_SZ] = {0};

    if(!msg2 || !g_a || !dh_smk)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    /* report_data = SHA256(g_a || g_b) || kdf_id
     * Verify kdf_id first.
     * 2-byte little-endian KDF-ID: 0x0001 AES-CMAC Entropy Extraction and Key Derivation
     */
    uint16_t *kdf_id = (uint16_t *)&msg2->report.body.report_data.d[sizeof(msg_hash)];
    if (*kdf_id != AES_CMAC_KDF_ID)
    {
        return SGX_ERROR_KDF_MISMATCH;
    }

    //Verify the MAC of message 2 obtained from the Session Initiator
    se_ret = verify_cmac128((const uint8_t*)dh_smk, (const uint8_t*)(&msg2->report), sizeof(sgx_report_t), msg2->cmac);
    if(SGX_SUCCESS != se_ret) 
    {
        return se_ret;
    }

    memcpy(&temp_report,&msg2->report,sizeof(sgx_report_t));

    // Verify message 2 report obtained from the Session Initiator
    se_ret = sgx_verify_report(&temp_report);
    if(SGX_SUCCESS != se_ret)
    {
        return se_ret;
    }

    memcpy(msg_buf, g_a, sizeof(sgx_ec256_public_t));
    memcpy(msg_buf + sizeof(sgx_ec256_public_t), &msg2->g_b, sizeof(sgx_ec256_public_t));

    se_ret = sgx_sha256_msg(msg_buf, 
                            MSG_BUF_LEN, 
                           (sgx_sha256_hash_t *)msg_hash);
    if(SGX_SUCCESS != se_ret)
    {
        return se_ret;
    }

    // report_data = SHA256(g_a || g_b) || kdf_id
    // Verify SHA256(g_a || g_b)
    if (0 != memcmp(msg_hash, 
                    &msg2->report.body.report_data, 
                    sizeof(msg_hash)))
    {
        return SGX_ERROR_MAC_MISMATCH; 
    }

    return SGX_SUCCESS;
}

static sgx_status_t dh_generate_message3(const sgx_dh_msg2_t *msg2,
                                      const sgx_ec256_public_t *g_a,
                                      const sgx_key_128bit_t *dh_smk,
                                      sgx_dh_msg3_t *msg3,
                                      uint32_t msg3_additional_prop_len)
{
    sgx_report_t temp_report; 
    sgx_report_data_t report_data;
    sgx_status_t se_ret = SGX_SUCCESS;
    uint32_t maced_size;

    uint8_t msg_buf[MSG_BUF_LEN] = {0};
    uint8_t msg_hash[MSG_HASH_SZ] = {0};

    sgx_target_info_t target;

    if(!msg2 || !g_a || !dh_smk || !msg3)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    maced_size = static_cast<uint32_t>(sizeof(sgx_dh_msg3_body_t)) + msg3_additional_prop_len;

    memset(msg3, 0, sizeof(sgx_dh_msg3_t)); // Don't clear the additional property since the content of the property is provided by caller.

    memcpy(msg_buf, &msg2->g_b, sizeof(sgx_ec256_public_t));
    memcpy(msg_buf + sizeof(sgx_ec256_public_t), g_a, sizeof(sgx_ec256_public_t));

    se_ret = sgx_sha256_msg(msg_buf, 
                            MSG_BUF_LEN, 
                            (sgx_sha256_hash_t *)msg_hash);
    if(se_ret != SGX_SUCCESS)
    {
        return se_ret;
    }

    memset(&target, 0, sizeof(sgx_target_info_t));

    // Get REPORT with SHA256(g_b||g_a) as user data
    memset(&report_data, 0, sizeof(sgx_report_data_t)); 
    memcpy(&report_data, &msg_hash, sizeof(msg_hash));  

    memcpy(&target.attributes,
           &msg2->report.body.attributes,
           sizeof(sgx_attributes_t));
    memcpy(&target.mr_enclave,
           &msg2->report.body.mr_enclave,
           sizeof(sgx_measurement_t));
    target.misc_select = msg2->report.body.misc_select;

    // Generate Report targeted towards Session Initiator
    se_ret = sgx_create_report(&target, &report_data, &temp_report); 
    if(se_ret != SGX_SUCCESS)
    {
        return se_ret;
    }

    memcpy(&msg3->msg3_body.report, 
           &temp_report, 
           sizeof(sgx_report_t)); 

    msg3->msg3_body.additional_prop_length = msg3_additional_prop_len;

    //Calculate the MAC for Message 3
    se_ret = sgx_rijndael128_cmac_msg(dh_smk, 
                                      (uint8_t *)&msg3->msg3_body, 
                                      maced_size, 
                                      (sgx_cmac_128bit_tag_t *)msg3->cmac);
    if(se_ret != SGX_SUCCESS)
    {
        return se_ret;
    }

    return SGX_SUCCESS;
}

static sgx_status_t dh_verify_message3(const sgx_dh_msg3_t *msg3,
                                    const sgx_ec256_public_t *g_a,
                                    const sgx_ec256_public_t *g_b,
                                    const sgx_key_128bit_t *dh_smk)
{
    sgx_report_t temp_report;
    uint32_t maced_size;
    sgx_status_t se_ret;

    uint8_t msg_buf[MSG_BUF_LEN] = {0};
    uint8_t msg_hash[MSG_HASH_SZ] = {0};

    if(!msg3 || !g_a || !g_b || !dh_smk)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    maced_size = static_cast<uint32_t>(sizeof(sgx_dh_msg3_body_t)) + msg3->msg3_body.additional_prop_length;

    //Verify the MAC of message 3 obtained from the Session Responder
    se_ret = verify_cmac128((const uint8_t*)dh_smk, (const uint8_t*)&msg3->msg3_body, maced_size, msg3->cmac);
    if(SGX_SUCCESS != se_ret)
    {
        return se_ret;
    }

    memcpy(&temp_report, &msg3->msg3_body.report, sizeof(sgx_report_t));

    // Verify message 3 report
    se_ret = sgx_verify_report(&temp_report);
    if(SGX_SUCCESS != se_ret)
    {
        return se_ret;
    }

    memcpy(msg_buf, 
           g_b, 
           sizeof(sgx_ec256_public_t));
    memcpy(msg_buf + sizeof(sgx_ec256_public_t), 
           g_a, 
           sizeof(sgx_ec256_public_t));

    se_ret = sgx_sha256_msg(msg_buf, 
                            MSG_BUF_LEN, 
                            (sgx_sha256_hash_t *)msg_hash);
    if(SGX_SUCCESS != se_ret)
    {
        return se_ret;
    }

    // Verify message 3 report data
    if (0 != memcmp(msg_hash, 
                    &msg3->msg3_body.report.body.report_data, 
                    sizeof(msg_hash)))
    {
        return SGX_ERROR_MAC_MISMATCH; 
    }

    return SGX_SUCCESS;
}

// sgx_status_t sgx_dh_init_session()
// @role indicates whether the caller is a Initiator (starting the session negotiation) or a Responder (responding to the intial session negotiation request). 
// @sgx_dh_session is the context of the session.
sgx_status_t sgx_dh_init_session(sgx_dh_session_role_t role, sgx_dh_session_t* sgx_dh_session)
{
    sgx_internal_dh_session_t* session = (sgx_internal_dh_session_t*)sgx_dh_session;

    if(!session || 0 == sgx_is_within_enclave(session, sizeof(sgx_internal_dh_session_t)))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    if(SGX_DH_SESSION_INITIATOR != role && SGX_DH_SESSION_RESPONDER != role)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    memset_s(session, sizeof(sgx_internal_dh_session_t), 0, sizeof(sgx_internal_dh_session_t));

    if(SGX_DH_SESSION_INITIATOR == role)
    {
        session->initiator.state = SGX_DH_SESSION_INITIATOR_WAIT_M1;
    }
    else
    {
        session->responder.state = SGX_DH_SESSION_STATE_RESET;
    }

    session->role = role;

    return SGX_SUCCESS;
}


// Function sgx_dh_responder_gen_msg1 generates M1 message and makes update to the context of the session.
sgx_status_t sgx_dh_responder_gen_msg1(sgx_dh_msg1_t* msg1, sgx_dh_session_t* sgx_dh_session)
{
    sgx_status_t se_ret;
    sgx_internal_dh_session_t* session = (sgx_internal_dh_session_t*)sgx_dh_session;

    // validate session
    if(!session || 
        0 == sgx_is_within_enclave(session, sizeof(sgx_internal_dh_session_t))) // session must be in enclave
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    if(!msg1 ||
       0 == sgx_is_within_enclave(msg1, sizeof(sgx_dh_msg1_t)) ||
       SGX_DH_SESSION_RESPONDER != session->role)
    {
        se_ret = SGX_ERROR_INVALID_PARAMETER;
        goto error;
    }

    if(SGX_DH_SESSION_STATE_RESET != session->responder.state)
    {
        se_ret = SGX_ERROR_INVALID_STATE;
        goto error;
    }

    se_ret = dh_generate_message1(msg1, session);
    if(SGX_SUCCESS != se_ret)
    {
        // return selected error to upper layer
        INTERNAL_SGX_ERROR_CODE_CONVERTOR(se_ret)
        goto error;
    }

    session->responder.state = SGX_DH_SESSION_RESPONDER_WAIT_M2;

    return SGX_SUCCESS;
error:
    // clear session
    memset_s(session, sizeof(sgx_internal_dh_session_t), 0, sizeof(sgx_internal_dh_session_t));
    session->responder.state = SGX_DH_SESSION_STATE_ERROR;
    return se_ret;
}

//sgx_dh_initiator_proc_msg1 processes M1 message, generates M2 message and makes update to the context of the session.
sgx_status_t sgx_dh_initiator_proc_msg1(const sgx_dh_msg1_t* msg1, sgx_dh_msg2_t* msg2, sgx_dh_session_t* sgx_dh_session)
{
    sgx_status_t se_ret;

    sgx_ec256_public_t pub_key;
    sgx_ec256_private_t priv_key;
    sgx_ec256_dh_shared_t shared_key;
    sgx_key_128bit_t dh_smk;

    sgx_internal_dh_session_t* session = (sgx_internal_dh_session_t*) sgx_dh_session;

    // validate session
    if(!session || 
        0 == sgx_is_within_enclave(session, sizeof(sgx_internal_dh_session_t))) // session must be in enclave
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    if( !msg1 || 
        !msg2 || 
        0 == sgx_is_within_enclave(msg1, sizeof(sgx_dh_msg1_t)) ||
        0 == sgx_is_within_enclave(msg2, sizeof(sgx_dh_msg2_t)) ||
        SGX_DH_SESSION_INITIATOR != session->role)
    {
        // clear secret when encounter error
        memset_s(session, sizeof(sgx_internal_dh_session_t), 0, sizeof(sgx_internal_dh_session_t));
        session->initiator.state = SGX_DH_SESSION_STATE_ERROR;
        return SGX_ERROR_INVALID_PARAMETER;
    }

    if(SGX_DH_SESSION_INITIATOR_WAIT_M1 != session->initiator.state)
    {
        // clear secret
        memset_s(session, sizeof(sgx_internal_dh_session_t), 0, sizeof(sgx_internal_dh_session_t));
        session->initiator.state = SGX_DH_SESSION_STATE_ERROR;
        return SGX_ERROR_INVALID_STATE;
    }

    //create ECC context
    sgx_ecc_state_handle_t ecc_state = NULL;
    se_ret = sgx_ecc256_open_context(&ecc_state);
    if(SGX_SUCCESS != se_ret)
    {
        goto error;
    }
    // generate private key and public key
    se_ret = sgx_ecc256_create_key_pair((sgx_ec256_private_t*)&priv_key, 
                                       (sgx_ec256_public_t*)&pub_key, 
                                        ecc_state);
    if(SGX_SUCCESS != se_ret)
    {
        goto error;
    }

    //generate shared_key
    se_ret = sgx_ecc256_compute_shared_dhkey(
                                            (sgx_ec256_private_t *)const_cast<sgx_ec256_private_t*>(&priv_key), 
                                            (sgx_ec256_public_t *)const_cast<sgx_ec256_public_t*>(&msg1->g_a), 
                                            (sgx_ec256_dh_shared_t *)&shared_key, 
                                             ecc_state);

    // clear private key for defense in depth
    memset_s(&priv_key, sizeof(sgx_ec256_private_t), 0, sizeof(sgx_ec256_private_t));

    if(SGX_SUCCESS != se_ret)
    {
        goto error;
    }

    se_ret = derive_key(&shared_key, "SMK", (uint32_t)(sizeof("SMK") -1), &dh_smk);
    if(SGX_SUCCESS != se_ret)
    {
        goto error;
    }

    se_ret = dh_generate_message2(msg1, &pub_key, &dh_smk, msg2);
    if(SGX_SUCCESS != se_ret)
    {
        goto error;
    }

    memcpy(&session->initiator.pub_key, &pub_key, sizeof(sgx_ec256_public_t));
    memcpy(&session->initiator.peer_pub_key, &msg1->g_a, sizeof(sgx_ec256_public_t));
    memcpy(&session->initiator.smk_aek, &dh_smk, sizeof(sgx_key_128bit_t));
    memcpy(&session->initiator.shared_key, &shared_key, sizeof(sgx_ec256_dh_shared_t));
    // clear shared key and SMK
    memset_s(&shared_key, sizeof(sgx_ec256_dh_shared_t), 0, sizeof(sgx_ec256_dh_shared_t));
    memset_s(&dh_smk, sizeof(sgx_key_128bit_t), 0, sizeof(sgx_key_128bit_t));

    if(SGX_SUCCESS != sgx_ecc256_close_context(ecc_state))
    {
        // clear session 
        memset_s(session, sizeof(sgx_internal_dh_session_t), 0, sizeof(sgx_internal_dh_session_t));
        // set error state
        session->initiator.state = SGX_DH_SESSION_STATE_ERROR;
        return SGX_ERROR_UNEXPECTED;
    }

    session->initiator.state = SGX_DH_SESSION_INITIATOR_WAIT_M3;
    return SGX_SUCCESS;

error:
    sgx_ecc256_close_context(ecc_state);

    // clear shared key and SMK
    memset_s(&shared_key, sizeof(sgx_ec256_dh_shared_t), 0, sizeof(sgx_ec256_dh_shared_t));
    memset_s(&dh_smk, sizeof(sgx_key_128bit_t), 0, sizeof(sgx_key_128bit_t));

    memset_s(session, sizeof(sgx_internal_dh_session_t), 0, sizeof(sgx_internal_dh_session_t));
    session->initiator.state = SGX_DH_SESSION_STATE_ERROR;

    // return selected error to upper layer
    INTERNAL_SGX_ERROR_CODE_CONVERTOR(se_ret)

    return se_ret;
}

//sgx_dh_responder_proc_msg2 processes M2 message, generates M3 message, and returns the session key AEK.
sgx_status_t sgx_dh_responder_proc_msg2(const sgx_dh_msg2_t* msg2, 
                                           sgx_dh_msg3_t* msg3, 
                                           sgx_dh_session_t* sgx_dh_session, 
                                           sgx_key_128bit_t* aek,
                                           sgx_dh_session_enclave_identity_t* initiator_identity)
{
    sgx_status_t se_ret;

    sgx_ec256_dh_shared_t shared_key;
    sgx_key_128bit_t dh_smk;

    sgx_internal_dh_session_t* session = (sgx_internal_dh_session_t*)sgx_dh_session;

    // validate session
    if(!session || 
        0 == sgx_is_within_enclave(session, sizeof(sgx_internal_dh_session_t))) // session must be in enclave
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    if(!msg3 || 
        msg3->msg3_body.additional_prop_length > (UINT_MAX - sizeof(sgx_dh_msg3_t)) || // check msg3 length overflow
        0 == sgx_is_within_enclave(msg3, (sizeof(sgx_dh_msg3_t)+msg3->msg3_body.additional_prop_length)) || // must be in enclave
        !msg2 || 
        0 == sgx_is_within_enclave(msg2, sizeof(sgx_dh_msg2_t)) || // must be in enclave
        !aek || 
        0 == sgx_is_within_enclave(aek, sizeof(sgx_key_128bit_t)) || // must be in enclave
        !initiator_identity ||
        0 == sgx_is_within_enclave(initiator_identity, sizeof(sgx_dh_session_enclave_identity_t)) || // must be in enclave
        SGX_DH_SESSION_RESPONDER != session->role)
    {
        // clear secret when encounter error
        memset_s(session, sizeof(sgx_internal_dh_session_t), 0, sizeof(sgx_internal_dh_session_t));
        session->responder.state = SGX_DH_SESSION_STATE_ERROR;
        return SGX_ERROR_INVALID_PARAMETER;
    }

    if(SGX_DH_SESSION_RESPONDER_WAIT_M2 != session->responder.state) // protocol state must be SGX_DH_SESSION_RESPONDER_WAIT_M2
    {
        // clear secret
        memset_s(session, sizeof(sgx_internal_dh_session_t), 0, sizeof(sgx_internal_dh_session_t));
        session->responder.state = SGX_DH_SESSION_STATE_ERROR;
        return SGX_ERROR_INVALID_STATE;
    }

    //create ECC context, and the ECC parameter is
    //NIST standard P-256 elliptic curve.
    sgx_ecc_state_handle_t ecc_state = NULL;
    se_ret = sgx_ecc256_open_context(&ecc_state);
    if(SGX_SUCCESS != se_ret)
    {
        goto error;
    }
    
    //generate shared key, which should be identical with enclave side,
    //from PSE private key and enclave public key
    se_ret = sgx_ecc256_compute_shared_dhkey((sgx_ec256_private_t *)&session->responder.prv_key, 
                                            (sgx_ec256_public_t *)const_cast<sgx_ec256_public_t*>(&msg2->g_b), 
                                            (sgx_ec256_dh_shared_t *)&shared_key, 
                                             ecc_state);

    // For defense-in-depth purpose, responder clears its private key from its enclave memory, as it's not needed anymore.
    memset_s(&session->responder.prv_key, sizeof(sgx_ec256_private_t), 0, sizeof(sgx_ec256_private_t));

    if(se_ret != SGX_SUCCESS)
    {
        goto error;
    }


    //derive keys from session shared key
    se_ret = derive_key(&shared_key, "SMK", (uint32_t)(sizeof("SMK") -1), &dh_smk);
    if(se_ret != SGX_SUCCESS)
    {
        goto error;
    }
    // Verify message 2 from Session Initiator and also Session Initiator's identity
    se_ret = dh_verify_message2(msg2, &session->responder.pub_key, &dh_smk);
    if(SGX_SUCCESS != se_ret)
    {
        goto error;
    }

    initiator_identity->isv_svn = msg2->report.body.isv_svn;
    initiator_identity->isv_prod_id = msg2->report.body.isv_prod_id;
    memcpy(&initiator_identity->attributes, &msg2->report.body.attributes, sizeof(sgx_attributes_t));
    memcpy(&initiator_identity->mr_signer, &msg2->report.body.mr_signer, sizeof(sgx_measurement_t));
    memcpy(&initiator_identity->mr_enclave, &msg2->report.body.mr_enclave, sizeof(sgx_measurement_t));

    // Generate message 3 to send back to initiator
    se_ret = dh_generate_message3(msg2, 
                               &session->responder.pub_key, 
                               &dh_smk, 
                               msg3, 
                               msg3->msg3_body.additional_prop_length);
    if(SGX_SUCCESS != se_ret)
    {
        goto error;
    }

    // derive session key
    se_ret = derive_key(&shared_key, "AEK", (uint32_t)(sizeof("AEK") -1), aek);
    if(se_ret != SGX_SUCCESS)
    {
        goto error;
    }

    // clear secret
    memset_s(&shared_key, sizeof(sgx_ec256_dh_shared_t), 0, sizeof(sgx_ec256_dh_shared_t)); 
    memset_s(&dh_smk, sizeof(sgx_key_128bit_t), 0, sizeof(sgx_key_128bit_t));
    // clear session
    memset_s(session, sizeof(sgx_internal_dh_session_t), 0, sizeof(sgx_internal_dh_session_t));

    se_ret = sgx_ecc256_close_context(ecc_state);
    if(SGX_SUCCESS != se_ret)
    {
        // set error state
        session->responder.state = SGX_DH_SESSION_STATE_ERROR;
        return SGX_ERROR_UNEXPECTED;
    }

    // set state
    session->responder.state = SGX_DH_SESSION_ACTIVE;

    return SGX_SUCCESS;

error:
    sgx_ecc256_close_context(ecc_state);
    // clear secret 
    memset_s(&shared_key, sizeof(sgx_ec256_dh_shared_t), 0, sizeof(sgx_ec256_dh_shared_t)); 
    memset_s(&dh_smk, sizeof(sgx_key_128bit_t), 0, sizeof(sgx_key_128bit_t));
    memset_s(session, sizeof(sgx_internal_dh_session_t), 0, sizeof(sgx_internal_dh_session_t));
    // set error state
    session->responder.state = SGX_DH_SESSION_STATE_ERROR;
    // return selected error to upper layer
    if (se_ret != SGX_ERROR_OUT_OF_MEMORY &&
        se_ret != SGX_ERROR_KDF_MISMATCH)
    {
        se_ret = SGX_ERROR_UNEXPECTED;
    }
    return se_ret;
}

//sgx_dh_initiator_proc_msg3 processes M3 message, and returns the session key AEK.
sgx_status_t sgx_dh_initiator_proc_msg3(const sgx_dh_msg3_t* msg3, 
                                       sgx_dh_session_t* sgx_dh_session, 
                                       sgx_key_128bit_t* aek,
                                       sgx_dh_session_enclave_identity_t* responder_identity)
{
    sgx_status_t se_ret;
    sgx_internal_dh_session_t* session = (sgx_internal_dh_session_t*)sgx_dh_session;

    // validate session
    if(!session || 
        0 == sgx_is_within_enclave(session, sizeof(sgx_internal_dh_session_t))) // session must be in enclave
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    if(!msg3 || 
        msg3->msg3_body.additional_prop_length > (UINT_MAX - sizeof(sgx_dh_msg3_t)) || // check msg3 length overflow
        0 == sgx_is_within_enclave(msg3, (sizeof(sgx_dh_msg3_t)+msg3->msg3_body.additional_prop_length)) || // msg3 buffer must be in enclave
        !aek || 
        0 == sgx_is_within_enclave(aek, sizeof(sgx_key_128bit_t)) || // aek buffer must be in enclave 
        !responder_identity ||
        0 == sgx_is_within_enclave(responder_identity, sizeof(sgx_dh_session_enclave_identity_t)) || // responder_identity buffer must be in enclave 
        SGX_DH_SESSION_INITIATOR != session->role) // role must be SGX_DH_SESSION_INITIATOR
    {
        memset_s(session, sizeof(sgx_internal_dh_session_t), 0, sizeof(sgx_internal_dh_session_t));
        session->initiator.state = SGX_DH_SESSION_STATE_ERROR;
        return SGX_ERROR_INVALID_PARAMETER;
    }

    if(SGX_DH_SESSION_INITIATOR_WAIT_M3 != session->initiator.state) // protocol state must be SGX_DH_SESSION_INITIATOR_WAIT_M3
    {
        memset_s(session, sizeof(sgx_internal_dh_session_t), 0, sizeof(sgx_internal_dh_session_t));
        session->initiator.state = SGX_DH_SESSION_STATE_ERROR;
        return SGX_ERROR_INVALID_STATE;
    }

    se_ret = dh_verify_message3(msg3, 
                             &session->initiator.peer_pub_key, 
                             &session->initiator.pub_key, 
                             &session->initiator.smk_aek);
    if(SGX_SUCCESS != se_ret)
    {
        goto error;
    }

    // derive AEK
    se_ret = derive_key(&session->initiator.shared_key, "AEK", (uint32_t)(sizeof("AEK") -1), aek);
    if(SGX_SUCCESS != se_ret)
    {
        goto error;
    }

    // clear session
    memset_s(session, sizeof(sgx_internal_dh_session_t), 0, sizeof(sgx_internal_dh_session_t));
    session->initiator.state = SGX_DH_SESSION_ACTIVE;

    // copy the common fields between REPORT and the responder enclave identity
    memcpy(responder_identity, &msg3->msg3_body.report.body, sizeof(sgx_dh_session_enclave_identity_t));

    return SGX_SUCCESS;

error:
    memset_s(session, sizeof(sgx_internal_dh_session_t), 0, sizeof(sgx_internal_dh_session_t));
    session->initiator.state = SGX_DH_SESSION_STATE_ERROR;
    INTERNAL_SGX_ERROR_CODE_CONVERTOR(se_ret)
    return se_ret;
}

