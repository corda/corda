/* Copyright © 2012, Intel Corporation.  All rights reserved. 
 
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
 
-       Redistributions of source code must retain the above copyright notice,
		this list of conditions and the following disclaimer.
-       Redistributions in binary form must reproduce the above copyright 
		notice, this list of conditions and the following disclaimer in the
		documentation and/or other materials provided with the distribution.
-       Neither the name of Intel Corporation nor the names of its contributors
		may be used to endorse or promote products derived from this software
		without specific prior written permission.
 
THIS SOFTWARE IS PROVIDED BY INTEL CORPORATION "AS IS" AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL INTEL CORPORATION BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE. */

/*! \file rdrand.h
 *  \brief Public header for rdrand API.
 *  
 * This is the public header for the rdrand API. It exposes the three public 
 * APIs, which access the rdrand instruction for various data sizes.
 */

#ifndef RDRAND_H
#define RDRAND_H

#include "config.h"

#if HAVE_INTTYPES_H
#include <inttypes.h>
#endif

#ifdef _MSC_VER
	/* MSVC specific */
	typedef unsigned __int16 uint16_t;
	typedef unsigned __int32 uint32_t;
	typedef unsigned __int64 uint64_t;
#endif

/*! \def RDRAND_SUCCESS
 *   The rdrand call was successful, the hardware was ready, and a random 
 *   number was returned.
 */
#define RDRAND_SUCCESS 1

/*! \def RDRAND_NOT_READY
 *  The rdrand call was unsuccessful, the hardware was not ready, and a
 *  random number was not returned. 
 */
#define RDRAND_NOT_READY -1

/*! \def RDRAND_SUPPORTED
 * The rdrand instruction is supported by the host hardware.
 */
#define RDRAND_SUPPORTED -2

/*! \def RDRAND_UNSUPPORTED
 * The rdrand instruction is unsupported by the host hardware.
 */
#define RDRAND_UNSUPPORTED -3

/*! \def RDRAND_SUPPORT_UNKNOWN 
 * Whether or not the hardware supports the rdrand instruction is unknown
 */
#define RDRAND_SUPPORT_UNKNOWN -4

/*! \brief Calls rdrand for a 16-bit result.
 *
 * This function calls rdrand requesting a 16-bit result. By default, it will
 * perform only a single call to rdrand, returning success or failure. On 
 * success, the data is written to memory pointed to by x. If the int retry is
 * true (non-zero), the function will enter a loop with count=10 until rdrand succeeds, at  
 * which point it write the random data and return success, or fails This    
 * function also ensures that rdrand is supported by the cpu or fails 
 * gracefully.
 * 
 * \param x pointer to memory to store the random result
 * \param retry int to determine whether or not to loop until rdrand succeeds
 *		  or until 10 failed attempts
 * 
 * \return whether or not the call was successful, or supported at all
 */
int rdrand_16(uint16_t* x, int retry);

/*! \brief Calls rdrand for a 32-byte result.
 *
 * This function calls rdrand requesting a 32-bit result. By default, it will
 * perform only a single call to rdrand, returning success or failure. On 
 * success, the data is written to memory pointed to by x. If the int retry is
 * true (non-zero), the function will enter a loop with count=10 until rdrand succeeds, at  
 * which point it write the random data and return success, or fails This    
 * function also ensures that rdrand is supported by the cpu or fails 
 * gracefully.
 * 
 * \param x pointer to memory to store the random result
 * \param retry int to determine whether or not to loop until rdrand succeeds
 *		  or until 10 failed attempts
 * 
 * \return whether or not the call was successful, or supported at all
 */
int rdrand_32(uint32_t* x, int retry);

/*! \brief Calls rdrand for a 64-byte result.
 *
 * This function calls rdrand requesting a 64-byte result. By default, it will
 * perform only a single call to rdrand, returning success or failure. On 
 * success, the data is written to memory pointed to by x. If the int retry is
 * true (non-zero), the function will enter a loop with count=10 until rdrand succeeds, at  
 * which point it write the random data and return success, or fails This    
 * function also ensures that rdrand is supported by the cpu or fails 
 * gracefully.
 *
 * Calling rdrand_64 on a 32-bit system is inefficient as it makes two calls
 * to rdrand_32 to produce a single 64-bit value, using a shift to populate
 * the high bits. The physical construction of the DRNG allows you to do this
 * up to a 128-bit value while retaining multiplicative prediction resistance
 * (i.e., don't do this to generate numbers larger than 128 bits).
 * 
 * \param x pointer to memory to store the random result
 * \param retry int to determine whether or not to loop until rdrand succeeds
 *		  or until 10 failed attempts
 * 
 * \return whether or not the call was successful, or supported at all
 */
int rdrand_64(uint64_t* x, int retry);

/*! \brief Calls rdrand to obtain multiple 64-byte results.
 *
 * This function calls rdrand requesting multiple 64-byte results. On 
 * success, the data is written to memory pointed to by x. This function
 * calls rdrand_64 and if any of those invocations fail, this function
 * fails. It returns the same values as rdrand_64.
 * 
 * This function is inefficient on 32-bit systems.
 */
int rdrand_get_n_64(unsigned int n, uint64_t* x);

/*! \brief Calls rdrand to obtain multiple 32-byte results.
 *
 * This function calls rdrand requesting multiple 32-byte results. On 
 * success, the data is written to memory pointed to by x. This function
 * calls rdrand_32 and if any of those invocations fail, this function
 * fails. It returns the same values as rdrand_32.
 */
int rdrand_get_n_32(unsigned int n, uint32_t* x);

/*! \brief Calls rdrand to fill a buffer of arbitrary size with random bytes.
 *
 * This function calls rdrand requesting multiple 64- or 32-bit results to
 * fill a buffer of arbitrary size.
 *
 * \param n size of the buffer to fill with random bytes
 * \param buffer pointer to memory to store the random result
 * 
 * \return whether or not the call was successful, or supported at all
 */

int rdrand_get_bytes(unsigned int n, unsigned char *buffer);

#endif // RDRAND_H
