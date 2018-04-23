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
// 
*/

#ifndef __OWNCP_H__
#define __OWNCP_H__

#ifndef __OWNDEFS_H__
  #include "owndefs.h"
#endif

#ifndef __IPPCP_H__
  #include "ippcp.h"
#endif

#if defined(_TXT_ACM_)
   //#pragma message ("cogifuration: TXT ACM")
   #include "pcpvariant_txt_acm.h"
#else
   //#pragma message ("cogifuration: STANDARD")
   #include "pcpvariant.h"
#endif


#pragma warning( disable : 4996 4324 4206)

/* ippCP length */
typedef int cpSize;

/*
// Common ippCP Macros
*/

/* size of cache line (bytes) */
#if (_IPP==_IPP_M5)
#define CACHE_LINE_SIZE      (16)
#define LOG_CACHE_LINE_SIZE   (4)
#else
#define CACHE_LINE_SIZE      (64)
#define LOG_CACHE_LINE_SIZE   (6)
#endif

/* swap data & pointers */
#define SWAP_PTR(ATYPE, pX,pY)   { ATYPE* aPtr=(pX); (pX)=(pY); (pY)=aPtr; }
#define SWAP(x,y)                {(x)^=(y); (y)^=(x); (x)^=(y);}

/* alignment value */
#define ALIGN_VAL ((int)sizeof(void*))

/* bitsize */
#define BYTESIZE     (8)
#define BITSIZE(x)   ((int)(sizeof(x)*BYTESIZE))

/* bit length -> byte/word length conversion */
#define BITS2WORD8_SIZE(x)  (((x)+ 7)>>3)
#define BITS2WORD16_SIZE(x) (((x)+15)>>4)
#define BITS2WORD32_SIZE(x) (((x)+31)>>5)
#define BITS2WORD64_SIZE(x) (((x)+63)>>6)

/* WORD and DWORD manipulators */
#define LODWORD(x)    ((Ipp32u)(x))
#define HIDWORD(x)    ((Ipp32u)(((Ipp64u)(x) >>32) & 0xFFFFFFFF))

#define MAKEHWORD(bLo,bHi) ((Ipp16u)(((Ipp8u)(bLo))  | ((Ipp16u)((Ipp8u)(bHi))) << 8))
#define MAKEWORD(hLo,hHi)  ((Ipp32u)(((Ipp16u)(hLo)) | ((Ipp32u)((Ipp16u)(hHi))) << 16))
#define MAKEDWORD(wLo,wHi) ((Ipp64u)(((Ipp32u)(wLo)) | ((Ipp64u)((Ipp32u)(wHi))) << 32))

/* extract byte */
#define EBYTE(w,n) ((Ipp8u)((w) >> (8 * (n))))

/* hexString <-> Ipp32u conversion */
#define HSTRING_TO_U32(ptrByte)  \
         (((ptrByte)[0]) <<24)   \
        +(((ptrByte)[1]) <<16)   \
        +(((ptrByte)[2]) <<8)    \
        +((ptrByte)[3])
#define U32_TO_HSTRING(ptrByte, x)  \
   (ptrByte)[0] = (Ipp8u)((x)>>24); \
   (ptrByte)[1] = (Ipp8u)((x)>>16); \
   (ptrByte)[2] = (Ipp8u)((x)>>8);  \
   (ptrByte)[3] = (Ipp8u)(x)

/* 32- and 64-bit masks for MSB of nbits-sequence */
#define MAKEMASK32(nbits) (0xFFFFFFFF >>((32 - ((nbits)&0x1F)) &0x1F))
#define MAKEMASK64(nbits) (0xFFFFFFFFFFFFFFFF >>((64 - ((nbits)&0x3F)) &0x3F))

/* Logical Shifts (right and left) of WORD */
#define LSR32(x,nBits)  ((x)>>(nBits))
#define LSL32(x,nBits)  ((x)<<(nBits))

/* Rorate (right and left) of WORD */
#if defined(_MSC_VER)
#  include <stdlib.h>
#  define ROR32(x, nBits)  _lrotr((x),(nBits))
#  define ROL32(x, nBits)  _lrotl((x),(nBits))
#else
#  define ROR32(x, nBits) (LSR32((x),(nBits)) | LSL32((x),32-(nBits)))
#  define ROL32(x, nBits) ROR32((x),(32-(nBits)))
#endif

/* Logical Shifts (right and left) of DWORD */
#define LSR64(x,nBits)  ((x)>>(nBits))
#define LSL64(x,nBits)  ((x)<<(nBits))

/* Rorate (right and left) of DWORD */
#define ROR64(x, nBits) (LSR64((x),(nBits)) | LSL64((x),64-(nBits)))
#define ROL64(x, nBits) ROR64((x),(64-(nBits)))

/* change endian */
#if defined(_MSC_VER)
#  define ENDIANNESS(x)   _byteswap_ulong((x))
#  define ENDIANNESS32(x)  ENDIANNESS((x))
#  define ENDIANNESS64(x) _byteswap_uint64((x))
#else
#  define ENDIANNESS(x) ((ROR32((x), 24) & 0x00ff00ff) | (ROR32((x), 8) & 0xff00ff00))
#  define ENDIANNESS32(x) ENDIANNESS((x))
#  define ENDIANNESS64(x) MAKEDWORD(ENDIANNESS(HIDWORD((x))), ENDIANNESS(LODWORD((x))))
#endif

#define IPP_MAKE_MULTIPLE_OF_8(x) ((x) = ((x)+7)&(~7))
#define IPP_MAKE_MULTIPLE_OF_16(x) ((x) = ((x)+15)&(~15))

/* 64-bit constant */
#if !defined(__GNUC__)
   #define CONST_64(x)  (x) /*(x##i64)*/
#else
   #define CONST_64(x)  (x##LL)
#endif


/* crypto NI */
#define AES_NI_ENABLED        (ippCPUID_AES)
#define CLMUL_NI_ENABLED      (ippCPUID_CLMUL)
#define AES_CLMUL_NI_ENABLED  (ippCPUID_AES|ippCPUID_CLMUL)
#define ADCOX_ENABLED         (ippCPUID_ADCOX)
#define SHA_NI_ENABLED        (ippCPUID_SHA)
#define RDRAND_NI_ENABLED     (ippCPUID_RDRAND)
#define RDSEED_NI_ENABLED     (ippCPUID_RDSEED)

/* test CPU crypto features */
__INLINE Ipp32u IsFeatureEnabled(Ipp64u niMmask)
{
   return ownGetFeature(niMmask);
}

/* copy under mask */
#define MASKED_COPY_BNU(dst, mask, src1, src2, len) { \
   cpSize i; \
   for(i=0; i<(len); i++) (dst)[i] = ((mask) & (src1)[i]) | (~(mask) & (src2)[i]); \
}

#endif /* __OWNCP_H__ */
