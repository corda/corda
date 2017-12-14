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
#ifndef __AE_MESSAGE_H__
#define __AE_MESSAGE_H__

#include <string.h>
#include <stdint.h>

#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wshadow"
#pragma GCC diagnostic ignored "-Wconversion"
#include "messages.pb.h"
#pragma GCC diagnostic pop

struct AEMessage{
    uint32_t    size;
    char*       data;

#ifdef __cplusplus
    AEMessage(): size(0), data(NULL) {}
    ~AEMessage(){
        if (data != NULL) delete [] data;
        data = NULL;
        size = 0;
    }

    bool operator==(const AEMessage &other) const
    {
        if (this == &other) return true;

        if (size != other.size)                  return false;
        if (memcmp(data, other.data, size) != 0) return false;
        return true;
    }

    void copyFields(const AEMessage& other)
    {
        size = other.size;

        if (other.data == NULL)
            data = NULL;
        else if (size != 0)
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

#endif
