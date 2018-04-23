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

#ifndef _WL_PUB_HH_
#define _WL_PUB_HH_
const sgx_ec256_public_t g_wl_root_pubkey =
{
    {
        0x29, 0x39, 0x1e, 0x9b, 0xcb, 0x86, 0xd6, 0xeb,
        0x3c, 0x17, 0x91, 0xc8, 0x8f, 0xc9, 0x5f, 0x8c,
        0xee, 0x0c, 0x1c, 0x75, 0x60, 0x9c, 0x16, 0xc2,
        0x18, 0x6d, 0x67, 0x31, 0x45, 0x5c, 0x36, 0xa9
    },
    {
        0x5f, 0x09, 0x83, 0x0d, 0xe1, 0x22, 0xda, 0xe4,
        0xed, 0x97, 0x54, 0xe6, 0xfe, 0xe2, 0xcc, 0x93,
        0x5e, 0x05, 0x99, 0x84, 0xc9, 0x4f, 0x44, 0x24,
        0x7a, 0x28, 0xcf, 0x81, 0xca, 0x11, 0x7e, 0xb6
    }
};
#endif

