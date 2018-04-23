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


 /**
  * File: pse_op.cpp
  * Description: Definition for interfaces provided by platform service enclave..
  *
  * Definition for interfaces provided by platform service enclave.
  */

#include "utility.h"
#include "session_mgr.h"
#include "monotonic_counter.h"
#include "trusted_time.h"
#include "pse_op_t.c"
#include "dh.h"
#include "pairing_blob.h"
#include "monotonic_counter_database_sqlite_bin_hash_tree_utility.h"

#define BREAK_ON_MALLOC_FAIL(ptr,ret_val)           if (!(ptr)) {ret_val=PSE_OP_INTERNAL_ERROR;break;}

typedef pse_op_error_t (*srv_pfn_t)(const isv_attributes_t &, const uint8_t*, uint8_t *);
static const struct service_handler_t
{
    uint16_t  service_id;
    uint16_t  service_cmd;
    uint16_t  req_size;
    uint16_t  resp_size;
    srv_pfn_t srv_pfn;
} service_handler[] = {
    {PSE_MC_SERVICE, PSE_MC_CREATE, sizeof(pse_mc_create_req_t), sizeof(pse_mc_create_resp_t), pse_mc_create},
    {PSE_MC_SERVICE, PSE_MC_READ,   sizeof(pse_mc_read_req_t),   sizeof(pse_mc_read_resp_t),   pse_mc_read  },
    {PSE_MC_SERVICE, PSE_MC_INC,    sizeof(pse_mc_inc_req_t),    sizeof(pse_mc_inc_resp_t),    pse_mc_inc   },
    {PSE_MC_SERVICE, PSE_MC_DEL,    sizeof(pse_mc_del_req_t),    sizeof(pse_mc_del_resp_t),    pse_mc_del   },
    {PSE_TRUSTED_TIME_SERVICE, PSE_TIMER_READ,   sizeof(pse_timer_read_req_t), sizeof(pse_timer_read_resp_t), pse_read_timer},
};

/*******************************************************************
**  Function name: create_session_wrapper
**  Descrption: 
**  This function will initialize the AppEnclave<->Pse-Op session establishment process.
**  Parameters:
**         tick - the number of milliseconds that have elapsed since the system was started,
**                used to detect which session is idle for the longest time.
**         sid -  session id
**  Returns: ae_error_t
*******************************************************************/
ae_error_t create_session_wrapper(
    /* IN  */ uint64_t  tick,         
    /* OUT */ uint32_t* sid,
    /* OUT */ pse_dh_msg1_t* dh_msg1)
{
    pse_op_error_t op_ret = OP_SUCCESS;

    if (!sid || !dh_msg1)
    {
        return PSE_OP_PARAMETER_ERROR;
    }

    // ephemeral session must have been established 
    if(!is_eph_session_active())
    {
        // the ephemeral session is not active
        return PSE_OP_EPHEMERAL_SESSION_INVALID;
    }

    op_ret = pse_create_session(tick, *sid, *dh_msg1);
    return error_reinterpret(op_ret);
}

/*******************************************************************
**  Function name: exchange_report_wrapper
**  Descrption: 
**  This function is used to exchange report between AppEnclave and Pse-Op, if success, 
**  a session will be established.
**  Parameters:
**         tick - the number of milliseconds that have elapsed since the system was started,
**                used to detect which session is idle for the longest time.
**         sid -  session id
**         dh_msg2 - DH message2
**         dh_msg3 - DH message3
**  Returns: ae_error_t
*******************************************************************/
ae_error_t exchange_report_wrapper(
    /* IN  */ uint64_t tick,
    /* IN  */ uint32_t sid,
    /* IN  */ sgx_dh_msg2_t* dh_msg2,
    /* OUT */ pse_dh_msg3_t* dh_msg3)
{
    pse_op_error_t op_ret = OP_SUCCESS;

    if(!dh_msg2 || !dh_msg3)
    {
        return PSE_OP_PARAMETER_ERROR;
    }

    // ephemeral session must have been established 
    if(!is_eph_session_active()) // the ephemeral session is not active
    {
        return PSE_OP_EPHEMERAL_SESSION_INVALID;
    }

    op_ret = pse_exchange_report(tick, sid, *dh_msg2, *dh_msg3);
    return error_reinterpret(op_ret);
}

/*******************************************************************
**  Function name: close_session_wrapper
**  Descrption: 
**  This function is used to close a session
**  Parameters:
**         sid -  session id
**  Returns: ae_error_t
*******************************************************************/
ae_error_t close_session_wrapper(
    /* IN  */ uint32_t sid
)
{
    return error_reinterpret(pse_close_session(sid));
}

/*******************************************************************
**  Function name: invoke_service_wrapper
**  Descrption: 
**  This function is used to invoke a service call
**  Parameters:
**         tick - the number of milliseconds that have elapsed since the system was started,
**                used to detect which session is idle for the longest time.
**         req_msg - service request message
**         req_msg_size - size of request message
**         resp_msg - service response message
**         resp_msg_size - size of response message
**  Returns: ae_error_t
*******************************************************************/
ae_error_t invoke_service_wrapper (
    /* IN  */ uint64_t  tick,
    /* IN  */ uint8_t*  req_msg,
    /* IN  */ uint32_t  req_msg_size,
    /* OUT */ uint8_t*  resp_msg,
    /* IN  */ uint32_t  resp_msg_size)
{
    // check parameter
    ae_error_t ae_ret = AE_SUCCESS;
    pse_message_t* pse_req_msg  = (pse_message_t*)req_msg;
    pse_message_t* pse_resp_msg = (pse_message_t*)resp_msg;
    pse_op_error_t op_ret;

    if (!req_msg || !resp_msg)
    {
        return PSE_OP_PARAMETER_ERROR;
    }

    if (req_msg_size < sizeof(pse_message_t)            // make sure the header is inside enclave
        || pse_req_msg->payload_size > UINT32_MAX - sizeof(pse_message_t)   // check potential overflow
        || req_msg_size != sizeof(pse_message_t) + pse_req_msg->payload_size)
    {
        return PSE_OP_PARAMETER_ERROR;
    }
    if (resp_msg_size < sizeof(pse_message_t)           // make sure the header is inside enclave
        || pse_req_msg->exp_resp_size > UINT32_MAX - sizeof(pse_message_t)   // check potential overflow
        || resp_msg_size < sizeof(pse_message_t) + pse_req_msg->exp_resp_size)
    {
        return PSE_OP_PARAMETER_ERROR;
    }

    pse_session_t* session = sid2session(pse_req_msg->session_id);

    // ephemeral session must have been established 
    if(!is_eph_session_active())
    {
        // the ephemeral session is not active
        return PSE_OP_EPHEMERAL_SESSION_INVALID;
    }
    
    //if session is invalid (session not exists or established, or sequence num overflow)
    if (!is_isv_session_valid(session))
    {
        return PSE_OP_SESSION_INVALID;
    }

    // update session tick
    update_session_tick_count(session, tick);

    //clear response message
    memset(resp_msg, 0, resp_msg_size);

    uint8_t* req = (uint8_t*)malloc(pse_req_msg->payload_size);
    uint8_t* resp= NULL;
    uint32_t session_seq_num = get_session_seq_num(session);
    do
    {
        BREAK_ON_MALLOC_FAIL(req, ae_ret)

        // decrypt service request message using session key
        if(false == decrypt_msg(pse_req_msg, req, (sgx_key_128bit_t*)session->active.AEK))
        {
            ae_ret = PSE_OP_SERVICE_MSG_ERROR;
            break;
        }
        pse_req_hdr_t* req_hdr = (pse_req_hdr_t*)req;

        // check session sequence number
        if(req_hdr->seq_num != session_seq_num)
        {
            ae_ret = PSE_OP_SESSION_INVALID;
            //close session
            free_session(session);
            break;
        }

        // Dispatch the service request to the proper handler
        int i;
        int service_count = static_cast<int>(sizeof(service_handler) / sizeof(service_handler_t));
        for (i = 0; i < service_count; i++)
        {
            if (req_hdr->service_id == service_handler[i].service_id &&
                req_hdr->service_cmd == service_handler[i].service_cmd)
            {
                if (pse_req_msg->payload_size != service_handler[i].req_size ||
                    pse_req_msg->exp_resp_size < service_handler[i].resp_size) // response message buffer must be large enough to hold response data 
                {
                    ae_ret = PSE_OP_SERVICE_MSG_ERROR;
                    goto clean_up;
                }
                resp = (uint8_t*)malloc(service_handler[i].resp_size);
                if (resp == NULL)
                {
                    ae_ret = PSE_OP_INTERNAL_ERROR;
                    goto clean_up;
                }
                // serve the request
                op_ret = service_handler[i].srv_pfn(session->isv_attributes, req, resp);
                if(op_ret != OP_SUCCESS)
                {
                    ae_ret = error_reinterpret(op_ret);
                    goto clean_up;
                }

                // set payload size for valid requests
                pse_resp_msg->payload_size = service_handler[i].resp_size;
                
                break;
            }
        }
        if (i == service_count)
        {
            // service_id or service_cmd mismatch
            resp = (uint8_t*)malloc(sizeof(pse_resp_hdr_t));
            BREAK_ON_MALLOC_FAIL(resp, ae_ret)

            // for unknown requests, payload data only includes response header
            pse_resp_msg->payload_size = sizeof(pse_resp_hdr_t);

            // set error status
            ((pse_resp_hdr_t*)resp)->status = PSE_ERROR_UNKNOWN_REQ;
        }

        // prepare the response message
        pse_resp_hdr_t* resp_hdr = (pse_resp_hdr_t*)resp;

        pse_resp_msg->exp_resp_size = 0;
        pse_resp_msg->session_id = pse_req_msg->session_id;

        //set response header, status code is already set in service functions
        resp_hdr->seq_num = session_seq_num + 1;    // addition overflow already checked in is_isv_session_valid()
        resp_hdr->service_id = req_hdr->service_id;
        resp_hdr->service_cmd = req_hdr->service_cmd;

        // update sequence number for current session
        set_session_seq_num(session, resp_hdr->seq_num + 1);

        // encrypt the response message
        if(false == encrypt_msg((pse_message_t*)pse_resp_msg, 
                                (uint8_t*)resp, 
                                (sgx_key_128bit_t*)session->active.AEK))
        {
            ae_ret = PSE_OP_INTERNAL_ERROR;
            break;
        }
    } while (0);

clean_up:
    SAFE_FREE(req);
    SAFE_FREE(resp);
    return ae_ret;
}

/*******************************************************************
**  Function name: initialize_sqlite_database_file_wrapper
**  Descrption: 
**  Initialize the vmc database
**  Parameters:
**         is_for_empty_db_creation - if true, always create a new database
**  Returns: ae_error_t
*******************************************************************/
ae_error_t initialize_sqlite_database_file_wrapper(bool is_for_empty_db_creation)
{
    // ephemeral session must have been established. 
    if(!is_eph_session_active())
    {
        return PSE_OP_EPHEMERAL_SESSION_INVALID;
    }
    return error_reinterpret(initialize_sqlite_database_file(is_for_empty_db_creation));
}

/*******************************************************************
**  Function name: ephemeral_session_m2m3_wrapper
**  Descrption: 
**  Exchange M2 and M3 between CSE and PSE-Op
**  Parameters:
**         sealed_blob - Long term pairing blob
**         pse_cse_msg2 - Message2 from CSE
**         pse_cse_msg3 - Message3 generated by PSE-Op
**  Returns: ae_error_t
*******************************************************************/
ae_error_t ephemeral_session_m2m3_wrapper(
    /* IN  */ pairing_blob_t* sealed_blob,
    /* IN  */ pse_cse_msg2_t* pse_cse_msg2,
    /* OUT */ pse_cse_msg3_t* pse_cse_msg3)
{
    pse_op_error_t op_ret = OP_SUCCESS;

    // check parameters
    if (!sealed_blob || !pse_cse_msg2 || !pse_cse_msg3)
    {
        return PSE_OP_PARAMETER_ERROR;
    }

    op_ret = ephemeral_session_m2m3(sealed_blob, *pse_cse_msg2, *pse_cse_msg3);
    return error_reinterpret(op_ret);
}

/*******************************************************************
**  Function name: ephemeral_session_m4_wrapper
**  Descrption: 
**  Handle Msg4 from CSE, if successful, an ephemeral session will be established
**  Parameters:
**         pse_cse_msg4 - Message4 from CSE
**  Returns: ae_error_t
*******************************************************************/
ae_error_t ephemeral_session_m4_wrapper(
    /* IN  */ pse_cse_msg4_t* pse_cse_msg4)
{
    pse_op_error_t op_ret = OP_SUCCESS;

    if (!pse_cse_msg4) 
    {
        return PSE_OP_PARAMETER_ERROR;
    }

    op_ret = ephemeral_session_m4(*pse_cse_msg4);
    return error_reinterpret(op_ret);
}
