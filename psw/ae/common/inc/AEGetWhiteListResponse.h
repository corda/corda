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
#ifndef __AE_GET_WHITE_LIST_RESPONSE_H
#define __AE_GET_WHITE_LIST_RESPONSE_H

#include <IAEResponse.h>
#include <stdint.h>

class ISerializer;

class AEGetWhiteListResponse : public IAEResponse
{
    public:
        AEGetWhiteListResponse();  //default ... will prepare a response that will later be inflated

        AEGetWhiteListResponse(int errorCode, uint32_t whiteListLength, const uint8_t * whiteList);
        AEGetWhiteListResponse(const AEGetWhiteListResponse& other);

        ~AEGetWhiteListResponse();

        //inflater
        bool inflateWithMessage(AEMessage* message, ISerializer* serializer);

        //getters
        inline uint32_t       GetWhiteListLength() const { return mWhiteListLength; }
        inline const uint8_t* GetWhiteList()       const { return mWhiteList; }

        AEMessage*  serialize(ISerializer* serializer);

        //this is used to inflate values from a serializer, instead of creating the object directly
        void inflateValues(int errorCode, uint32_t whiteListLength, const uint8_t * whiteList);

        //operators
        virtual bool operator==(const AEGetWhiteListResponse &other) const;
        AEGetWhiteListResponse& operator=(const AEGetWhiteListResponse &other);

        void visit(IAEResponseVisitor& visitor);

        //checks
        bool check();

    protected:
        void ReleaseMemory();
        void CopyFields(int errorCode, uint32_t whiteListLength, const uint8_t * whiteList);

        uint32_t mWhiteListLength;
        uint8_t * mWhiteList;
};

#endif
