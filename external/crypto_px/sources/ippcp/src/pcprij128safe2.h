/*
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
*/

#if !defined(_PCP_RIJ_SAFE2_H)
#define _PCP_RIJ_SAFE2_H

// transpose 4x4 Ipp8u matrix
#define TRANSPOSE(out, inp) \
   (out)[ 0] = (inp)[ 0]; \
   (out)[ 4] = (inp)[ 1]; \
   (out)[ 8] = (inp)[ 2]; \
   (out)[12] = (inp)[ 3]; \
   \
   (out)[ 1] = (inp)[ 4]; \
   (out)[ 5] = (inp)[ 5]; \
   (out)[ 9] = (inp)[ 6]; \
   (out)[13] = (inp)[ 7]; \
   \
   (out)[ 2] = (inp)[ 8]; \
   (out)[ 6] = (inp)[ 9]; \
   (out)[10] = (inp)[10]; \
   (out)[14] = (inp)[11]; \
   \
   (out)[ 3] = (inp)[12]; \
   (out)[ 7] = (inp)[13]; \
   (out)[11] = (inp)[14]; \
   (out)[15] = (inp)[15]

__INLINE void XorRoundKey(Ipp32u* state, const Ipp32u* RoundKey)
{
   state[0] ^= RoundKey[0];
   state[1] ^= RoundKey[1];
   state[2] ^= RoundKey[2];
   state[3] ^= RoundKey[3];
}

// xtime is a macro that finds the product of {02} and the argument to xtime modulo {1b}
__INLINE Ipp32u mask4(Ipp32u x)
{
   x &= 0x80808080;
   return (Ipp32u)((x<<1) - (x>>7));
}

__INLINE Ipp32u xtime4(Ipp32u x)
{
   Ipp32u t = (x+x) &0xFEFEFEFE;
   t ^= mask4(x) & 0x1B1B1B1B;
   return t;
}

#endif /* _PCP_RIJ_SAFE2_H */
