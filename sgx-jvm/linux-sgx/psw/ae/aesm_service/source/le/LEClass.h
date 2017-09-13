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

#ifndef _LE_CLASS_H_
#define _LE_CLASS_H_
#include "arch.h"
#include "AEClass.h"
#include "ae_debug_flag.hh"

class CLEClass: public SingletonEnclave<CLEClass>
{
    friend class Singleton<CLEClass>;
    friend class SingletonEnclave<CLEClass>;
    static aesm_enclave_id_t get_enclave_fid(){return LE_ENCLAVE_FID;}
protected:
    CLEClass(){};
    ~CLEClass(){};
    virtual int get_debug_flag() { return LE_DEBUG_FLAG;}
    void load_white_cert_list();
    ae_error_t load_enclave_only();/*protected function to load Enclave only without loading white list*/
    ae_error_t load_verified_white_cert_list();
    ae_error_t load_white_cert_list_to_be_verify();
    bool m_ufd; // if LEClass considers the platform ufd
public:
    virtual ae_error_t load_enclave();/*overload LE load enclave function since i) we have two different LE SigStruct now, ii) we need load white list*/
	int get_launch_token(
        uint8_t * mrenclave, uint32_t mrenclave_size,
        uint8_t *mrsigner, uint32_t mrsigner_size,
        uint8_t *se_attributes, uint32_t se_attributes_size,
        uint8_t * lictoken, uint32_t lictoken_size,
        uint32_t *ae_mrsigner_value=NULL
    );
    int white_list_register(
        const uint8_t *white_list_cert,
        uint32_t white_list_cert_size,
        bool save_to_persistent_storage=true);
    static ae_error_t update_white_list_by_url(void);
    bool is_ufd() { return m_ufd; }
};
#endif


