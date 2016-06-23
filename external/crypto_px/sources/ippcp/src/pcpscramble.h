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

#if !defined(_PC_SCRAMBLE_H)
#define _PC_SCRAMBLE_H

/*
// cpsScramblePut/cpsScrambleGet
// stores to/retrieves from pScrambleEntry position
// pre-computed data if fixed window method is used
*/
__INLINE void cpScramblePut(Ipp8u* pArray, cpSize colummSize,
                      const Ipp32u* pData, cpSize dataSize)
{
   int i;
   switch(colummSize) {
      case 1:
         dataSize *= sizeof(Ipp32u);
         for(i=0; i<dataSize; i++)
            pArray[i*CACHE_LINE_SIZE] = ((Ipp8u*)pData)[i];
         break;
      case 2:
         dataSize *= sizeof(Ipp16u);
         for(i=0; i<dataSize; i++)
            ((Ipp16u*)pArray)[i*CACHE_LINE_SIZE/sizeof(Ipp16u)] = ((Ipp16u*)pData)[i];
         break;
      case 4:
         for(i=0; i<dataSize; i++)
            ((Ipp32u*)pArray)[i*CACHE_LINE_SIZE/sizeof(Ipp32u)] = pData[i];
         break;
      case 8:
         for(; dataSize>=2; dataSize-=2, pArray+=CACHE_LINE_SIZE, pData+=2) {
            ((Ipp32u*)pArray)[0] = pData[0];
            ((Ipp32u*)pArray)[1] = pData[1];
         }
         if(dataSize)
            ((Ipp32u*)pArray)[0] = pData[0];
         break;
      case 16:
         for(; dataSize>=4; dataSize-=4, pArray+=CACHE_LINE_SIZE, pData+=4) {
            ((Ipp32u*)pArray)[0] = pData[0];
            ((Ipp32u*)pArray)[1] = pData[1];
            ((Ipp32u*)pArray)[2] = pData[2];
            ((Ipp32u*)pArray)[3] = pData[3];
         }
         for(; dataSize>0; dataSize--, pArray+=sizeof(Ipp32u), pData++)
            ((Ipp32u*)pArray)[0] = pData[0];
         break;
      case 32:
         for(; dataSize>=8; dataSize-=8, pArray+=CACHE_LINE_SIZE, pData+=8) {
            ((Ipp32u*)pArray)[0] = pData[0];
            ((Ipp32u*)pArray)[1] = pData[1];
            ((Ipp32u*)pArray)[2] = pData[2];
            ((Ipp32u*)pArray)[3] = pData[3];
            ((Ipp32u*)pArray)[4] = pData[4];
            ((Ipp32u*)pArray)[5] = pData[5];
            ((Ipp32u*)pArray)[6] = pData[6];
            ((Ipp32u*)pArray)[7] = pData[7];
         }
         for(; dataSize>0; dataSize--, pArray+=sizeof(Ipp32u), pData++)
            ((Ipp32u*)pArray)[0] = pData[0];
         break;
      default:
         break;
   }
}


/*
// Retrieve data from pArray
*/
#define u8_to_u32(b0,b1,b2,b3, x) \
  ((x) = (b0), \
   (x)|=((b1)<<8), \
   (x)|=((b2)<<16), \
   (x)|=((b3)<<24))
#define u16_to_u32(w0,w1, x) \
  ((x) = (w0), \
   (x)|=((w1)<<16))
#define u32_to_u64(dw0,dw1, x) \
  ((x) = (Ipp64u)(dw0), \
   (x)|= (((Ipp64u)(dw1))<<32))

__INLINE void cpScrambleGet(Ipp32u* pData, cpSize dataSize,
                      const Ipp8u* pArray, cpSize colummSize)
{
   int i;
   switch(colummSize) {
      case 1:
         for(i=0; i<dataSize; i++, pArray+=sizeof(Ipp32u)*CACHE_LINE_SIZE)
            u8_to_u32(pArray[0*CACHE_LINE_SIZE], pArray[1*CACHE_LINE_SIZE], pArray[2*CACHE_LINE_SIZE], pArray[3*CACHE_LINE_SIZE], pData[i]);
         break;
      case 2:
         for(i=0; i<dataSize; i++, pArray+=sizeof(Ipp16u)*CACHE_LINE_SIZE) {
            Ipp16u w0 = *((Ipp16u*)(pArray));
            Ipp16u w1 = *((Ipp16u*)(pArray+CACHE_LINE_SIZE));
            u16_to_u32( w0, w1, pData[i]);
         }
         break;
      case 4:
         for(i=0; i<dataSize; i++, pArray+=CACHE_LINE_SIZE)
            pData[i] = ((Ipp32u*)pArray)[0];
         break;
      case 8:
         for(; dataSize>=2; dataSize-=2, pArray+=CACHE_LINE_SIZE, pData+=2) {
            pData[0] = ((Ipp32u*)pArray)[0];
            pData[1] = ((Ipp32u*)pArray)[1];
         }
         if(dataSize)
            pData[0] = ((Ipp32u*)pArray)[0];
         break;
      case 16:
         for(; dataSize>=4; dataSize-=4, pArray+=CACHE_LINE_SIZE, pData+=4) {
            pData[0] = ((Ipp32u*)pArray)[0];
            pData[1] = ((Ipp32u*)pArray)[1];
            pData[2] = ((Ipp32u*)pArray)[2];
            pData[3] = ((Ipp32u*)pArray)[3];

         }
         for(; dataSize>0; dataSize--, pArray+=sizeof(Ipp32u), pData++)
            pData[0] = ((Ipp32u*)pArray)[0];
         break;
      case 32:
         for(; dataSize>=8; dataSize-=8, pArray+=CACHE_LINE_SIZE, pData+=8) {
            pData[0] = ((Ipp32u*)pArray)[0];
            pData[1] = ((Ipp32u*)pArray)[1];
            pData[2] = ((Ipp32u*)pArray)[2];
            pData[3] = ((Ipp32u*)pArray)[3];
            pData[4] = ((Ipp32u*)pArray)[4];
            pData[5] = ((Ipp32u*)pArray)[5];
            pData[6] = ((Ipp32u*)pArray)[6];
            pData[7] = ((Ipp32u*)pArray)[7];
         }
         for(; dataSize>0; dataSize--, pArray+=sizeof(Ipp32u), pData++)
            pData[0] = ((Ipp32u*)pArray)[0];
         break;
      default:
         break;
   }
}

#endif /* _PC_SCRAMBLE_H */
