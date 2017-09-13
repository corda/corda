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

#include "session_mgr.h"
#include "byte_order.h"
#include "psda_service.h"
#include "util.h"

static pse_op_error_t handle_trusted_time_errors(const pse_op_error_t op_error, pse_service_resp_status_t* status)
{
    switch(op_error)
    {
        case OP_SUCCESS:
            /*  SUCCESS  */
            *status = PSE_SUCCESS;
            return OP_SUCCESS;
        case OP_ERROR_CAP_NOT_AVAILABLE:
            *status = PSE_ERROR_CAP_NOT_AVAILABLE;
            return OP_SUCCESS;
        case OP_ERROR_PSDA_BUSY:
            *status = PSE_ERROR_BUSY;
            return OP_SUCCESS;
        // --- errors cannot be translated to status code
        case OP_ERROR_INVALID_EPH_SESSION:
        case OP_ERROR_PSDA_SESSION_LOST:
            return op_error;        // should not be translated to status code
        // --- end
        default:
            // OP_ERROR_INTERNAL
            // OP_ERROR_INVALID_PARAMETER
            // OP_ERROR_MALLOC
            // OP_ERROR_UNKNOWN_REQUEST
            *status = PSE_ERROR_INTERNAL;
            return OP_SUCCESS;
    }
}

// call PSDA service to get trusted time
pse_op_error_t pse_read_timer(
    const isv_attributes_t &owner_attributes,
    const uint8_t* req_msg, 
    uint8_t* resp_msg)
{
    UNUSED(req_msg);

    pse_op_error_t ret;
    pse_timer_read_resp_t* timer_resp = (pse_timer_read_resp_t*)resp_msg;

    // call psda service to set output value
    ret = psda_read_timer(owner_attributes,
        &timer_resp->timestamp,
        timer_resp->time_source_nonce);

    return handle_trusted_time_errors(ret, &timer_resp->resp_hdr.status);
}
