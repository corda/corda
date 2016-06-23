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
#include "pcpeccppoint.h"


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
