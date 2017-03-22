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
#ifndef __AE_REQUEST_H__
#define __AE_REQUEST_H__

#include <string.h>
#include <stdint.h>
#include <sgx_error.h>
#include <aeerror.h>
#include <Config.h>

struct AEMessage{
    uint32_t    size;
    char*       data;

#ifdef __cplusplus
    AEMessage(): size(0), data(NULL) {}
    ~AEMessage(){
        if (data != NULL) delete [] data;
        data = NULL;
    }

    bool operator==(const AEMessage &other) const
    {
        if (this == &other) return true;
        if (this == NULL)   return false;

        if (size != other.size)                  return false;
        if (memcmp(data, other.data, size) != 0) return false;
        return true;
    }

    void copyFields(const AEMessage& other)
    {
        size = other.size;

        if (other.data == NULL)
            data = NULL;
        else
        {
           data = new char[size];
               
           memcpy(data, other.data, size);
        }
    }

    AEMessage& operator=(const AEMessage& other)
    {
        if (this == &other)
            return *this;

        if (data != NULL)
            delete [] data;

        copyFields(other);

        return *this;
    }

    AEMessage(const AEMessage& other)
    {
        copyFields(other);
    }
#endif
};

class ISerializer;
class IAEResponse;
class IAESMLogic;

class AECloseSessionRequest;
class AEExchangeReportRequest;
class AEInvokeServiceRequest;
class AECreateSessionRequest;
class AEReportAttestationRequest;
class AEGetLaunchTokenRequest;
class AEGetQuoteRequest;
class AEInitQuoteRequest;
class AEGetPsCapRequest;
class AEGetWhiteListSizeRequest;
class AEGetWhiteListRequest;
class AESGXGetExtendedEpidGroupIdRequest;
class AESGXSwitchExtendedEpidGroupRequest;

class IAERequestVisitor
{
 public:
  virtual void visitInitQuoteRequest(AEInitQuoteRequest&) = 0;
  virtual void visitGetQuoteRequest(AEGetQuoteRequest&) = 0;
  virtual void visitGetLaunchTokenRequest(AEGetLaunchTokenRequest&) = 0;
  virtual void visitReportAttestationRequest(AEReportAttestationRequest&) = 0;
  virtual void visitCreateSessionRequest(AECreateSessionRequest&) = 0;
  virtual void visitInvokeServiceRequest(AEInvokeServiceRequest&) = 0;
  virtual void visitExchangeReportRequest(AEExchangeReportRequest&) = 0;
  virtual void visitCloseSessionRequest(AECloseSessionRequest&) = 0;  
  virtual void visitGetPsCapRequest(AEGetPsCapRequest&) = 0;
  virtual void visitGetWhiteListSizeRequest(AEGetWhiteListSizeRequest&) = 0;
  virtual void visitGetWhiteListRequest(AEGetWhiteListRequest&) = 0;
  virtual void visitSGXGetExtendedEpidGroupIdRequest(AESGXGetExtendedEpidGroupIdRequest&) = 0;
  virtual void visitSGXSwitchExtendedEpidGroupRequest(AESGXSwitchExtendedEpidGroupRequest&) = 0;

  virtual ~IAERequestVisitor() = 0;
};

class IAERequest{
    public:
  typedef  enum  {QUOTING_CLASS, LAUNCH_CLASS, PLATFORM_CLASS}  RequestClass;

        IAERequest(uint32_t timeout = IPC_LATENCY) : mTimeout(timeout), mValidSizeCheck(false) {} //constructor with default parameter
        virtual ~IAERequest() {}

        virtual void visit(IAERequestVisitor&) = 0;
        virtual AEMessage*  serialize(ISerializer* serializer) =0;

        virtual RequestClass getRequestClass() = 0;

        //this method is added especially for future compatibility (may be used to check thigs like the message MAC)
        virtual bool check() {return false;} //although only some requests will need more complex logic here, this will default to
        //invalid. Validity needs to be explicitly declared in children :)
        virtual IAEResponse* execute(IAESMLogic* aesmLogic) =0;

        //timeout 
        uint32_t GetTimeout() const { return mTimeout; }
    protected:
        uint32_t mTimeout;
        bool mValidSizeCheck;
};

#endif
