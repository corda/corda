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

#include "owndefs.h"
#include "owncp.h"
#include "pcpbn.h"
#include "pcpmontgomery.h"


/*
// Binary method of Exponentiation
*/
cpSize cpMontExpBin_BNU(BNU_CHUNK_T* dataY,
                  const BNU_CHUNK_T* dataX, cpSize nsX,
                  const BNU_CHUNK_T* dataE, cpSize nsE,
                        IppsMontState* pMont)
{
   cpSize nsM = MNT_SIZE(pMont);

   /*
   // test for special cases:
   //    x^0 = 1
   //    0^e = 0
   */
   if( cpEqu_BNU_CHUNK(dataE, nsE, 0) ) {
      COPY_BNU(dataY, MNT_1(pMont), nsM);
   }
   else if( cpEqu_BNU_CHUNK(dataX, nsX, 0) ) {
      ZEXPAND_BNU(dataY, 0, nsM);
   }

   /* general case */
   else {
      BNU_CHUNK_T* dataM = MNT_MODULUS(pMont);
      BNU_CHUNK_T m0 = MNT_HELPER(pMont);

      /* Montgomery engine buffers */
      BNU_CHUNK_T* pKBuffer = MNT_KBUFFER(pMont);
      BNU_CHUNK_T* pProduct = MNT_PRODUCT(pMont);

      BNU_CHUNK_T* dataT = MNT_TBUFFER(pMont);

      /* execute most significant part pE */
      BNU_CHUNK_T eValue = dataE[nsE-1];
      int n = cpNLZ_BNU(eValue)+1;

      /* expand base and init result */
      ZEXPAND_COPY_BNU(dataT, nsM, dataX, nsX);
      COPY_BNU(dataY, dataT, nsM);

      eValue <<= n;
      for(; n<BNU_CHUNK_BITS; n++, eValue<<=1) {
         /* squaring R = R*R mod Modulus */
         cpMontSqr_BNU(dataY,
                       dataY, nsM,
                       dataM, nsM, m0,
                       pProduct, pKBuffer);
         /* and multiply R = R*X mod Modulus */
         if(eValue & ((BNU_CHUNK_T)1<<(BNU_CHUNK_BITS-1)))
            cpMontMul_BNU(dataY,
                          dataY, nsM,
                          dataT, nsM,
                          dataM, nsM, m0,
                          pProduct, pKBuffer);
      }

      /* execute rest bits of E */
      for(--nsE; nsE>0; nsE--) {
         eValue = dataE[nsE-1];

         for(n=0; n<BNU_CHUNK_BITS; n++, eValue<<=1) {
            /* squaring: R = R*R mod Modulus */
            cpMontSqr_BNU(dataY,
                          dataY, nsM,
                          dataM, nsM, m0,
                          pProduct, pKBuffer);
            if(eValue & ((BNU_CHUNK_T)1<<(BNU_CHUNK_BITS-1)))
               cpMontMul_BNU(dataY,
                             dataY, nsM,
                             dataT, nsM,
                             dataM, nsM, m0,
                             pProduct, pKBuffer);
         }
      }
   }

   return nsM;
}
