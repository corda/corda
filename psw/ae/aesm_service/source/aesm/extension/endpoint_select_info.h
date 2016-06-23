/*
 * Copyright (C) 2011-2016 Intel Corporation. All rights reserved.
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

#ifndef _ENDPOINT_SELECT_INFO_H_
#define _ENDPOINT_SELECT_INFO_H_
#include "se_types.h"
#include "sgx_tseal.h"
#include "aeerror.h"
#include "tlv_common.h"
#include "se_thread.h"
#include "internal/se_rwlock.h"
#include "oal/oal.h"
#include "se_wrapper.h"
#include <time.h>
#include <string.h>
#include "AEClass.h"
#include "aesm_logic.h"

#define AESM_DATA_ENDPOINT_SELECTION_INFOS  'B'
#define AESM_DATA_ENDPOINT_SELECTION_VERSION 1
#pragma pack(1)
#include "aesm_config.h"

/*Struct for data to save endpoint selection protocol result into persistent data storage*/
typedef struct _endpoint_selection_infos_t{
    uint8_t      aesm_data_type;
    uint8_t      aesm_data_version;
    signed_pek_t pek;
    char         provision_url[MAX_PATH];
}endpoint_selection_infos_t;
#pragma pack()

/*An interface to provide the endpoint selection protocol and also provide some URLs (result of ES protocol or some static url)
 *Singleton class used to provide a singleton instance in memory and lock used so that it could be shared by PvE/PSEPR
 *EndpointSelectionInfo::instance().start_protocol(...) could be used to get endpoint selection result
 *   It will restart the ES protocol to get updated data. If the protocol fails, it may resue existing endpoint selection protocol result in persistent storage
 */
class EndpointSelectionInfo: public Singleton<EndpointSelectionInfo>{
    CLASS_UNCOPYABLE(EndpointSelectionInfo);
    friend class Singleton<EndpointSelectionInfo>;
private:
    AESMLogicMutex      _es_lock;             /*lock used since the data will be accessed by two different components: PSEPR and PVE*/
    aesm_config_infos_t _server_urls;         /*some readonly urls */
    bool                _is_server_url_valid; /*Set it to true when field _server_urls is valid*/
    bool                _is_server_url_loaded;/*Set it to true after trying to read _server_urls from persistent storage. If _is_server_url_valid is false but this field is true, it means there're problem in persistent storage access*/
    static ae_error_t read_pek(endpoint_selection_infos_t& es_info); /*read _es_info from persistent storage*/
    static ae_error_t write_pek(const endpoint_selection_infos_t& es_info); /*save _es_info to persistent storage*/
    ae_error_t verify_signature(const endpoint_selection_infos_t& es_info, uint8_t xid[XID_SIZE], uint8_t rsa_signature[PVE_RSA_KEY_BYTES], uint16_t ttl); /*verify rsa signature in ES protocol result*/
public:
    EndpointSelectionInfo(){
        memset(&_server_urls, 0, sizeof(_server_urls));
        _is_server_url_loaded=false;
        _is_server_url_valid=false;
    }
public:
    void  get_proxy(uint32_t& proxy_type, char proxy_url[MAX_PATH]);
    /*Function to get result of Endpoint Selection Protocol from Backend Server*/
    ae_error_t start_protocol(endpoint_selection_infos_t& es_info);
};
#endif /*_ENDPOINT_SELECT_INFO_H_*/

