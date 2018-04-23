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

#include "CertificateProvisioningProtocol.h"
#include <cstddef>
#include "oal/oal.h"
#include "network_encoding_wrapper.h"

CertificateProvisioningProtocol::CertificateProvisioningProtocol() :
    m_is_initialized(false),
    m_url(""),
    m_nextState(msg_next_state_M1),
    generalResponseStatus(GRS_OK),
    protocolResponseStatus(PSE_PRS_OK)
{
}


CertificateProvisioningProtocol::~CertificateProvisioningProtocol(void)
{
}

extern public_key_t s_public_key;

ae_error_t CertificateProvisioningProtocol::init(const char* szURL, const signed_pek_t& pek)
{
    if (NULL == szURL)
        return AESM_PSE_PR_BACKEND_INVALID_URL;

    m_url = szURL;

    memset(&m_publicKey, 0, sizeof(m_publicKey));
    // Swap n
    for (unsigned i = 0, j = static_cast<uint32_t>(sizeof(pek.n)-1); i < sizeof(pek.n); i++, j--)
    {
        m_publicKey.n[i] = pek.n[j];
    }
    // Swap e
    for (unsigned i = 0, j = static_cast<uint32_t>(sizeof(pek.e)-1); i < sizeof(pek.e); i++, j--)
    {
        ((uint8_t*)&m_publicKey.e)[i] = pek.e[j];
    }

    m_is_initialized = true;
    return AE_SUCCESS;
}


ae_error_t CertificateProvisioningProtocol::SendM1_ReceiveM2
(   /*in */ const uint32_t gid,
    /*out*/ upse::Buffer& nonce,
    /*out*/ upse::Buffer& sigRLBuffer
)
{
    ae_error_t status = AE_FAILURE;

    upse::Buffer serializedMsg1;
    upse::Buffer serializedMsg2;

    do
    {
        BREAK_IF_FALSE((m_is_initialized), status, AESM_PSE_PR_BACKEND_NOT_INITIALIZED);

        BREAK_IF_FALSE( (msg_next_state_M1 == m_nextState), status, AESM_PSE_PR_CALL_ORDER_ERROR);

        status = msg1_generate(*(const GroupId*)&gid, serializedMsg1);
        BREAK_IF_FAILED_ERR(status, AESM_PSE_PR_BACKEND_MSG1_GENERATE);
//        BREAK_IF_FAILED(status);

        status = sendReceive(serializedMsg1, serializedMsg2);
        if (AE_FAILED(status))
            break;

        status = msg2_process(serializedMsg2, nonce, sigRLBuffer);
        if (AE_FAILED(status))
            break;

        m_nextState = msg_next_state_M3;
    } while (0);

    return status;
}


ae_error_t CertificateProvisioningProtocol::SendM3_ReceiveM4
(   /*in */ const upse::Buffer& csrBuffer,
    /*in */ const upse::Buffer& quoteBuffer,
    /*out*/ std::list< upse::Buffer >& certificateChainList,
    /*out*/ platform_info_blob_wrapper_t& piBlobWrapper
)
{
    ae_error_t status = AE_FAILURE;

    upse::Buffer serializedMsg3;
    upse::Buffer serializedMsg4;
    AESM_DBG_TRACE("start to send M3");

    do
    {
        BREAK_IF_FALSE((m_is_initialized), status, AESM_PSE_PR_BACKEND_NOT_INITIALIZED);

        BREAK_IF_FALSE( (msg_next_state_M3 == m_nextState), status, AESM_PSE_PR_CALL_ORDER_ERROR);

        status = msg3_generate(csrBuffer, quoteBuffer, serializedMsg3);
        BREAK_IF_FAILED_ERR(status, AESM_PSE_PR_BACKEND_MSG3_GENERATE);
//        BREAK_IF_FAILED(status);
        AESM_DBG_TRACE("M3 generated");
        status = sendReceive(serializedMsg3, serializedMsg4);
        BREAK_IF_FAILED(status);
        AESM_DBG_TRACE("start to process M4");
        status = msg4_process(serializedMsg4, certificateChainList, piBlobWrapper);
        BREAK_IF_FAILED(status);
        AESM_DBG_TRACE("finished M4");
    } while (0);

    m_nextState = msg_next_state_init;

    return status;
}

//***************************************************************************************************************************************
//***************************************************************************************************************************************
//***************************************************************************************************************************************


ae_error_t CertificateProvisioningProtocol::check_response_header(const provision_response_header_t& header, uint8_t msg_type, uint32_t msg_size)
{
    ae_error_t status = AE_FAILURE;

    do
    {
        if (sizeof(provision_request_header_t) > msg_size)
            break;

        if (header.protocol != PSE_PROVISIONING || header.type != msg_type || header.version < TLV_VERSION_1)
            break;

        const uint32_t* temp = reinterpret_cast<const uint32_t*>(header.size);
        uint32_t totalSize = _ntohl(*temp);
        if (totalSize + PROVISION_RESPONSE_HEADER_SIZE != msg_size)
            break;

        if (sizeof(header.xid) != TransactionID.getSize())
            break;

        if (0 != memcmp(header.xid, TransactionID.getData(), sizeof(header.xid)))
            break;

        status = AE_SUCCESS;
    } while (0);

    return status;
}

ae_error_t CertificateProvisioningProtocol::check_response_status(const  provision_response_header_t& msg2_header)
{
    ae_error_t status = PVE_SERVER_REPORTED_ERROR;

    do
    {
        generalResponseStatus = static_cast<general_response_status_t>(lv_ntohs(msg2_header.gstatus));
        protocolResponseStatus = static_cast<pse_protocol_response_status_t>(lv_ntohs(msg2_header.pstatus));

        // gstatus: GRS_OK, GRS_SERVER_BUSY, GRS_INTEGRITY_CHECK_FAIL, GRS_INCORRECT_SYNTAX,
        //          GRS_INCOMPATIBLE_VERSION, GRS_TRANSACTION_STATE_LOST, GRS_PROTOCOL_ERROR, GRS_INTERNAL_ERROR
        // pstatus: PSE_PRS_OK, PSE_PRS_INVALID_GID, PSE_PRS_GID_REVOKED, PSE_PRS_INVALID_QUOTE, PSE_PRS_INVALID_REQUEST

        if (protocolResponseStatus != PSE_PRS_OK || generalResponseStatus != GRS_OK)
            break;

        status = AE_SUCCESS;
    } while (0);

    return status;
}



ae_error_t CertificateProvisioningProtocol::sendReceive(const upse::Buffer& sendSerialized, upse::Buffer& recvSerialized)
{
    ae_error_t status = AE_FAILURE;

    uint8_t* recv = NULL;
    uint32_t recv_size = 0;

    do
    {
        AESM_DBG_INFO("start send msg");
	    status = AESMNetworkEncoding::aesm_send_recv_msg_encoding(m_url.c_str(), const_cast<uint8_t *>(sendSerialized.getData()), sendSerialized.getSize(), recv, recv_size);
	    if (AE_FAILED(status))
            break;
        AESM_DBG_INFO("msg received with size %u", recv_size);

        status = recvSerialized.Alloc(recv_size);
        if (AE_FAILED(status))
            break;
        AESM_DBG_INFO("buffer alloced");
        status = upse::BufferWriter(recvSerialized).writeRaw(recv, recv_size);
        if (AE_FAILED(status))
            break;
        AESM_DBG_INFO("buffer written");
        status = AE_SUCCESS;

    } while (0);

    if (NULL != recv)
        AESMNetworkEncoding::aesm_free_response_msg(recv);

    return status;
}




