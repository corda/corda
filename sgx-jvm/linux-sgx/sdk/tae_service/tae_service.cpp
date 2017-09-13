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


#include "se_types.h"
#include "sgx_trts.h"
#include "sgx_utils.h"
#include "arch.h"
#include "sgx_tae_service.h"
#include "tae_service_internal.h"
#include "dh.h"
#include "sgx_dh.h"
#include "sgx_spinlock.h"
#include "sgx_thread.h"
#include "uncopyable.h"

#include "tae_config.h"

#include "sgx_tae_service_t.h"

#define ERROR_BREAK(x)  if(SGX_SUCCESS != (x)){break;}
#define SAFE_FREE(ptr) {if (NULL != (ptr)) {free(ptr); (ptr)=NULL;}}

#define INVALID_SESSION_ID (-1U)

typedef struct _session_t
{
    uint32_t session_id;
    sgx_key_128bit_t authenticated_encryption_key;
    se_ps_sec_prop_desc_internal_t ps_security_property;
    uint32_t transaction_number;//valid transaction_number is from 0 to 0x7FFFFFFF
    //seq_num in request message is transaction_number*2 and seq_num in response message is expected to be transaction_number*2+1
    bool session_inited;
}session_t;

static session_t g_pse_session;

class Mutex :private Uncopyable{
public:
    Mutex() {sgx_thread_mutex_init(&m_mutex, NULL);}
    ~Mutex() { sgx_thread_mutex_destroy(&m_mutex);}
    void lock() { sgx_thread_mutex_lock(&m_mutex); }
    void unlock() { sgx_thread_mutex_unlock(&m_mutex); }
private:
    sgx_thread_mutex_t m_mutex;
};
//mutex for change g_pse_session, create_pse_session, close_pse_session and crypt_invoke locks it.
static Mutex g_session_mutex;

static sgx_status_t uae_create_session(
    uint32_t* session_id,
    sgx_dh_msg1_t* se_dh_msg1,
    uint32_t timeout
    )
{
    sgx_status_t status;
    sgx_status_t ret = SGX_ERROR_UNEXPECTED;
    status = create_session_ocall(&ret, session_id, (uint8_t*)se_dh_msg1, sizeof(sgx_dh_msg1_t), timeout);
    if (status!=SGX_SUCCESS)
        return status;
    return ret;
}

static sgx_status_t uae_close_session(
    uint32_t session_id,
    uint32_t timeout
    )
{
    sgx_status_t status;
    sgx_status_t ret = SGX_ERROR_UNEXPECTED;
    status = close_session_ocall(&ret, session_id, timeout);
    if (status!=SGX_SUCCESS)
        return status;
    return ret;
}

static sgx_status_t uae_exchange_report(
    uint32_t session_id,
    sgx_dh_msg2_t* se_dh_msg2,
    sgx_dh_msg3_t* se_dh_msg3,
    uint32_t timeout
    )
{
    sgx_status_t status;
    sgx_status_t ret = SGX_ERROR_UNEXPECTED;
    status = exchange_report_ocall(&ret, session_id, (uint8_t*)se_dh_msg2, static_cast<uint32_t>(sizeof(sgx_dh_msg2_t)),
		                           (uint8_t*)se_dh_msg3, static_cast<uint32_t>(sizeof(sgx_dh_msg3_t)+sizeof(cse_sec_prop_t)),timeout);
    if (status!=SGX_SUCCESS)
        return status;
    return ret;
}

static sgx_status_t uae_invoke_service(
    uint8_t* pse_message_req, uint32_t pse_message_req_size,
    uint8_t* pse_message_resp, uint32_t pse_message_resp_size,
    uint32_t timeout
    )
{
    sgx_status_t status;
    sgx_status_t ret = SGX_ERROR_UNEXPECTED;
    status = invoke_service_ocall(&ret, pse_message_req, pse_message_req_size, pse_message_resp, pse_message_resp_size, timeout);
    if (status!=SGX_SUCCESS)
        return status;
    return ret;
}

static sgx_status_t close_pse_session_within_mutex()
{
    sgx_status_t status = SGX_SUCCESS;
    if (g_pse_session.session_inited)
    {
        g_pse_session.session_inited = false;
        memset_s(&g_pse_session.authenticated_encryption_key,sizeof(&g_pse_session.authenticated_encryption_key), 0, sizeof(sgx_key_128bit_t));
        uint32_t session_id = g_pse_session.session_id;
        //Ocall uae_service close_session
        status = uae_close_session(session_id, SE_CLOSE_SESSION_TIMEOUT_MSEC);

        if (status == SGX_ERROR_AE_SESSION_INVALID)
        {
            //means session is closed by PSE, it's acceptable
            status =  SGX_SUCCESS;
        }
    }
    return status;
}

sgx_status_t sgx_close_pse_session()
{
    sgx_status_t status = SGX_SUCCESS;
    g_session_mutex.lock();
    //check session status again after mutex lock got.
    status = close_pse_session_within_mutex();
    g_session_mutex.unlock();
    return status;
}

static sgx_status_t verify_pse(sgx_dh_session_enclave_identity_t* dh_id)
{
    //make sure debug flag is not set
    if(dh_id->attributes.flags & SGX_FLAGS_DEBUG)
    {
        return SGX_ERROR_UNEXPECTED;
    }
    return SGX_SUCCESS;
}
static sgx_status_t create_pse_session_within_mutex()
{
    if (g_pse_session.session_inited)
    {
        return SGX_SUCCESS;
    }

    sgx_dh_msg3_t* se_dh_msg3 = NULL;
    //set invalid session id
    uint32_t session_id = INVALID_SESSION_ID;
    sgx_status_t status = SGX_ERROR_UNEXPECTED;

    //for pse session
    sgx_dh_msg1_t se_dh_msg1;
    sgx_dh_msg2_t se_dh_msg2;

    //for dh session
    sgx_dh_session_t dh_session_context;
    sgx_key_128bit_t dh_aek;
    sgx_dh_session_enclave_identity_t dh_id;


    //set start status
    status = sgx_dh_init_session(SGX_DH_SESSION_INITIATOR, &dh_session_context);
    if (SGX_ERROR_OUT_OF_MEMORY == status)
            return SGX_ERROR_OUT_OF_MEMORY;
    if(status!=SGX_SUCCESS)
        return SGX_ERROR_UNEXPECTED;

    se_dh_msg3 = (sgx_dh_msg3_t*)malloc(sizeof(sgx_dh_msg3_t)+sizeof(cse_sec_prop_t));
    if(!se_dh_msg3)
        return SGX_ERROR_OUT_OF_MEMORY;

    do{
        //Ocall uae_service create_session, get session_id and se_dh_msg1 from PSE
        status = uae_create_session(&session_id,&se_dh_msg1, SE_CREATE_SESSION_TIMEOUT_MSEC);
        if (SGX_ERROR_INVALID_PARAMETER == status)
            status = SGX_ERROR_UNEXPECTED;
        ERROR_BREAK(status);

        //process msg1 and generate msg2
        status = sgx_dh_initiator_proc_msg1(&se_dh_msg1, &se_dh_msg2, &dh_session_context);
        if (SGX_ERROR_OUT_OF_MEMORY == status)
            break;
        if(status!=SGX_SUCCESS)
        {
            status = SGX_ERROR_UNEXPECTED;
            break;
        }

        //Ocall uae_service exchange_report, give se_dh_msg2, get se_dh_msg3
        status = uae_exchange_report(session_id,&se_dh_msg2, se_dh_msg3, SE_EXCHANGE_REPORT_TIMEOUT_MSEC);
        if (SGX_ERROR_INVALID_PARAMETER == status)
            status = SGX_ERROR_UNEXPECTED;
        ERROR_BREAK(status);

        //proc msg3 to get AEK
        status = sgx_dh_initiator_proc_msg3(se_dh_msg3, &dh_session_context, &dh_aek, &dh_id);
        if (SGX_ERROR_OUT_OF_MEMORY == status)
            break;
        if(status!=SGX_SUCCESS)
        {
            status = SGX_ERROR_UNEXPECTED;
            break;
        }

        //verify PSE same as hard-coded attributes
        status = verify_pse(&dh_id);
        ERROR_BREAK(status);

        status = sgx_verify_report(&se_dh_msg3->msg3_body.report);
        if (SGX_ERROR_OUT_OF_MEMORY == status)
            break;
        if(status!=SGX_SUCCESS)
        {
            status = SGX_ERROR_UNEXPECTED;
            break;
        }


        //fill g_pse_session
        g_pse_session.session_id = session_id;
        memcpy(&g_pse_session.authenticated_encryption_key , &dh_aek, sizeof(sgx_key_128bit_t));
        g_pse_session.ps_security_property.pse_miscselect = dh_id.misc_select;
        g_pse_session.ps_security_property.reserved1 = 0;
        memset(g_pse_session.ps_security_property.reserved2, 0, sizeof(g_pse_session.ps_security_property.reserved2));
        memcpy(&g_pse_session.ps_security_property.pse_attributes, &dh_id.attributes, sizeof(sgx_attributes_t));
        memcpy(&g_pse_session.ps_security_property.pse_isvsvn, &dh_id.isv_svn, sizeof(sgx_isv_svn_t));
        memcpy(&g_pse_session.ps_security_property.pse_mr_signer, &dh_id.mr_signer, sizeof(sgx_measurement_t));
        memcpy(&g_pse_session.ps_security_property.pse_prod_id, &dh_id.isv_prod_id, sizeof(sgx_prod_id_t));
        //copy CSE_SEC_PROP of SE_DH_MSG3 to g_pse_session.ps_security_property
        pse_dh_msg3_t* pse_dh_msg3 = (pse_dh_msg3_t*)se_dh_msg3;
        memcpy(&g_pse_session.ps_security_property.cse_sec_prop, &pse_dh_msg3->cse_sec_prop, sizeof(cse_sec_prop_t));
        g_pse_session.session_inited = true;
        //reset transaction_number to 0
        g_pse_session.transaction_number = 0;
        status = SGX_SUCCESS;
        break;
    }while(0);
    SAFE_FREE(se_dh_msg3);
    if(status != SGX_SUCCESS && session_id != INVALID_SESSION_ID)
        uae_close_session(session_id, SE_CLOSE_SESSION_TIMEOUT_MSEC);//we can do nothing if close_session fails
    return status;
}

sgx_status_t sgx_create_pse_session()
{
    sgx_status_t status= SGX_ERROR_UNEXPECTED;
    //lock mutex, only one thread can create session, others must wait.
    g_session_mutex.lock();
        status = create_pse_session_within_mutex();
    //unlock the session mutex
    g_session_mutex.unlock();
    return status;
}

sgx_status_t sgx_get_ps_sec_prop(sgx_ps_sec_prop_desc_t* ps_security_property)
{
    sgx_status_t ret;
    if(!ps_security_property)
        return SGX_ERROR_INVALID_PARAMETER;
    //lock mutex to read session status
    g_session_mutex.lock();
    if (g_pse_session.session_inited == true)
    {
        memcpy(ps_security_property,&g_pse_session.ps_security_property,sizeof(sgx_ps_sec_prop_desc_t));
        ret = SGX_SUCCESS;
    }
    else
        ret = SGX_ERROR_AE_SESSION_INVALID;
    //unlock the session mutex
    g_session_mutex.unlock();
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
    memcpy(&ps_security_property_ex->pse_mrsigner, &desc_internal->pse_mr_signer, sizeof(sgx_measurement_t));
    memcpy(&ps_security_property_ex->pse_prod_id, &desc_internal->pse_prod_id, sizeof(sgx_prod_id_t));
    memcpy(&ps_security_property_ex->pse_isv_svn, &desc_internal->pse_isvsvn, sizeof(sgx_isv_svn_t));
    return ret;
}

static sgx_status_t verify_msg_hdr(pse_req_hdr_t* req_payload_hdr, pse_resp_hdr_t* resp_payload_hdr)
{
    sgx_status_t ret = SGX_SUCCESS;
    if(resp_payload_hdr->service_id != req_payload_hdr->service_id ||
        resp_payload_hdr->service_cmd != req_payload_hdr->service_cmd ||
        //resp seq_num increases one by PSE
        resp_payload_hdr->seq_num != req_payload_hdr->seq_num+1||
        //transaction_number has increase one after setting seq_num
        g_pse_session.transaction_number != resp_payload_hdr->seq_num/2 +1)
    {
        ret = SGX_ERROR_UNEXPECTED;
    }
    else if(resp_payload_hdr->status != PSE_SUCCESS)
    {
        switch (resp_payload_hdr->status)
        {
        case PSE_ERROR_INTERNAL:
            ret = SGX_ERROR_UNEXPECTED;
            break;
        case PSE_ERROR_BUSY:
            ret = SGX_ERROR_BUSY;
            break;
        case PSE_ERROR_MC_NOT_FOUND:
            ret = SGX_ERROR_MC_NOT_FOUND;
            break;
        case PSE_ERROR_MC_NO_ACCESS_RIGHT:
            ret = SGX_ERROR_MC_NO_ACCESS_RIGHT;
            break;
        case PSE_ERROR_UNKNOWN_REQ:
            ret = SGX_ERROR_INVALID_PARAMETER;
            break;
        case PSE_ERROR_CAP_NOT_AVAILABLE:
            ret = SGX_ERROR_SERVICE_UNAVAILABLE;
            break;
        case PSE_ERROR_MC_USED_UP:
            ret = SGX_ERROR_MC_USED_UP;
            break;
        case PSE_ERROR_MC_OVER_QUOTA:
            ret = SGX_ERROR_MC_OVER_QUOTA;
            break;
        case PSE_ERROR_INVALID_POLICY:
            ret = SGX_ERROR_INVALID_PARAMETER;
            break;
        default:
            ret = SGX_ERROR_UNEXPECTED;
            break;
        }
    }
    return ret;
}

//increase nonce, build msg, encrypt msg, call invoke_service, decrypt msg, verify msg format
static sgx_status_t  crypt_invoke(pse_message_t* req_msg, uint32_t req_msg_size,
                                 pse_req_hdr_t* req_payload_hdr,
                                 uint32_t timeout,
                                 pse_message_t* resp_msg, uint32_t resp_msg_size,
                                 pse_resp_hdr_t* resp_payload_hdr
                                 )
{

    sgx_status_t ret = SGX_ERROR_UNEXPECTED;
    int retry = RETRY_TIMES;
    //lock transaction_number
    g_session_mutex.lock();
    //don't need to lock g_pse_session.sgx_spin_lock, g_pse_session only changes when g_session_mutex is locked.
    if (!g_pse_session.session_inited)
    {
        g_session_mutex.unlock();
        return SGX_ERROR_AE_SESSION_INVALID;
    }
    //retry only when return value of uae_invoke_service is SGX_ERROR_AE_SESSION_INVALID,
    //which means that session is closed by PSE or transaction_number is out of order.
    //In these situation, session needs to reestablish and retry the invoke_service.
    while(retry --)
    {
        //prevent transaction_number from rolling over. 0x7fffffff and below is valid
        if(g_pse_session.transaction_number > 0x7fffffff){
            //if unexpected failure of following close_pse_session_within_mutex() and create_pse_session_within_mutex()
            //return SGX_ERROR_AE_SESSION_INVALID to user
            ret = SGX_ERROR_AE_SESSION_INVALID;
            //need to close current session and create a new session
            //create_pse_session_within_mutex will reset the g_pse_session.transaction_number
            //close_session failure will always return SGX_ERROR_AE_SESSION_INVALID
            ERROR_BREAK(close_pse_session_within_mutex());
            //create_session failure will return SGX_ERROR_BUSY on SGX_ERROR_BUSY, SGX_ERROR_OUT_OF_MEMORY on SGX_ERROR_OUT_OF_MEMORY,
            //and SGX_ERROR_AE_SESSION_INVALID on other error code
            sgx_status_t aesm_status = create_pse_session_within_mutex();
            switch (aesm_status)
            {
            case SGX_ERROR_BUSY:
                ret = SGX_ERROR_BUSY;
                break;
            case SGX_ERROR_OUT_OF_MEMORY:
                ret = SGX_ERROR_OUT_OF_MEMORY;
                break;
            default:
                break;
            }
            ERROR_BREAK(aesm_status);
        }
        //set seq_num
        req_payload_hdr->seq_num = g_pse_session.transaction_number*2;
        //increase transaction_number
        g_pse_session.transaction_number++;

        //set request message session id
        req_msg->session_id = g_pse_session.session_id;


        //encrypt_msg with authenticated_encryption_key of the session
        if (!encrypt_msg(req_msg, (uint8_t*)req_payload_hdr, &g_pse_session.authenticated_encryption_key))
        {
            ret = SGX_ERROR_UNEXPECTED;
            break;
        }
        //ocall invoke_service
        ret = uae_invoke_service((uint8_t*)req_msg, (req_msg_size),
            (uint8_t*)resp_msg, resp_msg_size, timeout);
        if (SGX_ERROR_AE_SESSION_INVALID == ret)
        {
            //close_session failure will always return SGX_ERROR_AE_SESSION_INVALID
            ERROR_BREAK(close_pse_session_within_mutex());
            //recreating session
            sgx_status_t aesm_status = create_pse_session_within_mutex();
            if(SGX_SUCCESS == aesm_status)
                continue;
            switch (aesm_status)
            {
            case SGX_ERROR_BUSY:
                ret = SGX_ERROR_BUSY;
                break;
            case SGX_ERROR_OUT_OF_MEMORY:
                ret = SGX_ERROR_OUT_OF_MEMORY;
                break;
            default:
                break;
            }
        }
        ERROR_BREAK(ret);
        //decrypt_msg with authenticated_encryption_key of the session
        if(!decrypt_msg(resp_msg, (uint8_t*)resp_payload_hdr, &g_pse_session.authenticated_encryption_key))
        {
            ret = SGX_ERROR_UNEXPECTED;
            break;
        }
        ret = verify_msg_hdr(req_payload_hdr,resp_payload_hdr);
        break;
    }
    g_session_mutex.unlock();
    return ret;
}



sgx_status_t sgx_get_trusted_time(
    sgx_time_t* current_time,
    sgx_time_source_nonce_t* time_source_nonce
    )
{
    if(!current_time || !time_source_nonce)
        return SGX_ERROR_INVALID_PARAMETER;

    pse_message_t* req_msg = (pse_message_t*)malloc(PSE_TIMER_READ_REQ_SIZE);
    if (!req_msg)
        return SGX_ERROR_OUT_OF_MEMORY;
    pse_message_t* resp_msg = (pse_message_t*)malloc(PSE_TIMER_READ_RESP_SIZE);
    if (!resp_msg)
    {
        free(req_msg);
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    memset(req_msg, 0, PSE_TIMER_READ_REQ_SIZE);
    memset(resp_msg, 0, PSE_TIMER_READ_RESP_SIZE);
    req_msg->exp_resp_size = sizeof(pse_timer_read_resp_t);
    req_msg->payload_size = sizeof(pse_timer_read_req_t);

    pse_timer_read_req_t timer_req;
    timer_req.req_hdr.service_id = PSE_TRUSTED_TIME_SERVICE;
    timer_req.req_hdr.service_cmd = PSE_TIMER_READ;

    pse_timer_read_resp_t timer_resp;
    memset(&timer_resp, 0, sizeof(pse_timer_read_resp_t));

    sgx_status_t status;
    status = crypt_invoke(req_msg, PSE_TIMER_READ_REQ_SIZE, &timer_req.req_hdr, SE_GET_TRUSTED_TIME_TIMEOUT_MSEC,
        resp_msg, PSE_TIMER_READ_RESP_SIZE, &timer_resp.resp_hdr);
    if (status==SGX_SUCCESS)
    {
        memcpy(current_time, &timer_resp.timestamp, sizeof(sgx_time_t));
        memcpy(time_source_nonce,timer_resp.time_source_nonce,sizeof(sgx_time_source_nonce_t));
    }
    //error condition
    free(req_msg);
    free(resp_msg);
    return status;
}

se_static_assert(SGX_MC_POLICY_SIGNER == MC_POLICY_SIGNER);
se_static_assert(SGX_MC_POLICY_ENCLAVE == MC_POLICY_ENCLAVE);
sgx_status_t sgx_create_monotonic_counter_ex(
    uint16_t owner_policy,
    const sgx_attributes_t* owner_attribute_mask,
    sgx_mc_uuid_t* counter_uuid,
    uint32_t* counter_value
    )
{
    if (!counter_value || !counter_uuid || !owner_attribute_mask)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    if ( 0!= (~(MC_POLICY_SIGNER | MC_POLICY_ENCLAVE) & owner_policy) ||
        0 == ((MC_POLICY_SIGNER | MC_POLICY_ENCLAVE)& owner_policy))
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    pse_message_t* req_msg = (pse_message_t*)malloc(PSE_CREATE_MC_REQ_SIZE);
    if (!req_msg)
        return SGX_ERROR_OUT_OF_MEMORY;
    pse_message_t* resp_msg = (pse_message_t*)malloc(PSE_CREATE_MC_RESP_SIZE);
    if (!resp_msg)
    {
        free(req_msg);
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    memset(req_msg, 0, PSE_CREATE_MC_REQ_SIZE);
    memset(resp_msg, 0, PSE_CREATE_MC_RESP_SIZE);
    req_msg->exp_resp_size = sizeof(pse_mc_create_resp_t);
    req_msg->payload_size = sizeof(pse_mc_create_req_t);

    pse_mc_create_req_t mc_req;
    mc_req.req_hdr.service_id = PSE_MC_SERVICE;
    mc_req.req_hdr.service_cmd = PSE_MC_CREATE;
    mc_req.policy = owner_policy;
    memcpy(mc_req.attr_mask, owner_attribute_mask, sizeof(mc_req.attr_mask));

    pse_mc_create_resp_t mc_resp;
    memset(&mc_resp, 0, sizeof(pse_mc_create_resp_t));

    sgx_status_t status;
    status = crypt_invoke(req_msg, PSE_CREATE_MC_REQ_SIZE, &mc_req.req_hdr, SE_CREATE_MONOTONIC_COUNTER_TIMEOUT_MSEC,
        resp_msg, PSE_CREATE_MC_RESP_SIZE, &mc_resp.resp_hdr);
    if (status == SGX_SUCCESS)
    {
        memcpy(counter_uuid->counter_id, &mc_resp.counter_id,sizeof(counter_uuid->counter_id));
        memcpy(counter_uuid->nonce, &mc_resp.nonce,sizeof(counter_uuid->nonce));
        //align with initial counter_value hard-coded in PSE
        *counter_value = 0;
    }
    //error condition
    free(req_msg);
    free(resp_msg);
    return status;
}

sgx_status_t sgx_create_monotonic_counter(
    sgx_mc_uuid_t* counter_uuid,
    uint32_t* counter_value
    )
{
    // Default attribute mask
    sgx_attributes_t attr_mask;
    attr_mask.flags = DEFAULT_VMC_ATTRIBUTE_MASK;
    attr_mask.xfrm = DEFAULT_VMC_XFRM_MASK;

    return sgx_create_monotonic_counter_ex(MC_POLICY_SIGNER,
        &attr_mask,
        counter_uuid,
        counter_value
        );
}

sgx_status_t sgx_destroy_monotonic_counter(const sgx_mc_uuid_t* counter_uuid)
{
    if (!counter_uuid)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    pse_message_t* req_msg = (pse_message_t*)malloc(PSE_DEL_MC_REQ_SIZE);
    if (!req_msg)
        return SGX_ERROR_OUT_OF_MEMORY;
    pse_message_t* resp_msg = (pse_message_t*)malloc(PSE_DEL_MC_RESP_SIZE);
    if (!resp_msg)
    {
        free(req_msg);
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    memset(req_msg, 0, PSE_DEL_MC_REQ_SIZE);
    memset(resp_msg, 0, PSE_DEL_MC_RESP_SIZE);
    req_msg->exp_resp_size = sizeof(pse_mc_del_resp_t);
    req_msg->payload_size = sizeof(pse_mc_del_req_t);

    pse_mc_del_req_t mc_req;
    memcpy(mc_req.counter_id, counter_uuid->counter_id, sizeof(mc_req.counter_id));
    memcpy(mc_req.nonce, counter_uuid->nonce, sizeof(mc_req.nonce));
    mc_req.req_hdr.service_id = PSE_MC_SERVICE;
    mc_req.req_hdr.service_cmd = PSE_MC_DEL;

    pse_mc_del_resp_t mc_resp;
    memset(&mc_resp, 0, sizeof(pse_mc_del_resp_t));

    sgx_status_t status;
    status = crypt_invoke(req_msg, PSE_DEL_MC_REQ_SIZE, &mc_req.req_hdr, SE_DESTROY_MONOTONIC_COUNTER_TIMEOUT_MSEC,
        resp_msg, PSE_DEL_MC_RESP_SIZE, &mc_resp.resp_hdr);
    //error condition
    free(req_msg);
    free(resp_msg);
    return status;
}


sgx_status_t sgx_increment_monotonic_counter(
    const sgx_mc_uuid_t* counter_uuid,
    uint32_t* counter_value
    )
{
    if (!counter_value || !counter_uuid)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    pse_message_t* req_msg = (pse_message_t*)malloc(PSE_INC_MC_REQ_SIZE);
    if (!req_msg)
        return SGX_ERROR_OUT_OF_MEMORY;
    pse_message_t* resp_msg = (pse_message_t*)malloc(PSE_INC_MC_RESP_SIZE);
    if (!resp_msg)
    {
        free(req_msg);
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    memset(req_msg, 0, PSE_INC_MC_REQ_SIZE);
    memset(resp_msg, 0, PSE_INC_MC_RESP_SIZE);
    req_msg->exp_resp_size = sizeof(pse_mc_inc_resp_t);
    req_msg->payload_size = sizeof(pse_mc_inc_req_t);

    pse_mc_inc_req_t mc_req;
    memcpy(mc_req.counter_id, counter_uuid->counter_id, sizeof(mc_req.counter_id));
    memcpy(mc_req.nonce, counter_uuid->nonce, sizeof(mc_req.nonce));
    mc_req.req_hdr.service_id = PSE_MC_SERVICE;
    mc_req.req_hdr.service_cmd = PSE_MC_INC;

    pse_mc_inc_resp_t mc_resp;
    memset(&mc_resp, 0, sizeof(pse_mc_inc_resp_t));

    sgx_status_t status;
    status = crypt_invoke(req_msg, PSE_INC_MC_REQ_SIZE, &mc_req.req_hdr, SE_INCREMENT_MONOTONIC_COUNTER_TIMEOUT_MSEC,
        resp_msg, PSE_INC_MC_RESP_SIZE, &mc_resp.resp_hdr);
    if (status == SGX_SUCCESS)
    {
        *counter_value = mc_resp.counter_value;
    }
    //error condition
    free(req_msg);
    free(resp_msg);
    return status;
}


sgx_status_t sgx_read_monotonic_counter(
    const sgx_mc_uuid_t* counter_uuid,
    uint32_t* counter_value
    )
{
    if (!counter_value || !counter_uuid)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    pse_message_t* req_msg = (pse_message_t*)malloc(PSE_READ_MC_REQ_SIZE);
    if (!req_msg)
        return SGX_ERROR_OUT_OF_MEMORY;
    pse_message_t* resp_msg = (pse_message_t*)malloc(PSE_READ_MC_RESP_SIZE);
    if (!resp_msg)
    {
        free(req_msg);
        return SGX_ERROR_OUT_OF_MEMORY;
    }
    memset(req_msg, 0, PSE_READ_MC_REQ_SIZE);
    memset(resp_msg, 0, PSE_READ_MC_RESP_SIZE);
    req_msg->exp_resp_size = sizeof(pse_mc_read_resp_t);
    req_msg->payload_size = sizeof(pse_mc_read_req_t);

    pse_mc_read_req_t mc_req;
    memcpy(mc_req.counter_id, counter_uuid->counter_id, sizeof(mc_req.counter_id));
    memcpy(mc_req.nonce, counter_uuid->nonce, sizeof(mc_req.nonce));
    mc_req.req_hdr.service_id = PSE_MC_SERVICE;
    mc_req.req_hdr.service_cmd = PSE_MC_READ;

    pse_mc_read_resp_t mc_resp;
    memset(&mc_resp, 0, sizeof(pse_mc_read_resp_t));

    sgx_status_t status;
    status = crypt_invoke(req_msg, PSE_READ_MC_REQ_SIZE, &mc_req.req_hdr, SE_READ_MONOTONIC_COUNTER_TIMEOUT_MSEC,
        resp_msg, PSE_READ_MC_RESP_SIZE, &mc_resp.resp_hdr);
    if (status == SGX_SUCCESS)
    {
        *counter_value = mc_resp.counter_value;
    }
    //error condition
    free(req_msg);
    free(resp_msg);
    return status;
}

