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
#ifndef _AE_SGX_SWITCH_EXTENDED_GROUP_REQUEST_H
#define _AE_SGX_SWITCH_EXTENDED_GROUP_REQUEST_H

#include <IAERequest.h>

namespace aesm
{
    namespace message
    {
        class Request_SGXSwitchExtendedEpidGroupRequest;
    };
};

class AESGXSwitchExtendedEpidGroupRequest : public IAERequest
{
    public:
        AESGXSwitchExtendedEpidGroupRequest(const aesm::message::Request_SGXSwitchExtendedEpidGroupRequest& request);
        AESGXSwitchExtendedEpidGroupRequest(uint32_t extendedGroupId, uint32_t timeout);
        AESGXSwitchExtendedEpidGroupRequest(const AESGXSwitchExtendedEpidGroupRequest& other);
        ~AESGXSwitchExtendedEpidGroupRequest();

        AEMessage* serialize();


        //operators
        AESGXSwitchExtendedEpidGroupRequest& operator=(const AESGXSwitchExtendedEpidGroupRequest& request);

        //checks
        bool check();

        //hooks
        IAEResponse* execute(IAESMLogic* aesmLogic);

        //used to determine in which queue to be placed
        virtual RequestClass getRequestClass();

    protected:
        void ReleaseMemory();
        aesm::message::Request_SGXSwitchExtendedEpidGroupRequest* m_request;
};

#endif
