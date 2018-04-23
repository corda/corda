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

#ifndef _PSE_PR_INTERFACE_PSDA_H_
#define _PSE_PR_INTERFACE_PSDA_H_

#include <stdint.h>
#include "aeerror.h"
#include "pse_pr_sigma_1_1_defs.h"

namespace upse { class Buffer; }

class pse_pr_interface_psda
{
public:
    pse_pr_interface_psda(void);
    ~pse_pr_interface_psda(void);

    ae_error_t GetS1
        (   /*in*/  const uint8_t* pse_instance_id,
            /*out*/ upse::Buffer& s1
        );

    ae_error_t ExchangeS2AndS3
        (   /*in*/  const uint8_t* pse_instance_id,
            /*in */ const upse::Buffer& s2, 
            /*out*/ upse::Buffer& s3
        );

    ae_error_t get_csme_gid(
            /*out*/ EPID_GID* p_cse_gid
        );

private:

    // Disable class operations (default constructor, copy constructor, assignment operator, and address-of operator)
    //pse_pr_cse_interface();										        // default constructor
    pse_pr_interface_psda(const pse_pr_interface_psda& rhs);			    // copy constructor
    pse_pr_interface_psda& operator=(const pse_pr_interface_psda& rhs);   // address-of operator
    const pse_pr_interface_psda* operator&() const;				        // address-of operator

};

#endif
