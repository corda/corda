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


/* deriv.h - it defines C++ interfaces for derivation data. */
#ifndef DERIVE_H__
#define DERIVE_H__

#include "arch.h"
#include "sgx_key.h"
#include "sgx_error.h"

#ifdef __cplusplus
extern "C" {
#endif

#define OWNEREPOCH_SIZE     16
typedef uint8_t se_owner_epoch_t[OWNEREPOCH_SIZE];

/* Derive data for seal key */
typedef struct {
    uint16_t          key_name;        /* should always be 'SGX_KEYSELECT_SEAL' */
    sgx_attributes_t  tmp_attr;
    sgx_attributes_t  attribute_mask;  /* attribute mask from KEYREQUEST */
    se_owner_epoch_t  csr_owner_epoch;
    sgx_cpu_svn_t     cpu_svn;         /* CPUSVN from KEYREQUEST */
    sgx_isv_svn_t     isv_svn;         /* ISVSVN from KEYREQUEST */
    sgx_prod_id_t     isv_prod_id;     /* ISV PRODID from SECS   */
    sgx_measurement_t mrenclave;
    sgx_measurement_t mrsigner;
    sgx_key_id_t      key_id;          /* KEYID from KEYREQUEST  */
} dd_seal_key_t;

/* Derive data for report key */
typedef struct {
    uint16_t          key_name;        /* should always be 'SGX_KEYSELECT_REPORT' */
    sgx_attributes_t  attributes;      /* attributes from SECS */
    se_owner_epoch_t  csr_owner_epoch;
    sgx_measurement_t mrenclave;
    sgx_cpu_svn_t     cpu_svn;         /* CPUSVN from CPUSVN register */
    sgx_key_id_t      key_id;          /* KEYID from KEYREQUEST */
} dd_report_key_t;

/* Derive data for license key */
typedef struct {
    uint16_t          key_name;        /* should always be 'SGX_KEYSELECT_EINITTOKEN' */
    sgx_attributes_t  attributes;      /* attributes from SECS */
    se_owner_epoch_t  csr_owner_epoch;
    sgx_cpu_svn_t     cpu_svn;         /* CPUSVN from KEYREQUEST */
    sgx_isv_svn_t     isv_svn;         /* ISVSVN from KEYREQUEST */
    sgx_prod_id_t     isv_prod_id;     /* ISV PRODID from SECS   */
    sgx_key_id_t      key_id;          /* KEYID from KEYREQUEST  */
} dd_license_key_t;

/* Derive data for provision key */
typedef struct {
    uint16_t          key_name;        /* should always be 'SGX_KEYSELECT_PROVISION' */
    sgx_attributes_t  tmp_attr;
    sgx_attributes_t  attribute_mask;  /* attribute mask from KEYREQUEST */
    sgx_cpu_svn_t     cpu_svn;         /* CPUSVN from KEYREQUEST */
    sgx_isv_svn_t     isv_svn;         /* ISVSVN from KEYREQUEST */
    sgx_prod_id_t     isv_prod_id;     /* ISV PRODID from SECS   */
    sgx_measurement_t mrsigner;
} dd_provision_key_t;

/* The derivation data. */
typedef struct {
    int size;    /* the size of derivation data */

    union {
        /* key_name is the first field of all the following derivation data */
        uint16_t            key_name;
        uint8_t             ddbuf[1];

        dd_seal_key_t       ddsk;
        dd_report_key_t     ddrk;
        dd_license_key_t    ddlk;
        dd_provision_key_t  ddpk;
    };
} derivation_data_t;

/**
 * Get the internal CPU keys.
 *
 * @param key_name - the key name
 * @return NULL for invalid key name.
 */
const uint8_t* get_base_key(uint16_t key_name);

/** The internal routine to derive requested key.  Parameter checking
 *  is done in the caller.
 *
 * @param dd   - the pointer to derive data
 * @param okey - the output derived key
 */
void derive_key(const derivation_data_t* dd, sgx_key_128bit_t okey);

/** Compute the CMAC of a buffer.
 * @param key     - the key used to compute the CMAC
 * @param buf     - the buf to be digested
 * @param buf_len - length of the buffer in Bytes
 * @param cmac    - the pointer to the output buffer to store CMAC
 */
void cmac(const sgx_key_128bit_t *key, const uint8_t* buf, int buf_len, sgx_mac_t* cmac);

#ifdef __cplusplus
}
#endif

#endif
