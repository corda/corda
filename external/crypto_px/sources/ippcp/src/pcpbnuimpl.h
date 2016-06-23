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

#if !defined(_CP_BNU_IMPL_H)
#define _CP_BNU_IMPL_H

#define BNU_CHUNK_64BIT        (64)
#define BNU_CHUNK_32BIT        (32)


/*
// define BNU chunk data type
*/
#if ((_IPP_ARCH == _IPP_ARCH_EM64T) || (_IPP_ARCH == _IPP_ARCH_LP64) || (_IPP_ARCH == _IPP_ARCH_LRB) || (_IPP_ARCH == _IPP_ARCH_LRB2))
   typedef Ipp64u BNU_CHUNK_T;
   typedef Ipp64s BNS_CHUNK_T;
   #define BNU_CHUNK_LOG2  (6)
   #define BNU_CHUNK_BITS  BNU_CHUNK_64BIT

#else
   typedef Ipp32u BNU_CHUNK_T;
   typedef Ipp32s BNS_CHUNK_T;
   #define BNU_CHUNK_LOG2  (5)
   #define BNU_CHUNK_BITS  BNU_CHUNK_32BIT
#endif

#define BNU_CHUNK_MASK        (~(BNU_CHUNK_T)(0))

#if (BNU_CHUNK_BITS == BNU_CHUNK_64BIT)
   #pragma message ("BNU_CHUNK_BITS = 64 bit")
#elif (BNU_CHUNK_BITS == BNU_CHUNK_32BIT)
   #pragma message ("BNU_CHUNK_BITS = 32 bit")
#else
   #error BNU_CHUNK_BITS should be either 64 or 32 bit!
#endif


#ifdef _MSC_VER
#  pragma warning( disable : 4127)
#endif

/* user's API BNU chunk data type */
typedef Ipp32u API_BNU_CHUNK_T;

/* convert API_BNU_CHUNK_T (usual Ipp32u) length into the BNU_CHUNK_T length */
#define INTERNAL_BNU_LENGTH(apiLen) \
   ((apiLen) + sizeof(BNU_CHUNK_T)/sizeof(API_BNU_CHUNK_T) -1)/(sizeof(BNU_CHUNK_T)/sizeof(API_BNU_CHUNK_T))

/* Low and High parts of BNU_CHUNK_T value */
#define BNU_CHUNK_2H ((BNU_CHUNK_T)1 << (BNU_CHUNK_BITS/2))
#define LO_CHUNK(c)  ((BNU_CHUNK_T)(c) & (BNU_CHUNK_2H - 1))
#define HI_CHUNK(c)  ((BNU_CHUNK_T)(c) >> (BNU_CHUNK_BITS/2))

/* (carry,R) = A+B */
#define ADD_AB(CARRY,R, A,B)     \
do {                             \
   BNU_CHUNK_T __s = (A) + (B);  \
   (CARRY) = __s < (A);          \
   (R) = __s;                    \
} while(0)

/* (carry,R) = A+B+C */
#define ADD_ABC(CARRY,R, A,B,C)  \
do {                             \
   BNU_CHUNK_T __s = (A) + (B);  \
   BNU_CHUNK_T __t1= __s < (A);  \
   BNU_CHUNK_T __r = __s + (C);  \
   BNU_CHUNK_T __t2 = __r < __s; \
   (CARRY) = __t1 + __t2;        \
   (R) = __r;                    \
} while(0)

/* (borrow,R) = A-B */
#define SUB_AB(BORROW,R, A,B)  \
do {                          \
   (BORROW) = (A)<(B);        \
   (R) = (A)-(B);             \
} while(0)

/* (borrow,R) = A-B-C */
#define SUB_ABC(BORROW,R, A,B,C)  \
do {                             \
   BNU_CHUNK_T __s = (A) -( B);  \
   BNU_CHUNK_T __t1= __s > (A);  \
   BNU_CHUNK_T __r = __s - (C);  \
   BNU_CHUNK_T __t2 = __r > __s; \
   (BORROW) = __t1 + __t2;       \
   (R) = __r;                    \
} while(0)

/* (RH,RL) = A*B */
#define MUL_AB(RH, RL, A, B)  \
   do {                       \
   BNU_CHUNK_T __aL = LO_CHUNK((A));   \
   BNU_CHUNK_T __aH = HI_CHUNK((A));   \
   BNU_CHUNK_T __bL = LO_CHUNK((B));   \
   BNU_CHUNK_T __bH = HI_CHUNK((B));   \
   \
   BNU_CHUNK_T __x0 = (BNU_CHUNK_T) __aL * __bL;   \
   BNU_CHUNK_T __x1 = (BNU_CHUNK_T) __aL * __bH;   \
   BNU_CHUNK_T __x2 = (BNU_CHUNK_T) __aH * __bL;   \
   BNU_CHUNK_T __x3 = (BNU_CHUNK_T) __aH * __bH;   \
   \
   __x1 += HI_CHUNK(__x0);    \
   __x1 += __x2;              \
   if(__x1 < __x2)            \
      __x3 += BNU_CHUNK_2H;   \
   \
   (RH) = __x3 + HI_CHUNK(__x1); \
   (RL) = (__x1 << BNU_CHUNK_BITS/2) + LO_CHUNK(__x0); \
   } while (0)

#endif /* _CP_BNU_IMPL_H */
