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

#include "interface_psda.h"
#include "Buffer.h"
#include "byte_order.h"
#include "PSDAService.h"
#include "pse_types.h"

//
// need to fix this
//

#define PSDA_COMMAND_LT 1

#define BREAK_IF_TRUE(x, Sts, ErrCode)  if (x)    { Sts = ErrCode; break; }
#define BREAK_IF_FALSE(x, Sts, ErrCode) if (!(x)) { Sts = ErrCode; break; }

using namespace std;


static JVM_COMM_BUFFER commBuf_s1, commBuf_s3;
static INT32 responseCode;

#pragma pack(1)

typedef struct _pse_cse_lt_msg2_t
{
    uint8_t     s1[104];
}pse_cse_lt_msg2_t;

typedef struct _pse_cse_msg7_t
{
    uint8_t     s2[0];
}pse_cse_msg7_t;

typedef struct _pse_cse_msg8_t
{
    uint8_t     s3[0];
}pse_cse_msg8_t;

typedef struct _lt_session_m1_t
{
    psda_msg_hdr_t msg_hdr;
}lt_session_m1_t;

typedef struct _lt_session_m2_t
{
    psda_msg_hdr_t msg_hdr;
    pse_cse_lt_msg2_t msg2;
}lt_session_m2_t;

typedef struct _lt_session_m7_t
{
    psda_msg_hdr_t msg_hdr;
    pse_cse_msg7_t msg7;
}lt_session_m7_t;

typedef struct _lt_session_m8_t
{
    psda_msg_hdr_t msg_hdr;
    pse_cse_msg8_t msg8;
}lt_session_m8_t;
#pragma pack()


pse_pr_interface_psda::pse_pr_interface_psda(void)
{
}


pse_pr_interface_psda::~pse_pr_interface_psda(void)
{
}

ae_error_t pse_pr_interface_psda::GetS1(/*in*/const uint8_t* pse_instance_id, /*out*/ upse::Buffer& s1)
{
    ae_error_t status = AE_FAILURE;

    lt_session_m1_t lt_session_m1;
    lt_session_m2_t lt_session_m2;

    do
    {
        // endian-ness doesn't matter for these two; they're 0
        memcpy_s(lt_session_m1.msg_hdr.pse_instance_id, SW_INSTANCE_ID_SIZE, pse_instance_id, SW_INSTANCE_ID_SIZE);
        lt_session_m1.msg_hdr.msg_type = PSDA_MSG_TYPE_LT_M1;
        lt_session_m1.msg_hdr.msg_len = 0;

        memset(&lt_session_m2, 0, sizeof(lt_session_m2));
        commBuf_s1.TxBuf->buffer = &lt_session_m1;
        commBuf_s1.TxBuf->length = sizeof(lt_session_m1_t);
        commBuf_s1.RxBuf->buffer = &lt_session_m2;
        commBuf_s1.RxBuf->length = sizeof(lt_session_m2_t);

        status = PSDAService::instance().send_and_recv( 
            PSDA_COMMAND_LT, 
            &commBuf_s1, 
            &responseCode,
            AUTO_RETRY_ON_SESSION_LOSS);
        AESM_DBG_INFO("JHI_SendAndRecv2 response_code is %d", responseCode);

        if (status != AE_SUCCESS) {
            AESM_LOG_ERROR("%s", g_event_string_table[SGX_EVENT_DAL_COMM_FAILURE]);
            break;
        }
        if (PSDA_SUCCESS != responseCode) {
            AESM_LOG_ERROR("%s", g_event_string_table[SGX_EVENT_DAL_SIGMA_ERROR]);
            BREAK_IF_TRUE( (responseCode == PSDA_NOT_PROVISIONED), status, AESM_PSDA_NOT_PROVISONED_ERROR);
            BREAK_IF_TRUE( (responseCode == PSDA_PROTOCOL_NOT_SUPPORTED), status, AESM_PSDA_PROTOCOL_NOT_SUPPORTED);
            BREAK_IF_TRUE( (responseCode == PSDA_INTERNAL_ERROR) , status, AESM_PSDA_INTERNAL_ERROR);
            BREAK_IF_TRUE( (responseCode == PSDA_PERSISTENT_DATA_WRITE_THROTTLED) , status, AESM_PSDA_WRITE_THROTTLED);
        }

        uint32_t msg_len  = _ntohl(lt_session_m2.msg_hdr.msg_len);
        uint32_t msg_type = _ntohl(lt_session_m2.msg_hdr.msg_type);

        if (responseCode != PSDA_SUCCESS || msg_type != PSDA_MSG_TYPE_LT_M2 || msg_len != sizeof(pse_cse_lt_msg2_t))
        {
            status = AE_FAILURE;
            break;
        }

        if (commBuf_s1.RxBuf->length <= sizeof(lt_session_m2.msg_hdr)
            || msg_len != commBuf_s1.RxBuf->length - sizeof(lt_session_m2.msg_hdr))
        {
            AESM_DBG_INFO("Received invalid S1 message from PSDA!");
            status = AE_FAILURE;
            break;
        }

        status = s1.Alloc(msg_len);
        BREAK_IF_FAILED(status);

        upse::BufferWriter bw(s1);
        status = bw.writeRaw((UINT8*)&lt_session_m2.msg2, msg_len);
        BREAK_IF_FAILED(status);

    } while (0);

    return status;
}


ae_error_t pse_pr_interface_psda::ExchangeS2AndS3(/*in*/  const uint8_t* pse_instance_id,
                                                  /*in */ const upse::Buffer& s2, 
                                                  /*out*/ upse::Buffer& s3)
{
    ae_error_t status = AE_FAILURE;

    lt_session_m7_t* pLt_session_m7 = NULL;
    lt_session_m8_t* pLt_session_m8 = NULL;

    uint32_t lt_session_m7_size = static_cast<uint32_t>(sizeof(lt_session_m7_t) + s2.getSize());
    uint32_t lt_session_m8_size = 10000;

    do
    {
        pLt_session_m7 = (lt_session_m7_t*)malloc(lt_session_m7_size);
        BREAK_IF_TRUE( (NULL == pLt_session_m7), status, AESM_PSE_PR_INSUFFICIENT_MEMORY_ERROR);

        pLt_session_m8 = (lt_session_m8_t*)malloc(lt_session_m8_size);
        BREAK_IF_TRUE( (NULL == pLt_session_m8), status, AESM_PSE_PR_INSUFFICIENT_MEMORY_ERROR);
        memset(pLt_session_m8, 0, lt_session_m8_size);

        memcpy_s(pLt_session_m7->msg_hdr.pse_instance_id, SW_INSTANCE_ID_SIZE, pse_instance_id, SW_INSTANCE_ID_SIZE);
        pLt_session_m7->msg_hdr.msg_type = _htonl(PSDA_MSG_TYPE_LT_M7);
        pLt_session_m7->msg_hdr.msg_len  = _htonl(s2.getSize());

        memcpy_s(&pLt_session_m7->msg7, s2.getSize(), s2.getData(), s2.getSize());

        commBuf_s3.TxBuf->buffer = pLt_session_m7;
        commBuf_s3.TxBuf->length = lt_session_m7_size;
        commBuf_s3.RxBuf->buffer = pLt_session_m8;
        commBuf_s3.RxBuf->length = lt_session_m8_size;

        status = PSDAService::instance().send_and_recv( 
            PSDA_COMMAND_LT, 
            &commBuf_s3, 
            &responseCode,
            NO_RETRY_ON_SESSION_LOSS);
        AESM_DBG_INFO("JHI_SendAndRecv2 response_code is %d", responseCode);

        if (status != AE_SUCCESS) {
            AESM_LOG_ERROR("%s", g_event_string_table[SGX_EVENT_DAL_COMM_FAILURE]);
            break;
        }
        if (PSDA_SUCCESS != responseCode) {
            AESM_LOG_ERROR("%s", g_event_string_table[SGX_EVENT_DAL_SIGMA_ERROR]);
            BREAK_IF_TRUE( (responseCode == PSDA_INTEGRITY_ERROR), status, AESM_PSDA_LT_SESSION_INTEGRITY_ERROR);
            BREAK_IF_TRUE( (responseCode == PSDA_INTERNAL_ERROR) , status, AESM_PSDA_INTERNAL_ERROR);
            BREAK_IF_TRUE( (responseCode == PSDA_PLATFORM_KEYS_REVOKED) , status, AESM_PSDA_PLATFORM_KEYS_REVOKED);
            BREAK_IF_TRUE( (responseCode == PSDA_PERSISTENT_DATA_WRITE_THROTTLED) , status, AESM_PSDA_WRITE_THROTTLED);
            BREAK_IF_TRUE( (responseCode != PSDA_SUCCESS)        , status, AESM_PSDA_INTERNAL_ERROR);
        }

        uint32_t msg_len  = _ntohl(pLt_session_m8->msg_hdr.msg_len);
        uint32_t msg_type = _ntohl(pLt_session_m8->msg_hdr.msg_type);

        if (msg_type != PSDA_MSG_TYPE_LT_M8)
        {
            status = AE_FAILURE;
            break;
        }

        if (commBuf_s3.RxBuf->length <= sizeof(pLt_session_m8->msg_hdr) 
        || msg_len != commBuf_s3.RxBuf->length - sizeof(pLt_session_m8->msg_hdr))
        {
            AESM_DBG_INFO("Received invalid S3 message from PSDA!");
            status = AE_FAILURE;
            break;
        }

        status = s3.Alloc(msg_len);
        BREAK_IF_FAILED(status);
        upse::BufferWriter bw(s3);
        status = bw.writeRaw((UINT8*)&pLt_session_m8->msg8, msg_len);
        BREAK_IF_FAILED(status);

        status = AE_SUCCESS;

    } while (0);

    if (NULL != pLt_session_m7)
        free(pLt_session_m7);
    if (NULL != pLt_session_m8)
        free(pLt_session_m8);

    return status;
}


ae_error_t pse_pr_interface_psda::get_csme_gid(EPID_GID* p_cse_gid)
{
    if (p_cse_gid == NULL)
    {
        AESM_DBG_ERROR("input p_cse_gid is NULL");
        return AE_FAILURE;
    }

    psda_info_query_msg_t psda_cert_query_msg;
    psda_cert_query_msg.msg_hdr.msg_type = _htonl(PSDA_MSG_TYPE_CERT_INFO_QUERY);
    psda_cert_query_msg.msg_hdr.msg_len = 0;

    psda_cert_result_msg_t psda_cert_result_msg;
    memset(&psda_cert_result_msg, 0, sizeof(psda_cert_result_msg_t));

    JVM_COMM_BUFFER commBuf;
    commBuf.TxBuf->buffer = &psda_cert_query_msg;
    commBuf.TxBuf->length = sizeof(psda_info_query_msg_t);
    commBuf.RxBuf->buffer = &psda_cert_result_msg;
    commBuf.RxBuf->length = sizeof(psda_cert_result_msg_t);
    int response_code;

    ae_error_t status = PSDAService::instance().send_and_recv(
                                PSDA_COMMAND_INFO,
                                &commBuf,
                                &response_code,
                                AUTO_RETRY_ON_SESSION_LOSS);
    if (status != AE_SUCCESS || response_code != 0) {
        AESM_DBG_ERROR("JHI_SendAndRecv2 ret %d, response_code %d",status, response_code);
        AESM_LOG_ERROR("%s", g_event_string_table[SGX_EVENT_DAL_COMM_FAILURE]);
        if (response_code == PSDA_NOT_PROVISIONED)
            return AESM_PSDA_NOT_PROVISONED_ERROR;
        return AE_FAILURE;
    }

    if (_ntohl(psda_cert_result_msg.msg_hdr.msg_type) != PSDA_MSG_TYPE_CERT_INFO_RESULT
        || _ntohl(psda_cert_result_msg.msg_hdr.msg_len) != 24) {
        AESM_DBG_ERROR("msg_type %d, msg_len %d while expected value type %d, len %d",
        _ntohl(psda_cert_result_msg.msg_hdr.msg_type), _ntohl(psda_cert_result_msg.msg_hdr.msg_len),
        PSDA_MSG_TYPE_CERT_INFO_RESULT, 24);
        return AE_FAILURE;
    }

    memcpy_s(p_cse_gid, sizeof(EPID_GID), psda_cert_result_msg.cert_info, sizeof(EPID_GID));

    return AE_SUCCESS;
}
