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


/**
 * File: byte_order.h
 * Description: Header file to convert between host byte order and network byte order
 * The file assume the code is running in little endian machine
 */

#ifndef _BYTE_ORDER_H
#define _BYTE_ORDER_H

/*macro to get the kth byte of 32 bits integer x
 *k should be 0, 1, 2 or 3
 *k equal to 0 means to get the least significant byte
 */
#define GET_BYTE(x, k)  (((const unsigned char *)&(x))[k])

#define _htonl(x) ((uint32_t)(                                      \
        (((uint32_t)(x) & (uint32_t)0x000000ff) << 24) |            \
        (((uint32_t)(x) & (uint32_t)0x0000ff00) <<  8) |            \
        (((uint32_t)(x) & (uint32_t)0x00ff0000) >>  8) |            \
        (((uint32_t)(x) & (uint32_t)0xff000000) >> 24)))
#define _htons(x) ((uint16_t)(                                      \
        (((uint16_t)(x) & (uint16_t)0xff00) >> 8)  |                \
        (((uint16_t)(x) & (uint16_t)0x00ff) << 8)))

#define lv_htonl(x)  ( \
        (((uint32_t)GET_BYTE(x,0))<<24)| \
        (((uint32_t)GET_BYTE(x,1))<<16)| \
        (((uint32_t)GET_BYTE(x,2))<<8)| \
        (((uint32_t)GET_BYTE(x,3))) )
#define lv_htons(x) (\
        (uint16_t)((((uint16_t)GET_BYTE(x,0))<<8) |  \
        (((uint16_t)GET_BYTE(x,1))) ))

#define _ntohl(u32) _htonl(u32)
#define _ntohs(u16) _htons(u16)
#define lv_ntohl(u32) lv_htonl(u32)
#define lv_ntohs(u16) lv_htons(u16)

#endif /*_BYTE_ORDER_H*/

