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
//     ECCP SSCM stuff
// 
// 
*/

#include "precomp.h"
#include "owncp.h"
#include "pcpbn.h"
#include "pcpeccppoint.h"

#if defined (_USE_ECCP_SSCM_)
#pragma message ("ECCP SCCM version")

//#define LOG2_CACHE_LINE_SIZE (6) /* LOG2(CACHE_LINE_SIZE) */
#define LOG2_CACHE_LINE_SIZE (LOG_CACHE_LINE_SIZE)

static int div_upper(int a, int d)
{ return (a+d-1)/d; }

static int getNumOperations(int bitsize, int w)
{
   int n_overhead = (1<<w) -1;
   int n_ops = div_upper(bitsize, w) + n_overhead;
   return n_ops;
}

int cpECCP_OptimalWinSize(int bitsize)
{
#define LIMIT (LOG2_CACHE_LINE_SIZE)
   int w_opt = 1;
   int n_opt = getNumOperations(bitsize, w_opt);
   int w_trial;
   for(w_trial=w_opt+1; w_trial<=LIMIT; w_trial++) {
      int n_trial = getNumOperations(bitsize, w_trial);
      if(n_trial>=n_opt) break;
      w_opt = w_trial;
      n_opt = n_trial;
   }
   return w_opt;
#undef LIMIT
}

int cpECCP_ConvertRepresentation(BNU_CHUNK_T* pInput, int inpBits, int w)
{
   Ipp32u* pR   = (Ipp32u*)pInput;
   Ipp16u* pR16 = (Ipp16u*)pInput;

   int outBits = 0;
   Ipp32u base = (BNU_CHUNK_T)1<<w;
   Ipp32u digitMask = base-1;
   int i;

   cpSize nsR = BITS2WORD32_SIZE(inpBits);
   pR[nsR] = 0;               // expand 32-bit representation of input
   for(i=0; i<inpBits; i+=w) {
      cpSize chunkIdx = i/BITSIZE(Ipp16u);
      Ipp32u chunk = ((Ipp32u*)(pR16+chunkIdx))[0];
      int  digitShift = i % BITSIZE(Ipp16u);
      Ipp32u digit = (chunk>>digitShift) &digitMask;

      Ipp32u delta = (base-digit) & ~digitMask;
      delta <<= digitShift;
      cpDec_BNU32((Ipp32u*)(pR16+chunkIdx), (Ipp32u*)(pR16+chunkIdx), (2*nsR-chunkIdx+1)/2, delta);

      inpBits = BITSIZE_BNU32(pR, nsR);
      outBits += w;
   }

   return outBits;
}

/*
// cpsScramblePut/cpsScrambleGet
// stores to/retrieves from pScrambleEntry position
// pre-computed data if fixed window method is used
*/
void cpECCP_ScramblePut(Ipp8u* pScrambleEntry, int proposity,
                      const IppsECCPPointState* pPoint, cpSize coordLen)
{
   int i;
   Ipp8u* pCoord;

   BNU_CHUNK_T* pX = BN_NUMBER(ECP_POINT_X(pPoint));
   BNU_CHUNK_T* pY = BN_NUMBER(ECP_POINT_Y(pPoint));
   BNU_CHUNK_T* pZ = BN_NUMBER(ECP_POINT_Z(pPoint));
   int coordSize = coordLen*sizeof(BNU_CHUNK_T);

   ZEXPAND_BNU(pX, BN_SIZE(ECP_POINT_X(pPoint)), coordLen);
   ZEXPAND_BNU(pY, BN_SIZE(ECP_POINT_Y(pPoint)), coordLen);
   ZEXPAND_BNU(pZ, BN_SIZE(ECP_POINT_Z(pPoint)), coordLen);

   pCoord = (Ipp8u*)pX;
   for(i=0; i<coordSize; i++, pScrambleEntry+=proposity)
      *pScrambleEntry = pCoord[i];

   pCoord = (Ipp8u*)pY;
   for(i=0; i<coordSize; i++, pScrambleEntry+=proposity)
      *pScrambleEntry = pCoord[i];

   pCoord = (Ipp8u*)pZ;
   for(i=0; i<coordSize; i++, pScrambleEntry+=proposity)
      *pScrambleEntry = pCoord[i];
}

void cpECCP_ScrambleGet(IppsECCPPointState* pPoint, cpSize coordLen,
                      const Ipp8u* pScrambleEntry, int proposity)
{
   BNU_CHUNK_T* pX = BN_NUMBER(ECP_POINT_X(pPoint));
   BNU_CHUNK_T* pY = BN_NUMBER(ECP_POINT_Y(pPoint));
   BNU_CHUNK_T* pZ = BN_NUMBER(ECP_POINT_Z(pPoint));

   int coordSize = coordLen*sizeof(BNU_CHUNK_T);
   int i;

   Ipp8u* pCoord = (Ipp8u*)pX;
   for(i=0; i<coordSize; i++, pScrambleEntry+=proposity)
      pCoord[i] = *pScrambleEntry;

   pCoord = (Ipp8u*)pY;
   for(i=0; i<coordSize; i++, pScrambleEntry+=proposity)
      pCoord[i] = *pScrambleEntry;

   pCoord = (Ipp8u*)pZ;
   for(i=0; i<coordSize; i++, pScrambleEntry+=proposity)
      pCoord[i] = *pScrambleEntry;

   i = coordLen;
   FIX_BNU(pX, i);
   BN_SIZE(ECP_POINT_X(pPoint)) = i;

   i = coordLen;
   FIX_BNU(pY, i);
   BN_SIZE(ECP_POINT_Y(pPoint)) = i;

   i = coordLen;
   FIX_BNU(pZ, i);
   BN_SIZE(ECP_POINT_Z(pPoint)) = i;
}

#endif /* _USE_ECCP_SSCM_ */
