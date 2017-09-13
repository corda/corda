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

//tae_service_sim.cpp : Defines the exported functions
//

#include "sgx_tae_service.h"
#include "tae_service_internal.h"
#include "se_memcpy.h"
#include "pse_types.h"
#include "stdlib.h"
#include "string.h"
#include "sgx_spinlock.h"
#include "sgx_tae_service_t.h"

#define RETRY_TIMES 2
#define DEFAULT_VMC_ATTRIBUTE_MASK 0xFFFFFFFFFFFFFFCB
#define DEFAULT_VMC_XFRM_MASK 0x0

#ifdef _DEBUG
//wait for 10min at most for debug
#define DEFAULT_AESM_TIMEOUT 600000
#else
//wait for 10sec at most
#define DEFAULT_AESM_TIMEOUT 10000
#endif

static sgx_spinlock_t g_spin_lock;
static bool g_b_session_established = false;

sgx_status_t sgx_create_pse_session()
{
    sgx_spin_lock(&g_spin_lock);
    if(!g_b_session_established)
        g_b_session_established = true;
    sgx_spin_unlock(&g_spin_lock);
    return SGX_SUCCESS;
}

sgx_status_t sgx_close_pse_session()
{
    sgx_spin_lock(&g_spin_lock);
    if(g_b_session_established)
        g_b_session_established = false;
    sgx_spin_unlock(&g_spin_lock);
    return SGX_SUCCESS;
}

sgx_status_t sgx_get_ps_sec_prop(sgx_ps_sec_prop_desc_t *p_security_property)
{
    sgx_status_t ret = SGX_SUCCESS;
    if(!p_security_property)
        return SGX_ERROR_INVALID_PARAMETER;
    if (!g_b_session_established){
        return SGX_ERROR_AE_SESSION_INVALID;
    }
    else
    {
        se_ps_sec_prop_desc_internal_t *p_security_property_internal = (se_ps_sec_prop_desc_internal_t*)p_security_property;
        p_security_property_internal->desc_type = 0;
        p_security_property_internal->pse_miscselect = 0;
        p_security_property_internal->reserved1 = 0;
        memset(p_security_property_internal->reserved2, 0, sizeof(p_security_property_internal->reserved2));
        p_security_property_internal->pse_prod_id = 2;
        p_security_property_internal->pse_isvsvn = 1;
        p_security_property_internal->pse_attributes.flags = SGX_FLAGS_INITTED;
        p_security_property_internal->pse_attributes.xfrm = SGX_XFRM_LEGACY;
        memset(&(p_security_property_internal->pse_mr_signer), 0xEE,
               sizeof(p_security_property_internal->pse_mr_signer));
        p_security_property_internal->cse_sec_prop.gid_cse = 0;
        p_security_property_internal->cse_sec_prop.prvrl_version = 1;
        p_security_property_internal->cse_sec_prop.sigrl_version = 1;
        p_security_property_internal->cse_sec_prop.sec_info_type = 0;
        memset(&p_security_property_internal->cse_sec_prop.ca_id_cse, 0, sizeof(p_security_property_internal->cse_sec_prop.ca_id_cse));
        memset(&p_security_property_internal->cse_sec_prop.sec_info, 0, sizeof(p_security_property_internal->cse_sec_prop.sec_info));
    }
    return ret;
}

sgx_status_t sgx_get_ps_sec_prop_ex(sgx_ps_sec_prop_desc_ex_t* ps_security_property_ex)
{
    sgx_status_t ret;
    if (!ps_security_property_ex)
        return SGX_ERROR_INVALID_PARAMETER;
    ret = sgx_get_ps_sec_prop(&ps_security_property_ex->ps_sec_prop_desc);
    if (ret != SGX_SUCCESS)
    {
        return ret;
    }

    se_ps_sec_prop_desc_internal_t* desc_internal =
        (se_ps_sec_prop_desc_internal_t*)&ps_security_property_ex->ps_sec_prop_desc;
    memcpy_s(&ps_security_property_ex->pse_mrsigner, sizeof(ps_security_property_ex->pse_mrsigner),
        &desc_internal->pse_mr_signer, sizeof(sgx_measurement_t));
    memcpy_s(&ps_security_property_ex->pse_prod_id, sizeof(ps_security_property_ex->pse_prod_id),
         &desc_internal->pse_prod_id, sizeof(sgx_prod_id_t));
    memcpy_s(&ps_security_property_ex->pse_isv_svn, sizeof(ps_security_property_ex->pse_isv_svn),
         &desc_internal->pse_isvsvn, sizeof(sgx_isv_svn_t));
    return ret;
}

sgx_status_t sgx_get_trusted_time
(
    sgx_time_t *p_current_time,
    sgx_time_source_nonce_t *p_time_source_nonce)
{
    if(!p_current_time || !p_time_source_nonce)
        return SGX_ERROR_INVALID_PARAMETER;
    if (!g_b_session_established){
        return SGX_ERROR_AE_SESSION_INVALID;
    }

    pse_message_t *p_req_msg =
        (pse_message_t *)malloc(PSE_TIMER_READ_REQ_SIZE);
    if(!p_req_msg){
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    pse_message_t *p_resp_msg =
        (pse_message_t *)malloc(PSE_TIMER_READ_RESP_SIZE);
    if(!p_resp_msg){
        free(p_req_msg);
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    p_req_msg->exp_resp_size = sizeof(pse_timer_read_resp_t);
    p_req_msg->payload_size = sizeof(pse_timer_read_req_t);

    pse_timer_read_req_t *p_timer_req
        = (pse_timer_read_req_t *)p_req_msg->payload;
    p_timer_req->req_hdr.service_id = PSE_TRUSTED_TIME_SERVICE;
    p_timer_req->req_hdr.service_cmd = PSE_TIMER_READ;

    pse_timer_read_resp_t *p_timer_resp
        = (pse_timer_read_resp_t *)p_resp_msg->payload;

    sgx_status_t status = SGX_SUCCESS;
    sgx_status_t ret = SGX_SUCCESS;
    int retry = RETRY_TIMES;
    do {
        status = invoke_service_ocall(&ret,
                                      (uint8_t *)p_req_msg,
                                      PSE_TIMER_READ_REQ_SIZE,
                                      (uint8_t *)p_resp_msg,
                                      PSE_TIMER_READ_RESP_SIZE,
                                      DEFAULT_AESM_TIMEOUT);
        if(status != SGX_SUCCESS || ret != SGX_SUCCESS){
            status = SGX_ERROR_UNEXPECTED;
            continue;
        }

        if(p_timer_resp->resp_hdr.service_id != PSE_TRUSTED_TIME_SERVICE
           || p_timer_resp->resp_hdr.service_cmd != PSE_TIMER_READ
           || p_timer_resp->resp_hdr.status != PSE_SUCCESS){
            status = SGX_ERROR_UNEXPECTED;
        } else {
            memcpy_s(p_current_time, sizeof(*p_current_time), &p_timer_resp->timestamp, sizeof(sgx_time_t));
            memcpy_s(p_time_source_nonce, sizeof(*p_time_source_nonce), p_timer_resp->time_source_nonce,
                       sizeof(sgx_time_source_nonce_t));
            status = SGX_SUCCESS;
            break;
        }
    } while(retry--);
    free(p_req_msg);
    free(p_resp_msg);
    return status;
}

sgx_status_t sgx_create_monotonic_counter_ex(
    uint16_t owner_policy,
    const sgx_attributes_t* owner_attribute_mask,
    sgx_mc_uuid_t *p_counter_uuid,
    uint32_t *p_counter_value)
{
    if(!p_counter_value || !p_counter_uuid || !owner_attribute_mask){
        return SGX_ERROR_INVALID_PARAMETER;
    }
    if (0!= (~(MC_POLICY_SIGNER | MC_POLICY_ENCLAVE) & owner_policy)
        || 0 == ((MC_POLICY_SIGNER | MC_POLICY_ENCLAVE)& owner_policy))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    if (!g_b_session_established){
        return SGX_ERROR_AE_SESSION_INVALID;
    }

    pse_message_t *p_req_msg = (pse_message_t *)malloc(PSE_CREATE_MC_REQ_SIZE);
    if(!p_req_msg){
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    pse_message_t *p_resp_msg = (pse_message_t *)malloc(PSE_CREATE_MC_RESP_SIZE);
    if(!p_resp_msg){
        free(p_req_msg);
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    p_req_msg->exp_resp_size = sizeof(pse_mc_create_resp_t);
    p_req_msg->payload_size = sizeof(pse_mc_create_req_t);

    pse_mc_create_req_t *p_mc_req
        = (pse_mc_create_req_t *)p_req_msg->payload;
    p_mc_req->req_hdr.service_id = PSE_MC_SERVICE;
    p_mc_req->req_hdr.service_cmd = PSE_MC_CREATE;
    p_mc_req->policy = owner_policy;
    memcpy_s(&p_mc_req->attr_mask, sizeof(p_mc_req->attr_mask), owner_attribute_mask, sizeof(*owner_attribute_mask));

    pse_mc_create_resp_t *p_mc_resp
        = (pse_mc_create_resp_t *)p_resp_msg->payload;

    sgx_status_t status = SGX_SUCCESS;
    sgx_status_t ret = SGX_SUCCESS;
    int retry = RETRY_TIMES;
    do {
        sgx_spin_lock(&g_spin_lock);
        status = invoke_service_ocall(&ret,
                                      (uint8_t *)p_req_msg,
                                      PSE_CREATE_MC_REQ_SIZE,
                                      (uint8_t *)p_resp_msg,
                                      PSE_CREATE_MC_RESP_SIZE,
                                      DEFAULT_AESM_TIMEOUT);
        sgx_spin_unlock(&g_spin_lock);
        if(status != SGX_SUCCESS || ret != SGX_SUCCESS){
            status = SGX_ERROR_UNEXPECTED;
            continue;
        }

        if(p_mc_resp->resp_hdr.service_id != PSE_MC_SERVICE
           || p_mc_resp->resp_hdr.service_cmd != PSE_MC_CREATE
           || p_mc_resp->resp_hdr.status != PSE_SUCCESS){
            status = SGX_ERROR_UNEXPECTED;
        } else {
            memcpy_s(&p_counter_uuid->counter_id, sizeof(p_counter_uuid->counter_id),
                   &p_mc_resp->counter_id, sizeof(p_mc_resp->counter_id));
            memcpy_s(&p_counter_uuid->nonce, sizeof(p_counter_uuid->nonce),
                   &p_mc_resp->nonce, sizeof(p_mc_resp->nonce));
            *p_counter_value = 0;
            status = SGX_SUCCESS;
            break;
        }
    } while(retry--);
    free(p_req_msg);
    free(p_resp_msg);
    return status;
}

sgx_status_t sgx_increment_monotonic_counter(
    const sgx_mc_uuid_t *p_counter_uuid,
    uint32_t *p_counter_value)
{
    if(!p_counter_value || !p_counter_uuid ){
        return SGX_ERROR_INVALID_PARAMETER;
    }
    if (!g_b_session_established){
        return SGX_ERROR_AE_SESSION_INVALID;
    }

    pse_message_t *p_req_msg = (pse_message_t *)malloc(PSE_INC_MC_REQ_SIZE);
    if(!p_req_msg){
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    pse_message_t *p_resp_msg = (pse_message_t *)malloc(PSE_INC_MC_RESP_SIZE);
    if(!p_resp_msg){
        free(p_req_msg);
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    p_req_msg->exp_resp_size = sizeof(pse_mc_inc_resp_t);
    p_req_msg->payload_size = sizeof(pse_mc_inc_req_t);

    pse_mc_inc_req_t *p_mc_req
        = (pse_mc_inc_req_t *)p_req_msg->payload;
    memcpy_s(&p_mc_req->counter_id, sizeof(p_mc_req->counter_id),
           &p_counter_uuid->counter_id, sizeof(p_counter_uuid->counter_id));
    memcpy_s(&p_mc_req->nonce, sizeof(p_mc_req->nonce),
           &p_counter_uuid->nonce, sizeof(p_counter_uuid->nonce));
    p_mc_req->req_hdr.service_id = PSE_MC_SERVICE;
    p_mc_req->req_hdr.service_cmd = PSE_MC_INC;

    pse_mc_inc_resp_t *p_mc_resp
        = (pse_mc_inc_resp_t *)p_resp_msg->payload;

    sgx_status_t status = SGX_SUCCESS;
    sgx_status_t ret = SGX_SUCCESS;
    int retry = RETRY_TIMES;
    do {
        sgx_spin_lock(&g_spin_lock);
        status = invoke_service_ocall(&ret,
                                      (uint8_t *)p_req_msg,
                                      PSE_INC_MC_REQ_SIZE,
                                      (uint8_t *)p_resp_msg,
                                      PSE_INC_MC_RESP_SIZE,
                                      DEFAULT_AESM_TIMEOUT);
        sgx_spin_unlock(&g_spin_lock);
        if(status != SGX_SUCCESS || ret != SGX_SUCCESS){
            if(SGX_ERROR_MC_NOT_FOUND != ret)
                status = SGX_ERROR_UNEXPECTED;
            else
                status = SGX_ERROR_MC_NOT_FOUND;
            continue;
        }

        if(p_mc_resp->resp_hdr.service_id != PSE_MC_SERVICE
           || p_mc_resp->resp_hdr.service_cmd != PSE_MC_INC
           || p_mc_resp->resp_hdr.status != PSE_SUCCESS){
            if(PSE_ERROR_MC_NOT_FOUND == p_mc_resp->resp_hdr.status)
                status = SGX_ERROR_MC_NOT_FOUND;
            else
                status = SGX_ERROR_UNEXPECTED;
        } else
        {
            *p_counter_value = p_mc_resp->counter_value;
            status = SGX_SUCCESS;
            break;
        }
    } while(retry--);
    free(p_req_msg);
    free(p_resp_msg);
    return status;
}

sgx_status_t sgx_read_monotonic_counter(
    const sgx_mc_uuid_t *p_counter_uuid,
    uint32_t *p_counter_value)
{
    if(!p_counter_value || !p_counter_uuid){
        return SGX_ERROR_INVALID_PARAMETER;
    }
    if (!g_b_session_established){
        return SGX_ERROR_AE_SESSION_INVALID;
    }

    pse_message_t *p_req_msg = (pse_message_t *)malloc(PSE_READ_MC_REQ_SIZE);
    if(!p_req_msg){
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    pse_message_t *p_resp_msg = (pse_message_t *)malloc(PSE_READ_MC_RESP_SIZE);
    if(!p_resp_msg){
        free(p_req_msg);
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    p_req_msg->exp_resp_size = sizeof(pse_mc_read_resp_t);
    p_req_msg->payload_size = sizeof(pse_mc_read_req_t);

    pse_mc_read_req_t *p_mc_req
        = (pse_mc_read_req_t *)p_req_msg->payload;

    memcpy_s(&p_mc_req->counter_id, sizeof(p_mc_req->counter_id),
           &p_counter_uuid->counter_id, sizeof(p_counter_uuid->counter_id));
    memcpy_s(&p_mc_req->nonce, sizeof(p_mc_req->nonce),
           &p_counter_uuid->nonce, sizeof(p_counter_uuid->nonce));
    p_mc_req->req_hdr.service_id = PSE_MC_SERVICE;
    p_mc_req->req_hdr.service_cmd = PSE_MC_READ;

    pse_mc_read_resp_t *p_mc_resp
        = (pse_mc_read_resp_t *)p_resp_msg->payload;

    sgx_status_t status = SGX_SUCCESS;
    sgx_status_t ret = SGX_SUCCESS;
    int retry = RETRY_TIMES;
    do {
        sgx_spin_lock(&g_spin_lock);
        status = invoke_service_ocall(&ret,
                                      (uint8_t *)p_req_msg,
                                      PSE_READ_MC_REQ_SIZE,
                                      (uint8_t *)p_resp_msg,
                                      PSE_READ_MC_RESP_SIZE,
                                      DEFAULT_AESM_TIMEOUT);
        sgx_spin_unlock(&g_spin_lock);
        if(status != SGX_SUCCESS || ret != SGX_SUCCESS){
            if(SGX_ERROR_MC_NOT_FOUND != ret)
                status = SGX_ERROR_UNEXPECTED;
			else
                status = SGX_ERROR_MC_NOT_FOUND;
            continue;
        }

        if(p_mc_resp->resp_hdr.service_id != PSE_MC_SERVICE
           || p_mc_resp->resp_hdr.service_cmd != PSE_MC_READ
           || p_mc_resp->resp_hdr.status != PSE_SUCCESS){
            if(PSE_ERROR_MC_NOT_FOUND == p_mc_resp->resp_hdr.status)
                status = SGX_ERROR_MC_NOT_FOUND;
            else
                status = SGX_ERROR_UNEXPECTED;
        } else {
            *p_counter_value = p_mc_resp->counter_value;
            status = SGX_SUCCESS;
            break;
        }
    } while(retry--);
    free(p_req_msg);
    free(p_resp_msg);
    return status;
}

sgx_status_t sgx_create_monotonic_counter(
    sgx_mc_uuid_t *p_counter_uuid,
    uint32_t *p_counter_value)
{
    //Default attribute mask
    sgx_attributes_t attr_mask;
    attr_mask.flags = DEFAULT_VMC_ATTRIBUTE_MASK;
    attr_mask.xfrm = DEFAULT_VMC_XFRM_MASK;

    return sgx_create_monotonic_counter_ex(MC_POLICY_SIGNER, 
            &attr_mask,
            p_counter_uuid, 
            p_counter_value);
}

sgx_status_t sgx_destroy_monotonic_counter(const sgx_mc_uuid_t *p_counter_uuid)
{
    if(!p_counter_uuid){
        return SGX_ERROR_INVALID_PARAMETER;
    }
    if (!g_b_session_established){
        return SGX_ERROR_AE_SESSION_INVALID;
    }

    pse_message_t *p_req_msg = (pse_message_t *)malloc(PSE_DEL_MC_REQ_SIZE);
    if(!p_req_msg){
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    pse_message_t *p_resp_msg = (pse_message_t *)malloc(PSE_DEL_MC_RESP_SIZE);
    if(!p_resp_msg){
        free(p_req_msg);
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    p_req_msg->exp_resp_size = sizeof(pse_mc_del_resp_t);
    p_req_msg->payload_size = sizeof(pse_mc_del_req_t);

    pse_mc_del_req_t *p_mc_req
        = (pse_mc_del_req_t *)p_req_msg->payload;
    memcpy_s(&p_mc_req->counter_id, sizeof(p_mc_req->counter_id),
           &p_counter_uuid->counter_id, sizeof(p_counter_uuid->counter_id));
    memcpy_s(&p_mc_req->nonce, sizeof(p_mc_req->nonce),
           &p_counter_uuid->nonce, sizeof(p_counter_uuid->nonce));
    p_mc_req->req_hdr.service_id = PSE_MC_SERVICE;
    p_mc_req->req_hdr.service_cmd = PSE_MC_DEL;

    pse_mc_del_resp_t *p_mc_resp
        = (pse_mc_del_resp_t *)p_resp_msg->payload;

    sgx_status_t status = SGX_SUCCESS;
    sgx_status_t ret = SGX_SUCCESS;
    int retry = RETRY_TIMES;
    do {
        status = invoke_service_ocall(&ret,
                                      (uint8_t *)p_req_msg,
                                      PSE_DEL_MC_REQ_SIZE,
                                      (uint8_t *)p_resp_msg,
                                      PSE_DEL_MC_RESP_SIZE,
                                      DEFAULT_AESM_TIMEOUT);
        if(status != SGX_SUCCESS || ret != SGX_SUCCESS){
            if(SGX_ERROR_MC_NOT_FOUND != ret)
                status = SGX_ERROR_UNEXPECTED;
            else
                status = SGX_ERROR_MC_NOT_FOUND;
            continue;
        }

        if(p_mc_resp->resp_hdr.service_id != PSE_MC_SERVICE
           || p_mc_resp->resp_hdr.service_cmd != PSE_MC_DEL
           || p_mc_resp->resp_hdr.status != PSE_SUCCESS){
            if(PSE_ERROR_MC_NOT_FOUND == p_mc_resp->resp_hdr.status)
                status = SGX_ERROR_MC_NOT_FOUND;
            else
                status = SGX_ERROR_UNEXPECTED;
        } else {
            status = SGX_SUCCESS;
            break;
        }
    } while(retry--);
    free(p_req_msg);
    free(p_resp_msg);
    return status;
}

