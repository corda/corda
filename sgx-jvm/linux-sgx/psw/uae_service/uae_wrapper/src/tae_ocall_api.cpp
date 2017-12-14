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
#include <AEServicesProvider.h>
#include <AEServices.h>

#include <oal/uae_oal_api.h>
#include <aesm_error.h>

#include <AECreateSessionRequest.h>
#include <AECreateSessionResponse.h>

#include <AEInvokeServiceRequest.h>
#include <AEInvokeServiceResponse.h>

#include <AEExchangeReportRequest.h>
#include <AEExchangeReportResponse.h>

#include <AECloseSessionRequest.h>
#include <AECloseSessionResponse.h>

#include <new>

#define TRY_CATCH_BAD_ALLOC(block) \
    try{ \
        block; \
    } \
    catch(const std::bad_alloc& e) \
    { \
        *result = AESM_OUT_OF_MEMORY_ERROR; \
        return UAE_OAL_SUCCESS; \
    }

extern "C"
{


uae_oal_status_t oal_create_session(
        uint32_t *sid,
        uint8_t* dh_msg1,
        uint32_t dh_msg1_size,
        uint32_t timeout_usec, 
        aesm_error_t *result)
{
    TRY_CATCH_BAD_ALLOC({
    //get services provider
        AEServices* servicesProvider = AEServicesProvider::GetServicesProvider();
        if (servicesProvider == NULL)
            return UAE_OAL_ERROR_UNEXPECTED;


        AECreateSessionRequest createSessionRequest(dh_msg1_size, timeout_usec / 1000);

        AECreateSessionResponse createSessionResponse;
        uae_oal_status_t ret = servicesProvider->InternalInterface(&createSessionRequest, &createSessionResponse, timeout_usec / 1000);
        if (ret == UAE_OAL_SUCCESS)
        {
            bool valid = createSessionResponse.GetValues((uint32_t*)result, sid, dh_msg1_size, dh_msg1);
            if (!valid)
                ret = UAE_OAL_ERROR_UNEXPECTED;
        }
        return ret;
    });
}

uae_oal_status_t oal_close_session(uint32_t sid,
        uint32_t timeout_usec, 
        aesm_error_t *result)
{
    TRY_CATCH_BAD_ALLOC({
        AEServices* servicesProvider = AEServicesProvider::GetServicesProvider();
        if (servicesProvider == NULL)
            return UAE_OAL_ERROR_UNEXPECTED;

        AECloseSessionRequest closeSessionRequest(sid, timeout_usec / 1000);

        AECloseSessionResponse closeSessionResponse;
        uae_oal_status_t ret = servicesProvider->InternalInterface(&closeSessionRequest, &closeSessionResponse, timeout_usec / 1000);
        if (ret == UAE_OAL_SUCCESS)
        {
            bool valid = closeSessionResponse.GetValues((uint32_t*)result);
            if (!valid)
                ret = UAE_OAL_ERROR_UNEXPECTED;
        }
        return ret;
    });

}

uae_oal_status_t oal_exchange_report(uint32_t sid,
        const uint8_t* dh_msg2,
        uint32_t dh_msg2_size,
        uint8_t* dh_msg3,
        uint32_t dh_msg3_size,
        uint32_t timeout_usec, 
        aesm_error_t *result)
{
    TRY_CATCH_BAD_ALLOC({
        AEServices* servicesProvider = AEServicesProvider::GetServicesProvider();
        if (servicesProvider == NULL)
            return UAE_OAL_ERROR_UNEXPECTED;

        AEExchangeReportRequest exchangeReportRequest(sid, dh_msg2_size, dh_msg2, dh_msg3_size, timeout_usec / 1000);

        AEExchangeReportResponse exchangeReportResponse;
        uae_oal_status_t ret = servicesProvider->InternalInterface(&exchangeReportRequest, &exchangeReportResponse, timeout_usec / 1000);
        if (ret == UAE_OAL_SUCCESS)
        {
            bool valid = exchangeReportResponse.GetValues((uint32_t*)result, dh_msg3_size, dh_msg3);
            if (!valid)
                ret = UAE_OAL_ERROR_UNEXPECTED;
        }
        return ret;
    });
}

uae_oal_status_t oal_invoke_service(const uint8_t* pse_message_req,
        uint32_t pse_message_req_size,
        uint8_t* pse_message_resp,
        uint32_t pse_message_resp_size,
        uint32_t timeout_usec, 
        aesm_error_t *result)
{
    TRY_CATCH_BAD_ALLOC({
        AEServices* servicesProvider = AEServicesProvider::GetServicesProvider();
        if (servicesProvider == NULL)
            return UAE_OAL_ERROR_UNEXPECTED;

        AEInvokeServiceRequest invokeServiceRequest(pse_message_req_size, pse_message_req, pse_message_resp_size, timeout_usec / 1000);

        AEInvokeServiceResponse invokeServiceResponse;
        uae_oal_status_t ret = servicesProvider->InternalInterface(&invokeServiceRequest, &invokeServiceResponse, timeout_usec / 1000);
        if (ret == UAE_OAL_SUCCESS)
        {
            bool valid = invokeServiceResponse.GetValues((uint32_t*)result, pse_message_resp_size, pse_message_resp);
            if (!valid)
                ret = UAE_OAL_ERROR_UNEXPECTED;
        }
        return ret;
    });
}

} //end of extern block

