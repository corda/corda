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
#include <AEInternalServicesProvider.h>
#include <AEInternalServices.h>

#include <oal/uae_oal_api.h>
#include <aesm_error.h>

extern "C"
{


uae_oal_status_t oal_create_session(
        uint32_t *sid,
        uint8_t* dh_msg1,
        uint32_t dh_msg1_size,
        uint32_t timeout_usec, 
        aesm_error_t *result)
{
    //get services provider
    AEInternalServices* internalServices = AEInternalServicesProvider::GetInternalServicesProvider();
    if (internalServices == NULL)
        return UAE_OAL_ERROR_UNEXPECTED;

    CreateSessionInformation* info = internalServices->CreateSession(dh_msg1_size, timeout_usec);
   
    uae_oal_status_t retVal = UAE_OAL_ERROR_UNEXPECTED;
    if (info != NULL)
    { 
        *result =  (aesm_error_t)info->errorCode;
        retVal = info->uaeStatus;

        if (info->dh_msg1 != NULL)
        {
            *sid = info->sessionId;
            if (info->dh_msg1 != NULL && info->dh_msg1->data != NULL)
            {
                if (dh_msg1_size < info->dh_msg1->length)
                    retVal = UAE_OAL_ERROR_UNEXPECTED;
                else
                    memcpy(dh_msg1, info->dh_msg1->data, info->dh_msg1->length);
            }
        }
        delete info;
    }
    return retVal;
}

uae_oal_status_t oal_close_session(uint32_t sid,
        uint32_t timeout_usec, 
        aesm_error_t *result)
{
    AEInternalServices* internalServices = AEInternalServicesProvider::GetInternalServicesProvider();
    if (internalServices == NULL)
        return UAE_OAL_ERROR_UNEXPECTED; 

    PlainData* info = internalServices->CloseSession(sid, timeout_usec);
    
    uae_oal_status_t retVal = UAE_OAL_ERROR_UNEXPECTED;
    if (info != NULL)
    {
        *result = (aesm_error_t)info->errorCode;
        retVal = info->uaeStatus;
    }

    return retVal;
}

uae_oal_status_t oal_exchange_report(uint32_t sid,
        const uint8_t* dh_msg2,
        uint32_t dh_msg2_size,
        uint8_t* dh_msg3,
        uint32_t dh_msg3_size,
        uint32_t timeout_usec, 
        aesm_error_t *result)
{

    AEInternalServices* internalServices = AEInternalServicesProvider::GetInternalServicesProvider();
    if (internalServices == NULL)
        return UAE_OAL_ERROR_UNEXPECTED;

    PlainData* dhMsg2 = NULL;
    PlainData* msg3 = NULL;
    dhMsg2 = new PlainData;

    dhMsg2->length = dh_msg2_size;
    dhMsg2->data = new uint8_t[dh_msg2_size];
    memcpy(dhMsg2->data, dh_msg2, dh_msg2_size);

    msg3 = internalServices->ExchangeReport(sid, dhMsg2, dh_msg3_size, timeout_usec);

    uae_oal_status_t retVal = UAE_OAL_ERROR_UNEXPECTED;

    if (msg3 != NULL)
    {
        *result = (aesm_error_t)msg3->errorCode;
        retVal = msg3->uaeStatus;

        if (*result == AESM_SUCCESS)
        {
            if( msg3->data != NULL)
            {
                memcpy(dh_msg3, msg3->data, dh_msg3_size);
            }
            else
                retVal = UAE_OAL_ERROR_UNEXPECTED;
        }
    }

    delete msg3;
    delete dhMsg2;

    return retVal;
}

uae_oal_status_t oal_invoke_service(const uint8_t* pse_message_req,
        uint32_t pse_message_req_size,
        uint8_t* pse_message_resp,
        uint32_t pse_message_resp_size,
        uint32_t timeout_usec, 
        aesm_error_t *result)
{
    AEInternalServices* internalServices = AEInternalServicesProvider::GetInternalServicesProvider();
    if (internalServices == NULL)
        return UAE_OAL_ERROR_UNEXPECTED;

    PSEMessage* request = new PSEMessage;

    request->length = pse_message_req_size;
    request->data = new uint8_t[pse_message_req_size];
    memcpy(request->data, pse_message_req, pse_message_req_size);

    PSEMessage* pse_resp = internalServices->InvokeService(request, pse_message_resp_size, timeout_usec);
        
    uae_oal_status_t retVal = UAE_OAL_ERROR_UNEXPECTED;
    if (pse_resp != NULL)
    {
        *result = (aesm_error_t)pse_resp->errorCode;
        retVal = pse_resp->uaeStatus;

        if (pse_resp->data != NULL)
            memcpy(pse_message_resp, pse_resp->data, pse_message_resp_size);
    }

    delete request;
    delete pse_resp;

    return retVal;
}

} //end of extern block

