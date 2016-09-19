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
#ifndef __AE_RESPONSE_H
#define __AE_RESPONSE_H

#include <stdio.h>
#include <stdint.h>
#include <sgx_error.h>
#include <Config.h>


struct AEMessage;
class ISerializer;

class AECloseSessionResponse;
class AEExchangeReportResponse;
class AEInvokeServiceResponse;
class AECreateSessionResponse;
class AEReportAttestationResponse;
class AEGetLaunchTokenResponse;
class AEGetQuoteResponse;
class AEInitQuoteResponse;
class AEGetPsCapResponse;
class AEGetWhiteListSizeResponse;
class AEGetWhiteListResponse;
class AESGXGetExtendedEpidGroupIdResponse;
class AESGXSwitchExtendedEpidGroupResponse;

#include <iostream>

class IAEResponseVisitor
{
 public:
  virtual void visitInitQuoteResponse(AEInitQuoteResponse&) = 0;
  virtual void visitGetQuoteResponse(AEGetQuoteResponse&) = 0;
  virtual void visitGetLaunchTokenResponse(AEGetLaunchTokenResponse&) = 0;
  virtual void visitReportAttestationResponse(AEReportAttestationResponse&) = 0;
  virtual void visitCreateSessionResponse(AECreateSessionResponse&) = 0;
  virtual void visitInvokeServiceResponse(AEInvokeServiceResponse&) = 0;
  virtual void visitExchangeReportResponse(AEExchangeReportResponse&) = 0;
  virtual void visitCloseSessionResponse(AECloseSessionResponse&) = 0;
  virtual void visitGetPsCapResponse(AEGetPsCapResponse&) = 0;
  virtual void visitGetWhiteListSizeResponse(AEGetWhiteListSizeResponse&) = 0;
  virtual void visitGetWhiteListResponse(AEGetWhiteListResponse&) = 0;
  virtual void visitSGXGetExtendedEpidGroupIdResponse(AESGXGetExtendedEpidGroupIdResponse&) = 0;
  virtual void visitSGXSwitchExtendedEpidGroupResponse(AESGXSwitchExtendedEpidGroupResponse&) = 0;

  virtual ~IAEResponseVisitor() {};
};

class IAEResponse{
    public:
        IAEResponse() : mErrorCode(SGX_ERROR_UNEXPECTED),mValidSizeCheck(false) {}
        inline virtual ~IAEResponse() {}
        virtual AEMessage*  serialize(ISerializer* serializer) =0;
        virtual bool        inflateWithMessage(AEMessage* message, ISerializer* serializer) =0;

        //operators
        virtual bool operator==(const IAEResponse& other) const {return this == &other;}

        //this method is added especially for future compatibility (may be used to check thigs like the message MAC)
        virtual bool check() {return false;} //although only some responses will need more complex logic here, this will default to
        //invalid. Validity needs to be explicitly declared in children :)

        inline int      GetErrorCode()        const { return mErrorCode; }
        inline void     SetErrorCode(uint32_t error) { mErrorCode = error; }

        virtual void visit(IAEResponseVisitor& visitor) = 0;

    protected:
        uint32_t    mErrorCode;
        bool mValidSizeCheck;

};

#endif
