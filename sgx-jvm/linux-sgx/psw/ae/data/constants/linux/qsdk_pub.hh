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

#ifndef _QSDK_PUB_HH_
#define _QSDK_PUB_HH_
/* publicexponent = 010001*/
unsigned int g_qsdk_pub_key_e[] = {
    0x00010001
};

/* modulus =
   c3195b35fe43f9e358b1f7ec6456bedc0db2af138f8b9c7d6ac711a5ee824fe8
   7ac0bee3f829e489f9f79c83b947683805b64950d1eea5fb02ff89a67711e95e
   4f8058d9d24ae34db041c4245c9e3655c118c80ca69895b40ab6d5214bcfa63b
   742b4717c70f72d1415dea1a5844bb6d1635ea2043f4c1b26f6def568003bc37
   7822bf095b3a7f7f24556dca8e3ed903fd22292ad56370d6f58354635764c298
   fac1c874eb8040c28e28ef14b52198090cedc16c6fe56b221023ee7dcfdd5c82
   822d7fcdade9835c2fef3964bca24c9ee3672cec403189cadefc0e8f503fefef
   4c21e2c4d9809146f69d2d30219f7d17e93ce010202dacb9bd9505ced2a69adf
*/
const uint32_t g_qsdk_pub_key_n[] = {
    0xd2a69adf, 0xbd9505ce, 0x202dacb9, 0xe93ce010, 0x219f7d17,
    0xf69d2d30, 0xd9809146, 0x4c21e2c4, 0x503fefef, 0xdefc0e8f,
    0x403189ca, 0xe3672cec, 0xbca24c9e, 0x2fef3964, 0xade9835c,
    0x822d7fcd, 0xcfdd5c82, 0x1023ee7d, 0x6fe56b22, 0x0cedc16c,
    0xb5219809, 0x8e28ef14, 0xeb8040c2, 0xfac1c874, 0x5764c298,
    0xf5835463, 0xd56370d6, 0xfd22292a, 0x8e3ed903, 0x24556dca,
    0x5b3a7f7f, 0x7822bf09, 0x8003bc37, 0x6f6def56, 0x43f4c1b2,
    0x1635ea20, 0x5844bb6d, 0x415dea1a, 0xc70f72d1, 0x742b4717,
    0x4bcfa63b, 0x0ab6d521, 0xa69895b4, 0xc118c80c, 0x5c9e3655,
    0xb041c424, 0xd24ae34d, 0x4f8058d9, 0x7711e95e, 0x02ff89a6,
    0xd1eea5fb, 0x05b64950, 0xb9476838, 0xf9f79c83, 0xf829e489,
    0x7ac0bee3, 0xee824fe8, 0x6ac711a5, 0x8f8b9c7d, 0x0db2af13,
    0x6456bedc, 0x58b1f7ec, 0xfe43f9e3, 0xc3195b35,
};
#endif

