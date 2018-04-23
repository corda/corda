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

#ifndef __X509Parser_H__
#define __X509Parser_H__

#include <stdint.h>
#include "epid/common/types.h"
#include "epid/common/1.1/types.h"
#include "pse_pr_types.h"
#include "pse_pr_sigma_common_defs.h"


class X509Parser
{
public:
    ~X509Parser(void);

    static UINT32 ParseGroupCertificate
    (
        /*in */ const EcDsaPubKey* pSerializedPublicKey,
        /*in */ const X509_GROUP_CERTIFICATE_VLR* pGroupCertVlr, 
        /*out*/ UINT32* pGID, 
        /*out*/ Epid11GroupPubKey* pGroupPubKey 
    );

private:

    // Disable class operations (default constructor, copy constructor, assignment operator, and address-of operator)
    X509Parser();									// default constructor
    X509Parser(const X509Parser& rhs);			    // copy constructor
    X509Parser& operator=(const X509Parser& rhs);   // assignment operator
    X509Parser* operator&();						// address-of operator
    const X509Parser* operator&() const;			// address-of operator

};

#endif

