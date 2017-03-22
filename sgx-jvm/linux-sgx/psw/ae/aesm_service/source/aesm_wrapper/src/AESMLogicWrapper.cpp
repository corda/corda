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
#include "AESMLogicWrapper.h"
#include <iostream>
#include <unistd.h>

#include "LEClass.h"
#include "Config.h"

aesm_error_t AESMLogicWrapper::initQuote(uint8_t    *target_info,
                                uint32_t   target_info_length,
                                uint8_t    *gid,
                                uint32_t   gid_length)
{
     return AESMLogic::init_quote(target_info, target_info_length,
                     gid, gid_length);
}

aesm_error_t AESMLogicWrapper::getQuote(uint32_t reportLength, const uint8_t* report,
                               uint32_t quoteType,
                               uint32_t spidLength, const uint8_t* spid,
                               uint32_t nonceLength, const uint8_t* nonce,
                               uint32_t sig_rlLength, const uint8_t* sig_rl,
                               uint32_t bufferSize, uint8_t* quote,
                               uint32_t qe_reportSize, uint8_t* qe_report)
{
   
    return AESMLogic::get_quote(report, reportLength,
                        quoteType,
                        spid, spidLength,
                        nonce, nonceLength,
                        sig_rl, sig_rlLength,
                        qe_report, qe_reportSize,
                        quote, bufferSize);

}

aesm_error_t AESMLogicWrapper::closeSession(uint32_t sessionId)
{
    return AESMLogic::close_session(sessionId);
}

aesm_error_t AESMLogicWrapper::createSession(uint32_t *session_id,
                                      uint8_t *se_dh_msg1,
                                      uint32_t se_dh_msg1_size)
{
   
    return AESMLogic::create_session(session_id, se_dh_msg1, se_dh_msg1_size);
    
}

aesm_error_t AESMLogicWrapper::exchangeReport(uint32_t session_id,
                                       const uint8_t* se_dh_msg2,
                                       uint32_t se_dh_msg2_size,
                                       uint8_t* se_dh_msg3,
                                       uint32_t se_dh_msg3_size )
{
    return AESMLogic::exchange_report(session_id, 
                                  se_dh_msg2,
                                  se_dh_msg2_size,
                                  se_dh_msg3,
                                  se_dh_msg3_size);
}

aesm_error_t AESMLogicWrapper::getLaunchToken(const uint8_t  *measurement,
                                      uint32_t measurement_size,
                                      const uint8_t  *mrsigner,
                                      uint32_t mrsigner_size,
                                      const uint8_t  *se_attributes,
                                      uint32_t se_attributes_size,
                                      uint8_t  **launch_token,
                                      uint32_t *launch_token_size)
{
    *launch_token_size = sizeof(token_t);
    *launch_token      = new uint8_t[*launch_token_size];

    return AESMLogic::get_launch_token(measurement,
                                    measurement_size,
                                    mrsigner,
                                    mrsigner_size,
                                    se_attributes,
                                    se_attributes_size,
                                    *launch_token,
                                    *launch_token_size);
    
}

aesm_error_t AESMLogicWrapper::invokeService(const uint8_t  *pse_message_req,
                                      uint32_t pse_message_req_size,
                                      uint8_t  *pse_message_resp,
                                      uint32_t pse_message_resp_size)
{
    return AESMLogic::invoke_service(pse_message_req,
                                 pse_message_req_size,
                                 pse_message_resp,
                                 pse_message_resp_size);
}

aesm_error_t AESMLogicWrapper::getPsCap(uint64_t* ps_cap)
{
    return AESMLogic::get_ps_cap(ps_cap);
}

aesm_error_t AESMLogicWrapper::reportAttestationStatus(uint8_t* platform_info, uint32_t platform_info_size,
        uint32_t attestation_error_code,
        uint8_t* update_info, uint32_t update_info_size)

{
    return AESMLogic::report_attestation_status(platform_info,platform_info_size, 
            attestation_error_code,
            update_info, update_info_size);
}

aesm_error_t AESMLogicWrapper::getWhiteListSize(uint32_t* white_list_size)
{
    return AESMLogic::get_white_list_size(white_list_size);
}

aesm_error_t AESMLogicWrapper::getWhiteList(uint8_t *white_list,
                                      uint32_t white_list_size)
{
    return AESMLogic::get_white_list(white_list, white_list_size);    
}

aesm_error_t AESMLogicWrapper::sgxGetExtendedEpidGroupId(uint32_t* x_group_id)
{
    return AESMLogic::get_extended_epid_group_id(x_group_id);
}

aesm_error_t AESMLogicWrapper::sgxSwitchExtendedEpidGroup(uint32_t x_group_id)
{
    return AESMLogic::switch_extended_epid_group(x_group_id);
}

void AESMLogicWrapper::service_stop()
{
    AESMLogic::service_stop();
}
