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
#ifndef _PEK_SK_PUB_HH_
#define _PEK_SK_PUB_HH_
const sgx_ec256_public_t g_pek_pub_key_little_endian =
{
    {
        0xd3, 0x43, 0x31, 0x3b, 0xf7, 0x3a, 0x1b, 0xa1,
        0xca, 0x47, 0xb2, 0xab, 0xb2, 0xa1, 0x43, 0x4d,
        0x1a, 0xcd, 0x4b, 0xf5, 0x94, 0x77, 0xeb, 0x44,
        0x5a, 0x06, 0x2d, 0x13, 0x4b, 0xe1, 0xc5, 0xa0
    },
    {
        0x33, 0xf8, 0x41, 0x6b, 0xb2, 0x39, 0x45, 0xcc,
        0x8d, 0xcd, 0x81, 0xb4, 0x80, 0xb4, 0xbd, 0x82,
        0x11, 0xf2, 0xdc, 0x9c, 0x0b, 0x4c, 0x8a, 0x28,
        0xb0, 0xaa, 0xca, 0x65, 0x14, 0xd2, 0xc8, 0x62
    }
};
#endif

