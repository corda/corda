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

#include "sgx_eid.h"
#include "EnclaveMessageExchange.h"
#include "error_codes.h"
#include "Utility_E1.h"
#include "stdlib.h"
#include "string.h"

uint32_t marshal_input_parameters_e2_foo1(uint32_t target_fn_id, uint32_t msg_type, uint32_t var1, uint32_t var2, char** marshalled_buff, size_t* marshalled_buff_len)
{
    ms_in_msg_exchange_t *ms;
    size_t param_len, ms_len;
    char *temp_buff;
        
    param_len = sizeof(var1)+sizeof(var2);
    temp_buff = (char*)malloc(param_len);
    if(!temp_buff)
        return MALLOC_ERROR;

    memcpy(temp_buff,&var1,sizeof(var1));
    memcpy(temp_buff+sizeof(var1),&var2,sizeof(var2));
    ms_len = sizeof(ms_in_msg_exchange_t) + param_len;
    ms = (ms_in_msg_exchange_t *)malloc(ms_len);
    if(!ms)
    {
        SAFE_FREE(temp_buff);
        return MALLOC_ERROR;
    }
    ms->msg_type = msg_type;
    ms->target_fn_id = target_fn_id;
    ms->inparam_buff_len = (uint32_t)param_len;
    memcpy(&ms->inparam_buff, temp_buff, param_len);
    *marshalled_buff = (char*)ms;
    *marshalled_buff_len = ms_len;
    SAFE_FREE(temp_buff);
    return SUCCESS;
}

uint32_t unmarshal_retval_and_output_parameters_e2_foo1(char* out_buff, char** retval)
{
    size_t retval_len;
    ms_out_msg_exchange_t *ms;
    if(!out_buff)
        return INVALID_PARAMETER_ERROR;
    ms = (ms_out_msg_exchange_t *)out_buff;
    retval_len = ms->retval_len;
    *retval = (char*)malloc(retval_len);
    if(!*retval)
        return MALLOC_ERROR;

    memcpy(*retval, ms->ret_outparam_buff, retval_len);
    return SUCCESS;
}

uint32_t unmarshal_input_parameters_e1_foo1(external_param_struct_t *pstruct, ms_in_msg_exchange_t* ms)
{
    char* buff;
    size_t len;
    if(!pstruct || !ms)
        return INVALID_PARAMETER_ERROR;

    buff = ms->inparam_buff;
    len = ms->inparam_buff_len;
    if(len != (sizeof(pstruct->var1)+sizeof(pstruct->var2)+sizeof(pstruct->p_internal_struct->ivar1)+sizeof(pstruct->p_internal_struct->ivar2)))
        return ATTESTATION_ERROR;

    memcpy(&pstruct->var1, buff, sizeof(pstruct->var1));
    memcpy(&pstruct->var2, buff + sizeof(pstruct->var1), sizeof(pstruct->var2));
    memcpy(&pstruct->p_internal_struct->ivar1, buff+(sizeof(pstruct->var1)+sizeof(pstruct->var2)), sizeof(pstruct->p_internal_struct->ivar1)); 
    memcpy(&pstruct->p_internal_struct->ivar2, buff+(sizeof(pstruct->var1)+sizeof(pstruct->var2)+sizeof(pstruct->p_internal_struct->ivar1)), sizeof(pstruct->p_internal_struct->ivar2));

    return SUCCESS;
}

uint32_t marshal_retval_and_output_parameters_e1_foo1(char** resp_buffer, size_t* resp_length, uint32_t retval, external_param_struct_t *p_struct_var, size_t len_data, size_t len_ptr_data)
{
    ms_out_msg_exchange_t *ms;
    size_t param_len, ms_len, ret_param_len;;
    char *temp_buff;
    int* addr;
    char* struct_data;
    size_t retval_len;
    
    if(!resp_length || !p_struct_var)
        return INVALID_PARAMETER_ERROR;

    retval_len = sizeof(retval);
    struct_data = (char*)p_struct_var;    
    param_len = len_data + len_ptr_data;
    ret_param_len = param_len + retval_len;
    addr = *(int **)(struct_data + len_data);
    temp_buff = (char*)malloc(ret_param_len);
    if(!temp_buff)
        return MALLOC_ERROR;

    memcpy(temp_buff, &retval, sizeof(retval)); 
    memcpy(temp_buff + sizeof(retval), struct_data, len_data);
    memcpy(temp_buff + sizeof(retval) + len_data, addr, len_ptr_data);
    ms_len = sizeof(ms_out_msg_exchange_t) + ret_param_len;
    ms = (ms_out_msg_exchange_t *)malloc(ms_len);
    if(!ms)
    {
        SAFE_FREE(temp_buff);
        return MALLOC_ERROR;
    }
    ms->retval_len = (uint32_t)retval_len;
    ms->ret_outparam_buff_len = (uint32_t)ret_param_len;
    memcpy(&ms->ret_outparam_buff, temp_buff, ret_param_len);
    *resp_buffer = (char*)ms;
    *resp_length = ms_len;
    
    SAFE_FREE(temp_buff);
    return SUCCESS;
}

uint32_t marshal_message_exchange_request(uint32_t target_fn_id, uint32_t msg_type, uint32_t secret_data, char** marshalled_buff, size_t* marshalled_buff_len)
{
    ms_in_msg_exchange_t *ms;
    size_t secret_data_len, ms_len;
    if(!marshalled_buff_len)
        return INVALID_PARAMETER_ERROR;
    secret_data_len = sizeof(secret_data);
    ms_len = sizeof(ms_in_msg_exchange_t) + secret_data_len;
    ms = (ms_in_msg_exchange_t *)malloc(ms_len);
    if(!ms)
        return MALLOC_ERROR;

    ms->msg_type = msg_type;
    ms->target_fn_id = target_fn_id;
    ms->inparam_buff_len = (uint32_t)secret_data_len;
    memcpy(&ms->inparam_buff, &secret_data, secret_data_len);
    *marshalled_buff = (char*)ms;
    *marshalled_buff_len = ms_len;
     return SUCCESS;
}

uint32_t umarshal_message_exchange_request(uint32_t* inp_secret_data, ms_in_msg_exchange_t* ms)
{
    char* buff;
    size_t len;
    if(!inp_secret_data || !ms)
        return INVALID_PARAMETER_ERROR;
    buff = ms->inparam_buff;
    len = ms->inparam_buff_len;
    if(len != sizeof(uint32_t))
        return ATTESTATION_ERROR;

    memcpy(inp_secret_data, buff, sizeof(uint32_t));    

    return SUCCESS;
}

uint32_t marshal_message_exchange_response(char** resp_buffer, size_t* resp_length, uint32_t secret_response)
{
    ms_out_msg_exchange_t *ms;
    size_t secret_response_len, ms_len;
    size_t retval_len, ret_param_len;
    if(!resp_length)
        return INVALID_PARAMETER_ERROR;
    secret_response_len = sizeof(secret_response);
    retval_len = secret_response_len;
    ret_param_len = secret_response_len;
    ms_len = sizeof(ms_out_msg_exchange_t) + ret_param_len;
    ms = (ms_out_msg_exchange_t *)malloc(ms_len);
    if(!ms)
        return MALLOC_ERROR;

    ms->retval_len = (uint32_t)retval_len;
    ms->ret_outparam_buff_len = (uint32_t)ret_param_len;
    memcpy(&ms->ret_outparam_buff, &secret_response, secret_response_len);
    *resp_buffer = (char*)ms;
    *resp_length = ms_len;
    return SUCCESS;
}

uint32_t umarshal_message_exchange_response(char* out_buff, char** secret_response)
{
    size_t retval_len;
    ms_out_msg_exchange_t *ms;
    if(!out_buff)
        return INVALID_PARAMETER_ERROR;
    ms = (ms_out_msg_exchange_t *)out_buff;
    retval_len = ms->retval_len;
    *secret_response = (char*)malloc(retval_len);
    if(!*secret_response)
    {
        return MALLOC_ERROR;
    }
    memcpy(*secret_response, ms->ret_outparam_buff, retval_len);
    return SUCCESS;
}

