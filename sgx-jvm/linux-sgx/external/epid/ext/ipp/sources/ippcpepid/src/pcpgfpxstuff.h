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
//               Cryptographic Primitives (ippCP)
//               GF(p) extension internal
// 
*/

#if !defined(_PCP_GFPEXT_H_)
#define _PCP_GFPEXT_H_

#include "pcpgfpstuff.h"


#define _EXTENSION_2_BINOMIAL_SUPPORT_
#define _EXTENSION_3_BINOMIAL_SUPPORT_

#if defined(_EXTENSION_2_BINOMIAL_SUPPORT_) && defined(_EXTENSION_3_BINOMIAL_SUPPORT_)
   /* Intel(R) EPID specific:
      (Fq2) GF(q^2) generating polynomial is g(t) = t^2 + beta, beta = 1
      
   */
   #define _EPID20_GF_PARAM_SPECIFIC_
#endif

/* GF(p^d) pool */
#define GFPX_PESIZE(pGF)   GFP_FELEN((pGF))
#define GFPX_POOL_SIZE     (14) //(8)   /* Number of temporary variables in pool */

/* address of ground field element inside expanded field element */
#define GFPX_IDX_ELEMENT(pxe, idx, eleSize) ((pxe)+(eleSize)*(idx))

#if 0
/* internal function prototypes */
__INLINE BNU_CHUNK_T* cpGFpxGetPool(int n, IppsGFpState* pGFpx)
{
    BNU_CHUNK_T* pPool = GFP_POOL(pGFpx);
    GFP_POOL(pGFpx) += n*GFPX_PESIZE(pGFpx);
    return pPool;
}
__INLINE void cpGFpxReleasePool(int n, IppsGFpState* pGFpx)
{
   GFP_POOL(pGFpx) -= n * GFPX_PESIZE(pGFpx);
}
#endif

__INLINE int degree(const BNU_CHUNK_T* pE, const IppsGFpState* pGFpx)
{
    int groundElemLen = GFP_FELEN( (IppsGFpState*)GFP_GROUNDGF(pGFpx) );
    int deg;
    for(deg=GFP_DEGREE(pGFpx)-1; deg>=0; deg-- ) {
        if(!GFP_IS_ZERO(pE+groundElemLen*deg, groundElemLen)) break;
    }
    return deg;
}

__INLINE IppsGFpState* cpGFpBasic(const IppsGFpState* pGFp)
{
   while( !GFP_IS_BASIC(pGFp) ) {
      pGFp = GFP_GROUNDGF(pGFp);
   }
   return (IppsGFpState*)pGFp;
}
__INLINE int cpGFpBasicDegreeExtension(const IppsGFpState* pGFp)
{
   int degree = GFP_DEGREE(pGFp);
   while( !GFP_IS_BASIC(pGFp) ) {
      IppsGFpState* pGroundGF = GFP_GROUNDGF(pGFp);
      degree *= GFP_DEGREE(pGroundGF);
      pGFp = pGroundGF;
   }
   return degree;
}

/* convert external data (Ipp32u) => internal element (BNU_CHUNK_T) representation
   returns length of element (in BNU_CHUNK_T)
*/
__INLINE int cpGFpxCopyToChunk(BNU_CHUNK_T* pElm, const Ipp32u* pA, int nsA, const IppsGFpState* pGFpx)
{
   IppsGFpState* pBasicGF = cpGFpBasic(pGFpx);
   int basicExtension = cpGFpBasicDegreeExtension(pGFpx);
   int basicElmLen32 = GFP_FELEN32(pBasicGF);
   int basicElmLen = GFP_FELEN(pBasicGF);
   int deg;
   for(deg=0; deg<basicExtension && nsA>0; deg++, nsA -= basicElmLen32) {
      int srcLen = IPP_MIN(nsA, basicElmLen32);
      ZEXPAND_COPY_BNU((Ipp32u*)pElm, basicElmLen*(int)(sizeof(BNU_CHUNK_T)/sizeof(Ipp32u)), pA,srcLen);
      pElm += basicElmLen;
      pA += basicElmLen32;
   }
   return basicElmLen*deg;
}

/* convert internal element (BNU_CHUNK_T) => external data (Ipp32u) representation
   returns length of data (in Ipp32u)
*/
__INLINE int cpGFpxCopyFromChunk(Ipp32u* pA, const BNU_CHUNK_T* pElm, const IppsGFpState* pGFpx)
{
   IppsGFpState* pBasicGF = cpGFpBasic(pGFpx);
   int basicExtension = cpGFpBasicDegreeExtension(pGFpx);
   int basicElmLen32 = GFP_FELEN32(pBasicGF);
   int basicElmLen = GFP_FELEN(pBasicGF);
   int deg;
   for(deg=0; deg<basicExtension; deg++) {
      COPY_BNU(pA, (Ipp32u*)pElm, basicElmLen32);
      pA += basicElmLen32;
      pElm += basicElmLen;
   }
   return basicElmLen32*deg;
}

/*
// cpScramblePut/cpScrambleGet
// stores to/retrieves from pScrambleEntry position
// pre-computed data if fixed window method is used
*/
__INLINE void cpScramblePut(Ipp8u* pScrambleEntry, int scale, const Ipp8u* pData, int dataSize)
{
   int i;
   for(i=0; i<dataSize; i++)
      pScrambleEntry[i*scale] = pData[i];
}

__INLINE void cpScrambleGet(Ipp8u* pData, int dataSize, const Ipp8u* pScrambleEntry, int scale)
{
   int i;
   for(i=0; i<dataSize; i++)
      pData[i] = pScrambleEntry[i*scale];
}

int cpGFpxCompare(const IppsGFpState* pGFpx1, const IppsGFpState* pGFpx2);

BNU_CHUNK_T* cpGFpxRand(BNU_CHUNK_T* pR, IppsGFpState* pGFpx, IppBitSupplier rndFunc, void* pRndParam, int montSpace);
BNU_CHUNK_T* cpGFpxSet (BNU_CHUNK_T* pR, const BNU_CHUNK_T* pDataA, int nsA, IppsGFpState* pGFpx, int montSpace);
BNU_CHUNK_T* cpGFpxGet (BNU_CHUNK_T* pDataA, int nsA, const BNU_CHUNK_T* pR, IppsGFpState* pGFpx, int montSpace);

BNU_CHUNK_T* cpGFpxSetPolyTerm (BNU_CHUNK_T* pR, int deg, const BNU_CHUNK_T* pDataA, int nsA, IppsGFpState* pGFpx, int montSpace);
BNU_CHUNK_T* cpGFpxGetPolyTerm (BNU_CHUNK_T* pDataA, int nsA, const BNU_CHUNK_T* pR, int deg, IppsGFpState* pGFpx, int montSpace);

BNU_CHUNK_T* cpGFpxAdd     (BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, IppsGFpState* pGFpx);
BNU_CHUNK_T* cpGFpxSub     (BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, IppsGFpState* pGFpx);
BNU_CHUNK_T* cpGFpxMul     (BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, IppsGFpState* pGFpx);
BNU_CHUNK_T* cpGFpxSqr     (BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGFpx);
BNU_CHUNK_T* cpGFpxAdd_GFE (BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pGroundB, IppsGFpState* pGFpx);
BNU_CHUNK_T* cpGFpxSub_GFE (BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pGroundB, IppsGFpState* pGFpx);
BNU_CHUNK_T* cpGFpxMul_GFE (BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pGroundB, IppsGFpState* pGFpx);
int cpGFpGetOptimalWinSize(int bitsize);
BNU_CHUNK_T* cpGFpxExp     (BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pE, int nsE, IppsGFpState* pGFpx, Ipp8u* pScratchBuffer);
BNU_CHUNK_T* cpGFpxMultiExp(BNU_CHUNK_T* pR, const BNU_CHUNK_T* ppA[], const BNU_CHUNK_T* ppE[], int nsE[], int nItems,
                          IppsGFpState* pGFpx, Ipp8u* pScratchBuffer);

BNU_CHUNK_T* cpGFpxConj(BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGFpx);
BNU_CHUNK_T* cpGFpxNeg (BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGFpx);
BNU_CHUNK_T* cpGFpxInv (BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGFpx);
BNU_CHUNK_T* cpGFpxHalve (BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, IppsGFpState* pGFpx);

//BNU_CHUNK_T* gfpolyDiv(BNU_CHUNK_T* pQ, BNU_CHUNK_T* pR, const BNU_CHUNK_T* pA, const BNU_CHUNK_T* pB, IppsGFpState* pGFpx);

#endif /* _PCP_GFPEXT_H_ */
