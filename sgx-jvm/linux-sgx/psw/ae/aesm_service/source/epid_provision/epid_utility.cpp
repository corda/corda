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

#include "epid_utility.h"

ae_error_t tlv_error_2_pve_error(tlv_status_t tlv_status)
{
    switch(tlv_status){
    case TLV_SUCCESS:
        return AE_SUCCESS;
    case TLV_INVALID_PARAMETER_ERROR:
        return PVE_PARAMETER_ERROR;
    case TLV_INVALID_MSG_ERROR:
    case TLV_INVALID_FORMAT:
        return PVE_MSG_ERROR;
    case TLV_INSUFFICIENT_MEMORY:
        return PVE_INSUFFICIENT_MEMORY_ERROR;
    case TLV_OUT_OF_MEMORY_ERROR:
        return AE_OUT_OF_MEMORY_ERROR;
    case TLV_UNKNOWN_ERROR:
    case TLV_MORE_TLVS:
    case TLV_UNSUPPORTED:
        return PVE_UNEXPECTED_ERROR;
    }
    return PVE_UNEXPECTED_ERROR;
}

ae_error_t check_endpoint_pg_stauts(const provision_response_header_t *msg_header)
{
    uint16_t gstatus;
    gstatus = lv_ntohs(msg_header->gstatus);
    switch(gstatus){
    case GRS_SERVER_BUSY:
        return PVE_SERVER_BUSY_ERROR;
    case GRS_INTEGRITY_CHECK_FAIL:
        return PVE_INTEGRITY_CHECK_ERROR;
    case GRS_INCOMPATIBLE_VERSION://Backend report that PSW has used too old protocol, we need update PSW software
        return PSW_UPDATE_REQUIRED;
    case GRS_INCORRECT_SYNTAX:
        return PVE_MSG_ERROR;
    case GRS_OK:
        return AE_SUCCESS;
    default: //For endpoint selection, no MAC checking of error code required so that return server reported error directly
        return PVE_SERVER_REPORTED_ERROR;
    }
}

ae_error_t check_epid_pve_pg_status_before_mac_verification(const  provision_response_header_t *msg_header)
{
    uint16_t gstatus;
    gstatus = lv_ntohs(msg_header->gstatus);
    switch(gstatus){
    case GRS_SERVER_BUSY:
        return PVE_SERVER_BUSY_ERROR;
    case GRS_INTEGRITY_CHECK_FAIL:
        return PVE_INTEGRITY_CHECK_ERROR;
    case GRS_INCOMPATIBLE_VERSION://Backend report that PSW has used too old protocol, we need update PSW software, no MAC provided
        return PSW_UPDATE_REQUIRED;
    case GRS_INCORRECT_SYNTAX:
        return PVE_MSG_ERROR;
    case GRS_OK:
        return AE_SUCCESS;
    case GRS_PROTOCOL_ERROR:
        return AE_SUCCESS;//ignore those errors to check them after mac verification passed
    default:
        return PVE_SERVER_REPORTED_ERROR;
    }
}

ae_error_t check_epid_pve_pg_status_after_mac_verification(const  provision_response_header_t *msg_header)
{
    uint16_t gstatus, pstatus;
    gstatus = lv_ntohs(msg_header->gstatus);
    pstatus = lv_ntohs(msg_header->pstatus);
    switch(gstatus){
    case GRS_OK:
        if(pstatus!=SE_PRS_OK)
            return PVE_SERVER_REPORTED_ERROR;
        return AE_SUCCESS;
    case GRS_PROTOCOL_ERROR:
        AESM_LOG_INFO_ADMIN("%s (%d)", g_admin_event_string_table[SGX_ADMIN_EVENT_EPID_PROV_BACKEND_PROTOCOL_ERROR], (int)pstatus);
        AESM_DBG_INFO("Server reported protocol error %d", (int)pstatus);
        switch(pstatus){
        case SE_PRS_STATUS_INTEGRITY_FAILED:
            return PVE_INTEGRITY_CHECK_ERROR;
        case SE_PRS_PLATFORM_REVOKED:
            return PVE_REVOKED_ERROR;
        case SE_PRS_PERFORMANCE_REKEY_NOT_SUPPORTED:
            return PVE_PERFORMANCE_REKEY_NOT_SUPPORTED;
        case SE_PRS_PROV_ATTEST_KEY_NOT_FOUND:
            return PVE_PROV_ATTEST_KEY_NOT_FOUND;
        case SE_PRS_INVALID_REPORT:
            return PVE_INVALID_REPORT;
        default:
            return PVE_SERVER_REPORTED_ERROR;
        }
    default:
        return PVE_SERVER_REPORTED_ERROR;
    }
}
