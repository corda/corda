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


#ifndef _SIGMA_HELPER_H_
#define _SIGMA_HELPER_H_

#include <stdint.h>
#include <list>
#include "aeerror.h"
#include "pse_pr_sigma_1_1_defs.h"
#include "pse_pr_sigma_common_defs.h"

typedef uint32_t SAFEID_GID;


namespace upse
{
    class Buffer;
};

class SigmaHelper
{

public:

    static ae_error_t SetGID(upse::Buffer& gid);

	static ae_error_t GetRLsFromServer
		(   /*out*/ upse::Buffer& sigRlOut,
			/*out*/ upse::Buffer& privRlOut
		);

    static ae_error_t GetOcspResponseFromServer
        (
            /*in */ const std::list<upse::Buffer>& certChain,
            /*in */ const OCSP_REQ& ocspReq,
            /*out*/ upse::Buffer& ocspResp
        );

private:

    static void GetRootCA(upse::Buffer& b);

    static upse::Buffer m_gid;

    // Disable class operations (default constructor, copy constructor, assignment operator, and address-of operator)
    SigmaHelper(void);                                // default constructor
    SigmaHelper(const SigmaHelper& rhs);              // copy constructor
    SigmaHelper& operator=(const SigmaHelper& rhs);   // assignment operator
    SigmaHelper* operator&();                         // address-of operator
    const SigmaHelper* operator&() const;             // address-of operator

};

#endif
