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

#include "uae_service_sim.h"

#ifdef  __cplusplus
extern "C" {
#endif

static const uint8_t TIME_SOURCE_NONCE_SIM[32] = {
    0x9d, 0x7c, 0x25, 0x07, 0x38, 0x53, 0x23, 0xb1,
    0x9f, 0xba, 0xc8, 0x7b, 0xc0, 0x89, 0xde, 0x2d,
    0x2b, 0x5f, 0x34, 0x6d, 0x9c, 0x35, 0xf5, 0xbc,
    0xcd, 0x34, 0x7f, 0x75, 0x96, 0xc8, 0x27, 0xcc};

sgx_status_t pse_mc_create_sim(
    uint8_t *p_req_payload,
    uint8_t *p_resp_payload)
{
    UNUSED(p_req_payload);
    sgx_status_t ret = SGX_ERROR_UNEXPECTED;
    pse_mc_create_resp_t *p_create_resp
        = (pse_mc_create_resp_t *)p_resp_payload;

    //initial create_resp
    p_create_resp->resp_hdr.service_id = PSE_MC_SERVICE;
    p_create_resp->resp_hdr.service_cmd = PSE_MC_CREATE;
    p_create_resp->resp_hdr.status = PSE_ERROR_INTERNAL;
    memset(p_create_resp->counter_id, 0xFF, sizeof(p_create_resp->counter_id));
    memset(p_create_resp->nonce, 0xFF, sizeof(p_create_resp->nonce));

    vmc_sim_t temp_vmc = {{0}, {0}, 0};
    ret = get_counter_id(&temp_vmc);
    if(SGX_SUCCESS != ret){
        return ret;
    }
    ret = store_vmc_sim(&temp_vmc);
    if(SGX_SUCCESS != ret){
        return ret;
    }

    p_create_resp->resp_hdr.status = PSE_SUCCESS;
    if(memcpy_s(p_create_resp->counter_id,
                sizeof(p_create_resp->counter_id),
                temp_vmc.counter_id,
                sizeof(temp_vmc.counter_id))){
        return SGX_ERROR_UNEXPECTED;
    }
    if(memcpy_s(p_create_resp->nonce,
                sizeof(p_create_resp->nonce),
                temp_vmc.nonce,
                sizeof(temp_vmc.nonce))){
        return SGX_ERROR_UNEXPECTED;
    }

    return ret;
}

sgx_status_t pse_mc_read_sim(
    uint8_t *p_req_payload,
    uint8_t *p_resp_payload)
{
    sgx_status_t ret = SGX_ERROR_UNEXPECTED;
    pse_mc_read_req_t *p_read_req = (pse_mc_read_req_t *)p_req_payload;
    pse_mc_read_resp_t *p_read_resp = (pse_mc_read_resp_t *)p_resp_payload;

    //initial create_resp
    p_read_resp->counter_value = 0;
    p_read_resp->resp_hdr.service_id = PSE_MC_SERVICE;
    p_read_resp->resp_hdr.service_cmd = PSE_MC_READ;
    p_read_resp->resp_hdr.status = PSE_ERROR_INTERNAL;

    vmc_sim_t temp_vmc = {{0}, {0}, 0};
    if(memcpy_s(&temp_vmc.counter_id,
                sizeof(temp_vmc.counter_id),
                p_read_req->counter_id,
                sizeof(p_read_req->counter_id))){
        return SGX_ERROR_UNEXPECTED;
    }
    if(memcpy_s(&temp_vmc.nonce,
                sizeof(temp_vmc.nonce),
                p_read_req->nonce,
                sizeof(p_read_req->nonce))){
        return SGX_ERROR_UNEXPECTED;
    }
    ret = load_vmc_sim(&temp_vmc);
    if(SGX_SUCCESS != ret){
        memset(&p_read_resp->counter_value, 0xFF,
               sizeof(p_read_resp->counter_value));
        return ret;
    }

    p_read_resp->resp_hdr.status = PSE_SUCCESS;
    if(memcpy_s(&p_read_resp->counter_value,
                sizeof(p_read_resp->counter_value),
                &temp_vmc.counter_value,
                sizeof(temp_vmc.counter_value))){
        return SGX_ERROR_UNEXPECTED;
    }

    return ret;
}

sgx_status_t pse_mc_inc_sim(
    uint8_t *p_req_payload,
    uint8_t *p_resp_payload)
{
    sgx_status_t ret = SGX_ERROR_UNEXPECTED;
    pse_mc_inc_req_t *p_inc_req = (pse_mc_inc_req_t *)p_req_payload;
    pse_mc_inc_resp_t *p_inc_resp = (pse_mc_inc_resp_t *)p_resp_payload;

    //initial create_resp
    p_inc_resp->counter_value = 0;
    p_inc_resp->resp_hdr.service_id = PSE_MC_SERVICE;
    p_inc_resp->resp_hdr.service_cmd = PSE_MC_INC;
    p_inc_resp->resp_hdr.status = PSE_ERROR_INTERNAL;

    vmc_sim_t temp_vmc = {{0}, {0}, 0};
    if(memcpy_s(&temp_vmc.counter_id,
                sizeof(temp_vmc.counter_id),
                p_inc_req->counter_id,
                sizeof(p_inc_req->counter_id))){
        return SGX_ERROR_UNEXPECTED;
    }
    if(memcpy_s(&temp_vmc.nonce,
                sizeof(temp_vmc.nonce),
                p_inc_req->nonce,
                sizeof(p_inc_req->nonce))){
        return SGX_ERROR_UNEXPECTED;
    }
    ret = load_vmc_sim(&temp_vmc);
    if(SGX_SUCCESS != ret){
        return ret;
    }
    temp_vmc.counter_value++;
    ret = store_vmc_sim(&temp_vmc);
    if(SGX_SUCCESS != ret){
        return ret;
    }

    p_inc_resp->resp_hdr.status = PSE_SUCCESS;
    if(memcpy_s(&p_inc_resp->counter_value,
                sizeof(p_inc_resp->counter_value),
                &temp_vmc.counter_value,
                sizeof(temp_vmc.counter_value))){
        return SGX_ERROR_UNEXPECTED;
    }

    return ret;
}

sgx_status_t pse_mc_del_sim(
    uint8_t *p_req_payload,
    uint8_t *p_resp_payload)
{
    sgx_status_t ret = SGX_ERROR_UNEXPECTED;
    pse_mc_del_req_t *p_del_req = (pse_mc_del_req_t *)p_req_payload;
    pse_mc_del_resp_t *p_del_resp = (pse_mc_del_resp_t *)p_resp_payload;

    //initial create_resp
    p_del_resp->resp_hdr.service_id = PSE_MC_SERVICE;
    p_del_resp->resp_hdr.service_cmd = PSE_MC_DEL;
    p_del_resp->resp_hdr.status = PSE_ERROR_INTERNAL;

    vmc_sim_t temp_vmc = {{0}, {0}, 0};
    if(memcpy_s(&temp_vmc.counter_id,
                sizeof(temp_vmc.counter_id),
                p_del_req->counter_id,
                sizeof(p_del_req->counter_id))){
        return SGX_ERROR_UNEXPECTED;
    }
    if(memcpy_s(&temp_vmc.nonce,
                sizeof(temp_vmc.nonce),
                p_del_req->nonce,
                sizeof(p_del_req->nonce))){
        return SGX_ERROR_UNEXPECTED;
    }
    ret = del_vmc_sim(&temp_vmc);
    if(SGX_SUCCESS != ret){
        return ret;
    }

    p_del_resp->resp_hdr.status = PSE_SUCCESS;
    return ret;
}

sgx_status_t pse_read_timer_sim(
    uint8_t *p_req_payload,
    uint8_t *p_resp_payload)
{
    UNUSED(p_req_payload);
    pse_timer_read_resp_t *p_time_resp
        = (pse_timer_read_resp_t *)p_resp_payload;

    time_t t = time(0);
    p_time_resp->resp_hdr.service_id = PSE_TRUSTED_TIME_SERVICE;
    p_time_resp->resp_hdr.service_cmd = PSE_TIMER_READ;
    p_time_resp->timestamp = (uint64_t)t;
    if(memcpy_s(p_time_resp->time_source_nonce,
             sizeof(p_time_resp->time_source_nonce),
        TIME_SOURCE_NONCE_SIM, sizeof(p_time_resp->time_source_nonce))){
        return SGX_ERROR_UNEXPECTED;
    }
    p_time_resp->resp_hdr.status = PSE_SUCCESS;
    return SGX_SUCCESS;
}

typedef sgx_status_t (*srv_pfn_t)(uint8_t *, uint8_t *);
static const struct service_handler_t {
    uint16_t service_id;
    uint16_t service_cmd;
    uint16_t req_msg_size;
    uint16_t resp_msg_size;
    srv_pfn_t srv_pfn;
} SERVICE_HANDLER[] = {
    {PSE_MC_SERVICE, PSE_MC_CREATE, sizeof(pse_mc_create_req_t),
     sizeof(pse_mc_create_resp_t), pse_mc_create_sim},
    {PSE_MC_SERVICE, PSE_MC_READ, sizeof(pse_mc_read_req_t),
     sizeof(pse_mc_read_resp_t), pse_mc_read_sim},
    {PSE_MC_SERVICE, PSE_MC_INC, sizeof(pse_mc_inc_req_t),
     sizeof(pse_mc_inc_resp_t), pse_mc_inc_sim},
    {PSE_MC_SERVICE, PSE_MC_DEL, sizeof(pse_mc_del_req_t),
     sizeof(pse_mc_del_resp_t), pse_mc_del_sim},
    {PSE_TRUSTED_TIME_SERVICE, PSE_TIMER_READ, sizeof(pse_timer_read_req_t),
     sizeof(pse_timer_read_resp_t), pse_read_timer_sim}
};

static sgx_status_t invoke_service(
    const uint8_t *p_pse_message_req,
    size_t pse_message_req_size,
    uint8_t *p_pse_message_resp,
    size_t pse_message_resp_size,
    unsigned long timeout)
{
    UNUSED(timeout);
    sgx_status_t ret = SGX_SUCCESS;
    if(!p_pse_message_req || !p_pse_message_resp){
        return SGX_ERROR_INVALID_PARAMETER;
    }

    const pse_message_t *p_req_msg = (const pse_message_t *)p_pse_message_req;
    pse_message_t *p_resp_msg = (pse_message_t *)p_pse_message_resp;

    if(pse_message_req_size != sizeof(pse_message_t)
       + p_req_msg->payload_size){
        return SGX_ERROR_INVALID_PARAMETER;
    }
    if(pse_message_resp_size < sizeof(pse_message_t)
       + p_resp_msg->exp_resp_size){
        return SGX_ERROR_INVALID_PARAMETER;
    }

    const uint8_t *p_req_payload = p_req_msg->payload;
    uint8_t *p_resp_payload = p_resp_msg->payload;
    const pse_req_hdr_t *p_req_hdr = (const pse_req_hdr_t *)p_req_payload;
    for(unsigned int i = 0;
        i < sizeof(SERVICE_HANDLER) / sizeof(service_handler_t); i++)
    {
        if(p_req_hdr->service_id == SERVICE_HANDLER[i].service_id
            && p_req_hdr->service_cmd == SERVICE_HANDLER[i].service_cmd){
            if(p_req_msg->payload_size != SERVICE_HANDLER[i].req_msg_size
                || p_req_msg->exp_resp_size != SERVICE_HANDLER[i].resp_msg_size)
            {
                ret = SGX_ERROR_UNEXPECTED;
                break;
            }

            memset(p_resp_payload, 0, p_req_msg->exp_resp_size);
            ret = SERVICE_HANDLER[i].srv_pfn(const_cast<uint8_t*>(p_req_payload), p_resp_payload);

            p_resp_msg->payload_size = p_req_msg->exp_resp_size;
            break;
        }
    }

    return ret;
}

sgx_status_t create_session_ocall(uint32_t *p_sid, uint8_t *p_dh_msg1,
    uint32_t dh_msg1_size, uint32_t timeout)
{
    UNUSED(p_sid);
    UNUSED(p_dh_msg1);
    UNUSED(dh_msg1_size);
    UNUSED(timeout);
    return SGX_SUCCESS;
}

sgx_status_t exchange_report_ocall(uint32_t sid,
                                  const uint8_t *p_dh_msg2,
                                  uint32_t dh_msg2_size,
                                  uint8_t *p_dh_msg3,
                                  uint32_t dh_msg3_size,
                                  uint32_t timeout)
{
    UNUSED(sid);
    UNUSED(p_dh_msg2);
    UNUSED(dh_msg2_size);
    UNUSED(p_dh_msg3);
    UNUSED(dh_msg3_size);
    UNUSED(timeout);
    return SGX_SUCCESS;
}

sgx_status_t close_session_ocall(uint32_t sid, uint32_t timeout)
{
    UNUSED(sid);
    UNUSED(timeout);
    return SGX_SUCCESS;
}

sgx_status_t invoke_service_ocall(
    const uint8_t* pse_message_req, uint32_t pse_message_req_size,
    uint8_t* pse_message_resp, uint32_t pse_message_resp_size,
    uint32_t timeout
    )
{
    return invoke_service(pse_message_req, pse_message_req_size,
                          pse_message_resp, pse_message_resp_size, timeout);
}

sgx_status_t sgx_get_ps_cap(
    sgx_ps_cap_t* p_sgx_ps_cap)
{
    if (!p_sgx_ps_cap)
        return SGX_ERROR_INVALID_PARAMETER;

    p_sgx_ps_cap->ps_cap0 = 0x3;
    p_sgx_ps_cap->ps_cap1 = 0;
    return SGX_SUCCESS;
}

#ifdef  __cplusplus
}
#endif
