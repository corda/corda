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

#ifndef __UECALL_BRIDGE_H__
#define __UECALL_BRIDGE_H__

#include "aeerror.h"
#include "sgx_eid.h"

namespace upse
{
    class Buffer;
};


void SaveEnclaveID(sgx_enclave_id_t eid);


#if 0
ae_error_t tTempSetPrivKey
(
    /*in */ upse::Buffer& privateKey,
    /*out*/ upse::Buffer& pairingBlob
);
#endif

ae_error_t tPrepareForCertificateProvisioning
(
    /*in */ upse::Buffer& nonce,
    /*in */ upse::Buffer& targetInfo,
    /*out*/ upse::Buffer& csrPse,
    /*out*/ upse::Buffer& report,
    /*out*/ upse::Buffer& pairingBlob
);

#if defined(NO_PROVISIONING_SERVER)
ae_error_t tPrepareForCertificateProvisioning_hardcoded_privatekey
(

    /*out*/ upse::Buffer& pairingBlob
);
#endif

ae_error_t tGenM7
(
    /*in */ upse::Buffer& s1,
    /*in */ upse::Buffer& sigRL, 
    /*in */ upse::Buffer& ocspResp, 
    /*in */ upse::Buffer& verifierCert,
    /*in */ upse::Buffer& pairingBlob,
    /*out*/ upse::Buffer& s2 
);

ae_error_t tVerifyM8
(
    /*in */ upse::Buffer& s3, 
    /*in */ upse::Buffer& privRL,
    /*i/o*/ upse::Buffer& pairingBlob,
    /*out*/ bool& new_pairing
);


#endif
