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

#ifndef _LE2BE_MACROS_H_
#define _LE2BE_MACROS_H_
#ifdef __cplusplus
extern "C" {
#endif

/*
 *		Endianness translation macros used for SafeId serialization/deserialization
 */
// LE<->BE translation of DWORD
#define SwapEndian_DW(dw)	( (((dw) & 0x000000ff) << 24) |  (((dw) & 0x0000ff00) << 8) | (((dw) & 0x00ff0000) >> 8) | (((dw) & 0xff000000) >> 24) )
// LE<->BE translation of 8 byte big number
#define SwapEndian_8B(ptr)													\
{																			\
    unsigned int Temp = 0;													\
    unsigned int* p = reinterpret_cast<unsigned int*>(ptr);									\
    Temp = SwapEndian_DW(p[0]);						\
    p[0] = SwapEndian_DW(p[1]);	\
    p[1] = Temp;										\
}																			\
// LE<->BE translation of 10 byte (80 bit) big number
#define SwapEndian_10B(ptr)																									\
{																															\
    unsigned int Temp1 = 0;																									\
    unsigned int Temp2 = 0;																									\
    unsigned int* p = reinterpret_cast<unsigned int*>(ptr);									\
    Temp1 = SwapEndian_DW( (unsigned int)( (((unsigned char*)(ptr))[8] << 16 ) | ( ((unsigned char*)(ptr))[9] << 24) ) );	\
    Temp2 = SwapEndian_DW(p[1]);																		\
    *(reinterpret_cast<unsigned int*>((unsigned char*)(ptr) + 6)) = SwapEndian_DW(p[0]);								\
    *(reinterpret_cast<unsigned int*>((unsigned char*)(ptr) + 2)) = Temp2;																	\
    p[0] = (Temp1 & 0x0000ffff) | (p[0] & 0xffff0000);							\
}
// LE<->BE translation of 16 byte (128-bit) big number
#define SwapEndian_16B(ptr)													\
{																			\
    unsigned int Temp = 0;													\
    unsigned int* p = reinterpret_cast<unsigned int*>(ptr);									\
    Temp = SwapEndian_DW(p[0]);						\
    p[0] = SwapEndian_DW(p[3]);	\
    p[3] = Temp;										\
    Temp = SwapEndian_DW(p[1]);						\
    p[1] = SwapEndian_DW(p[2]);	\
    p[2] = Temp;										\
}				
// LE<->BE translation of 32 byte big number
#define SwapEndian_32B(ptr)													\
{																			\
    unsigned int Temp = 0;													\
    unsigned int* p = reinterpret_cast<unsigned int*>(ptr);									\
    Temp = SwapEndian_DW(p[0]);						\
    p[0] = SwapEndian_DW(p[7]);	\
    p[7] = Temp;										\
    Temp = SwapEndian_DW(p[1]);						\
    p[1] = SwapEndian_DW(p[6]);	\
    p[6] = Temp;										\
    Temp = SwapEndian_DW(p[2]);						\
    p[2] = SwapEndian_DW(p[5]);	\
    p[5] = Temp;										\
    Temp = SwapEndian_DW(p[3]);						\
    p[3] = SwapEndian_DW(p[4]);	\
    p[4] = Temp;										\
}
// LE<->BE translation of 64 byte big number
#define SwapEndian_64B(ptr)													\
{																			\
    unsigned int Temp = 0;													\
    unsigned int* p = reinterpret_cast<unsigned int*>(ptr);									\
    Temp = SwapEndian_DW(p[0]);						\
    p[0] = SwapEndian_DW(p[15]);	\
    p[15] = Temp;										\
    Temp = SwapEndian_DW(p[1]);						\
    p[1] = SwapEndian_DW(p[14]);	\
    p[14] = Temp;										\
    Temp = SwapEndian_DW(p[2]);						\
    p[2] = SwapEndian_DW(p[13]);	\
    p[13] = Temp;										\
    Temp = SwapEndian_DW(p[3]);						\
    p[3] = SwapEndian_DW(p[12]);	\
    p[12] = Temp;										\
    Temp = SwapEndian_DW(p[4]);						\
    p[4] = SwapEndian_DW(p[11]);	\
    p[11] = Temp;										\
    Temp = SwapEndian_DW(p[5]);						\
    p[5] = SwapEndian_DW(p[10]);	\
    p[10] = Temp;										\
    Temp = SwapEndian_DW(p[6]);						\
    p[6] = SwapEndian_DW(p[9]);	\
    p[9] = Temp;										\
    Temp = SwapEndian_DW(p[7]);						\
    p[7] = SwapEndian_DW(p[8]);	\
    p[8] = Temp;										\
}
// LE<->BE translation of 75 byte (600 bit) big number
#define SwapEndian_75B(ptr)																																				\
{																																										\
    unsigned int Temp1 = 0;																																				\
    unsigned int Temp2  = 0;																																			\
    unsigned char i = 0;																																				\
    unsigned int* p = reinterpret_cast<unsigned int*>(ptr);									\
    Temp1 = SwapEndian_DW( (unsigned int)( ( ((unsigned char*)(ptr))[72] << 8 ) | ( ((unsigned char*)(ptr))[73] << 16 ) | ( ((unsigned char*)(ptr))[74] << 24 ) ) );	\
    Temp2 = (p[17] & 0xff000000);																													\
    *((unsigned int*)((unsigned char*)(ptr) + 71)) = SwapEndian_DW(p[0]);																			\
    p[0] = Temp1;																																	\
    Temp1 = SwapEndian_DW(p[1]);																													\
    *((unsigned int*)((unsigned char*)(ptr) + 3 )) = SwapEndian_DW( ( (p[17] & 0x00ffffff) | Temp2 ) );											\
    for(i = 0; i < 8; i++) {																																			\
        Temp2 = (p[16 - i] & 0xff000000);																											\
        *((unsigned int*)((unsigned char*)(ptr) + 67 - 4*i)) = Temp1;																									\
        Temp1 = SwapEndian_DW(p[2 + i]);																											\
        *((unsigned int*)((unsigned char*)(ptr) + 7 + 4*i)) = SwapEndian_DW( ( (p[16 - i] & 0x00ffffff) | Temp2 ) );								\
    }																																									\
}

#if defined(DEFINE_GPOINTS_IN_LE2BE_MACROS)

/*
 *	Some structures useful during the conversion of SafeId data
 */

typedef struct _G1Point {
    unsigned char x[32];
    unsigned char y[32];
} G1Point;

typedef struct _G2Point {
    unsigned char x0[32];
    unsigned char x1[32];
    unsigned char x2[32];
    unsigned char y0[32];
    unsigned char y1[32];
    unsigned char y2[32];
} G2Point;

typedef G1Point G3Point;

typedef struct _GTPoint {
    unsigned char x0[32];
    unsigned char x1[32];
    unsigned char x2[32];
    unsigned char x3[32];
    unsigned char x4[32];
    unsigned char x5[32];
} GTPoint;

#endif

#ifdef __cplusplus
}
#endif

#endif

