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
#ifndef _AE_CREATE_SESSION_RESPONSE_H
#define _AE_CREATE_SESSION_RESPONSE_H

#include <IAEResponse.h>
#include <stdint.h>

class AECreateSessionResponse : public IAEResponse
{
    public:
        AECreateSessionResponse();
        AECreateSessionResponse(int errorCode, uint32_t sessionId, uint32_t dhMsg1Length, const uint8_t* dhMsg1);
        AECreateSessionResponse(const AECreateSessionResponse& other);
        ~AECreateSessionResponse();

        AEMessage* serialize(ISerializer* serializer);
        bool inflateWithMessage(AEMessage* message, ISerializer* serializer);
        void inflateValues(int errorCode, uint32_t sessionId, uint32_t dhMsg1Length, const uint8_t* dhMsg1);

        inline int              GetSessionId()    const { return mSessionId; }
        inline uint32_t         GetDHMsg1Length() const { return mDHMsg1Length; }
        inline const uint8_t*   GetDHMsg1()       const { return mDHMsg1; }

        //operators
        bool operator==(const AECreateSessionResponse& other) const;
        AECreateSessionResponse& operator=(const AECreateSessionResponse& other);

        //checks
        bool check();
        virtual void visit(IAEResponseVisitor& visitor);

    protected:
        void ReleaseMemory();
        void CopyFields(int errorCode, uint32_t sessionId, uint32_t dhMsg1Length, const uint8_t* dhMsg1);

        uint32_t mSessionId;
        uint32_t mDHMsg1Length;
        uint8_t* mDHMsg1;
};

#endif
