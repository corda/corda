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

#ifndef _PSE_PR_HELPER_H_
#define _PSE_PR_HELPER_H_

#include "aeerror.h"
#include "oal/oal.h"
#include "pairing_blob.h"
#include "epid/common/types.h"
#include <list>

namespace upse
{
    class Buffer;
};

class Helper
{
public:

    static bool noPseCert();
    static bool noLtpBlob();

    static ae_error_t read_ltp_blob(pairing_blob_t& pairing_blob);
    static ae_error_t read_ltp_blob(upse::Buffer& pairing_blob);

    static ae_error_t write_ltp_blob(upse::Buffer& pairing_blob);

    static ae_error_t delete_ltp_blob();

    static ae_error_t read_ocsp_response_vlr(upse::Buffer& ocsp_response_vlr);
    static ae_error_t write_ocsp_response_vlr(upse::Buffer& ocsp_response_vlr);
    static ae_error_t delete_ocsp_response_vlr();

    static uint32_t ltpBlobPsdaSvn(const pairing_blob_t& pairing_blob);
    static uint32_t ltpBlobCseGid(const pairing_blob_t& pairing_blob);

    static ae_error_t RemoveCertificateChain();

    static ae_error_t SaveCertificateChain
        (
        /*in*/ std::list<upse::Buffer>& certChain
        );

    static ae_error_t LoadCertificateChain
        (
        /*out*/ std::list<upse::Buffer>& certChain
        );

    static ae_error_t PrepareCertificateChainVLR
        ( 
        /*in*/ std::list<upse::Buffer>& certChain, 
        /*out*/ upse::Buffer& certChainVLR
        );

};


class upsePersistentStorage
{
public:

    static ae_error_t Delete(aesm_data_id_t data_id);

    static ae_error_t Read(aesm_data_id_t data_id, upse::Buffer& data);
    static ae_error_t Write(aesm_data_id_t data_id, upse::Buffer& data);

private:

    // Disable class operations (default constructor, copy constructor, assignment operator, and address-of operator)
    upsePersistentStorage(void);                                            // default constructor
    upsePersistentStorage(const upsePersistentStorage& rhs);                // copy constructor
    upsePersistentStorage& operator=(const upsePersistentStorage& rhs);     // assignment operator
    upsePersistentStorage* operator&();                                     // address-of operator
    const upsePersistentStorage* operator&() const;                         // address-of operator

};

#endif

