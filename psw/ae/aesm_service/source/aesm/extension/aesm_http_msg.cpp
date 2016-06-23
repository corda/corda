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


#include <string>
#include "aesm_encode.h"
#include "oal.h"
#include "se_wrapper.h"
#include "prof_fun.h"
#include "aesm_proxy_type.h"
#include "endpoint_select_info.h"
#include "util.h"

#include <curl/curl.h>
#ifndef INTERNET_DEFAULT_HTTP_PORT
#define INTERNET_DEFAULT_HTTP_PORT 80
#endif

#define AESM_DEFAULT_CONN_TIME_OUT 1000
#define AESM_DEFAULT_TIME_OUT 10000

bool is_curl_initialized_succ(void);//network is available only when curl library is successfully initialized

typedef struct _network_malloc_info_t{
    char *base;
    uint32_t size;
}network_malloc_info_t;

static size_t write_callback(void *ptr, size_t size, size_t nmemb, void *stream)
{
    network_malloc_info_t* s=reinterpret_cast<network_malloc_info_t *>(stream);
    uint32_t start=0;
    if(s->base==NULL){
        s->base = reinterpret_cast<char *>(malloc(size*nmemb));
        s->size = static_cast<uint32_t>(size*nmemb);
        if(s->base==NULL){
            AESM_DBG_ERROR("malloc error in write callback fun");
            return 0;
        }
    }else{
        uint32_t newsize = s->size+static_cast<uint32_t>(size*nmemb);
        char *p=reinterpret_cast<char *>(malloc(newsize));
        if(p == NULL){
            free(s->base);
            s->base = NULL;
            AESM_DBG_ERROR("malloc error in write callback fun");
            return 0;
        }
        memcpy(p, s->base, s->size);
        free(s->base);
        start = s->size;
        s->base = p;
        s->size = newsize;
    }
    memcpy(s->base +start, ptr, size*nmemb);
    return nmemb;
}

static ae_error_t http_network_init(CURL **curl, const char *url, bool is_ocsp)
{
    CURLcode cc = CURLE_OK;
    UNUSED(is_ocsp);
    AESM_DBG_TRACE("http init for url %s",url);
    if(!is_curl_initialized_succ()){
        AESM_DBG_ERROR("libcurl not initialized");
        return AE_FAILURE;//fatal error that libcurl could not be initialized
    }
    std::string url_path = url;
    uint32_t proxy_type;
    char proxy_url[MAX_PATH];
    EndpointSelectionInfo::instance().get_proxy(proxy_type, proxy_url);

    *curl = curl_easy_init();
    if(!*curl){
         AESM_DBG_ERROR("fail to init curl handle");
         return AE_FAILURE;
    }
    if((cc=curl_easy_setopt(*curl, CURLOPT_URL, url_path.c_str()))!=CURLE_OK){
       AESM_DBG_ERROR("fail error code %d in set url %s",(int)cc, url_path.c_str());
       return AE_FAILURE;
    }
    (void)curl_easy_setopt(*curl, CURLOPT_REDIR_PROTOCOLS, CURLPROTO_HTTP | CURLPROTO_HTTPS);
    //setting proxy now
    if(proxy_type == AESM_PROXY_TYPE_DIRECT_ACCESS){
        AESM_DBG_TRACE("use no proxy");
        (void)curl_easy_setopt(*curl, CURLOPT_NOPROXY , "*");
    }else if(proxy_type == AESM_PROXY_TYPE_MANUAL_PROXY){
        AESM_DBG_TRACE("use manual proxy %s",proxy_url);
        (void)curl_easy_setopt(*curl, CURLOPT_PROXY, proxy_url);
    }
    return AE_SUCCESS;
}

static ae_error_t http_network_send_data(CURL *curl, const char *req_msg, uint32_t msg_size, char **resp_msg, uint32_t& resp_size, http_methods_t method, bool is_ocsp)
{
    AESM_DBG_TRACE("send data method=%d",method);
    struct curl_slist *headers=NULL;
    CURLcode cc=CURLE_OK;
    if(is_ocsp){
        headers = curl_slist_append(headers, "Accept: application/ocsp-response");
        if(headers==NULL){
            AESM_DBG_ERROR("fail in add accept ocsp-response header");
            return AE_FAILURE;
        }
        headers = curl_slist_append(headers, "Content-Type: application/ocsp-request");
        if(headers == NULL){
           AESM_DBG_ERROR("fail in add content type ocsp-request");
           return AE_FAILURE;
        }
        AESM_DBG_TRACE("ocsp request");
    }
    char buf[50];
    sprintf(buf, "Content-Length: %u", (unsigned int)msg_size);
    headers = curl_slist_append(headers, buf);
    if(headers == NULL){
         AESM_DBG_ERROR("fail to add content-length header");
         return AE_FAILURE;
    }
    if((cc=curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers))!=CURLE_OK){
        AESM_DBG_ERROR("fail to set http header:%d",(int)cc);
        return AE_FAILURE;
    }
    if(method == POST){
        if((cc=curl_easy_setopt(curl, CURLOPT_POSTFIELDS, req_msg))!=CURLE_OK){
            AESM_DBG_ERROR("fail to set POST fields:%d",(int)cc);
            return AE_FAILURE;
        }
        if((cc=curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, msg_size))!=CURLE_OK){
            AESM_DBG_ERROR("fail to set POST fields size:%d",(int)cc);
            return AE_FAILURE;
        }
    }
    if((cc=curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_callback))!=CURLE_OK){
        AESM_DBG_ERROR("Fail to set callback function:%d",(int)cc);
        return AE_FAILURE;
    }

    network_malloc_info_t malloc_info;
    malloc_info.base=NULL;
    malloc_info.size = 0;
    if((cc=curl_easy_setopt(curl, CURLOPT_WRITEDATA, reinterpret_cast<void *>(&malloc_info)))!=CURLE_OK){
       AESM_DBG_ERROR("fail to set write back function parameter:%d",(int)cc);
       return AE_FAILURE;
    }
    if((cc=curl_easy_perform(curl))!=CURLE_OK){
        if(malloc_info.base){
            free(malloc_info.base);
        }
        AESM_DBG_ERROR("fail in connect:%d",(int)cc);
        return OAL_NETWORK_UNAVAILABLE_ERROR;
    }
    *resp_msg = malloc_info.base;
    resp_size = malloc_info.size;
    AESM_DBG_TRACE("get response size=%d",resp_size);
    return AE_SUCCESS;
}

static void http_network_fini(CURL *curl)
{
    if(curl!=NULL)
        curl_easy_cleanup(curl);
}


ae_error_t aesm_network_send_receive(const char *server_url, const uint8_t *req, uint32_t req_size,
                                       uint8_t **p_resp, uint32_t *p_resp_size, http_methods_t method, bool is_ocsp)
{
    AESM_PROFILE_FUN;
    ae_error_t ret= AE_SUCCESS;
    CURL *curl = NULL;
    ret = http_network_init(&curl, server_url, is_ocsp);
    if(ret != AE_SUCCESS){
        goto ret_point;
    }
    ret = http_network_send_data(curl, reinterpret_cast<const char *>(req), req_size,
        reinterpret_cast<char **>(p_resp), *p_resp_size, method, is_ocsp);
ret_point:
    http_network_fini(curl);
    return ret;
}

void aesm_free_network_response_buffer(uint8_t *resp)
{
    if(resp!=NULL)free(resp);
}
