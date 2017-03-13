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
#ifndef __AE_GET_LICENSE_TOKEN_H
#define __AE_GET_LICENSE_TOKEN_H

#include <IAERequest.h>
#include <stdint.h>

class IAESMLogic;

class AEGetLaunchTokenRequest : public IAERequest
{
    public:
        AEGetLaunchTokenRequest();
        AEGetLaunchTokenRequest(uint32_t measurementLength, const uint8_t* measurement,
                uint32_t sigstructLength, const uint8_t* sigstruct,
                uint32_t attributesLength, const uint8_t* attributes, uint32_t timeout = 0);
        AEGetLaunchTokenRequest(const AEGetLaunchTokenRequest& other);

        ~AEGetLaunchTokenRequest();


        AEMessage* serialize(ISerializer* serializer);
        void inflateValues(uint32_t measurementLength,const uint8_t* measurement,
                uint32_t sigstructLength,const uint8_t* sigstruct,
                uint32_t attributesLength,const uint8_t* attributes,
                uint32_t timeout = 0);

        //inlines
        uint32_t        GetMeasurementLength() const { return mEnclaveMeasurementLength; }
        const uint8_t*  GetMeasurement()       const { return mEnclaveMeasurement; }
        uint32_t        GetSigstructLength()   const { return mSigstructLength; }
        const uint8_t*  GetSigstruct()         const { return mSigstruct; }
        uint32_t        GetAttributesLength()  const { return mSEAttributesLength; }
        const uint8_t*  GetAttributes()        const { return mSEAttributes; }

        //operators
        bool operator==( const AEGetLaunchTokenRequest& other) const;
        AEGetLaunchTokenRequest& operator=(const AEGetLaunchTokenRequest& other);

        //checks
        bool check();
        virtual IAEResponse* execute(IAESMLogic*);

        void visit(IAERequestVisitor& visitor);

        //used to determin in which queue to be placed
        virtual RequestClass getRequestClass();
    protected:
        //release all members
        void ReleaseMemory();
        void CopyFields(uint32_t measurementLength,const uint8_t* measurement,
                uint32_t sigstructLength,const uint8_t* sigstruct,
                uint32_t attributesLength,const uint8_t* attributes,
                uint32_t timeout);

        uint32_t    mEnclaveMeasurementLength;
        uint8_t*    mEnclaveMeasurement;

        uint32_t    mSigstructLength;
        uint8_t*    mSigstruct;

        uint32_t    mSEAttributesLength;
        uint8_t*    mSEAttributes;
};

#endif
