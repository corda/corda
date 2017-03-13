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

#include <stdio.h>
#include <string.h>

#include "rdrand.h"

#define BUFFSIZE 1275

int main()
{
	int r;
	uint16_t u16;
	uint32_t u32;
	uint64_t u64;
	uint32_t array32[10];
	uint64_t array64[10];
	unsigned char buffer[BUFFSIZE];

	r = rdrand_16(&u16, 10);
	if (r != RDRAND_SUCCESS ) printf("rdrand instruction failed with code %d\n", r);

	r = rdrand_32(&u32, 10);
	if (r != RDRAND_SUCCESS ) printf("rdrand instruction failed with code %d\n", r);

	r = rdrand_64(&u64, 10);
	if (r != RDRAND_SUCCESS ) printf("rdrand instruction failed with code %d\n", r);

	printf("uint16: %u\n", u16);
	printf("uint32: %u\n", u32);	
	printf("uint64: %llu\n", (unsigned long long) u64);

	r = rdrand_get_n_32(10, array32);
	if ( r == RDRAND_SUCCESS ) {
		int i;
		printf("\n10 uint32's:\n");
		for (i= 0; i< 10; ++i) {
			printf("%u\n", array32[i]);
		}
	} else printf("rdrand instruction failed with code %d\n", r);

	r = rdrand_get_n_64(10, array64);
	if ( r == RDRAND_SUCCESS ) {
		int i;
		printf("\n10 uint64's:\n");
		for (i= 0; i< 10; ++i) {
			printf("%llu\n", (unsigned long long) array64[i]);
		}
	} else printf("rdrand instruction failed with code %d\n", r);

	memset(buffer, 0, BUFFSIZE);
	r = rdrand_get_bytes(BUFFSIZE, buffer);
	if ( r == RDRAND_SUCCESS ) {
		int i, j;
		printf("\nBuffer of %ld bytes:\n", (long) BUFFSIZE);

		j= 0;
		for (i= 0; i< BUFFSIZE; ++i)
		{
			printf("%02x ", buffer[i]);

			++j;

			if ( j == 16 ) {
				j= 0;
				printf("\n");
			} else if ( j == 8 ) printf(" ");

		}
		printf("\n");
	} else printf("rdrand instruction failed with code %d\n", r);
} 
