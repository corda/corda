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

#include "owncp.h"
#include "pcpbnuarith.h"


void cpMontRedAdc_BNU(BNU_CHUNK_T* pR,
                      BNU_CHUNK_T* pProduct,
                const BNU_CHUNK_T* pModulus, cpSize nsM, BNU_CHUNK_T m0)
{
   BNU_CHUNK_T carry;
   BNU_CHUNK_T extension;

   cpSize n;
   for(n=0, carry = 0; n<(nsM-1); n++) {
      BNU_CHUNK_T u = pProduct[n]*m0;
      BNU_CHUNK_T t = pProduct[nsM +n +1] + carry;

      extension = cpAddMulDgt_BNU(pProduct+n, pModulus, nsM, u);
      ADD_AB(carry, pProduct[nsM+n], pProduct[nsM+n], extension);
      t += carry;

      carry = t<pProduct[nsM+n+1];
      pProduct[nsM+n+1] = t;
   }

   m0 *= pProduct[nsM-1];
   extension = cpAddMulDgt_BNU(pProduct+nsM-1, pModulus, nsM, m0);
   ADD_AB(extension, pProduct[2*nsM-1], pProduct[2*nsM-1], extension);

   carry |= extension;
   carry -= cpSub_BNU(pR, pProduct+nsM, pModulus, nsM);
   /* condition copy: R = carry? Product+mSize : R */
   MASKED_COPY_BNU(pR, carry, pProduct+nsM, pR, nsM);
}
