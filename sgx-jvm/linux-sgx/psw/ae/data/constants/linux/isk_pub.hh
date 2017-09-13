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

#ifndef _ISK_PUB_HH_
#define _ISK_PUB_HH_
/* This is the x component of production public key used for EC-DSA verify for EPID Signing key. */
const uint8_t g_sgx_isk_pubkey[] = {
    0x26, 0x9c, 0x10, 0x82, 0xe3, 0x5a, 0x78, 0x26,
    0xee, 0x2e, 0xcc, 0x0d, 0x29, 0x50, 0xc9, 0xa4,
    0x7a, 0x21, 0xdb, 0xcf, 0xa7, 0x6a, 0x95, 0x92,
    0xeb, 0x2f, 0xb9, 0x24, 0x89, 0x88, 0xbd, 0xce,
    0xb8, 0xe0, 0xf2, 0x41, 0xc3, 0xe5, 0x35, 0x52,
    0xbc, 0xef, 0x9c, 0x04, 0x02, 0x06, 0x48, 0xa5,
    0x76, 0x10, 0x1b, 0xa4, 0x28, 0xe4, 0x8e, 0xa9,
    0xcf, 0xba, 0x41, 0x75, 0xdf, 0x06, 0x50, 0x62
};
#endif

