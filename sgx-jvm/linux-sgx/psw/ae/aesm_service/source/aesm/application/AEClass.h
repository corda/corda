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
#ifndef _AE_CLASS_H_
#define _AE_CLASS_H_
#include "sgx_eid.h"
#include "sgx_key.h"
#include "se_thread.h"
#include "sgx_urts.h"
#include "internal/se_stdio.h"
#include "oal/oal.h"
#include "aeerror.h"
#include <stdlib.h>
#include <exception>

template<class T>
class Singleton
{
    CLASS_UNCOPYABLE(Singleton)
public:
    static T& instance()
    {
        if (0 == _instance)
        {
            _instance = new T();
            atexit(destroy);
        }
        return *_instance;
    }

    virtual ~Singleton()
    {
        _instance = NULL;
    }

protected:
    Singleton(){}
private:
    static void destroy()
    {
        if ( _instance != 0 )
        {
            delete _instance;
            _instance = 0;
        }
    }
    static T * volatile _instance;
};

template<class T>
class SingletonEnclave: public Singleton<T>
{
    CLASS_UNCOPYABLE(SingletonEnclave)
public:

    virtual ae_error_t load_enclave();
    void unload_enclave();
protected:
    sgx_enclave_id_t m_enclave_id;
    sgx_launch_token_t m_launch_token;
    sgx_misc_attribute_t m_attributes;
    SingletonEnclave():m_enclave_id(0)
    {
        memset(&m_launch_token, 0, sizeof(m_launch_token));
        memset(&m_attributes, 0, sizeof(m_attributes));
    }
    ~SingletonEnclave(){}
    virtual void before_enclave_load() {}
    virtual int get_debug_flag() = 0;

};
template <class T>
ae_error_t SingletonEnclave<T>::load_enclave()
{
    before_enclave_load();

    if(m_enclave_id)
        return AE_SUCCESS;

    aesm_enclave_id_t aesm_enclave_id = T::get_enclave_fid();
    AESM_DBG_INFO("loading enclave %d",aesm_enclave_id);

    sgx_status_t ret;
    ae_error_t ae_err;
    char enclave_path[MAX_PATH]= {0};
    if((ae_err = aesm_get_pathname(FT_ENCLAVE_NAME, aesm_enclave_id, enclave_path,
        MAX_PATH))
        !=AE_SUCCESS){
        AESM_DBG_ERROR("fail to get enclave pathname");
        return ae_err;
    }
    int launch_token_update;
    ret = sgx_create_enclave(enclave_path, get_debug_flag(), &m_launch_token,
        &launch_token_update, &m_enclave_id,
        &m_attributes);
    if (ret == SGX_ERROR_NO_DEVICE){
        AESM_DBG_ERROR("AE SERVER NOT AVAILABLE in load enclave: %s",enclave_path);
        return AE_SERVER_NOT_AVAILABLE;
    }
    if(ret == SGX_ERROR_OUT_OF_EPC){
        AESM_DBG_ERROR("No enough EPC to load AE: %s",enclave_path);
        AESM_LOG_ERROR("%s %s", g_event_string_table[SGX_EVENT_OUT_OF_EPC], enclave_path);
        return AESM_AE_OUT_OF_EPC;
    }
    if (ret != SGX_SUCCESS){
        AESM_DBG_ERROR("Create Enclave failed:%d",ret);
        return AE_SERVER_NOT_AVAILABLE;
    }
    AESM_DBG_INFO("enclave %d loaded with id 0X%llX",aesm_enclave_id,m_enclave_id);

    return AE_SUCCESS;
}

template <class T>
void SingletonEnclave<T>::unload_enclave()
{
    if (m_enclave_id)
    {
        AESM_DBG_INFO("unload enclave 0X%llX",m_enclave_id);
        sgx_destroy_enclave(m_enclave_id);
        m_enclave_id = 0;
    }

    return;
}

template<class T>
T * volatile Singleton<T>::_instance = 0;

#define AESM_RETRY_COUNT    3

ae_error_t sgx_error_to_ae_error(sgx_status_t status);
#endif

