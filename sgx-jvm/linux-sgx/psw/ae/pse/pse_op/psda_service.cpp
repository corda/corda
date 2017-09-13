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


#include "psda_service.h"
#include "session_mgr.h"
#include "se_memcpy.h"
#include "pse_op_t.h"
#include "util.h"
#include "byte_order.h"
#include <stdlib.h>
#include "utility.h"
#include "sgx_tcrypto.h"

// ephemeral session
extern eph_session_t        g_eph_session;

// Encrypt psda message by AES-CTR-128
static pse_op_error_t encrypt_psda_msg(psda_service_message_t* psda_service_msg,
                      const uint8_t* payload_data,
                      uint32_t payload_size,
                      const sgx_key_128bit_t* tsk)
{
    assert(psda_service_msg != NULL);
    assert(payload_data != NULL);
    assert(tsk != NULL);

    Ipp8u ctr[16];
    uint32_t ctr_num_bit_size = 32;

    // number of blocks in message should not exceed counter
    //if ((uint64_t)payload_size > ((uint64_t)1<<ctr_num_bit_size)*AES_BLOCK_SIZE)
    //{
    //    // counter not enough for encryption
    //    return OP_ERROR_INVALID_PARAMETER;
    //}

    // generate random iv, 96 bits=12 bytes here
    if(sgx_read_rand(&psda_service_msg->service_message.payload_iv[0],
        PSDA_MESSAGE_IV_SIZE-4) != SGX_SUCCESS)
    {
        return OP_ERROR_INTERNAL;
    }

    // setup COUNTER part to all 0, IV[127:0] = counter[127:96] | random[95:0]
    memset(&psda_service_msg->service_message.payload_iv[12], 0, 4);
    //*((uint32_t*)&psda_service_msg->service_message.payload_iv[0]) = 0;
    // for other ctr_num_bit_size:
    //uint64_t* low_ptr = (uint64_t*)&psda_service_msg->service_message.payload_iv[0];
    //uint64_t* high_ptr = (uint64_t*)&psda_service_msg->service_message.payload_iv[8];
    //*low_ptr &= (uint64_t)(-1) << ctr_num_bit_size;
    //*high_ptr &= (uint64_t)(-1) << (ctr_num_bit_size > 64? ctr_num_bit_size - 64 : 0);

     memcpy(ctr, psda_service_msg->service_message.payload_iv, AES_BLOCK_SIZE);
    sgx_status_t stat = sgx_aes_ctr_encrypt((const sgx_aes_ctr_128bit_key_t*)tsk,
                            (const uint8_t*)payload_data,
                            payload_size,
                            ctr,
                            ctr_num_bit_size,
                            psda_service_msg->service_message.payload);
    if (stat == SGX_SUCCESS)
        return OP_SUCCESS;
    else
        return OP_ERROR_INTERNAL;
}

// Decrypt psda message by AES-CTR-128
static pse_op_error_t decrypt_psda_msg(const psda_service_message_t* psda_service_msg,
                      uint8_t* payload_data,
                      const sgx_key_128bit_t* tsk)
{
    assert(psda_service_msg != NULL);
    assert(payload_data != NULL);
    assert(tsk != NULL);

    Ipp8u ctr[16];
    uint32_t ctr_num_bit_size = 32;
    memcpy(ctr, psda_service_msg->service_message.payload_iv, AES_BLOCK_SIZE);

    sgx_status_t stat = sgx_aes_ctr_decrypt((const sgx_aes_ctr_128bit_key_t*)tsk,
                            psda_service_msg->service_message.payload,
                            psda_service_msg->service_message.payload_size,
                            ctr,
                            ctr_num_bit_size,
                            (uint8_t*)payload_data);
    if (stat == SGX_SUCCESS)
        return OP_SUCCESS;
    else
        return OP_ERROR_INTERNAL;
}

static pse_op_error_t check_ephemeral_session_state()
{
    // check ephemeral session status
    if (g_eph_session.state != SESSION_ACTIVE || g_eph_session.seq_num >= (uint32_t)-2)
    {
        // ephemeral session not established or sequence number overflows
        memset_s(&g_eph_session, sizeof(eph_session_t), 0, sizeof(eph_session_t));
        return OP_ERROR_INVALID_EPH_SESSION;
    }

    return OP_SUCCESS;
}

static pse_op_error_t invoke_psda_service(uint8_t* req,
                               uint32_t req_size,
                               uint8_t* resp,
                               uint32_t resp_size)
{
    assert(req!= NULL);
    assert(resp!= NULL);

    pse_op_error_t ret = OP_SUCCESS;
    psda_service_message_t* service_req_message = NULL;
    psda_service_message_t* service_resp_message = NULL;
    psda_req_hdr_t* req_hdr_ptr = (psda_req_hdr_t*)req;
    psda_resp_hdr_t* resp_hdr_ptr = (psda_resp_hdr_t*)resp;
    sgx_status_t stat = SGX_SUCCESS;
    ae_error_t ocall_ret;

    // change endian. Note only members in header are of little endian. Other fields
    // are already converted to big endian before this function is called
    req_hdr_ptr->service_id = _htons(req_hdr_ptr->service_id);
    req_hdr_ptr->service_cmd = _htons(req_hdr_ptr->service_cmd);
    // set sequence number in request header
    req_hdr_ptr->seqnum = _htonl(g_eph_session.seq_num);

    // check ephemeral session
    ret = check_ephemeral_session_state();
    if (ret != OP_SUCCESS)
    {
        goto clean_up;
    }

    // allocate buffer for request message
    service_req_message = (psda_service_message_t*)malloc(
        sizeof(psda_service_message_t) + req_size);
    if (service_req_message == NULL)
    {
        ret = OP_ERROR_MALLOC;
        goto clean_up;
    }

    // Fill request message with proper values
    copy_pse_instance_id(service_req_message->msg_hdr.pse_instance_id);      // set global instance id
    service_req_message->msg_hdr.msg_type = BE_PSDA_MSG_TYPE_SERV_REQ;
    service_req_message->msg_hdr.msg_len = _htonl(sizeof(service_message_t) + req_size);
    service_req_message->service_message.version = BE_PSDA_API_VERSION;
    service_req_message->service_message.session_id = 0;
    service_req_message->service_message.msg_type_exp_resp_size = _htonl(resp_size);
    service_req_message->service_message.payload_size = _htonl(req_size);

    // encrypt message body with AES-CTR
    if ((ret = encrypt_psda_msg(service_req_message,
        req,
        req_size,
        (sgx_key_128bit_t*)g_eph_session.TSK)) != OP_SUCCESS)
    {
        goto clean_up;
    }

    // sign encrypted payload data with HMAC-SHA256
    if (ippsHMAC_Message(service_req_message->service_message.payload,
            req_size,
            g_eph_session.TMK,
            sizeof(g_eph_session.TMK),
            service_req_message->service_message.payload_mac,
            SGX_SHA256_HASH_SIZE, IPP_ALG_HASH_SHA256) != ippStsNoErr)
    {
        ret = OP_ERROR_INTERNAL;
        goto clean_up;
    }

    // allocate response message buffer
    service_resp_message = (psda_service_message_t*)malloc(
        sizeof(psda_service_message_t) + resp_size);
    if (service_resp_message == NULL)
    {
        ret = OP_ERROR_MALLOC;
        goto clean_up;
    }

    // always increase seqnum by 2
    g_eph_session.seq_num += 2;

    // OCALL PSDA service
    stat = psda_invoke_service_ocall(&ocall_ret,
            (uint8_t*)service_req_message,
            static_cast<uint32_t>(sizeof(psda_service_message_t)) + req_size,
            (uint8_t*)service_resp_message,
            static_cast<uint32_t>(sizeof(psda_service_message_t)) + resp_size);

    if (stat != SGX_SUCCESS)
    {
        ret = OP_ERROR_INTERNAL;
        goto clean_up;
    }
    else {
        switch(ocall_ret)
        {
        case AE_SUCCESS:
            break;
        case AESM_PSDA_INTERNAL_ERROR:
            /* Internal errors or under attack */
            ret = OP_ERROR_INTERNAL;
            goto clean_up;
        case AESM_PSDA_NEED_REPAIRING:
            ret = OP_ERROR_INVALID_EPH_SESSION;
            goto clean_up;
        case AESM_PSDA_SESSION_LOST:
            ret = OP_ERROR_PSDA_SESSION_LOST;
            goto clean_up;
        default:
            ret = OP_ERROR_INTERNAL;
            goto clean_up;
        }
    }

    // The message we received is of BigEndian format, convert to LE
    service_resp_message->service_message.payload_size = _ntohl(service_resp_message->service_message.payload_size);

    // verify response message type and size
    if (service_resp_message->msg_hdr.msg_type != BE_PSDA_MSG_TYPE_SERV_RESP
        || service_resp_message->service_message.version != BE_PSDA_API_VERSION
        || service_resp_message->service_message.payload_size != resp_size)
    {
        // return OP_ERROR_INVALID_EPH_SESSION will trigger ephemeral session re-establishment
        ret = OP_ERROR_INVALID_EPH_SESSION;
        memset_s(&g_eph_session, sizeof(eph_session_t), 0, sizeof(eph_session_t));
        goto clean_up;
    }

    // verify HMAC
    if (!verify_hmac_sha256(g_eph_session.TMK,
            sizeof(g_eph_session.TMK),
            service_resp_message->service_message.payload,
            service_resp_message->service_message.payload_size,
            service_resp_message->service_message.payload_mac))
    {
        // return OP_ERROR_INVALID_EPH_SESSION will trigger ephemeral session re-establishment
        ret = OP_ERROR_INVALID_EPH_SESSION;
        memset_s(&g_eph_session, sizeof(eph_session_t), 0, sizeof(eph_session_t));
        goto clean_up;
    }

    // decrypt message body
    if ((ret = decrypt_psda_msg(service_resp_message,
        resp,
        (sgx_key_128bit_t*)g_eph_session.TSK)) != OP_SUCCESS)
    {
        goto clean_up;
    }

    // resp_msg is of BigEndian format, convert to LE first
    resp_hdr_ptr->service_id = _ntohs(resp_hdr_ptr->service_id);
    resp_hdr_ptr->service_cmd = _ntohs(resp_hdr_ptr->service_cmd);
    resp_hdr_ptr->seqnum = _ntohl(resp_hdr_ptr->seqnum);
    resp_hdr_ptr->status = _ntohl(resp_hdr_ptr->status);

    // verify service command, id, status and SEQNUM
    if (resp_hdr_ptr->service_id != _ntohs(req_hdr_ptr->service_id)
        || resp_hdr_ptr->service_cmd != _ntohs(req_hdr_ptr->service_cmd)
        || resp_hdr_ptr->seqnum != g_eph_session.seq_num - 1)
    {
        // return OP_ERROR_INVALID_EPH_SESSION will trigger ephemeral session re-establishment
        ret = OP_ERROR_INVALID_EPH_SESSION;
        memset_s(&g_eph_session, sizeof(eph_session_t), 0, sizeof(eph_session_t));
        goto clean_up;
    }

    switch (resp_hdr_ptr->status)
    {
    case CSE_SERVICE_SUCCESS:
        break;
    case CSE_ERROR_UNKNOWN_REQ:
        // for the first generation of PSE-PSDA product, CSE_UNKOWN_REQUEST_RESP is not expected
        ret = OP_ERROR_UNKNOWN_REQUEST;
        goto clean_up;
    case CSE_ERROR_CAP_NOT_AVAILABLE:
        ret = OP_ERROR_CAP_NOT_AVAILABLE;
        goto clean_up;
    case CSE_ERROR_INVALID_PARAM:
        ret = OP_ERROR_INVALID_PARAMETER;
        goto clean_up;
    case CSE_ERROR_INTERNAL:
        ret = OP_ERROR_INTERNAL;
        goto clean_up;
    case CSE_ERROR_PERSISTENT_DATA_WRITE_THROTTLED:
        ret = OP_ERROR_PSDA_BUSY;
        goto clean_up;
    default:
        ret = OP_ERROR_INTERNAL;
        goto clean_up;
    }

clean_up:
    // memory free
    SAFE_FREE(service_req_message);
    SAFE_FREE(service_resp_message);

    return ret;
}


/*
Calculate TimeSourceNonce
*/
static pse_op_error_t calculate_time_source_nonce(const uint8_t* pairing_nonce,
                                             const uint32_t pairing_nonce_size,
                                             const uint8_t* time_epoch,
                                             const uint32_t time_epoch_size,
                                             const sgx_measurement_t *mrsigner,
                                             void* time_source_nonce)
{
    assert(pairing_nonce != NULL);
    assert(time_epoch != NULL);
    assert(time_source_nonce != NULL);

    sgx_sha_state_handle_t ctx = NULL;
    sgx_status_t sgx_ret = SGX_SUCCESS;

    do
    {
        // Init
        sgx_ret = sgx_sha256_init(&ctx);
        BREAK_ON_ERROR(sgx_ret);

        // pairing-nonce
        sgx_ret = sgx_sha256_update(pairing_nonce,
                                    pairing_nonce_size,
                                    ctx);
        BREAK_ON_ERROR(sgx_ret);

        // PRTC-EPOCH
        sgx_ret = sgx_sha256_update(time_epoch,
                                    time_epoch_size,
                                    ctx);
        BREAK_ON_ERROR(sgx_ret);

        // MRSIGNER
        sgx_ret = sgx_sha256_update((const uint8_t*)mrsigner,
                                    sizeof(sgx_measurement_t),
                                    ctx);
        BREAK_ON_ERROR(sgx_ret);

        // Finalize
        sgx_ret = sgx_sha256_get_hash(ctx, (sgx_sha256_hash_t*)time_source_nonce);
        BREAK_ON_ERROR(sgx_ret);
    } while (0);

    if(ctx)
    {
        sgx_status_t ret = sgx_sha256_close(ctx);
        sgx_ret = (sgx_ret != SGX_SUCCESS)? sgx_ret : ret;
    }

    if (sgx_ret == SGX_SUCCESS)
    {
        return OP_SUCCESS;
    }
    else
    {
        return OP_ERROR_INTERNAL;
    }
}

// call PSDA RPDATA service to read RPDATA
pse_op_error_t psda_read_rpdata(uint8_t* rpdata, uint32_t* rp_epoch)
{
    assert(rpdata != NULL);
    assert(rp_epoch != NULL);

    pse_op_error_t ret;
    cse_rpdata_read_req_t cse_rpdata_read_req;
    cse_rpdata_resp_t cse_rpdata_resp;

    // prepare request header
    cse_rpdata_read_req.req_hdr.service_id = CSE_RPDATA_SERVICE;
    cse_rpdata_read_req.req_hdr.service_cmd = CSE_RPDATA_READ;

    ret = invoke_psda_service(
        (uint8_t*)&cse_rpdata_read_req,
        sizeof(cse_rpdata_read_req_t),
        (uint8_t*)&cse_rpdata_resp,
        sizeof(cse_rpdata_resp_t));
    if (ret != OP_SUCCESS)
    {
        return ret;
    }

    // copy RPDATA buffer
    memcpy(rpdata, cse_rpdata_resp.rpdata, SGX_RPDATA_SIZE);
    *rp_epoch = cse_rpdata_resp.rp_epoch;

    return OP_SUCCESS;
}


// call PSDA RPDATA service to update RPDATA
pse_op_error_t psda_update_rpdata(uint8_t* rpdata_cur, uint8_t* rpdata_new, uint32_t* rp_epoch)
{
    assert(rpdata_cur != NULL);
    assert(rpdata_new != NULL);
    assert(rp_epoch != NULL);

    pse_op_error_t ret;
    cse_rpdata_update_req_t cse_rpdata_update_req;
    cse_rpdata_resp_t cse_rpdata_resp;

    // prepare request header
    cse_rpdata_update_req.req_hdr.service_id = CSE_RPDATA_SERVICE;
    cse_rpdata_update_req.req_hdr.service_cmd = CSE_RPDATA_UPDATE;
    memcpy(cse_rpdata_update_req.rpdata_cur, rpdata_cur, SGX_RPDATA_SIZE);
    memcpy(cse_rpdata_update_req.rpdata_new, rpdata_new, SGX_RPDATA_SIZE);

    ret = invoke_psda_service(
        (uint8_t*)&cse_rpdata_update_req,
        sizeof(cse_rpdata_update_req_t),
        (uint8_t*)&cse_rpdata_resp,
        sizeof(cse_rpdata_resp_t));
    if (ret != OP_SUCCESS)
    {
        return ret;
    }

    if (memcmp(rpdata_new, &cse_rpdata_resp.rpdata[0], SGX_RPDATA_SIZE) != 0)
        return OP_ERROR_INTERNAL;

    // update success
    *rp_epoch = cse_rpdata_resp.rp_epoch;

    return OP_SUCCESS;
}

// call PSDA RPDATA service to reset RPDATA
pse_op_error_t psda_reset_rpdata(uint8_t* rpdata_cur, uint8_t* rpdata_new, uint32_t* rp_epoch)
{
    assert(rpdata_cur != NULL);
    assert(rp_epoch != NULL);
    assert(rpdata_new != NULL);

    pse_op_error_t ret;
    cse_rpdata_reset_req_t cse_rpdata_reset_req;
    cse_rpdata_resp_t cse_rpdata_resp;

    // prepare request header
    cse_rpdata_reset_req.req_hdr.service_id = CSE_RPDATA_SERVICE;
    cse_rpdata_reset_req.req_hdr.service_cmd = CSE_RPDATA_RESET;
    memcpy(cse_rpdata_reset_req.rpdata_cur, rpdata_cur, SGX_RPDATA_SIZE);

    ret = invoke_psda_service(
        (uint8_t*)&cse_rpdata_reset_req,
        sizeof(cse_rpdata_reset_req_t),
        (uint8_t*)&cse_rpdata_resp,
        sizeof(cse_rpdata_resp_t));
    if (ret != OP_SUCCESS)
    {
        return ret;
    }

    // reset success
    *rp_epoch = cse_rpdata_resp.rp_epoch;
    memcpy(rpdata_new, &cse_rpdata_resp.rpdata, SGX_RPDATA_SIZE);

    return OP_SUCCESS;
}

// call PSDA service to get trusted time
pse_op_error_t psda_read_timer(
    const isv_attributes_t &owner_attributes,
    uint64_t* timestamp,
    uint8_t* time_source_nonce)
{
    cse_timer_read_req_t cse_timer_req;
    cse_timer_read_resp_t cse_timer_resp;

    // prepare request header
    cse_timer_req.req_hdr.service_id = CSE_TRUSTED_TIME_SERVICE;
    cse_timer_req.req_hdr.service_cmd = CSE_TIMER_READ;

    pse_op_error_t ret = invoke_psda_service(
        (uint8_t*)&cse_timer_req,
        sizeof(cse_timer_read_req_t),
        (uint8_t*)&cse_timer_resp,
        sizeof(cse_timer_read_resp_t));
    if(OP_SUCCESS == ret)
    {
        // set trusted time ( note cse_timer_resp.timestamp is of BE format)
        uint32_t high = (uint32_t)(cse_timer_resp.timestamp & 0x00000000FFFFFFFFLL);
        uint32_t low = (uint32_t)((cse_timer_resp.timestamp & 0xFFFFFFFF00000000LL) >> 32);
        high = _ntohl(high);
        low = _ntohl(low);
        *timestamp = (uint64_t)low + (((uint64_t)high) << 32);

        uint32_t prtc_epoch = cse_timer_resp.epoch;
        uint8_t pairing_nonce[16];

        if (!copy_global_pairing_nonce(&pairing_nonce[0]))
            return OP_ERROR_INTERNAL;

        // TimeSourceNonce = SHA256(pairing-nonce||PRTC-EPOCH||Session.ENCALVEMRSIGNER)
        if (calculate_time_source_nonce(pairing_nonce,
                                    sizeof(Nonce128_t),
                                    (uint8_t*)&prtc_epoch,
                                    sizeof(prtc_epoch),
                                    &owner_attributes.mr_signer,
                                    time_source_nonce
                                    ) != OP_SUCCESS)
        {
            ret = OP_ERROR_INTERNAL;
        }

    }

    return ret;
}

