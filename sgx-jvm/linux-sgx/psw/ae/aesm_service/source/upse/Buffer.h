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

 
#ifndef _BUFFER_H_
#define _BUFFER_H_
#include <stdint.h>
#include <malloc.h>
#include <memory.h>
#include "aeerror.h"
#include "se_wrapper.h"

namespace upse
{
    class BufferWriter;

    class Buffer
    {
        friend class BufferWriter;

    public:
        Buffer() : buf(NULL), size(0)
        {}

        // Needed by list<>
        Buffer(const Buffer& buf_) : buf(NULL), size(0)
        {
            // No error is thrown; when it fails, size and buf will be set to 0 and nullptr
            Clone(buf_);
        }


        ~Buffer()
        {
            if (buf != NULL)
            {
                free(buf);
                buf = NULL;
                size = 0;
            }
        }

        ae_error_t Alloc(uint32_t size_)
        {
            uint8_t* p = NULL;
            if (0 != size_)
            {
                p = (uint8_t*)calloc(1, size_);
                if (NULL == p)
                    return AE_OUT_OF_MEMORY_ERROR;
            }

            if (NULL != buf)
                free(buf);

            buf = p;
            size = size_;
            return AE_SUCCESS;
        }

        ae_error_t Alloc(const uint8_t* buf_, uint32_t size_)
        {
            ae_error_t status = Alloc(size_);
            if (AE_SUCCEEDED(status) && size_ > 0)
                memcpy_s(buf, size, buf_, size_);

            return status;
        }

        ae_error_t Clone(const Buffer& buf_)
        {
            return Alloc(buf_.getData(), buf_.getSize());
        }

        void zeroMemory()
        {
            if (NULL != buf && size > 0)
                memset(buf, 0, size);
        }

        const uint8_t* getData() const
        {
            return buf;
        }

        uint32_t getSize() const
        {
            return size;
        }

        ae_error_t Not(Buffer& b) const
        {
            ae_error_t status = b.Alloc(size);
            if (AE_SUCCEEDED(status) && size > 0)
            {
                for (uint32_t i = 0; i < b.size; i++)
                {
                    b.buf[i] = (uint8_t)~buf[i];
                }
            }

            return status;
        }

    protected:
        uint8_t* buf;
        uint32_t size;

    private:

        Buffer& operator=(const Buffer &that);
        Buffer operator~() const;

    };

#if 0
    inline bool operator==(const Buffer &lhs, const Buffer &rhs)
    {
        if (lhs.getSize() != rhs.getSize())
        {
            return false;
        }
        int result = memcmp(lhs.getData(), rhs.getData(), lhs.getSize());
        return result == 0;
    }

    inline bool operator!=(const Buffer& lhs, const Buffer& rhs)
    {
        return !(lhs == rhs);
    }
#endif


    class BufferReader
    {
    public:
        BufferReader(const Buffer& buf_) 
            : buf(buf_.getData()), currentPos(0), size(buf_.getSize())
        {}

#if 0 // DEAD CODE -- disable, but keep
        BufferReader(const uint8_t* buf_, uint32_t size_) 
            : buf(buf_), currentPos(0), size(size_)
        {}

        BufferReader() : buf(NULL), currentPos(0), size(0)
        {}
#endif


        ae_error_t readRaw(const uint8_t** ptr)
        {
            return readRaw(size-currentPos, ptr);
        }

        ae_error_t readRaw(uint32_t numBytes, const uint8_t** ptr)
        {
            if (numBytes > size - currentPos)
                return AE_INSUFFICIENT_DATA_IN_BUFFER;
            if (NULL == ptr)
                return AE_INVALID_PARAMETER;
            *ptr = buf + currentPos;
            currentPos += numBytes;
            return AE_SUCCESS;
        }

        uint32_t getRemainingSize() const
        {
            return size - currentPos;
        }

#if 0 // DEAD CODE -- disable, but keep
        //Move current position by skipSize bytes
        ae_error_t skip(uint32_t skipSize)
        {
            if (skipSize > size - currentPos)
                return AE_INSUFFICIENT_DATA_IN_BUFFER;

            currentPos += skipSize;
            return AE_SUCCESS;
        }

        uint32_t getSize() const
        {
            return size;
        }

        uint32_t getOffset() const
        {
            return currentPos;
        }

        bool isDataAvailable() const
        {
            return currentPos != size;
        }
#endif

    protected:
        const uint8_t* buf;
        uint32_t currentPos;
        const uint32_t size;

    private:
        BufferReader& operator=(const BufferReader&);

    };


    class BufferWriter
    {
    public:
        BufferWriter(Buffer& buf_) 
            : buf(buf_.buf), currentPos(0), size(buf_.size)
        {}

#if 0 // DEAD CODE -- disable, but keep
        BufferWriter(uint8_t* buf_, uint32_t size_) 
            : buf(buf_), currentPos(0), size(size_)
        {}

        BufferWriter(const BufferWriter& buf_) : buf(buf_.buf), currentPos(buf_.currentPos), size(buf_.size)
        {}
#endif

        // writes specified number of bytes to the buffer at the current position
        // returns pointer to the position of the buffer at which write started
        ae_error_t writeRaw(const uint8_t* data_, uint32_t size_, uint8_t** startPtr = NULL)
        {
            if (size_ > (size - currentPos))
                return AE_INSUFFICIENT_DATA_IN_BUFFER;

            uint8_t* ptr = buf + currentPos;
            memcpy_s(ptr, (size-currentPos), data_, size_);
            currentPos += size_;

            if (NULL != startPtr)
                *startPtr = ptr;

            return AE_SUCCESS;
        }

#if 0 // DEAD CODE -- disable, but keep
        ae_error_t write(BufferReader& reader, uint32_t numBytes)
        {
            const uint8_t* p;
            ae_error_t status = reader.readRaw(&p);
            if (AE_FAILED(status))
                return status;
            return writeRaw(p, numBytes);
        }
#endif

        ae_error_t reserve(uint32_t size_, uint8_t** ptr)
        {
            if (NULL == ptr)
                return AE_FAILURE;

            if (size_ > (size - currentPos))
                return AE_INSUFFICIENT_DATA_IN_BUFFER;

            *ptr = buf + currentPos;
            currentPos += size_;
            return AE_SUCCESS;
        }


    protected:
        uint8_t* buf;
        uint32_t currentPos;
        const uint32_t size;

    private:
        BufferWriter& operator=(const BufferWriter&);
        BufferWriter& operator<< (BufferReader& reader);
        BufferWriter& operator<< (const BufferReader& reader);
        BufferWriter& operator<< (const Buffer& buffer);

    };
}
#endif

