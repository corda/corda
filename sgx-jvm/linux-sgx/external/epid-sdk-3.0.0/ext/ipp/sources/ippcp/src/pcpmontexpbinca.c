/*############################################################################
  # Copyright 2016 Intel Corporation
  #
  # Licensed under the Apache License, Version 2.0 (the "License");
  # you may not use this file except in compliance with the License.
  # You may obtain a copy of the License at
  #
  #     http://www.apache.org/licenses/LICENSE-2.0
  #
  # Unless required by applicable law or agreed to in writing, software
  # distributed under the License is distributed on an "AS IS" BASIS,
  # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  # See the License for the specific language governing permissions and
  # limitations under the License.
  ############################################################################*/

/* 
// 
//  Purpose:
//     Cryptography Primitive.
//     Modular Exponentiation (binary version)
// 
// 
*/

#include "precomp.h"
#include "owncp.h"
#include "pcpbn.h"
#include "pcpmontgomery.h"


#if 0
/*
// Exponentiation
//    Y = X^e
// X and Y belongs Montgomery Domain
*/
void cpMontExp_32u(IppsBigNumState* pY,
             const IppsBigNumState* pX, Ipp32u e,
                   IppsMontState* pMont)
{
   if(pY!=pX)  /* copy base */
      BN_copy(pX, pY);

   {
      int j, back_step;

      /* Montgomery engine data */
      Ipp32u* pModulus = BN_NUMBER(MNT_MODULO(pMont));
      int mSize = BN_SIZE(MNT_MODULO(pMont));
      Ipp32u* pHelper = MNT_HELPER(pMont);

      /* result and base of exponent */
      Ipp32u* dataY = BN_NUMBER(pY);
      Ipp32u* dataX = BN_BUFFER(pY);
      int ySize = BN_SIZE(pY);

      /* temporary buffers */
      Ipp32u* dataT   = BN_NUMBER(MNT_PRODUCT(pMont));
      Ipp32u* pBuffer = BN_BUFFER(MNT_PRODUCT(pMont));

      /* expand result */
      ZEXPAND_BNU(dataY,ySize, mSize);
      /* copy base */
      COPY_BNU(dataY,dataX, mSize);

      j = 32-NLZ32u(e)-1;

      back_step = 0;
      for(j-=1; j>=0; j--) {
         int i;
         Ipp32u mask_pattern = (Ipp32u)(back_step-1);

         /* T = (Y[] and mask_pattern) or (X[] and ~mask_pattern) */
         for(i=0; i<mSize; i++)
            dataT[i] = (dataY[i] & mask_pattern) | (dataX[i] & ~mask_pattern);

         /* squaring/multiplication Y = Y*T mod Modulus */
         #if defined(_USE_NN_MONTMUL_)
         cpMontMul(dataY,    ySize,
                   dataT,    mSize,
                   pModulus, mSize,
                   dataY,   &ySize,
                   pHelper, pBuffer);
         #else
         cpMontMul(dataY,    ySize,
                   dataT,    mSize,
                   pModulus, mSize,
                   dataY,   &ySize,
                   pHelper, pBuffer, MNT_BUFFER(pMont));
         #endif

         /* update back_step and j */
         back_step = ((e>>j) & 0x01) & (back_step^1);
         j += back_step;
      }

      BN_SIZE(pY) = ySize;
   }
}
#endif

/*
// Binary method of Exponentiation
*/
#if !defined(_USE_ERNIE_CBA_MITIGATION_) && !defined(_USE_GRES_CBA_MITIGATION_) // unsafe version
void cpSafeMontExp_Binary(IppsBigNumState* pY,
                const IppsBigNumState* pX, const IppsBigNumState* pE,
                      IppsMontState* pMont)
{
   int k;

   /* if E==0 then Y=R mod m */
   if (pE->size == 1 && pE->number[0] == 0) {
      int len = IPP_MULTIPLE_OF(pMont->n->size, BNUBASE_TYPE_SIZE);
      cpMemset32u(pMont->wb->number, 0, len);
      pMont->wb->number[len] = 1;

      cpMod_BNU(pMont->wb->number, len + 1, pMont->n->number, pMont->n->size, &pY->size);

      cpMemcpy32u(pY->number, pMont->wb->number, pY->size);
      pY->sgn = ippBigNumPOS;
      return;
   }

   else {
      Ipp32u* r_number = pY->workBuffer;
      int r_size = pY->size;

      int flag=1;
      Ipp32u power = pE->number[pE->size-1];

      for( k = 31; k >= 0; k-- ) {
         Ipp32u powd = power & 0x80000000;/* from top to bottom*/
         power <<= 1;

         if((flag == 1) && (powd == 0))
            continue;

         else if (flag == 0) {
            #if defined(_USE_NN_MONTMUL_)
            cpMontMul(r_number, r_size, r_number,r_size,
                      pMont->n->number, pMont->n->size,
                      r_number,&r_size, pMont->n0, pMont->wb->number);
            #else
            cpMontMul(r_number, r_size, r_number,r_size,
                      pMont->n->number, pMont->n->size,
                      r_number,&r_size, pMont->n0, pMont->wb->number, pMont->pBuffer);
            #endif
            if (powd)
               #if defined(_USE_NN_MONTMUL_)
               cpMontMul(r_number, r_size, pX->number,pX->size,
                         pMont->n->number, pMont->n->size,
                         r_number,&r_size, pMont->n0, pMont->wb->number);
               #else
               cpMontMul(r_number, r_size, pX->number,pX->size,
                         pMont->n->number, pMont->n->size,
                         r_number,&r_size, pMont->n0, pMont->wb->number, pMont->pBuffer);
               #endif
         }

         else {
            int i;
            flag = 0;
            r_size = pMont->n->size;
            if( pX->size < pMont->n->size )
               for(i = r_size - 1; i >= pX->size; i-- )
                  r_number[i] = 0;

            for( i = pX->size - 1; i >= 0; i-- )
               r_number[i] = pX->number[i];
         }
      }

      if (pE->size > 1) {
         struct BNU {
            Ipp32u *number;
            int    *size;
         } BNUs[2];
         BNUs[0].number = r_number;
         BNUs[0].size   = &r_size;
         BNUs[1].number = pX->number;
         BNUs[1].size   = &(((IppsBigNumState*)pX)->size);

         for( k = pE->size - 2; k >= 0; k-- ) {
            int j;
            Ipp32u powd = 0;
            power = pE->number[k];

            for( j = 31; j >= 0; j-- ) {
               #if defined(_USE_NN_MONTMUL_)
               cpMontMul(r_number, r_size, BNUs[powd].number, *(BNUs[powd].size),
                         pMont->n->number, pMont->n->size,
                         r_number,&r_size, pMont->n0, pMont->wb->number);
               #else
               cpMontMul(r_number, r_size, BNUs[powd].number, *(BNUs[powd].size),
                         pMont->n->number, pMont->n->size,
                         r_number,&r_size, pMont->n0, pMont->wb->number, pMont->pBuffer);
               #endif
               //gres: powd = ((power >> j) & 0x1) * (powd ^ 1);
               powd = ((power >> j) & 0x1) & (powd ^ 1);
               j += powd;
            }
         }
      }

      for(k=r_size-1; k>= 0; k--)
         pY->number[k] = r_number[k];

      pY->sgn = ippBigNumPOS;
      pY->size = r_size;

      while((pY->size > 1) && (pY->number[pY->size-1] == 0))
         pY->size--;

      return;
   }
}
#endif /* _xUSE_ERNIE_CBA_MITIGATION_,  _xUSE_GRES_CBA_MITIGATION_ */


#if defined(_USE_ERNIE_CBA_MITIGATION_)
/*
// The version below was designed according to recommendation
// from Ernie Brickell and Mattew Wood.
// The reason was to mitigate "cache monitoring" attack on RSA
// Note: this version slower than pre-mitigated version ~ 30-40%
*/
#define SET_BNU(dst,val,len) \
{ \
   int n; \
   for(n=0; n<(len); n++) (dst)[n] = (val); \
}

#define AND_BNU(dst,src1,src2,len) \
{ \
   int n; \
   for(n=0; n<(len); n++) (dst)[n] = (src1)[n] & (src2)[n]; \
}

void cpSafeMontExp_Binary(IppsBigNumState* pY,
                const IppsBigNumState* pX, const IppsBigNumState* pE,
                      IppsMontState* pMont)
{
   Ipp32u* eData = BN_NUMBER(pE);
   int eSize = BN_SIZE(pE);

   /*
   // if e==0 then r=R mod m (i.e MontEnc(1))
   */
   if (eSize == 1 && eData[0] == 0) {
      cpBN_copy(MNT_1(pMont), pY);
      return;
   }

   /*
   // modulo exponentiation
   */
   if(pY!=pX) /* init result */
      cpBN_copy(pX, pY);

   {
      Ipp32u eValue;
      int nBits;

      Ipp32u* pModulus = BN_NUMBER(MNT_MODULO(pMont));
      int mSize = BN_SIZE(MNT_MODULO(pMont));
      Ipp32u* pHelper = MNT_HELPER(pMont);

      Ipp32u* yData = BN_NUMBER(pY);
      Ipp32u* xData = BN_BUFFER(pY);
      int ySize = BN_SIZE(pY);

      Ipp32u* tData   = BN_NUMBER(MNT_PRODUCT(pMont));
      Ipp32u* pBuffer = BN_BUFFER(MNT_PRODUCT(pMont));
      Ipp32u* pMontOne= BN_NUMBER(MNT_1(pMont));

      /* expand Mont(1) */
      ZEXPAND_BNU(pMontOne, BN_SIZE(MNT_1(pMont)), mSize);

      /* copy base */
      ZEXPAND_COPY_BNU(yData,ySize, xData,mSize);


      /* execute most significant part pE */
      eValue = eData[eSize-1];
      nBits = 32-NLZ32u(eValue);
      eValue <<= (32-nBits);

      nBits--;
      eValue <<=1;
      for(; nBits>0; nBits--, eValue<<=1) {
         Ipp32u carry;

         /* squaring: R^2 mod Modulus */
         #if defined(_USE_NN_MONTMUL_)
         cpMontMul(yData,     ySize,
                   yData,     ySize,
                   pModulus,  mSize,
                   yData,    &ySize,
                   pHelper,   pBuffer);
         #else
         cpMontMul(yData,     ySize,
                   yData,     ySize,
                   pModulus,  mSize,
                   yData,    &ySize,
                   pHelper,   pBuffer, MNT_BUFFER(pMont));
         #endif

         /* T = (X-1)*bitof(E,j) + 1 */
         SET_BNU(pBuffer, ((Ipp32s)eValue)>>31, mSize);
         carry = cpSub_BNU(tData, xData, pMontOne, mSize);
         AND_BNU(tData, tData, pBuffer, mSize);
         carry = cpAdd_BNU(tData, tData, pMontOne, mSize);

         /* multiply: Y*T mod Modulus */
         #if defined(_USE_NN_MONTMUL_)
         cpMontMul(yData,    ySize,
                   tData,    mSize,
                   pModulus, mSize,
                   yData,   &ySize,
                   pHelper, pBuffer);
         #else
         cpMontMul(yData,    ySize,
                   tData,    mSize,
                   pModulus, mSize,
                   yData,   &ySize,
                   pHelper, pBuffer, MNT_BUFFER(pMont));
         #endif
      }

      /* execute rest bits of E */
      eSize--;
      for(; eSize>0; eSize--) {
         eValue = eData[eSize-1];

         for(nBits=32; nBits>0; nBits--, eValue<<=1) {
            Ipp32u carry;

            /* squaring: R^2 mod Modulus */
            #if defined(_USE_NN_MONTMUL_)
            cpMontMul(yData,     ySize,
                      yData,     ySize,
                      pModulus,  mSize,
                      yData,    &ySize,
                      pHelper,   pBuffer);
            #else
            cpMontMul(yData,     ySize,
                      yData,     ySize,
                      pModulus,  mSize,
                      yData,    &ySize,
                      pHelper,   pBuffer, MNT_BUFFER(pMont));
            #endif

            /* T = (X-1)*bitof(E,j) + 1 */
            SET_BNU(pBuffer, ((Ipp32s)eValue)>>31, mSize);
            carry = cpSub_BNU(tData, xData, pMontOne, mSize);
            AND_BNU(tData, tData, pBuffer, mSize);
            carry = cpAdd_BNU(tData, tData, pMontOne, mSize);

            /* multiply: R*T mod Modulus */
            #if defined(_USE_NN_MONTMUL_)
            cpMontMul(yData,    ySize,
                      tData,    mSize,
                      pModulus, mSize,
                      yData,   &ySize,
                      pHelper, pBuffer);
            #else
            cpMontMul(yData,    ySize,
                      tData,    mSize,
                      pModulus, mSize,
                      yData,   &ySize,
                      pHelper, pBuffer, MNT_BUFFER(pMont));
            #endif
         }
      }

      BN_SIZE(pY) = ySize;
   }
}
#endif /* _USE_ERNIE_CBA_MITIGATION_ */


#if defined(_USE_GRES_CBA_MITIGATION_)
/*
// The reason was to mitigate "cache monitoring" attack on RSA
//
// This is improved version of modular exponentiation.
// Current version provide both either mitigation and perrformance.
// This version in comparison with previous (IPP 4.1.3) one ~30-40% faster,
// i.e the the performance stayed as was for pre-mitigated version
//
*/
cpSize cpMontExpBin_BNU_sscm(BNU_CHUNK_T* dataY,
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
      BNU_CHUNK_T* sscmBuffer = MNT_SBUFFER(pMont);

      cpSize i;
      BNU_CHUNK_T mask_pattern;

      /* execute most significant part pE */
      BNU_CHUNK_T eValue = dataE[nsE-1];
      int j = BNU_CHUNK_BITS - cpNLZ_BNU(eValue)-1;

      int back_step = 0;

      /* expand base and init result */
      ZEXPAND_COPY_BNU(dataT, nsM, dataX, nsX);
      COPY_BNU(dataY, dataT, nsM);

      for(j-=1; j>=0; j--) {
         mask_pattern = (BNU_CHUNK_T)(back_step-1);

         /* safeBuffer = (Y[] and mask_pattern) or (X[] and ~mask_pattern) */
         for(i=0; i<nsM; i++)
            sscmBuffer[i] = (dataY[i] & mask_pattern) | (dataT[i] & ~mask_pattern);

         /* squaring/multiplication: R = R*T mod Modulus */
         cpMontMul_BNU(dataY,
                       dataY, nsM,
                       sscmBuffer, nsM,
                       dataM,  nsM, m0,
                       pProduct,  pKBuffer);

         /* update back_step and j */
         back_step = ((eValue>>j) & 0x1) & (back_step^1);
         j += back_step;
      }

      /* execute rest bits of E */
      for(--nsE; nsE>0; nsE--) {
         eValue = dataE[nsE-1];

         for(j=BNU_CHUNK_BITS-1; j>=0; j--) {
            mask_pattern = (BNU_CHUNK_T)(back_step-1);

            /* safeBuffer = (Y[] and mask_pattern) or (X[] and ~mask_pattern) */
            for(i=0; i<nsM; i++)
               sscmBuffer[i] = (dataY[i] & mask_pattern) | (dataT[i] & ~mask_pattern);

            /* squaring/multiplication: R = R*T mod Modulus */
            cpMontMul_BNU(dataY,
                          dataY, nsM,
                          sscmBuffer, nsM,
                          dataM,  nsM, m0,
                          pProduct,  pKBuffer);

            /* update back_step and j */
            back_step = ((eValue>>j) & 0x1) & (back_step^1);
            j += back_step;
         }
      }
   }

   return nsM;
}
#endif /* _USE_GRES_CBA_MITIGATION_ */


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
