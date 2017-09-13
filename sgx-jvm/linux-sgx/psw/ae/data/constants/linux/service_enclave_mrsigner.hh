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

#ifndef _SERVICE_ENCLAVE_MRSIGNER_HH_
#define _SERVICE_ENCLAVE_MRSIGNER_HH_
/* hard-coded mrsigner is SHA256 of public key of production signing key*/
const sgx_measurement_t G_SERVICE_ENCLAVE_MRSIGNER[] =
{
    {
        {//MR_SIGNER of PvE provided
            0xec, 0x15, 0xb1, 0x07, 0x87, 0xd2, 0xf8, 0x46,
            0x67, 0xce, 0xb0, 0xb5, 0x98, 0xff, 0xc4, 0x4a,
            0x1f, 0x1c, 0xb8, 0x0f, 0x67, 0x0a, 0xae, 0x5d,
            0xf9, 0xe8, 0xfa, 0x9f, 0x63, 0x76, 0xe1, 0xf8
        }
    },
    {
        {//MR_SIGNER of PCE provided
            0xC5, 0x4A, 0x62, 0xF2, 0xBE, 0x9E, 0xF7, 0x6E,
            0xFB, 0x1F, 0x39, 0x30, 0xAD, 0x81, 0xEA, 0x7F,
            0x60, 0xDE, 0xFC, 0x1F, 0x5F, 0x25, 0xE0, 0x9B,
            0x7C, 0x06, 0x7A, 0x81, 0x5A, 0xE0, 0xC6, 0xCB
        }
    }
};

#define AE_MR_SIGNER  0
#define PCE_MR_SIGNER 1
#endif
