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
//     SHA512 message digest
// 
//  Contents:
//     ippsSHA512GetSize()
//     ippsSHA512Init()
//     ippsSHA512Pack()
//     ippsSHA512Unpack()
//     ippsSHA512Duplicate()
//     ippsSHA512Update()
//     ippsSHA512GetTag()
//     ippsSHA512Final()
//     ippsSHA512MessageDigest()
// 
// 
*/

#include "precomp.h"
#include "owncp.h"
#include "pcphash.h"
#include "pcptool.h"


#if !defined(_ENABLE_ALG_SHA512_)
#pragma message("IPP_ALG_HASH_SHA512 disabled")
#else
#pragma message("IPP_ALG_HASH_SHA512 enabled")
#endif

#if !defined(_ENABLE_ALG_SHA384_)
#pragma message("IPP_ALG_HASH_SHA384 disabled")
#else
#pragma message("IPP_ALG_HASH_SHA384 enabled")
#endif


/*
// SHA512 init context
*/
#if defined (_ENABLE_ALG_SHA512_) || defined (_ENABLE_ALG_SHA384_)
IppStatus GetSizeSHA512(int* pSize)
{
   /* test pointer */
   IPP_BAD_PTR1_RET(pSize);

   *pSize = sizeof(IppsSHA512State) +(SHA512_ALIGNMENT-1);

   return ippStsNoErr;
}

IppStatus InitSHA512(const DigestSHA512 IV, IppsSHA512State* pState)
{
   /* test state pointer */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsSHA512State*)( IPP_ALIGNED_PTR(pState, SHA512_ALIGNMENT) );

   /* set state ID */
   SHS_ID(pState) = idCtxSHA512;

   /* zeros message length */
   SHS_LENL(pState) = 0;
   SHS_LENH(pState) = 0;

   /* message buffer is free */
   SHS_INDX(pState) = 0;

   /* setup initial digest */
   SHS_HASH(pState)[0] = IV[0];
   SHS_HASH(pState)[1] = IV[1];
   SHS_HASH(pState)[2] = IV[2];
   SHS_HASH(pState)[3] = IV[3];
   SHS_HASH(pState)[4] = IV[4];
   SHS_HASH(pState)[5] = IV[5];
   SHS_HASH(pState)[6] = IV[6];
   SHS_HASH(pState)[7] = IV[7];

   return ippStsNoErr;
}
#endif

/*F*
//    Name: ippsSHA512GetSize
//          ippsSHA384GetSize
//
// Purpose: Returns size (bytes) of IppsSHA512State state.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSize == NULL
//    ippStsNoErr             no errors
//
// Parameters:
//    pSize       pointer to state size
//
*F*/
#if defined (_ENABLE_ALG_SHA512_)
IPPFUN(IppStatus, ippsSHA512GetSize,(int* pSize))
{
   return GetSizeSHA512(pSize);
}
#endif

#if defined (_ENABLE_ALG_SHA384_)
IPPFUN(IppStatus, ippsSHA384GetSize,(int* pSize))
{
   return GetSizeSHA512(pSize);
}
#endif


/*F*
//    Name: ippsSHA512Init
//          ippsSHA384Init
//
// Purpose: Init SHA512
//
// Returns:                Reason:
//    ippStsNullPtrErr        pState == NULL
//    ippStsNoErr             no errors
//
// Parameters:
//    pState      pointer to the SHA512 state
//
*F*/
#if defined (_ENABLE_ALG_SHA512_)
IPPFUN(IppStatus, ippsSHA512Init,(IppsSHA512State* pState))
{
   return InitSHA512(SHA512_IV, pState);
}
#endif

#if defined (_ENABLE_ALG_SHA384_)
IPPFUN(IppStatus, ippsSHA384Init,(IppsSHA384State* pState))
{
   return InitSHA512(SHA384_IV, pState);
}
#endif


/*F*
//    Name: ippsSHA512Pack
//          ippsSHA384Pack
//
// Purpose: Copy initialized context to the buffer.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSize == NULL
//                            pCtx == NULL
//    ippStsNoErr             no errors
//
// Parameters:
//    pCtx        pointer hash state
//    pSize       pointer to the packed spec size
//
*F*/
#if defined (_ENABLE_ALG_SHA512_) || defined (_ENABLE_ALG_SHA384_)
IPPFUN(IppStatus, ippsSHA512Pack,(const IppsSHA512State* pCtx, Ipp8u* pBuffer))
{
   /* test pointers */
   IPP_BAD_PTR2_RET(pCtx, pBuffer);
   /* use aligned context */
   pCtx = (IppsSHA512State*)( IPP_ALIGNED_PTR(pCtx, SHA512_ALIGNMENT) );
   /* test the context */
   IPP_BADARG_RET(idCtxSHA512 !=SHS_ID(pCtx), ippStsContextMatchErr);

   CopyBlock(pCtx, pBuffer, sizeof(IppsSHA512State));
   return ippStsNoErr;
}
#endif

#if defined (_ENABLE_ALG_SHA384_)
IPPFUN(IppStatus, ippsSHA384Pack,(const IppsSHA384State* pCtx, Ipp8u* pBuffer))
{
   return ippsSHA512Pack(pCtx, pBuffer);
}
#endif


/*F*
//    Name: ippsSHA512Unpack
//          ippsSHA384Unpack
//
// Purpose: Unpack buffer content into the initialized context.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSize == NULL
//                            pCtx == NULL
//    ippStsNoErr             no errors
//
// Parameters:
//    pCtx        pointer hash state
//    pSize       pointer to the packed spec size
//
*F*/
#if defined (_ENABLE_ALG_SHA512_) || defined (_ENABLE_ALG_SHA384_)
IPPFUN(IppStatus, ippsSHA512Unpack,(const Ipp8u* pBuffer, IppsSHA512State* pCtx))
{
   /* test pointers */
   IPP_BAD_PTR2_RET(pCtx, pBuffer);
   /* use aligned context */
   pCtx = (IppsSHA512State*)( IPP_ALIGNED_PTR(pCtx, SHA512_ALIGNMENT) );

   CopyBlock(pBuffer, pCtx, sizeof(IppsSHA512State));
   return ippStsNoErr;
}
#endif

#if defined (_ENABLE_ALG_SHA384_)
IPPFUN(IppStatus, ippsSHA384Unpack,(const Ipp8u* pBuffer, IppsSHA384State* pCtx))
{
   return ippsSHA512Unpack(pBuffer, pCtx);
}
#endif


/*F*
//    Name: ippsSHA512Duplicate
//          ippsSHA384Duplicate
//
// Purpose: Clone SHA512 state.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSrcState == NULL
//                            pDstState == NULL
//    ippStsContextMatchErr   pSrcState->idCtx != idCtxSHA512
//                            pDstState->idCtx != idCtxSHA512
//    ippStsNoErr             no errors
//
// Parameters:
//    pSrcState   pointer to the source SHA512 state
//    pDstState   pointer to the target SHA512 state
// Note:
//    pDstState may to be uninitialized by ippsSHA512Init()
//
*F*/
#if defined (_ENABLE_ALG_SHA512_) || defined (_ENABLE_ALG_SHA384_)
IPPFUN(IppStatus, ippsSHA512Duplicate,(const IppsSHA512State* pSrcState, IppsSHA512State* pDstState))
{
   /* test state pointers */
   IPP_BAD_PTR2_RET(pSrcState, pDstState);
   /* use aligned context */
   pSrcState = (IppsSHA512State*)( IPP_ALIGNED_PTR(pSrcState, SHA512_ALIGNMENT) );
   pDstState = (IppsSHA512State*)( IPP_ALIGNED_PTR(pDstState, SHA512_ALIGNMENT) );
   /* test states ID */
   IPP_BADARG_RET(idCtxSHA512 !=SHS_ID(pSrcState), ippStsContextMatchErr);
   //IPP_BADARG_RET(idCtxSHA512 !=SHS_ID(pDstState), ippStsContextMatchErr);

   /* copy state */
   CopyBlock(pSrcState, pDstState, sizeof(IppsSHA512State));

   return ippStsNoErr;
}
#endif

#if defined (_ENABLE_ALG_SHA384_)
IPPFUN(IppStatus, ippsSHA384Duplicate,(const IppsSHA384State* pSrcState, IppsSHA384State* pDstState))
{
   return ippsSHA512Duplicate(pSrcState, pDstState);
}
#endif


/*F*
//    Name: ippsSHA512Update
//          ippsSHA384Update
//
// Purpose: Updates intermadiate digest based on input stream.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSrc == NULL
//                            pState == NULL
//    ippStsContextMatchErr   pState->idCtx != idCtxSHA512
//    ippStsLengthErr         len <0
//    ippStsNoErr             no errors
//
// Parameters:
//    pSrc        pointer to the input stream
//    len         input stream length
//    pState      pointer to the SHA512 state
//
*F*/
#if defined (_ENABLE_ALG_SHA512_) || defined (_ENABLE_ALG_SHA384_)
IPPFUN(IppStatus, ippsSHA512Update,(const Ipp8u* pSrc, int len, IppsSHA512State* pState))
{
   /* test state pointer and ID */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsSHA512State*)( IPP_ALIGNED_PTR(pState, SHA512_ALIGNMENT) );

   /* test state ID */
   IPP_BADARG_RET(idCtxSHA512 !=SHS_ID(pState), ippStsContextMatchErr);
   /* test input length */
   IPP_BADARG_RET((len<0), ippStsLengthErr);
   /* test source pointer */
   IPP_BADARG_RET((len && !pSrc), ippStsNullPtrErr);

   /*
   // handle non empty message
   */
   if(len) {
      int processingLen;

      int n = SHS_INDX(pState);
      Ipp8u* pBuffer = SHS_BUFF(pState);
      Ipp8u* pHash = (Ipp8u*)SHS_HASH(pState);

      Ipp64u lenLo = SHS_LENL(pState);
      Ipp64u lenHi = SHS_LENH(pState);
      lenLo += len;
      if(lenLo < SHS_LENL(pState)) lenHi++;

      /* if non empty internal buffer filling */
      if(n) {
         /* copy from input stream to the internal buffer as match as possible */
         processingLen = IPP_MIN(len, (MBS_SHA512-n));
         CopyBlock(pSrc, pBuffer+n, processingLen);

         pSrc += processingLen;
         len  -= processingLen;
         SHS_INDX(pState) = n += processingLen;

         /* update digest if buffer full */
         if(MBS_SHA512 == n) {
            UpdateSHA512(pHash, pBuffer, MBS_SHA512, SHA512_cnt);
            SHS_INDX(pState) = 0;
         }
      }

      /* main message part processing */
      processingLen = len & ~(MBS_SHA512-1);
      if(processingLen) {
         UpdateSHA512(pHash, pSrc, processingLen, SHA512_cnt);
         pSrc += processingLen;
         len  -= processingLen;
      }

      /* store rest of message into the internal buffer */
      if(len) {
         CopyBlock(pSrc, pBuffer, len);
         SHS_INDX(pState) += len;
      }

      SHS_LENL(pState) = lenLo;
      SHS_LENH(pState) = lenHi;
   }

   return ippStsNoErr;
}
#endif

#if defined (_ENABLE_ALG_SHA384_)
IPPFUN(IppStatus, ippsSHA384Update,(const Ipp8u* pSrc, int len, IppsSHA384State* pState))
{
   return ippsSHA512Update(pSrc, len, pState);
}
#endif


/*
// Compute digest
*/
#if defined (_ENABLE_ALG_SHA512_) || defined (_ENABLE_ALG_SHA384_)
void ComputeDigestSHA512(Ipp64u* pHash, const IppsSHA512State* pState)
{
   const Ipp8u* stateBuff = SHS_BUFF(pState);
   int stateBuffLen = SHS_INDX(pState);

   /* local buffer and it length */
   Ipp8u buffer[MBS_SHA512*2];
   int bufferLen = stateBuffLen < (MBS_SHA512-(int)sizeof(Ipp64u)*2)? MBS_SHA512 : MBS_SHA512*2; 

   /* copy rest of message into internal buffer */
   CopyBlock(stateBuff, buffer, stateBuffLen);

   /* padd message */
   buffer[stateBuffLen++] = 0x80;
   PaddBlock(0, buffer+stateBuffLen, bufferLen-stateBuffLen-sizeof(Ipp64u)*2);

   /* message length representation */
   {
      Ipp64u lo = SHS_LENL(pState);      /* message length in bytes */
      Ipp64u hi = SHS_LENH(pState);
      hi = LSL64(hi,3) | LSR64(lo,63-3); /* message length in bits */
      lo = LSL64(lo,3);
      ((Ipp64u*)(buffer+bufferLen))[-2] = ENDIANNESS64(hi);
      ((Ipp64u*)(buffer+bufferLen))[-1] = ENDIANNESS64(lo);
   }

   /* copmplete hash computation */
   UpdateSHA512(pHash, buffer, bufferLen, SHA512_cnt);

   /* convert hash into big endian */
   pHash[0] = ENDIANNESS64(pHash[0]);
   pHash[1] = ENDIANNESS64(pHash[1]);
   pHash[2] = ENDIANNESS64(pHash[2]);
   pHash[3] = ENDIANNESS64(pHash[3]);
   pHash[4] = ENDIANNESS64(pHash[4]);
   pHash[5] = ENDIANNESS64(pHash[5]);
   pHash[6] = ENDIANNESS64(pHash[6]);
   pHash[7] = ENDIANNESS64(pHash[7]);
}
#endif


/*F*
//    Name: ippsSHA512GetTag
//          ippsSHA384GetTag
//
// Purpose: Compute digest based on current state.
//          Note, that futher digest update is possible
//
// Returns:                Reason:
//    ippStsNullPtrErr        pTag == NULL
//                            pState == NULL
//    ippStsContextMatchErr   pState->idCtx != idCtxSHA512
//    ippStsLengthErr         max_SHA_digestLen < tagLen <1
//    ippStsNoErr             no errors
//
// Parameters:
//    pTag        address of the output digest
//    tagLen      length of digest
//    pState      pointer to the SHS state
//
*F*/
#if defined (_ENABLE_ALG_SHA512_)
IPPFUN(IppStatus, ippsSHA512GetTag,(Ipp8u* pTag, Ipp32u tagLen, const IppsSHA512State* pState))
{
   /* test state pointer and ID */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsSHA512State*)( IPP_ALIGNED_PTR(pState, SHA512_ALIGNMENT) );
   /* test state ID */
   IPP_BADARG_RET(idCtxSHA512 !=SHS_ID(pState), ippStsContextMatchErr);

   /* test digest pointer */
   IPP_BAD_PTR1_RET(pTag);
   IPP_BADARG_RET((tagLen<1)||(sizeof(DigestSHA512)<tagLen), ippStsLengthErr);

   {
      DigestSHA512 digest;

      CopyBlock(SHS_HASH(pState), digest, sizeof(DigestSHA512));
      ComputeDigestSHA512(digest, pState);
      CopyBlock(digest, pTag, tagLen);

      return ippStsNoErr;
   }
}
#endif

#if defined (_ENABLE_ALG_SHA384_)
IPPFUN(IppStatus, ippsSHA384GetTag,(Ipp8u* pTag, Ipp32u tagLen, const IppsSHA384State* pState))
{
   /* test state pointer and ID */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsSHA384State*)( IPP_ALIGNED_PTR(pState, SHA512_ALIGNMENT) );
   /* test state ID */
   IPP_BADARG_RET(idCtxSHA512 !=SHS_ID(pState), ippStsContextMatchErr);

   /* test digest pointer */
   IPP_BAD_PTR1_RET(pTag);
   IPP_BADARG_RET((tagLen<1)||(sizeof(DigestSHA384)<tagLen), ippStsLengthErr);

   {
      DigestSHA512 digest;

      CopyBlock(SHS_HASH(pState), digest, sizeof(DigestSHA512));
      ComputeDigestSHA512(digest, pState);
      CopyBlock(digest, pTag, tagLen);

      return ippStsNoErr;
   }
}
#endif


/*F*
//    Name: ippsSHA512Final
//          ippsSHA384Final
//
// Purpose: Stop message digesting and return digest.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pDigest == NULL
//                            pState == NULL
//    ippStsContextMatchErr   pState->idCtx != idCtxSHA512
//    ippStsNoErr             no errors
//
// Parameters:
//    pMD         address of the output digest
//    pState      pointer to the SHA512 state
//
*F*/
#if defined (_ENABLE_ALG_SHA512_)
IPPFUN(IppStatus, ippsSHA512Final,(Ipp8u* pMD, IppsSHA512State* pState))
{
   /* test state pointer and ID */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsSHA512State*)( IPP_ALIGNED_PTR(pState, SHA512_ALIGNMENT) );
   /* test state ID */
   IPP_BADARG_RET(idCtxSHA512 !=SHS_ID(pState), ippStsContextMatchErr);

   /* test digest pointer */
   IPP_BAD_PTR1_RET(pMD);

   ComputeDigestSHA512(SHS_HASH(pState), pState);
   CopyBlock(SHS_HASH(pState), pMD, sizeof(DigestSHA512));
   InitSHA512(SHA512_IV, pState);

   return ippStsNoErr;
}
#endif

#if defined (_ENABLE_ALG_SHA384_)
IPPFUN(IppStatus, ippsSHA384Final,(Ipp8u* pMD, IppsSHA384State* pState))
{
   /* test state pointer and ID */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsSHA384State*)( IPP_ALIGNED_PTR(pState, SHA512_ALIGNMENT) );
   /* test state ID */
   IPP_BADARG_RET(idCtxSHA512 !=SHS_ID(pState), ippStsContextMatchErr);

   /* test digest pointer */
   IPP_BAD_PTR1_RET(pMD);

   ComputeDigestSHA512(SHS_HASH(pState), pState);
   CopyBlock(SHS_HASH(pState), pMD, sizeof(DigestSHA384));
   InitSHA512(SHA384_IV, pState);

   return ippStsNoErr;
}
#endif


#if defined (_ENABLE_ALG_SHA512_) || defined (_ENABLE_ALG_SHA384_)
IppStatus cpSHA512MessageDigest(DigestSHA512 hash, const Ipp8u* pMsg, int msgLen, const DigestSHA512 IV)
{
   /* test digest pointer */
   IPP_BAD_PTR1_RET(hash);
   /* test message length */
   IPP_BADARG_RET((msgLen<0), ippStsLengthErr);
   /* test message pointer */
   IPP_BADARG_RET((msgLen && !pMsg), ippStsNullPtrErr);

   {
      /* message length in the multiple MBS and the rest */
      int msgLenBlks = msgLen & (-MBS_SHA512);
      int msgLenRest = msgLen - msgLenBlks;

      /* setup initial digest */
      hash[0] = IV[0];
      hash[1] = IV[1];
      hash[2] = IV[2];
      hash[3] = IV[3];
      hash[4] = IV[4];
      hash[5] = IV[5];
      hash[6] = IV[6];
      hash[7] = IV[7];

      /* process main part of the message */
      if(msgLenBlks)
         UpdateSHA512(hash, pMsg, msgLenBlks, SHA512_cnt);

      /* process message padding */
      {
         #define MREP_SIZE_SHA512   (2*sizeof(Ipp64u))
         Ipp8u buffer[MBS_SHA512*2];
         int bufferLen = msgLenRest < (int)(MBS_SHA512-MREP_SIZE_SHA512)? MBS_SHA512 : MBS_SHA512*2;

         /* message bitlength representation */
         Ipp64u msgLenBits = (Ipp64u)msgLen*8;
         msgLenBits = ENDIANNESS64(msgLenBits);

         /* copy end of message */
         CopyBlock(pMsg+msgLen-msgLenRest, buffer, msgLenRest);

         /* end of message bit */
         buffer[msgLenRest++] = 0x80;

         /* padd buffer */
         PaddBlock(0, buffer+msgLenRest, bufferLen-msgLenRest-MREP_SIZE_SHA512+sizeof(Ipp64u));
         /* copy message bitlength representation */
         ((Ipp64u*)(buffer+bufferLen))[-1] = msgLenBits;

         UpdateSHA512(hash, buffer, bufferLen, SHA512_cnt);
         #undef MREP_SIZE_SHA512
      }

      /* swap hash bytes */
      hash[0] = ENDIANNESS64(hash[0]);
      hash[1] = ENDIANNESS64(hash[1]);
      hash[2] = ENDIANNESS64(hash[2]);
      hash[3] = ENDIANNESS64(hash[3]);
      hash[4] = ENDIANNESS64(hash[4]);
      hash[5] = ENDIANNESS64(hash[5]);
      hash[6] = ENDIANNESS64(hash[6]);
      hash[7] = ENDIANNESS64(hash[7]);

      return ippStsNoErr;
   }
}
#endif

/*F*
//    Name: ippsSHA512MessageDigest
//          ippsSHA384MessageDigest
//
// Purpose: Digest of the whole message.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pMsg == NULL
//                            pDigest == NULL
//    ippStsLengthErr         len <0
//    ippStsNoErr             no errors
//
// Parameters:
//    pMsg        pointer to the input message
//    len         input message length
//    pMD         address of the output digest
//
*F*/
#if defined (_ENABLE_ALG_SHA512_)
IPPFUN(IppStatus, ippsSHA512MessageDigest,(const Ipp8u* pMsg, int msgLen, Ipp8u* pMD))
{
   /* test digest pointer */
   IPP_BAD_PTR1_RET(pMD);

   {
      DigestSHA512 hash;
      IppStatus sts = cpSHA512MessageDigest(hash, pMsg, msgLen, SHA512_IV);
      if(ippStsNoErr==sts)
         CopyBlock(hash, pMD, IPP_SHA512_DIGEST_BITSIZE/BYTESIZE);
      return sts;
   }
}
#endif

#if defined (_ENABLE_ALG_SHA384_)
IPPFUN(IppStatus, ippsSHA384MessageDigest,(const Ipp8u* pMsg, int msgLen, Ipp8u* pMD))
{
   /* test digest pointer */
   IPP_BAD_PTR1_RET(pMD);

   {
      DigestSHA512 hash;
      IppStatus sts = cpSHA512MessageDigest(hash, pMsg, msgLen, SHA384_IV);
      if(ippStsNoErr==sts)
         CopyBlock(hash, pMD, IPP_SHA384_DIGEST_BITSIZE/BYTESIZE);
      return sts;
   }
}
#endif
