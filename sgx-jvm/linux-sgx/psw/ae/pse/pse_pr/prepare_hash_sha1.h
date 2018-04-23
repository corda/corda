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


#ifndef _PREPARE_HASH_SHA1_H_
#define _PREPARE_HASH_SHA1_H_

#include <stdint.h>
#include <stddef.h>


typedef uint32_t    SHA1_HASH[5];

#define SHA1_HASH_BITS  (160)
#define SHA1_HASH_LEN   (SHA1_HASH_BITS / 8)


class PrepareHashSHA1
{
public:
    PrepareHashSHA1();
    ~PrepareHashSHA1(void);

    // Include pData in the computed HASH
    bool Update(const void* pData, size_t numBytes);

	// pHash will contain the computed HASH if successful
	bool Finalize(SHA1_HASH *pHash);

private:
	bool m_status;

    void *m_pCtx;

	// Disable class operations (default constructor, copy constructor, assignment operator, and address-of operator)
	//PrepareHashSHA1();									// default constructor
	PrepareHashSHA1(const PrepareHashSHA1& rhs);			// copy constructor
	PrepareHashSHA1& operator=(const PrepareHashSHA1& rhs); // assignment operator
	PrepareHashSHA1* operator&();							// address-of operator
	const PrepareHashSHA1* operator&() const;				// address-of operator

};

#endif
