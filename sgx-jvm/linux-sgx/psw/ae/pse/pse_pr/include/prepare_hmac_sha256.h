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


#ifndef _PREPARE_HMAC_SHA256_H_
#define _PREPARE_HMAC_SHA256_H_

#include "pse_pr_inc.h"
#include "pse_pr_types.h"
#include "ae_ipp.h"


class PrepareHMACSHA256
{
public:
    PrepareHMACSHA256(const Ipp8u* key, size_t keyLength);
    ~PrepareHMACSHA256(void);

    // Include pData in the computed HMAC
    ae_error_t Update(const void* pData, size_t numBytes);

	// pHMAC will contain the computed HMAC if successful
	ae_error_t Finalize(SIGMA_HMAC *pHMAC);

private:
    IppStatus m_ippstatus;

    IppsHMACState *m_pCtx;

	// Disable class operations (default constructor, copy constructor, assignment operator, and address-of operator)
	PrepareHMACSHA256();										// default constructor
	PrepareHMACSHA256(const PrepareHMACSHA256& rhs);			// copy constructor
	PrepareHMACSHA256& operator=(const PrepareHMACSHA256& rhs); // assignment operator
	PrepareHMACSHA256* operator&();							    // address-of operator
	const PrepareHMACSHA256* operator&() const;				    // address-of operator

};

#endif
