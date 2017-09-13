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


#include "monotonic_counter.h"
#include "monotonic_counter_database_sqlite_rpdb.h"
#include <assert.h>

static pse_op_error_t handle_vmc_errors(const pse_op_error_t op_error, pse_service_resp_status_t* status)
{
    switch(op_error)
    {
        case OP_SUCCESS:
            /*  SUCCESS  */
            *status = PSE_SUCCESS;
            return OP_SUCCESS;
        case OP_ERROR_INVALID_COUNTER:
            /*  No VMC entry matches the counter ID  */
            *status = PSE_ERROR_MC_NOT_FOUND;
            return OP_SUCCESS;
        case OP_ERROR_INVALID_OWNER:
            *status = PSE_ERROR_MC_NO_ACCESS_RIGHT;
            return OP_SUCCESS;
        case OP_ERROR_CAP_NOT_AVAILABLE:
            *status = PSE_ERROR_CAP_NOT_AVAILABLE;
            return OP_SUCCESS;
        case OP_ERROR_DATABASE_FULL:
            *status = PSE_ERROR_MC_USED_UP;
            return OP_SUCCESS;
        case OP_ERROR_DATABASE_OVER_QUOTA:
            *status = PSE_ERROR_MC_OVER_QUOTA;
            return OP_SUCCESS;
        case OP_ERROR_INVALID_POLICY:
            *status = PSE_ERROR_INVALID_POLICY;
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
            // OP_ERROR_SQLITE_INTERNAL
            // OP_ERROR_UNKNOWN_REQUEST
            // OP_ERROR_COPY_PREBUILD_DB
            *status = PSE_ERROR_INTERNAL;
            return OP_SUCCESS;
    }
}

pse_op_error_t pse_mc_create(
    const isv_attributes_t &owner_attributes,
    const uint8_t* req,
    uint8_t* resp)
{
    assert(req != NULL && resp != NULL);

    pse_op_error_t op_ret = OP_SUCCESS;
    const pse_mc_create_req_t* create_req = (const pse_mc_create_req_t*)req;
    pse_mc_create_resp_t* create_resp = (pse_mc_create_resp_t*)resp;

    //initial create_resp
    memset(create_resp->counter_id, 0xFF, UUID_ENTRY_INDEX_SIZE);
    memset(create_resp->nonce, 0x0, UUID_NONCE_SIZE);

    mc_rpdb_uuid_t uuid;
    vmc_data_blob_t data;

    memset(&uuid, 0xff, sizeof(uuid));
    memset(&data, 0, sizeof(data));

    memcpy(data.owner_attr_mask, create_req->attr_mask, sizeof(data.owner_attr_mask));
    data.owner_policy    = create_req->policy;

    if(0 == (create_req->policy & (MC_POLICY_SIGNER | MC_POLICY_ENCLAVE))) // Invalid policy
    {
        return OP_ERROR_INVALID_POLICY;
    }

    op_ret = create_vmc(owner_attributes, data, uuid);
    if(OP_SUCCESS == op_ret)
    {
        memcpy(create_resp->counter_id, uuid.entry_index, UUID_ENTRY_INDEX_SIZE);
        memcpy(create_resp->nonce, uuid.nonce, UUID_NONCE_SIZE);
    }

    return handle_vmc_errors(op_ret, &create_resp->resp_hdr.status);
}

pse_op_error_t pse_mc_read(
    const isv_attributes_t &owner_attributes,
    const uint8_t* req,
    uint8_t* resp)
{
    assert(req != NULL && resp != NULL);

    pse_op_error_t op_ret = OP_SUCCESS;
    const pse_mc_read_req_t* read_req = (const pse_mc_read_req_t*)req;
    pse_mc_read_resp_t* read_resp = (pse_mc_read_resp_t*)resp;
    vmc_data_blob_t vmc;
    mc_rpdb_uuid_t uuid;

    memset(&vmc,  0, sizeof(vmc));

    //initial read_resp
    read_resp->counter_value = 0;

    memcpy(uuid.entry_index, read_req->counter_id, UUID_ENTRY_INDEX_SIZE);
    memcpy(uuid.nonce, read_req->nonce, UUID_NONCE_SIZE);

    op_ret = read_vmc(owner_attributes, uuid, vmc);
    if(OP_SUCCESS == op_ret)
    {
        read_resp->counter_value = vmc.value;
    }

    return handle_vmc_errors(op_ret, &read_resp->resp_hdr.status);;
}

pse_op_error_t pse_mc_inc(
    const isv_attributes_t &owner_attributes,
    const uint8_t* req,
    uint8_t* resp)
{
    assert(req != NULL && resp != NULL);

    pse_op_error_t op_ret = OP_SUCCESS;
    const pse_mc_inc_req_t* inc_req = (const pse_mc_inc_req_t*)req;
    pse_mc_inc_resp_t* inc_resp = (pse_mc_inc_resp_t*)resp;

    //initial inc_resp
    inc_resp->counter_value = 0;

    vmc_data_blob_t vmc;
    mc_rpdb_uuid_t uuid;

    memset(&vmc,  0, sizeof(vmc));

    memcpy(uuid.entry_index, inc_req->counter_id, UUID_ENTRY_INDEX_SIZE);
    memcpy(uuid.nonce, inc_req->nonce, UUID_NONCE_SIZE);

    op_ret = inc_vmc(owner_attributes, uuid, vmc);
    if(op_ret == OP_SUCCESS)
    {
        inc_resp->counter_value = vmc.value;
    }

    return handle_vmc_errors(op_ret, &inc_resp->resp_hdr.status);;
}

pse_op_error_t pse_mc_del(
    const isv_attributes_t &owner_attributes,
    const uint8_t* req,
    uint8_t* resp)
{
    assert(req != NULL && resp != NULL);

    pse_op_error_t op_ret;
    const pse_mc_del_req_t* del_req = (const pse_mc_del_req_t*)req;
    mc_rpdb_uuid_t uuid;

    memcpy(uuid.entry_index, del_req->counter_id, UUID_ENTRY_INDEX_SIZE);
    memcpy(uuid.nonce, del_req->nonce, UUID_NONCE_SIZE);

    op_ret = delete_vmc(owner_attributes, uuid);

    return handle_vmc_errors(op_ret, &((pse_mc_inc_resp_t*)resp)->resp_hdr.status);;
}
