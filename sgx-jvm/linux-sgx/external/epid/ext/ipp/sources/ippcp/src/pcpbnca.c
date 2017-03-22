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
//               Intel(R) Integrated Performance Primitives
//                   Cryptographic Primitives (ippcp)
// 
//  Contents:
//     ippsBigNumGetSize()
//     ippsBigNumInit()
// 
//     ippsSet_BN()
//     ippsGet_BN()
//     ippsGetSize_BN()
//     ippsExtGet_BN()
//     ippsRef_BN()
// 
//     ippsCmpZero_BN()
//     ippsCmp_BN()
// 
//     ippsAdd_BN()
//     ippsSub_BN()
//     ippsMul_BN()
//     ippsMAC_BN_I()
//     ippsDiv_BN()
//     ippsMod_BN()
//     ippsGcd_BN()
//     ippsModInv_BN()
// 
//     cpPackBigNumCtx(), cpUnpackBigNumCtx()
// 
// 
*/

#include "precomp.h"
#include "owncp.h"
#include "pcpbn.h"
#include "pcptool.h"

/* BN(1) and reference */
static IppsBigNumStateChunk cpChunk_BN1 = {
   {
      idCtxBigNum,
      ippBigNumPOS,
      1,1,
      &cpChunk_BN1.value,&cpChunk_BN1.temporary
   },
   1,0
};
IppsBigNumState* cpBN_OneRef(void)
{ return &cpChunk_BN1.bn; };

/* BN(2) and reference */
static IppsBigNumStateChunk cpChunk_BN2 = {
   {
      idCtxBigNum,
      ippBigNumPOS,
      1,1,
      &cpChunk_BN2.value,&cpChunk_BN2.temporary
   },
   2,0
};
IppsBigNumState* cpBN_TwoRef(void)
{ return &cpChunk_BN2.bn; };

/* BN(3) and reference */
static IppsBigNumStateChunk cpChunk_BN3 = {
   {
      idCtxBigNum,
      ippBigNumPOS,
      1,1,
      &cpChunk_BN3.value,&cpChunk_BN3.temporary
   },
   3,0
};
IppsBigNumState* cpBN_ThreeRef(void)
{ return &cpChunk_BN3.bn; };



/*F*
//    Name: ippsBigNumGetSize
//
// Purpose: Returns size of BigNum ctx (bytes).
//
// Returns:                Reason:
//    ippStsNullPtrErr        pCtxSize == NULL
//    ippStsLengthErr         len32 < 1
//    ippStsNoErr             no errors
//
// Parameters:
//    pCtxSize pointer BigNum ctx size
//
*F*/
IPPFUN(IppStatus, ippsBigNumGetSize, (cpSize len32, cpSize *pCtxSize))
{
   IPP_BAD_PTR1_RET(pCtxSize);
   IPP_BADARG_RET(len32<1, ippStsLengthErr);

   {
      /* convert length to the number of BNU_CHUNK_T */
      cpSize len = INTERNAL_BNU_LENGTH(len32);
      /* reserve one BNU_CHUNK_T above for cpDiv_BNU, multiplication, mont exponentiation */
      len++;

      *pCtxSize = sizeof(IppsBigNumState)
                + len*sizeof(BNU_CHUNK_T)
                + len*sizeof(BNU_CHUNK_T)
                + BN_ALIGNMENT-1;

      return ippStsNoErr;
   }
}


/*F*
//    Name: ippsBigNumInit
//
// Purpose: Init BigNum spec for future usage.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pBN == NULL
//    ippStsLengthErr         len32<1
//    ippStsNoErr             no errors
//
// Parameters:
//    len32    max BN length (32-bits segments)
//    pBN      BigNum ctx
//
*F*/
IPPFUN(IppStatus, ippsBigNumInit, (cpSize len32, IppsBigNumState* pBN))
{
   IPP_BADARG_RET(len32<1, ippStsLengthErr);
   IPP_BAD_PTR1_RET(pBN);
   pBN = (IppsBigNumState*)( IPP_ALIGNED_PTR(pBN, BN_ALIGNMENT) );

   {
      Ipp8u* ptr = (Ipp8u*)pBN;

      /* convert length to the number of BNU_CHUNK_T */
      cpSize len = INTERNAL_BNU_LENGTH(len32);

      BN_ID(pBN) = idCtxBigNum;
      BN_SIGN(pBN) = ippBigNumPOS;
      BN_SIZE(pBN) = 1;     /* initial valie is zero */
      BN_ROOM(pBN) = len;   /* close to what has been passed by user */

      /* reserve one BNU_CHUNK_T above for cpDiv_BNU, multiplication, mont exponentiation */
      len++;

      /* allocate buffers */
      BN_NUMBER(pBN) = (BNU_CHUNK_T*)(ptr += sizeof(IppsBigNumState));
      BN_BUFFER(pBN) = (BNU_CHUNK_T*)(ptr += len*sizeof(BNU_CHUNK_T)); /* use expanded length here */

      /* set BN zero */
      ZEXPAND_BNU(BN_NUMBER(pBN), 0, len);

      return ippStsNoErr;
   }
}

/*
// Serialize / Deserialize bigNum context
*/
void cpPackBigNumCtx(const IppsBigNumState* pBN, Ipp8u* pBuffer)
{
   IppsBigNumState* pAlignedBuffer = (IppsBigNumState*)(IPP_ALIGNED_PTR((pBuffer), BN_ALIGNMENT));
   CopyBlock(pBN, pAlignedBuffer, sizeof(IppsBigNumState));
   BN_NUMBER(pAlignedBuffer) = (BNU_CHUNK_T*)((Ipp8u*)NULL + IPP_UINT_PTR(BN_NUMBER(pBN))-IPP_UINT_PTR(pBN));
   BN_BUFFER(pAlignedBuffer) = (BNU_CHUNK_T*)((Ipp8u*)NULL + IPP_UINT_PTR(BN_BUFFER(pBN))-IPP_UINT_PTR(pBN));
   CopyBlock(BN_NUMBER(pBN), (Ipp8u*)pAlignedBuffer+IPP_UINT_PTR(BN_NUMBER(pAlignedBuffer)), BN_ROOM(pBN)*sizeof(BNU_CHUNK_T));
   CopyBlock(BN_BUFFER(pBN), (Ipp8u*)pAlignedBuffer+IPP_UINT_PTR(BN_BUFFER(pAlignedBuffer)), BN_ROOM(pBN)*sizeof(BNU_CHUNK_T));
}

void cpUnpackBigNumCtx(const Ipp8u* pBuffer, IppsBigNumState* pBN)
{
   IppsBigNumState* pAlignedBuffer = (IppsBigNumState*)(IPP_ALIGNED_PTR((pBuffer), BN_ALIGNMENT));
   CopyBlock(pBuffer, pBN, sizeof(IppsBigNumState));
   BN_NUMBER(pBN) = (BNU_CHUNK_T*)((Ipp8u*)pBN + IPP_UINT_PTR(BN_NUMBER(pAlignedBuffer)));
   BN_BUFFER(pBN) = (BNU_CHUNK_T*)((Ipp8u*)pBN + IPP_UINT_PTR(BN_BUFFER(pAlignedBuffer)));
   CopyBlock((Ipp8u*)pAlignedBuffer+IPP_UINT_PTR(BN_NUMBER(pAlignedBuffer)), BN_NUMBER(pBN), BN_ROOM(pBN)*sizeof(BNU_CHUNK_T));
   CopyBlock((Ipp8u*)pAlignedBuffer+IPP_UINT_PTR(BN_BUFFER(pAlignedBuffer)), BN_BUFFER(pBN), BN_ROOM(pBN)*sizeof(BNU_CHUNK_T));
}


/*F*
//    Name: ippsCmpZero_BN
//
// Purpose: Test BigNum value.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pBN == NULL
//                            pResult == NULL
//    ippStsContextMatchErr   BN_VALID_ID()
//    ippStsNoErr             no errors
//
// Parameters:
//    pBN      BigNum ctx
//    pResult  result of comparison
//
*F*/
IPPFUN(IppStatus, ippsCmpZero_BN, (const IppsBigNumState* pBN, Ipp32u* pResult))
{
   IPP_BAD_PTR2_RET(pBN, pResult);

   pBN = (IppsBigNumState*)( IPP_ALIGNED_PTR(pBN, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pBN), ippStsContextMatchErr);

   if(BN_SIZE(pBN)==1 && BN_NUMBER(pBN)[0]==0)
      *pResult = IS_ZERO;
   else if (BN_SIGN(pBN)==ippBigNumPOS)
      *pResult = GREATER_THAN_ZERO;
   else if (BN_SIGN(pBN)==ippBigNumNEG)
      *pResult = LESS_THAN_ZERO;

   return ippStsNoErr;
}


/*F*
//    Name: ippsCmp_BN
//
// Purpose: Compare two BigNums.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pA == NULL
//                            pB == NULL
//                            pResult == NULL
//    ippStsContextMatchErr   BN_VALID_ID(pA)
//                            BN_VALID_ID(pB)
//    ippStsNoErr             no errors
//
// Parameters:
//    pA       BigNum ctx
//    pB       BigNum ctx
//    pResult  result of comparison
//
*F*/
IPPFUN(IppStatus, ippsCmp_BN,(const IppsBigNumState* pA, const IppsBigNumState* pB, Ipp32u *pResult))
{
   IPP_BAD_PTR3_RET(pA, pB, pResult);

   pA = (IppsBigNumState*)( IPP_ALIGNED_PTR(pA, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pA), ippStsContextMatchErr);
   pB = (IppsBigNumState*)( IPP_ALIGNED_PTR(pB, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pB), ippStsContextMatchErr);

   {
      int res;
      if(BN_SIGN(pA)==BN_SIGN(pB)) {
         res = cpCmp_BNU(BN_NUMBER(pA), BN_SIZE(pA), BN_NUMBER(pB), BN_SIZE(pB));
         if(ippBigNumNEG==BN_SIGN(pA))
            res = -res;
      }
      else
         res = (ippBigNumPOS==BN_SIGN(pA))? 1 :-1;

      *pResult = (1==res)? IPP_IS_GT : (-1==res)? IPP_IS_LT : IPP_IS_EQ;

      return ippStsNoErr;
   }
}


/*F*
//    Name: ippsGetSize_BN
//
// Purpose: Returns BigNum room.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pBN == NULL
//                            pSize == NULL
//    ippStsContextMatchErr   BN_VALID_ID(pBN)
//    ippStsNoErr             no errors
//
// Parameters:
//    pBN      BigNum ctx
//    pSize    max BigNum length (in Ipp32u chunks)
//
*F*/
IPPFUN(IppStatus, ippsGetSize_BN, (const IppsBigNumState* pBN, cpSize* pSize))
{
   IPP_BAD_PTR2_RET(pBN, pSize);

   pBN = (IppsBigNumState*)( IPP_ALIGNED_PTR(pBN, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pBN), ippStsContextMatchErr);

   *pSize = BN_ROOM(pBN)*sizeof(BNU_CHUNK_T)/sizeof(Ipp32u);

    return ippStsNoErr;
}


/*F*
//    Name: ippsSet_BN
//
// Purpose: Set BigNum.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pBN == NULL
//                            pData == NULL
//    ippStsContextMatchErr   BN_VALID_ID(pBN)
//    ippStsLengthErr         len32 < 1
//    ippStsOutOfRangeErr     len32 > BN_ROOM()
//    ippStsNoErr             no errors
//
// Parameters:
//    sgn      sign
//    len32    data size (in Ipp32u chunks)
//    pData    source data pointer
//    pBn      BigNum ctx
//
*F*/
IPPFUN(IppStatus, ippsSet_BN, (IppsBigNumSGN sgn, cpSize len32, const Ipp32u* pData,
                               IppsBigNumState* pBN))
{
   IPP_BAD_PTR2_RET(pData, pBN);

   pBN = (IppsBigNumState*)( IPP_ALIGNED_PTR(pBN, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pBN), ippStsContextMatchErr);

   IPP_BADARG_RET(len32<1, ippStsLengthErr);

    /* compute real size */
   FIX_BNU(pData, len32);

   {
      cpSize len = INTERNAL_BNU_LENGTH(len32);
      IPP_BADARG_RET(len > BN_ROOM(pBN), ippStsOutOfRangeErr);

      ZEXPAND_COPY_BNU((Ipp32u*)BN_NUMBER(pBN), BN_ROOM(pBN)*(int)(sizeof(BNU_CHUNK_T)/sizeof(Ipp32u)), pData, len32);

      BN_SIZE(pBN) = len;

      if(len32==1 && pData[0] == 0)
         sgn = ippBigNumPOS;  /* consider zero value as positive */
      BN_SIGN(pBN) = sgn;

      return ippStsNoErr;
   }
}


/*F*
//    Name: ippsGet_BN
//
// Purpose: Get BigNum.
//
// Returns:                   Reason:
//    ippStsNullPtrErr           pBN == NULL
//                               pData == NULL
//                               pSgn == NULL
//                               pLen32 ==NULL
//    ippStsContextMatchErr      !BN_VALID_ID(pBN)
//    ippStsNoErr                no errors
//
// Parameters:
//    pSgn     pointer to the sign
//    pLen32   pointer to the data size (in Ipp32u chunks)
//    pData    pointer to the data buffer
//    pBN      BigNum ctx
//
*F*/
IPPFUN(IppStatus, ippsGet_BN, (IppsBigNumSGN* pSgn, cpSize* pLen32, Ipp32u* pData,
                               const IppsBigNumState* pBN))
{
   IPP_BAD_PTR4_RET(pSgn, pLen32, pData, pBN);

   pBN = (IppsBigNumState*)( IPP_ALIGNED_PTR(pBN, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pBN), ippStsContextMatchErr);

   {
      cpSize len32 = BN_SIZE(pBN)*sizeof(BNU_CHUNK_T)/sizeof(Ipp32u);
      Ipp32u* bnData = (Ipp32u*)BN_NUMBER(pBN);

      FIX_BNU(bnData, len32);
      COPY_BNU(pData, bnData, len32);

      *pSgn = BN_SIGN(pBN);
      *pLen32 = len32;

      return ippStsNoErr;
   }
}


/*F*
//    Name: ippsRef_BN
//
// Purpose: Get BigNum info.
//
// Returns:                   Reason:
//    ippStsNullPtrErr           pBN == NULL
//    ippStsContextMatchErr      BN_VALID_ID(pBN)
//    ippStsNoErr                no errors
//
// Parameters:
//    pSgn     pointer to the sign
//    pBitSize pointer to the data size (in bits)
//    ppData   pointer to the data buffer
//    pBN      BigNum ctx
//
*F*/
IPPFUN(IppStatus, ippsRef_BN, (IppsBigNumSGN* pSgn, cpSize* pBitSize, Ipp32u** const ppData,
                               const IppsBigNumState *pBN))
{
   IPP_BAD_PTR1_RET(pBN);

   pBN = (IppsBigNumState*)( IPP_ALIGNED_PTR(pBN, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pBN), ippStsContextMatchErr);

   if(pSgn)
      *pSgn = BN_SIGN(pBN);
   if(pBitSize) {
      cpSize bitLen = BITSIZE_BNU(BN_NUMBER(pBN), BN_SIZE(pBN));
      *pBitSize = bitLen? bitLen : 1;
   }

   if(ppData)
      *ppData = (Ipp32u*)BN_NUMBER(pBN);

   return ippStsNoErr;
}


IPPFUN(IppStatus, ippsExtGet_BN, (IppsBigNumSGN* pSgn, cpSize* pBitSize, Ipp32u* pData,
                               const IppsBigNumState* pBN))
{
   IPP_BAD_PTR1_RET(pBN);

   pBN = (IppsBigNumState*)( IPP_ALIGNED_PTR(pBN, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pBN), ippStsContextMatchErr);

   {
      cpSize bitSize = BITSIZE_BNU(BN_NUMBER(pBN), BN_SIZE(pBN));
      if(pData)
         COPY_BNU(pData, (Ipp32u*)BN_NUMBER(pBN), BITS2WORD32_SIZE(bitSize));
      if(pSgn)
         *pSgn = BN_SIGN(pBN);
      if(pBitSize)
         *pBitSize = bitSize? bitSize : 1;

      return ippStsNoErr;
   }
}


/*F*
//    Name: ippsAdd_BN
//
// Purpose: Add BigNum.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pA  == NULL
//                            pB  == NULL
//                            pR  == NULL
//    ippStsContextMatchErr   BN_VALID_ID(pA)
//                            BN_VALID_ID(pB)
//                            BN_VALID_ID(pR)
//    ippStsOutOfRangeErr     pR can not hold result
//    ippStsNoErr             no errors
//
// Parameters:
//    pA    source BigNum
//    pB    source BigNum
//    pR    resultant BigNum
//
*F*/
IPPFUN(IppStatus, ippsAdd_BN, (IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR))
{
   IPP_BAD_PTR3_RET(pA, pB, pR);

   pA = (IppsBigNumState*)( IPP_ALIGNED_PTR(pA, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pA), ippStsContextMatchErr);
   pB = (IppsBigNumState*)( IPP_ALIGNED_PTR(pB, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pB), ippStsContextMatchErr);
   pR = (IppsBigNumState*)( IPP_ALIGNED_PTR(pR, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pR), ippStsContextMatchErr);

   {
      cpSize nsA = BN_SIZE(pA);
      cpSize nsB = BN_SIZE(pB);
      cpSize nsR = BN_ROOM(pR);
      IPP_BADARG_RET(nsR < IPP_MAX(nsA, nsB), ippStsOutOfRangeErr);

      {
         BNU_CHUNK_T* pDataR = BN_NUMBER(pR);

         IppsBigNumSGN sgnA = BN_SIGN(pA);
         IppsBigNumSGN sgnB = BN_SIGN(pB);
         BNU_CHUNK_T* pDataA = BN_NUMBER(pA);
         BNU_CHUNK_T* pDataB = BN_NUMBER(pB);

         BNU_CHUNK_T carry;

         if(sgnA==sgnB) {
            if(nsA < nsB) {
               SWAP(nsA, nsB);
               SWAP_PTR(BNU_CHUNK_T, pDataA, pDataB);
            }

            carry = cpAdd_BNU(pDataR, pDataA, pDataB, nsB);
            if(nsA>nsB)
               carry = cpInc_BNU(pDataR+nsB, pDataA+nsB, nsA-nsB, carry);
            if(carry) {
               if(nsR>nsA)
                  pDataR[nsA++] = carry;
               else
                  IPP_ERROR_RET(ippStsOutOfRangeErr);
            }
            BN_SIGN(pR) = sgnA;
         }

         else {
            int cmpRes = cpCmp_BNU(pDataA, nsA, pDataB, nsB);

            if(0==cmpRes) {
               pDataR[0] = 0;
               BN_SIZE(pR) = 1;
               BN_SIGN(pR) = ippBigNumPOS;
               return ippStsNoErr;
            }

            if(0>cmpRes) {
               SWAP(nsA, nsB);
               SWAP_PTR(BNU_CHUNK_T, pDataA, pDataB);
            }

            carry = cpSub_BNU(pDataR, pDataA, pDataB, nsB);
            if(nsA>nsB)
               cpDec_BNU(pDataR+nsB, pDataA+nsB, nsA-nsB, carry);

            BN_SIGN(pR) = cmpRes>0? sgnA : INVERSE_SIGN(sgnA);
         }

         FIX_BNU(pDataR, nsA);
         BN_SIZE(pR) = nsA;

         return ippStsNoErr;
      }
   }
}


/*F*
//    Name: ippsSub_BN
//
// Purpose: Subtcrac BigNum.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pA  == NULL
//                            pB  == NULL
//                            pR  == NULL
//    ippStsContextMatchErr   BN_VALID_ID(pA)
//                            BN_VALID_ID(pB)
//                            BN_VALID_ID(pR)
//    ippStsOutOfRangeErr     pR can not hold result
//    ippStsNoErr             no errors
//
// Parameters:
//    pA    source BigNum
//    pB    source BigNum
//    pR    resultant BigNum
//
*F*/
IPPFUN(IppStatus, ippsSub_BN, (IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR))
{
   IPP_BAD_PTR3_RET(pA, pB, pR);

   pA = (IppsBigNumState*)( IPP_ALIGNED_PTR(pA, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pA), ippStsContextMatchErr);
   pB = (IppsBigNumState*)( IPP_ALIGNED_PTR(pB, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pB), ippStsContextMatchErr);
   pR = (IppsBigNumState*)( IPP_ALIGNED_PTR(pR, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pR), ippStsContextMatchErr);

   {
      cpSize nsA = BN_SIZE(pA);
      cpSize nsB = BN_SIZE(pB);
      cpSize nsR = BN_ROOM(pR);
      IPP_BADARG_RET(nsR < IPP_MAX(nsA, nsB), ippStsOutOfRangeErr);

      {
         BNU_CHUNK_T* pDataR = BN_NUMBER(pR);

         IppsBigNumSGN sgnA = BN_SIGN(pA);
         IppsBigNumSGN sgnB = BN_SIGN(pB);
         BNU_CHUNK_T* pDataA = BN_NUMBER(pA);
         BNU_CHUNK_T* pDataB = BN_NUMBER(pB);

         BNU_CHUNK_T carry;

         if(sgnA!=sgnB) {
            if(nsA < nsB) {
               SWAP(nsA, nsB);
               SWAP_PTR(BNU_CHUNK_T, pDataA, pDataB);
            }

            carry = cpAdd_BNU(pDataR, pDataA, pDataB, nsB);
            if(nsA>nsB)
               carry = cpInc_BNU(pDataR+nsB, pDataA+nsB, nsA-nsB, carry);
            if(carry) {
               if(nsR > nsA)
                  pDataR[nsA++] = carry;
               else
                  IPP_ERROR_RET(ippStsOutOfRangeErr);
            }
            BN_SIGN(pR) = sgnA;
         }

         else {
            int cmpRes= cpCmp_BNU(pDataA, nsA, pDataB, nsB);

            if(0==cmpRes) {
               ZEXPAND_BNU(pDataR,0, nsR);
               BN_SIZE(pR) = 1;
               BN_SIGN(pR) = ippBigNumPOS;
               return ippStsNoErr;
            }

            if(0>cmpRes) {
               SWAP(nsA, nsB);
               SWAP_PTR(BNU_CHUNK_T, pDataA, pDataB);
            }

            carry = cpSub_BNU(pDataR, pDataA, pDataB, nsB);
            if(nsA>nsB)
               cpDec_BNU(pDataR+nsB, pDataA+nsB, nsA-nsB, carry);

            BN_SIGN(pR) = cmpRes>0? sgnA : INVERSE_SIGN(sgnA);
         }

         FIX_BNU(pDataR, nsA);
         BN_SIZE(pR) = nsA;

         return ippStsNoErr;
      }
   }
}


/*F*
//    Name: ippsMul_BN
//
// Purpose: Multiply BigNum.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pA  == NULL
//                            pB  == NULL
//                            pR  == NULL
//    ippStsContextMatchErr   BN_VALID_ID(pA)
//                            BN_VALID_ID(pB)
//                            BN_VALID_ID(pR)
//    ippStsOutOfRangeErr     pR can not hold result
//    ippStsNoErr             no errors
//
// Parameters:
//    pA    source BigNum
//    pB    source BigNum
//    pR    resultant BigNum
//
*F*/
IPPFUN(IppStatus, ippsMul_BN, (IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR))
{
   IPP_BAD_PTR3_RET(pA, pB, pR);

   pA = (IppsBigNumState*)( IPP_ALIGNED_PTR(pA, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pA), ippStsContextMatchErr);
   pB = (IppsBigNumState*)( IPP_ALIGNED_PTR(pB, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pB), ippStsContextMatchErr);
   pR = (IppsBigNumState*)( IPP_ALIGNED_PTR(pR, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pR), ippStsContextMatchErr);

   {
      BNU_CHUNK_T* pDataA = BN_NUMBER(pA);
      BNU_CHUNK_T* pDataB = BN_NUMBER(pB);
      BNU_CHUNK_T* pDataR = BN_NUMBER(pR);

      cpSize nsA = BN_SIZE(pA);
      cpSize nsB = BN_SIZE(pB);
      cpSize nsR = BN_ROOM(pR);

      cpSize bitSizeA = BITSIZE_BNU(pDataA, nsA);
      cpSize bitSizeB = BITSIZE_BNU(pDataB, nsB);

      /* test if multiplicant/multiplier is zero */
      if(!bitSizeA || !bitSizeB) {
         BN_SIZE(pR) = 1;
         BN_SIGN(pR) = IppsBigNumPOS;
         pDataR[0] = 0;
         return ippStsNoErr;
      }

      /* test if even low estimation of product A*B exceeded */
      IPP_BADARG_RET(nsR*BNU_CHUNK_BITS < (bitSizeA+bitSizeB-1), ippStsOutOfRangeErr);

      {
         BNU_CHUNK_T* aData = pDataA;
         BNU_CHUNK_T* bData = pDataB;

         if(pA == pR) {
            aData = BN_BUFFER(pR);
            COPY_BNU(aData, pDataA, nsA);
         }
         if((pB == pR) && (pA != pB)) {
            bData = BN_BUFFER(pR);
            COPY_BNU(bData, pDataB, nsB);
         }

         /* clear result */
         ZEXPAND_BNU(pDataR, 0, nsR+1);

         if(pA==pB)
            cpSqr_BNU_school(pDataR, aData, nsA);
         else
            cpMul_BNU_school(pDataR, aData, nsA, bData, nsB);

         nsR = (bitSizeA + bitSizeB + BNU_CHUNK_BITS - 1) /BNU_CHUNK_BITS;
         FIX_BNU(pDataR, nsR);
         IPP_BADARG_RET(nsR>BN_ROOM(pR), ippStsOutOfRangeErr);

         BN_SIZE(pR) = nsR;
         BN_SIGN(pR) = (BN_SIGN(pA)==BN_SIGN(pB)? ippBigNumPOS : ippBigNumNEG);
         return ippStsNoErr;
      }
   }
}


/*F*
//    Name: ippsMAC_BN_I
//
// Purpose: Multiply and Accumulate BigNum.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pA  == NULL
//                            pB  == NULL
//                            pR  == NULL
//    ippStsContextMatchErr   BN_VALID_ID(pA)
//                            BN_VALID_ID(pB)
//                            BN_VALID_ID(pR)
//    ippStsOutOfRangeErr     pR can not fit result
//    ippStsNoErr             no errors
//
// Parameters:
//    pA    source BigNum
//    pB    source BigNum
//    pR    resultant BigNum
//
*F*/
IPPFUN(IppStatus, ippsMAC_BN_I, (IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pR))
{
   IPP_BAD_PTR3_RET(pA, pB, pR);

   pA = (IppsBigNumState*)( IPP_ALIGNED_PTR(pA, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pA), ippStsContextMatchErr);
   pB = (IppsBigNumState*)( IPP_ALIGNED_PTR(pB, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pB), ippStsContextMatchErr);
   pR = (IppsBigNumState*)( IPP_ALIGNED_PTR(pR, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pR), ippStsContextMatchErr);

   {
      BNU_CHUNK_T* pDataA = BN_NUMBER(pA);
      BNU_CHUNK_T* pDataB = BN_NUMBER(pB);

      cpSize nsA = BN_SIZE(pA);
      cpSize nsB = BN_SIZE(pB);

      cpSize bitSizeA = BITSIZE_BNU(pDataA, nsA);
      cpSize bitSizeB = BITSIZE_BNU(pDataB, nsB);
      /* size of temporary pruduct */
      cpSize nsP = BITS_BNU_CHUNK(bitSizeA+bitSizeB);

      /* test if multiplicant/multiplier is zero */
      if(!nsP) return ippStsNoErr;
      /* test if product can't fit to the result */
      IPP_BADARG_RET(BN_ROOM(pR)<nsP, ippStsOutOfRangeErr);

      {
         BNU_CHUNK_T* pDataR  = BN_NUMBER(pR);
         IppsBigNumSGN sgnR = BN_SIGN(pR);
         cpSize nsR = BN_SIZE(pR);
         cpSize room = BN_ROOM(pR);

         /* temporary product */
         BNU_CHUNK_T* pDataP = BN_BUFFER(pR);
         IppsBigNumSGN sgnP = BN_SIGN(pA)==BN_SIGN(pB)? ippBigNumPOS : ippBigNumNEG;

         /* clear the rest of R data buffer */
         ZEXPAND_BNU(pDataR, nsR, room);

         /* temporary product */
         if(pA==pB)
            cpSqr_BNU_school(pDataP, pDataA, nsA);
         else
            cpMul_BNU_school(pDataP, pDataA, nsA, pDataB, nsB);
         /* clear the rest of rpoduct */
         ZEXPAND_BNU(pDataP, nsP, room);

         if(sgnR==sgnP) {
            BNU_CHUNK_T carry = cpAdd_BNU(pDataR, pDataR, pDataP, room);
            if(carry) {
               BN_SIZE(pR) = room;
               IPP_ERROR_RET(ippStsOutOfRangeErr);
            }
         }

         else {
            BNU_CHUNK_T* pTmp = pDataR;
            int cmpRes = cpCmp_BNU(pDataR, room, pDataP, room);
            if(0>cmpRes) {
               SWAP_PTR(BNU_CHUNK_T, pTmp, pDataP);
            }
            cpSub_BNU(pDataR, pTmp, pDataP, room);

            BN_SIGN(pR) = cmpRes>0? sgnR : INVERSE_SIGN(sgnR);
         }

         FIX_BNU(pDataR, room);
         BN_SIZE(pR) = room;

         return ippStsNoErr;
      }
   }
}


/*F*
//    Name: ippsDiv_BN
//
// Purpose: Divide BigNum.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pA  == NULL
//                            pB  == NULL
//                            pQ  == NULL
//                            pR  == NULL
//    ippStsContextMatchErr   BN_VALID_ID(pA)
//                            BN_VALID_ID(pB)
//                            BN_VALID_ID(pQ)
//                            BN_VALID_ID(pR)
//    ippStsOutOfRangeErr     pQ and/or pR can not hold result
//    ippStsNoErr             no errors
//
// Parameters:
//    pA    source BigNum
//    pB    source BigNum
//    pQ    quotient BigNum
//    pR    reminder BigNum
//
//    A = Q*B + R, 0 <= val(R) < val(B), sgn(A)==sgn(R)
//
*F*/
IPPFUN(IppStatus, ippsDiv_BN, (IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pQ, IppsBigNumState* pR))
{
   IPP_BAD_PTR4_RET(pA, pB, pQ, pR);

   pA = (IppsBigNumState*)( IPP_ALIGNED_PTR(pA, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pA), ippStsContextMatchErr);
   pB = (IppsBigNumState*)( IPP_ALIGNED_PTR(pB, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pB), ippStsContextMatchErr);
   pQ = (IppsBigNumState*)( IPP_ALIGNED_PTR(pQ, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pQ), ippStsContextMatchErr);
   pR = (IppsBigNumState*)( IPP_ALIGNED_PTR(pR, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pR), ippStsContextMatchErr);

   IPP_BADARG_RET(BN_SIZE(pB)== 1 && BN_NUMBER(pB)[0]==0, ippStsDivByZeroErr);

   IPP_BADARG_RET(BN_ROOM(pR)<BN_SIZE(pB), ippStsOutOfRangeErr);
   IPP_BADARG_RET((int)BN_ROOM(pQ)<(int)(BN_SIZE(pA)-BN_SIZE(pB)), ippStsOutOfRangeErr);

   {
      BNU_CHUNK_T* pDataA = BN_BUFFER(pA);
      cpSize nsA = BN_SIZE(pA);
      BNU_CHUNK_T* pDataB = BN_NUMBER(pB);
      cpSize nsB = BN_SIZE(pB);
      BNU_CHUNK_T* pDataQ = BN_NUMBER(pQ);
      cpSize nsQ;
      BNU_CHUNK_T* pDataR = BN_NUMBER(pR);
      cpSize nsR;

      COPY_BNU(pDataA, BN_NUMBER(pA), nsA);
      nsR = cpDiv_BNU(pDataQ, &nsQ, pDataA, nsA, pDataB, nsB);
      COPY_BNU(pDataR, pDataA, nsR);

      BN_SIGN(pQ) = BN_SIGN(pA)==BN_SIGN(pB)? ippBigNumPOS : ippBigNumNEG;
      FIX_BNU(pDataQ, nsQ);
      BN_SIZE(pQ) = nsQ;
      if(nsQ==1 && pDataQ[0]==0) BN_SIGN(pQ) = ippBigNumPOS;

      BN_SIGN(pR) = BN_SIGN(pA);
      FIX_BNU(pDataR, nsR);
      BN_SIZE(pR) = nsR;
      if(nsR==1 && pDataR[0]==0) BN_SIGN(pR) = ippBigNumPOS;

      return ippStsNoErr;
   }
}


/*F*
//    Name: ippsMod_BN
//
// Purpose: reduction BigNum.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pA  == NULL
//                            pM  == NULL
//                            pR  == NULL
//    ippStsContextMatchErr   BN_VALID_ID(pA)
//                            BN_VALID_ID(pM)
//                            BN_VALID_ID(pR)
//    ippStsOutOfRangeErr     pR can not hold result
//    ippStsNoErr             no errors
//
// Parameters:
//    pA    source BigNum
//    pB    source BigNum
//    pR    reminder BigNum
//
//    A = Q*M + R, 0 <= R < B
//
*F*/
IPPFUN(IppStatus, ippsMod_BN, (IppsBigNumState* pA, IppsBigNumState* pM, IppsBigNumState* pR))
{
   IPP_BAD_PTR3_RET(pA, pM, pR);

   pA = (IppsBigNumState*)( IPP_ALIGNED_PTR(pA, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pA), ippStsContextMatchErr);
   pM = (IppsBigNumState*)( IPP_ALIGNED_PTR(pM, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pM), ippStsContextMatchErr);
   pR = (IppsBigNumState*)( IPP_ALIGNED_PTR(pR, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pR), ippStsContextMatchErr);

   IPP_BADARG_RET(BN_NEGATIVE(pM), ippStsBadModulusErr);
   IPP_BADARG_RET(BN_SIZE(pM)== 1 && BN_NUMBER(pM)[0]==0, ippStsBadModulusErr);

   IPP_BADARG_RET(BN_ROOM(pR)<BN_SIZE(pM), ippStsOutOfRangeErr);

   if(cpEqu_BNU_CHUNK(BN_NUMBER(pA), BN_SIZE(pA), 0)) {
      BN_SIGN(pR) = ippBigNumPOS;
      BN_SIZE(pR) = 1;
      BN_NUMBER(pR)[0] = 0;
   }

   else {
      BNU_CHUNK_T* pDataM = BN_NUMBER(pM);
      cpSize nsM = BN_SIZE(pM);
      BNU_CHUNK_T* pBuffA = BN_BUFFER(pA);
      cpSize nsA = BN_SIZE(pA);
      BNU_CHUNK_T* pDataR = BN_NUMBER(pR);
      cpSize nsR;

      COPY_BNU(pBuffA, BN_NUMBER(pA), nsA);
      nsR = cpMod_BNU(pBuffA, nsA, pDataM, nsM);

      COPY_BNU(pDataR, pBuffA, nsR);
      BN_SIZE(pR) = nsR;
      BN_SIGN(pR) = ippBigNumPOS;

      if(BN_NEGATIVE(pA) && !(nsR==1 && pDataR[0]==0)) {
         ZEXPAND_BNU(pDataR, nsR, nsM);
         cpSub_BNU(pDataR, pDataM, pDataR, nsM);
         FIX_BNU(pDataR, nsM);
         BN_SIZE(pR) = nsM;
      }
   }

   return ippStsNoErr;
}


/*F*
//    Name: ippsGcd_BN
//
// Purpose: compute GCD value.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pA  == NULL
//                            pB  == NULL
//                            pG  == NULL
//    ippStsContextMatchErr   BN_VALID_ID(pA)
//                            BN_VALID_ID(pB)
//                            BN_VALID_ID(pG)
//    ippStsBadArgErr         A==B==0
//    ippStsOutOfRangeErr     pG can not hold result
//    ippStsNoErr             no errors
//
// Parameters:
//    pA    source BigNum
//    pB    source BigNum
//    pG    GCD value
//
*F*/
IPPFUN(IppStatus, ippsGcd_BN, (IppsBigNumState* pA, IppsBigNumState* pB, IppsBigNumState* pG))
{
   IPP_BAD_PTR3_RET(pA, pB, pG);

   pA = (IppsBigNumState*)(IPP_ALIGNED_PTR(pA, BN_ALIGNMENT));
   pB = (IppsBigNumState*)(IPP_ALIGNED_PTR(pB, BN_ALIGNMENT));
   pG = (IppsBigNumState*)(IPP_ALIGNED_PTR(pG, BN_ALIGNMENT));
   IPP_BADARG_RET(!BN_VALID_ID(pA), ippStsContextMatchErr);
   IPP_BADARG_RET(!BN_VALID_ID(pB), ippStsContextMatchErr);
   IPP_BADARG_RET(!BN_VALID_ID(pG), ippStsContextMatchErr);

   IPP_BADARG_RET(BN_ROOM(pG) < IPP_MIN(BN_SIZE(pA), BN_SIZE(pB)), ippStsOutOfRangeErr);

   {
      IppsBigNumState* x = pA;
      IppsBigNumState* y = pB;
      IppsBigNumState* g = pG;

      int aIsZero = BN_SIZE(pA)==1 && BN_NUMBER(pA)[0]==0;
      int bIsZero = BN_SIZE(pB)==1 && BN_NUMBER(pB)[0]==0;

      if(aIsZero && bIsZero)
         return ippStsBadArgErr;
      if(aIsZero && !bIsZero) {
         COPY_BNU(BN_NUMBER(g), BN_NUMBER(pB), BN_SIZE(pB));
         BN_SIZE(g) = BN_SIZE(pB);
         BN_SIGN(g) = ippBigNumPOS;
         return ippStsNoErr;
      }
      if(bIsZero && !aIsZero) {
         COPY_BNU(BN_NUMBER(g), BN_NUMBER(pA), BN_SIZE(pA));
         BN_SIZE(g) = BN_SIZE(pA);
         BN_SIGN(g) = ippBigNumPOS;
         return ippStsNoErr;
      }

      /*
      // Lehmer's algorithm requres that first number must be greater than second
      // x is the first, y is the second
      */
      {
         int cmpRes = cpCmp_BNU(BN_NUMBER(x), BN_SIZE(x), BN_NUMBER(y), BN_SIZE(y));
         if(0>cmpRes)
            SWAP_PTR(IppsBigNumState, x, y);
         if(0==cmpRes) {
            COPY_BNU(BN_NUMBER(g), BN_NUMBER(x), BN_SIZE(x));
            BN_SIGN(g) = ippBigNumPOS;
            BN_SIZE(g) = BN_SIZE(x);
            return ippStsNoErr;
         }
         if(BN_SIZE(x)==1) {
            BNU_CHUNK_T gcd = cpGcd_BNU(BN_NUMBER(x)[0], BN_NUMBER(y)[0]);
            BN_NUMBER(g)[0] = gcd;
            BN_SIZE(g) = 1;
            return ippStsNoErr;
         }
      }

      {
         Ipp32u* xBuffer = (Ipp32u*)BN_BUFFER(x);
         Ipp32u* yBuffer = (Ipp32u*)BN_BUFFER(y);
         Ipp32u* gBuffer = (Ipp32u*)BN_BUFFER(g);
         Ipp32u* xData = (Ipp32u*)BN_NUMBER(x);
         Ipp32u* yData = (Ipp32u*)BN_NUMBER(y);
         Ipp32u* gData = (Ipp32u*)BN_NUMBER(g);
         cpSize nsXmax = BN_ROOM(x)*(sizeof(BNU_CHUNK_T)/sizeof(Ipp32u));
         cpSize nsYmax = BN_ROOM(y)*(sizeof(BNU_CHUNK_T)/sizeof(Ipp32u));
         cpSize nsGmax = BN_ROOM(g)*(sizeof(BNU_CHUNK_T)/sizeof(Ipp32u));
         cpSize nsX = BN_SIZE(x)*(sizeof(BNU_CHUNK_T)/sizeof(Ipp32u));
         cpSize nsY = BN_SIZE(y)*(sizeof(BNU_CHUNK_T)/sizeof(Ipp32u));

         Ipp32u* T;
         Ipp32u* u;

         FIX_BNU(xData, nsX);
         FIX_BNU(yData, nsY);

         /* init buffers */
         ZEXPAND_COPY_BNU(xBuffer, nsX, xData, nsXmax);
         ZEXPAND_COPY_BNU(yBuffer, nsY, yData, nsYmax);

         T = gBuffer;
         u = gData;
         ZEXPAND_BNU(T, 0, nsGmax);
         ZEXPAND_BNU(u, 0, nsGmax);

         while(nsX > (cpSize)(sizeof(BNU_CHUNK_T)/sizeof(Ipp32u))) {
            /* xx and yy is the high-order digits of x and y (yy could be 0) */

            Ipp64u xx = (Ipp64u)(xBuffer[nsX-1]);
            Ipp64u yy = (nsY < nsX)? 0 : (Ipp64u)(yBuffer[nsY-1]);

            Ipp64s AA = 1;
            Ipp64s BB = 0;
            Ipp64s CC = 0;
            Ipp64s DD = 1;
            Ipp64s t;

            while((yy+CC)!=0 && (yy+DD)!=0) {
               Ipp64u q  = ( xx + AA ) / ( yy + CC );
               Ipp64u q1 = ( xx + BB ) / ( yy + DD );
               if(q!=q1)
                  break;
               t = AA - q*CC;
               AA = CC;
               CC = t;
               t = BB - q*DD;
               BB = DD;
               DD = t;
               t = xx - q*yy;
               xx = yy;
               yy = t;
            }

            if(BB == 0) {
               /* T = x mod y */
               cpSize nsT = cpMod_BNU32(xBuffer, nsX, yBuffer, nsY);
               ZEXPAND_BNU(T, 0, nsGmax);
               COPY_BNU(T, xBuffer, nsT);
               /* a = b; b = T; */
               ZEXPAND_BNU(xBuffer, 0, nsXmax);
               COPY_BNU(xBuffer, yBuffer, nsY);
               ZEXPAND_BNU(yBuffer, 0, nsYmax);
               COPY_BNU(yBuffer, T, nsY);
            }

            else {
               Ipp32u carry;
               /*
               // T = AA*x + BB*y;
               // u = CC*x + DD*y;
               // b = u; a = T;
               */
               if((AA <= 0)&&(BB>=0)) {
                  Ipp32u a1 = (Ipp32u)(-AA);
                  carry = cpMulDgt_BNU32(T, yBuffer, nsY, (Ipp32u)BB);
                  carry = cpMulDgt_BNU32(u, xBuffer, nsY, a1);
                  /* T = BB*y - AA*x; */
                  carry = cpSub_BNU32(T, T, u, nsY);
               }
               else {
                  if((AA >= 0)&&(BB<=0)) {
                     Ipp32u b1 = (Ipp32u)(-BB);
                     carry = cpMulDgt_BNU32(T, xBuffer, nsY, (Ipp32u)AA);
                     carry = cpMulDgt_BNU32(u, yBuffer, nsY, b1);
                     /* T = AA*x - BB*y; */
                     carry = cpSub_BNU32(T, T, u, nsY);
                  }
                  else {
                     /*AA*BB>=0 */
                     carry = cpMulDgt_BNU32(T, xBuffer, nsY, (Ipp32u)AA);
                     carry = cpMulDgt_BNU32(u, yBuffer, nsY, (Ipp32u)BB);
                     /* T = AA*x + BB*y; */
                     carry = cpAdd_BNU32(T, T, u, nsY);
                  }
               }

               /* Now T is reserved. We use only u for intermediate results. */
               if((CC <= 0)&&(DD>=0)){
                  Ipp32u c1 = (Ipp32u)(-CC);
                  /* u = x*CC; x = u; */
                  carry = cpMulDgt_BNU32(u, xBuffer, nsY, c1);
                  COPY_BNU(xBuffer, u, nsY);
                  /* u = y*DD; */
                  carry = cpMulDgt_BNU32(u, yBuffer, nsY, (Ipp32u)DD);
                  /* u = DD*y - CC*x; */
                  carry = cpSub_BNU32(u, u, xBuffer, nsY);
               }
               else {
                  if((CC >= 0)&&(DD<=0)){
                     Ipp32u d1 = (Ipp32u)(-DD);
                     /* u = y*DD; y = u */
                     carry = cpMulDgt_BNU32(u, yBuffer, nsY, d1);
                     COPY_BNU(yBuffer, u, nsY);
                     /* u = CC*x; */
                     carry = cpMulDgt_BNU32(u, xBuffer, nsY, (Ipp32u)CC);
                     /* u = CC*x - DD*y; */
                     carry = cpSub_BNU32(u, u, yBuffer, nsY);
                  }
                  else {
                     /*CC*DD>=0 */
                     /* y = y*DD */
                     carry = cpMulDgt_BNU32(u,  yBuffer, nsY, (Ipp32u)DD);
                     COPY_BNU(yBuffer, u, nsY);
                     /* u = x*CC */
                     carry = cpMulDgt_BNU32(u, xBuffer, nsY, (Ipp32u)CC);
                     /* u = x*CC + y*DD */
                     carry = cpAdd_BNU32(u, u, yBuffer, nsY);
                  }
               }

               /* y = u; x = T; */
               COPY_BNU(yBuffer, u, nsY);
               COPY_BNU(xBuffer, T, nsY);
            }

            FIX_BNU(xBuffer, nsX);
            FIX_BNU(yBuffer, nsY);

            if (nsY > nsX) {
               SWAP_PTR(IppsBigNumState, x, y);
               SWAP(nsX, nsY);
            }

            if (nsY==1 && yBuffer[nsY-1]==0) {
               /* End evaluation */
               ZEXPAND_BNU(gData, 0, nsGmax);
               COPY_BNU(gData, xBuffer, nsX);
               BN_SIZE(g) = INTERNAL_BNU_LENGTH(nsX);
               BN_SIGN(g) = ippBigNumPOS;
               return ippStsNoErr;
            }
         }

         BN_NUMBER(g)[0] = cpGcd_BNU(((BNU_CHUNK_T*)xBuffer)[0], ((BNU_CHUNK_T*)yBuffer)[0]);
         BN_SIZE(g) = 1;
         BN_SIGN(g) = ippBigNumPOS;
         return ippStsNoErr;
      }
   }
}


/*F*
//    Name: ippsModInv_BN
//
// Purpose: Multiplicative Inversion BigNum.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pA  == NULL
//                            pM  == NULL
//                            pR  == NULL
//    ippStsContextMatchErr   BN_VALID_ID(pA)
//                            BN_VALID_ID(pM)
//                            BN_VALID_ID(pR)
//    ippStsBadArgErr         A<=0
//    ippStsBadModulusErr     M<=0
//    ippStsScaleRangeErr     A>=M
//    ippStsOutOfRangeErr     pR can not hold result
//    ippStsNoErr             no errors
//    ippStsBadModulusErr     inversion not found
//
// Parameters:
//    pA    source (value) BigNum
//    pM    source (modulus) BigNum
//    pR    reminder BigNum
//
*F*/
IPPFUN(IppStatus, ippsModInv_BN, (IppsBigNumState* pA, IppsBigNumState* pM, IppsBigNumState* pR) )
{
   IPP_BAD_PTR3_RET(pA, pM, pR);

   pA = (IppsBigNumState*)( IPP_ALIGNED_PTR(pA, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pA), ippStsContextMatchErr);
   pM = (IppsBigNumState*)( IPP_ALIGNED_PTR(pM, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pM), ippStsContextMatchErr);
   pR = (IppsBigNumState*)( IPP_ALIGNED_PTR(pR, BN_ALIGNMENT) );
   IPP_BADARG_RET(!BN_VALID_ID(pR), ippStsContextMatchErr);

    IPP_BADARG_RET(BN_ROOM(pR) < BN_SIZE(pM), ippStsOutOfRangeErr);
    IPP_BADARG_RET(BN_NEGATIVE(pA) || (BN_SIZE(pA)==1 && BN_NUMBER(pA)[0]==0), ippStsBadArgErr);
    IPP_BADARG_RET(BN_NEGATIVE(pM) || (BN_SIZE(pM)==1 && BN_NUMBER(pM)[0]==0), ippStsBadModulusErr);
    IPP_BADARG_RET(cpCmp_BNU(BN_NUMBER(pA), BN_SIZE(pA), BN_NUMBER(pM), BN_SIZE(pM)) >= 0, ippStsScaleRangeErr);

   {
      cpSize nsR = cpModInv_BNU(BN_NUMBER(pR),
                                BN_NUMBER(pA), BN_SIZE(pA),
                                BN_NUMBER(pM), BN_SIZE(pM),
                                BN_BUFFER(pR), BN_BUFFER(pA), BN_BUFFER(pM));
      if(nsR) {
         BN_SIGN(pR) = ippBigNumPOS;
         BN_SIZE(pR) = nsR;
         return ippStsNoErr;
      }
      else
         return ippStsBadModulusErr;
    }
}

