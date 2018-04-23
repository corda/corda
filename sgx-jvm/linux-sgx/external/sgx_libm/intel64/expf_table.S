/*
* Math library
*
* Copyright (C) 2016 Intel Corporation. All rights reserved.
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
*
* Author Name <jingwei.zhang@intel.com>
*   History:
*   03-14-2016 Initial version. numerics svn rev. 12864
*/
	.file "expf_table.c"
	.section .rodata, "a"
	.align 8
	.align 8
	.globl __libm_expf_table_64
__libm_expf_table_64:
	.long	0x667f3bcd,0x3fe6a09e
	.long	0x3c651a2f,0x3fe6dfb2
	.long	0xe8ec5f74,0x3fe71f75
	.long	0x564267c9,0x3fe75feb
	.long	0x73eb0187,0x3fe7a114
	.long	0x36cf4e62,0x3fe7e2f3
	.long	0x994cce13,0x3fe82589
	.long	0x9b4492ed,0x3fe868d9
	.long	0x422aa0db,0x3fe8ace5
	.long	0x99157736,0x3fe8f1ae
	.long	0xb0cdc5e5,0x3fe93737
	.long	0x9fde4e50,0x3fe97d82
	.long	0x82a3f090,0x3fe9c491
	.long	0x7b5de565,0x3fea0c66
	.long	0xb23e255d,0x3fea5503
	.long	0x5579fdbf,0x3fea9e6b
	.long	0x995ad3ad,0x3feae89f
	.long	0xb84f15fb,0x3feb33a2
	.long	0xf2fb5e47,0x3feb7f76
	.long	0x904bc1d2,0x3febcc1e
	.long	0xdd85529c,0x3fec199b
	.long	0x2e57d14b,0x3fec67f1
	.long	0xdcef9069,0x3fecb720
	.long	0x4a07897c,0x3fed072d
	.long	0xdcfba487,0x3fed5818
	.long	0x03db3285,0x3feda9e6
	.long	0x337b9b5f,0x3fedfc97
	.long	0xe78b3ff6,0x3fee502e
	.long	0xa2a490da,0x3feea4af
	.long	0xee615a27,0x3feefa1b
	.long	0x5b6e4540,0x3fef5076
	.long	0x819e90d8,0x3fefa7c1
	.long	0x00000000,0x3ff00000
	.long	0x3e778061,0x3ff02c9a
	.long	0xd3158574,0x3ff059b0
	.long	0x18759bc8,0x3ff08745
	.long	0x6cf9890f,0x3ff0b558
	.long	0x32d3d1a2,0x3ff0e3ec
	.long	0xd0125b51,0x3ff11301
	.long	0xaea92de0,0x3ff1429a
	.long	0x3c7d517b,0x3ff172b8
	.long	0xeb6fcb75,0x3ff1a35b
	.long	0x3168b9aa,0x3ff1d487
	.long	0x88628cd6,0x3ff2063b
	.long	0x6e756238,0x3ff2387a
	.long	0x65e27cdd,0x3ff26b45
	.long	0xf51fdee1,0x3ff29e9d
	.long	0xa6e4030b,0x3ff2d285
	.long	0x0a31b715,0x3ff306fe
	.long	0xb26416ff,0x3ff33c08
	.long	0x373aa9cb,0x3ff371a7
	.long	0x34e59ff7,0x3ff3a7db
	.long	0x4c123422,0x3ff3dea6
	.long	0x21f72e2a,0x3ff4160a
	.long	0x6061892d,0x3ff44e08
	.long	0xb5c13cd0,0x3ff486a2
	.long	0xd5362a27,0x3ff4bfda
	.long	0x769d2ca7,0x3ff4f9b2
	.long	0x569d4f82,0x3ff5342b
	.long	0x36b527da,0x3ff56f47
	.long	0xdd485429,0x3ff5ab07
	.long	0x15ad2148,0x3ff5e76f
	.long	0xb03a5585,0x3ff6247e
	.long	0x82552225,0x3ff66238
	.long	0x667f3bcd,0x3ff6a09e
	.type	__libm_expf_table_64,@object
	.size	__libm_expf_table_64,520
	.data
	.section .note.GNU-stack, ""
# End
