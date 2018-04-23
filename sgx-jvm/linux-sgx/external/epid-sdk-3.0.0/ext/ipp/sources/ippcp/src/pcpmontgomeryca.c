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
// 
*/

#include "precomp.h"
#include "owncp.h"
#include "pcpbn.h"
#include "pcpmontgomery.h"
#include "pcpmulbnukara.h"
#include "pcptool.h"

/*F*
// Name: ippsMontGetSize
//
// Purpose: Specifies size of buffer in bytes.
//
// Returns:                Reason:
//      ippStsNullPtrErr    pCtxSize==NULL
//      ippStsLengthErr     maxLen32 < 1
//      ippStsNoErr         no errors
//
// Parameters:
//      method    selected exponential method (unused parameter)
//      maxLen32  max modulus length (in Ipp32u chunks)
//      pCtxSize  size of context
//
// Notes: Function always use method=ippBinaryMethod,
//        so this parameter is ignored
*F*/
IPPFUN(IppStatus, ippsMontGetSize, (IppsExpMethod method, cpSize maxLen32, cpSize* pCtxSize))
{
   IPP_BAD_PTR1_RET(pCtxSize);
   IPP_BADARG_RET(maxLen32< 1, ippStsLengthErr);

   UNREFERENCED_PARAMETER(method);

   {
      /* convert modulus length to the number of BNU_CHUNK_T */
      cpSize modSize = INTERNAL_BNU_LENGTH(maxLen32);

      /* size of Karatsuba buffer */
      cpSize buffSize = 0;
      #if defined(_USE_KARATSUBA_)
      buffSize = cpKaratsubaBufferSize(modSize);
      #if defined( _OPENMP )
      buffSize <<=1; /* double buffer size if OpenMP used */
      #endif
      #else
      buffSize = 0;
      #endif

      *pCtxSize= sizeof(IppsMontState)
               + modSize*sizeof(BNU_CHUNK_T)    /* modulus  */
               + modSize*sizeof(BNU_CHUNK_T)    /* identity */
               + modSize*sizeof(BNU_CHUNK_T)    /* square R */
               + modSize*sizeof(BNU_CHUNK_T)    /* cube R */
               + modSize*sizeof(BNU_CHUNK_T)    /* internal buffer */
               + modSize*sizeof(BNU_CHUNK_T)    /* internal sscm buffer */
               + modSize*sizeof(BNU_CHUNK_T)*2  /* internal product */
               + buffSize                       /* K-mul buffer */
               + MONT_ALIGNMENT-1;

      return ippStsNoErr;
   }
}


/*F*
// Name: ippsMontInit
//
// Purpose: Initializes the symbolic data structure and partitions the
//      specified buffer space.
//
// Returns:                Reason:
//      ippStsNullPtrErr    pMont==NULL
//      ippStsLengthErr     maxLen32 < 1
//      ippStsNoErr         no errors
//
// Parameters:
//      method    selected exponential method (unused parameter)
//      maxLen32  max modulus length (in Ipp32u chunks)
//      pMont     pointer to Montgomery context
*F*/
IPPFUN(IppStatus, ippsMontInit,(IppsExpMethod method, int maxLen32, IppsMontState* pMont))
{
   IPP_BADARG_RET(maxLen32<1, ippStsLengthErr);

   IPP_BAD_PTR1_RET(pMont);
   pMont = (IppsMontState*)( IPP_ALIGNED_PTR(pMont, MONT_ALIGNMENT) );

   UNREFERENCED_PARAMETER(method);

   MNT_ID(pMont)     = idCtxMontgomery;
   MNT_ROOM(pMont)   = INTERNAL_BNU_LENGTH(maxLen32);
   MNT_SIZE(pMont)   = 0;
   MNT_HELPER(pMont) = 0;

   {
      Ipp8u* ptr = (Ipp8u*)pMont;

      /* convert modulus length to the number of BNU_CHUNK_T */
      cpSize modSize = MNT_ROOM(pMont);

      /* size of buffer */
      cpSize buffSize = 0;
      #if defined(_USE_KARATSUBA_)
      buffSize = cpKaratsubaBufferSize(modSize);
      #if defined( _OPENMP )
      buffSize <<=1; /* double buffer size if OpenMP used */
      #endif
      #else
      buffSize = 0;
      #endif

      /* assign internal buffers */
      MNT_MODULUS(pMont) = (BNU_CHUNK_T*)( ptr += sizeof(IppsMontState) );

      MNT_1(pMont)       = (BNU_CHUNK_T*)( ptr += modSize*sizeof(BNU_CHUNK_T) );
      MNT_SQUARE_R(pMont)= (BNU_CHUNK_T*)( ptr += modSize*sizeof(BNU_CHUNK_T) );
      MNT_CUBE_R(pMont)  = (BNU_CHUNK_T*)( ptr += modSize*sizeof(BNU_CHUNK_T) );

      MNT_TBUFFER(pMont) = (BNU_CHUNK_T*)( ptr += modSize*sizeof(BNU_CHUNK_T) );
      MNT_SBUFFER(pMont) = (BNU_CHUNK_T*)( ptr += modSize*sizeof(BNU_CHUNK_T) );
      MNT_PRODUCT(pMont) = (BNU_CHUNK_T*)( ptr += modSize*sizeof(BNU_CHUNK_T) );
      MNT_KBUFFER(pMont) = (buffSize)? (BNU_CHUNK_T*)( ptr += modSize*sizeof(BNU_CHUNK_T)*2 ) : NULL;

      /* init internal buffers */
      ZEXPAND_BNU(MNT_MODULUS(pMont), 0, modSize);
      ZEXPAND_BNU(MNT_1(pMont), 0, modSize);
      ZEXPAND_BNU(MNT_SQUARE_R(pMont), 0, modSize);
      ZEXPAND_BNU(MNT_CUBE_R(pMont), 0, modSize);

      return ippStsNoErr;
   }
}

void cpPackMontCtx(const IppsMontState* pCtx, Ipp8u* pBuffer)
{
   IppsMontState* pAlignedBuffer = (IppsMontState*)(IPP_ALIGNED_PTR((pBuffer), MONT_ALIGNMENT));

   /* max modulus length */
   int modSize = MNT_ROOM(pCtx);
   /* size of context (bytes) without product and Karatsuba buffers */
   int ctxSize = sizeof(IppsMontState)
                +sizeof(BNU_CHUNK_T)*(modSize*4);

   CopyBlock(pCtx, pAlignedBuffer, ctxSize);
   MNT_MODULUS(pAlignedBuffer)  = (BNU_CHUNK_T*)((Ipp8u*)NULL + IPP_UINT_PTR(MNT_MODULUS(pCtx))-IPP_UINT_PTR(pCtx));
   MNT_1(pAlignedBuffer)        = (BNU_CHUNK_T*)((Ipp8u*)NULL + IPP_UINT_PTR(MNT_1(pCtx))-IPP_UINT_PTR(pCtx));
   MNT_SQUARE_R(pAlignedBuffer) = (BNU_CHUNK_T*)((Ipp8u*)NULL + IPP_UINT_PTR(MNT_SQUARE_R(pCtx))-IPP_UINT_PTR(pCtx));
   MNT_CUBE_R(pAlignedBuffer)   = (BNU_CHUNK_T*)((Ipp8u*)NULL + IPP_UINT_PTR(MNT_CUBE_R(pCtx))-IPP_UINT_PTR(pCtx));
   MNT_PRODUCT(pAlignedBuffer)  = (BNU_CHUNK_T*)((Ipp8u*)NULL + IPP_UINT_PTR(MNT_PRODUCT(pCtx))-IPP_UINT_PTR(pCtx));
   MNT_TBUFFER(pAlignedBuffer)  = (BNU_CHUNK_T*)((Ipp8u*)NULL + IPP_UINT_PTR(MNT_TBUFFER(pCtx))-IPP_UINT_PTR(pCtx));
   MNT_KBUFFER(pAlignedBuffer)  = MNT_KBUFFER(pCtx)? (BNU_CHUNK_T*)((Ipp8u*)NULL + IPP_UINT_PTR(MNT_KBUFFER(pCtx))-IPP_UINT_PTR(pCtx)) : NULL;
}

void cpUnpackMontCtx(const Ipp8u* pBuffer, IppsMontState* pCtx)
{
   IppsMontState* pAlignedBuffer = (IppsMontState*)(IPP_ALIGNED_PTR((pBuffer), MONT_ALIGNMENT));

   /* max modulus length */
   int modSize = MNT_ROOM(pAlignedBuffer);
   /* size of context (bytes) without product and Karatsuba buffers */
   int ctxSize = sizeof(IppsMontState)
                +sizeof(BNU_CHUNK_T)*(modSize*4);

   CopyBlock(pAlignedBuffer, pCtx, ctxSize);
   MNT_MODULUS(pCtx)  = (BNU_CHUNK_T*)((Ipp8u*)pCtx+ IPP_UINT_PTR(MNT_MODULUS(pAlignedBuffer)));
   MNT_1(pCtx)        = (BNU_CHUNK_T*)((Ipp8u*)pCtx+ IPP_UINT_PTR(MNT_1(pAlignedBuffer)));
   MNT_SQUARE_R(pCtx) = (BNU_CHUNK_T*)((Ipp8u*)pCtx+ IPP_UINT_PTR(MNT_SQUARE_R(pAlignedBuffer)));
   MNT_CUBE_R(pCtx)   = (BNU_CHUNK_T*)((Ipp8u*)pCtx+ IPP_UINT_PTR(MNT_CUBE_R(pAlignedBuffer)));
   MNT_PRODUCT(pCtx)  = (BNU_CHUNK_T*)((Ipp8u*)pCtx+ IPP_UINT_PTR(MNT_PRODUCT(pAlignedBuffer)));
   MNT_TBUFFER(pCtx)  = (BNU_CHUNK_T*)((Ipp8u*)pCtx+ IPP_UINT_PTR(MNT_TBUFFER(pAlignedBuffer)));
   MNT_KBUFFER(pCtx)  = MNT_KBUFFER(pCtx)? (BNU_CHUNK_T*)((Ipp8u*)pCtx + IPP_UINT_PTR(MNT_KBUFFER(pAlignedBuffer))) : NULL;
}

/*F*
// Name: ippsMontSet
//
// Purpose: Setup modulus value
//
// Returns:                   Reason:
//    ippStsNullPtrErr           pMont==NULL
//                               pModulus==NULL
//    ippStsContextMatchErr      !MNT_VALID_ID()
//    ippStsLengthErr            len32<1
//    ippStsNoErr                no errors
//
// Parameters:
//    pModulus    pointer to the modulus buffer
//    len32       length of the  modulus (in Ipp32u chunks).
//    pMont       pointer to the context
*F*/

/*
// See Dusse & Kaliski "A cryptographic library for the Motorola DSP56000"
// Unfortunately there is a misprint in the paper above
*/
static BNU_CHUNK_T cpMontHelper(BNU_CHUNK_T m0)
{
   BNU_CHUNK_T y = 1;
   BNU_CHUNK_T x = 2;
   BNU_CHUNK_T mask = 2*x-1;

   int i;
   for(i=2; i<=BNU_CHUNK_BITS; i++, x<<=1) {
      BNU_CHUNK_T rH, rL;
      MUL_AB(rH, rL, m0, y);
      if( x < (rL & mask) ) /* x < ((m0*y) mod (2*x)) */
         y+=x;
      mask += mask + 1;
   }
   return 0-y;
}

IPPFUN(IppStatus, ippsMontSet,(const Ipp32u* pModulus, cpSize len32, IppsMontState* pMont))
{
   IPP_BAD_PTR2_RET(pModulus, pMont);
   pMont = (IppsMontState*)(IPP_ALIGNED_PTR((pMont), MONT_ALIGNMENT));
   IPP_BADARG_RET(!MNT_VALID_ID(pMont), ippStsContextMatchErr);

   IPP_BADARG_RET(len32<1, ippStsLengthErr);

   /* modulus is not an odd number */
   IPP_BADARG_RET((pModulus[0] & 1) == 0, ippStsBadModulusErr);
   IPP_BADARG_RET(MNT_ROOM(pMont)<(int)(INTERNAL_BNU_LENGTH(len32)), ippStsOutOfRangeErr);

   {
      BNU_CHUNK_T m0;
      cpSize len;

      /* fix input modulus */
      FIX_BNU(pModulus, len32);

      /* store modulus */
      ZEXPAND_BNU(MNT_MODULUS(pMont), 0, MNT_ROOM(pMont));
      COPY_BNU((Ipp32u*)(MNT_MODULUS(pMont)), pModulus, len32);
      /* store modulus length */
      len = INTERNAL_BNU_LENGTH(len32);
      MNT_SIZE(pMont) = len;

      /* pre-compute helper m0, m0*m = -1 mod R */
      m0 = cpMontHelper(MNT_MODULUS(pMont)[0]);
      MNT_HELPER(pMont) = m0;

      /* setup identity */
      ZEXPAND_BNU(MNT_1(pMont), 0, len);
      MNT_1(pMont)[len] = 1;
      cpMod_BNU(MNT_1(pMont), len+1, MNT_MODULUS(pMont), len);

      /* setup square */
      ZEXPAND_BNU(MNT_SQUARE_R(pMont), 0, len);
      COPY_BNU(MNT_SQUARE_R(pMont)+len, MNT_1(pMont), len);
      cpMod_BNU(MNT_SQUARE_R(pMont), 2*len, MNT_MODULUS(pMont), len);

      /* setup cube */
      ZEXPAND_BNU(MNT_CUBE_R(pMont), 0, len);
      COPY_BNU(MNT_CUBE_R(pMont)+len, MNT_SQUARE_R(pMont), len);
      cpMod_BNU(MNT_CUBE_R(pMont), 2*len, MNT_MODULUS(pMont), len);

      /* clear buffers */
      ZEXPAND_BNU(MNT_TBUFFER(pMont), 0, len);
      ZEXPAND_BNU(MNT_SBUFFER(pMont), 0, len);
      ZEXPAND_BNU(MNT_PRODUCT(pMont), 0, 2*len);

      return ippStsNoErr;
   }
}

/*F*
// Name: ippsMontGet
//
// Purpose: Extracts modulus.
//
// Returns:                   Reason:
//    ippStsNullPtrErr           pMont==NULL
//                               pModulus==NULL
//                               pLen32==NULL
//    ippStsContextMatchErr      !MNT_VALID_ID()
//    ippStsNoErr                no errors
//
// Parameters:
//    pModulus    pointer to the modulus buffer
//    pLen32      pointer to the modulus length (in Ipp32u chunks).
//    pMont       pointer to the context
*F*/
IPPFUN(IppStatus, ippsMontGet,(Ipp32u* pModulus, cpSize* pLen32, const IppsMontState* pMont))
{
    IPP_BAD_PTR3_RET(pMont, pModulus, pLen32);

   pMont = (IppsMontState*)(IPP_ALIGNED_PTR((pMont), MONT_ALIGNMENT));
   IPP_BADARG_RET(!MNT_VALID_ID(pMont), ippStsContextMatchErr);

   {
      cpSize len32 = MNT_SIZE(pMont)*sizeof(BNU_CHUNK_T)/sizeof(Ipp32u);
      Ipp32u* bnData = (Ipp32u*)MNT_MODULUS(pMont);

      FIX_BNU(bnData, len32);
      COPY_BNU(pModulus, bnData, len32);
      *pLen32 = len32;

      return ippStsNoErr;
   }
}

/*F*
// Name: ippsMontForm
//
// Purpose: Converts input into Montgomery domain.
//
// Returns:                   Reason:
//    ippStsNullPtrErr           pMont==NULL
//                               pA==NULL
//                               pR==NULL
// ippStsContextMatchErr         !MNT_VALID_ID()
//                               !BN_VALID_ID(pA)
//                               !BN_VALID_ID(pR)
//      ippStsBadArgErr          A < 0.
//      ippStsScaleRangeErr      A >= Modulus.
//      ippStsOutOfRangeErr      R can't hold result
//      ippStsNoErr              no errors
//
// Parameters:
//    pA    pointer to the input [0, modulus-1]
//    pMont Montgomery context
//    pR    pointer to the output (A*R mod modulus)
*F*/
IPPFUN(IppStatus, ippsMontForm,(const IppsBigNumState* pA, IppsMontState* pMont, IppsBigNumState* pR))
{
   IPP_BAD_PTR3_RET(pMont, pA, pR);

   pMont = (IppsMontState*)(IPP_ALIGNED_PTR((pMont), MONT_ALIGNMENT));
   pA = (IppsBigNumState*)( IPP_ALIGNED_PTR(pA, BN_ALIGNMENT) );
   pR = (IppsBigNumState*)( IPP_ALIGNED_PTR(pR, BN_ALIGNMENT) );

   IPP_BADARG_RET(!MNT_VALID_ID(pMont), ippStsContextMatchErr);
   IPP_BADARG_RET(!BN_VALID_ID(pA), ippStsContextMatchErr);
   IPP_BADARG_RET(!BN_VALID_ID(pR), ippStsContextMatchErr);

   IPP_BADARG_RET(BN_SIGN(pA) != ippBigNumPOS, ippStsBadArgErr);
   IPP_BADARG_RET(cpCmp_BNU(BN_NUMBER(pA), BN_SIZE(pA), MNT_MODULUS(pMont), MNT_SIZE(pMont)) >= 0, ippStsScaleRangeErr);
   IPP_BADARG_RET(BN_ROOM(pR) < MNT_SIZE(pMont), ippStsOutOfRangeErr);

   cpMontEnc_BN(pR, pA, pMont);

   return ippStsNoErr;
}


/*F*
// Name: ippsMontMul
//
// Purpose: Computes Montgomery modular multiplication for positive big
//      number integers of Montgomery form. The following pseudocode
//      represents this function:
//      r <- ( a * b * R^(-1) ) mod m
//
// Returns:                Reason:
//      ippStsNoErr         Returns no error.
//      ippStsNullPtrErr    Returns an error when pointers are null.
//      ippStsBadArgErr     Returns an error when a or b is a negative integer.
//      ippStsScaleRangeErr Returns an error when a or b is more than m.
//      ippStsOutOfRangeErr Returns an error when IppsBigNumState *r is larger than
//                          IppsMontState *m.
//      ippStsContextMatchErr Returns an error when the context parameter does
//                          not match the operation.
//
// Parameters:
//      a   Multiplicand within the range [0, m - 1].
//      b   Multiplier within the range [0, m - 1].
//      m   Modulus.
//      r   Montgomery multiplication result.
//
// Notes: The size of IppsBigNumState *r should not be less than the data
//      length of the modulus m.
*F*/
IPPFUN(IppStatus, ippsMontMul, (const IppsBigNumState* pA, const IppsBigNumState* pB, IppsMontState* pMont, IppsBigNumState* pR))
{
   IPP_BAD_PTR4_RET(pA, pB, pMont, pR);

   pMont = (IppsMontState*)(IPP_ALIGNED_PTR((pMont), MONT_ALIGNMENT));
   pA = (IppsBigNumState*)( IPP_ALIGNED_PTR(pA, BN_ALIGNMENT) );
   pB = (IppsBigNumState*)( IPP_ALIGNED_PTR(pB, BN_ALIGNMENT) );
   pR = (IppsBigNumState*)( IPP_ALIGNED_PTR(pR, BN_ALIGNMENT) );

   IPP_BADARG_RET(!MNT_VALID_ID(pMont), ippStsContextMatchErr);
   IPP_BADARG_RET(!BN_VALID_ID(pA), ippStsContextMatchErr);
   IPP_BADARG_RET(!BN_VALID_ID(pB), ippStsContextMatchErr);
   IPP_BADARG_RET(!BN_VALID_ID(pR), ippStsContextMatchErr);

   IPP_BADARG_RET(BN_NEGATIVE(pA) || BN_NEGATIVE(pB), ippStsBadArgErr);
   IPP_BADARG_RET(cpCmp_BNU(BN_NUMBER(pA), BN_SIZE(pA), MNT_MODULUS(pMont), MNT_SIZE(pMont)) >= 0, ippStsScaleRangeErr);
   IPP_BADARG_RET(cpCmp_BNU(BN_NUMBER(pB), BN_SIZE(pB), MNT_MODULUS(pMont), MNT_SIZE(pMont)) >= 0, ippStsScaleRangeErr);
   IPP_BADARG_RET(BN_ROOM(pR) < MNT_SIZE(pMont), ippStsOutOfRangeErr);

   {
      BNU_CHUNK_T* pDataR = BN_NUMBER(pR);
      cpSize nsM = MNT_SIZE(pMont);

      cpMontMul_BNU(pDataR,
                    BN_NUMBER(pA), BN_SIZE(pA),
                    BN_NUMBER(pB), BN_SIZE(pB),
                    MNT_MODULUS(pMont), nsM,
                    MNT_HELPER(pMont),
                    MNT_PRODUCT(pMont), MNT_KBUFFER(pMont));

      FIX_BNU(pDataR, nsM);
      BN_SIZE(pR) = nsM;
      BN_SIGN(pR) = ippBigNumPOS;

      return ippStsNoErr;
   }
}


/*******************************************************************************
// Name:             ippsMontExp
// Description: ippsMontExp() computes the Montgomery exponentiation with exponent
//              IppsBigNumState *e to the given big number integer of Montgomery form
//              IppsBigNumState *a with respect to the modulus IppsMontState *m.
// Input Arguments: a - big number integer of Montgomery form within the
//                      range [0,m-1]
//                  e - big number exponent
//                  m - Montgomery modulus of IppsMontState.
// Output Arguments: r - the Montgomery exponentiation result.
// Returns: IPPC_STATUS_OK - No Error
//          IPPC_STATUS_MONT_BAD_MODULUS - If a>m or b>m or m>R or P_MONT *m has
//                                         not been initialized by the primitive
//                                         function ippsMontInit( ).
//          IPPC_STATUS_BAD_ARG - Bad Arguments
// Notes: IppsBigNumState *r should possess enough memory space as to hold the result
//        of the operation. i.e. both pointers r->d and r->buffer should possess
//        no less than (m->n->length) number of 32-bit words.
*******************************************************************************/
IPPFUN(IppStatus, ippsMontExp, (const IppsBigNumState* pA, const IppsBigNumState* pE, IppsMontState* pMont, IppsBigNumState* pR))
{
   IPP_BAD_PTR4_RET(pA, pE, pMont, pR);

   pMont = (IppsMontState*)(IPP_ALIGNED_PTR((pMont), MONT_ALIGNMENT));
   pA = (IppsBigNumState*)( IPP_ALIGNED_PTR(pA, BN_ALIGNMENT) );
   pE = (IppsBigNumState*)( IPP_ALIGNED_PTR(pE, BN_ALIGNMENT) );
   pR = (IppsBigNumState*)( IPP_ALIGNED_PTR(pR, BN_ALIGNMENT) );

   IPP_BADARG_RET(!MNT_VALID_ID(pMont), ippStsContextMatchErr);
   IPP_BADARG_RET(!BN_VALID_ID(pA), ippStsContextMatchErr);
   IPP_BADARG_RET(!BN_VALID_ID(pE), ippStsContextMatchErr);
   IPP_BADARG_RET(!BN_VALID_ID(pR), ippStsContextMatchErr);

   IPP_BADARG_RET(BN_ROOM(pR) < MNT_SIZE(pMont), ippStsOutOfRangeErr);
   /* check a */
   IPP_BADARG_RET(BN_NEGATIVE(pA), ippStsBadArgErr);
   IPP_BADARG_RET(cpCmp_BNU(BN_NUMBER(pA), BN_SIZE(pA), MNT_MODULUS(pMont), MNT_SIZE(pMont)) >= 0, ippStsScaleRangeErr);
   /* check e */
   IPP_BADARG_RET(BN_NEGATIVE(pE), ippStsBadArgErr);

   cpMontExpBin_BN(pR, pA, pE, pMont);

   return ippStsNoErr;
}
