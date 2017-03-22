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
//     Digesting message according to SHA256
// 
//  Contents:
//     ippsSHA256GetSize()
//     ippsSHA256Init()
//     ippsSHA256Pack()
//     ippsSHA256Unpack()
//     ippsSHA256Duplicate()
//     ippsSHA256Update()
//     ippsSHA256GetTag()
//     ippsSHA256Final()
//     ippsSHA256MessageDigest()
// 
// 
*/

#include "precomp.h"
#include "owncp.h"
#include "pcphash.h"
#include "pcptool.h"


#if !defined(_ENABLE_ALG_SHA256_)
#pragma message("IPP_ALG_HASH_SHA256 disabled")
#else
#pragma message("IPP_ALG_HASH_SHA256 enabled")
#endif

#if !defined(_ENABLE_ALG_SHA224_)
#pragma message("IPP_ALG_HASH_SHA224 disabled")
#else
#pragma message("IPP_ALG_HASH_SHA224 enabled")
#endif


/*
// SHA256 init context
*/
#if defined (_ENABLE_ALG_SHA256_) || defined (_ENABLE_ALG_SHA224_)
IppStatus GetSizeSHA256(int* pSize)
{
   /* test pointer */
   IPP_BAD_PTR1_RET(pSize);

   *pSize = sizeof(IppsSHA256State) +(SHA256_ALIGNMENT-1);

   return ippStsNoErr;
}

IppStatus InitSHA256(const DigestSHA256 IV, IppsSHA256State* pState)
{
   /* test state pointer */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsSHA256State*)( IPP_ALIGNED_PTR(pState, SHA256_ALIGNMENT) );

   /* set state ID */
   SHS_ID(pState) = idCtxSHA256;

   /* zeros message length */
   SHS_LENL(pState) = 0;

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
//    Name: ippsSHA256GetSize
//          ippsSHA224GetSize
//
// Purpose: Returns size (bytes) of IppsSHA256State state.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSize == NULL
//    ippStsNoErr             no errors
//
// Parameters:
//    pSize       pointer to state size
//
*F*/
#if defined (_ENABLE_ALG_SHA256_)
IPPFUN(IppStatus, ippsSHA256GetSize,(int* pSize))
{
   return GetSizeSHA256(pSize);
}
#endif

#if defined (_ENABLE_ALG_SHA224_)
IPPFUN(IppStatus, ippsSHA224GetSize,(int* pSize))
{
   return GetSizeSHA256(pSize);
}
#endif


/*F*
//    Name: ippsSHA256Init
//          ippsSHA224Init
//
// Purpose: Init SHA256
//
// Returns:                Reason:
//    ippStsNullPtrErr        pState == NULL
//    ippStsNoErr             no errors
//
// Parameters:
//    pState      pointer to the SHA512 state
//
*F*/
#if defined (_ENABLE_ALG_SHA256_)
IPPFUN(IppStatus, ippsSHA256Init,(IppsSHA256State* pState))
{
   return InitSHA256(SHA256_IV, pState);
}
#endif

#if defined (_ENABLE_ALG_SHA224_)
IPPFUN(IppStatus, ippsSHA224Init,(IppsSHA224State* pState))
{
   return InitSHA256(SHA224_IV, pState);
}
#endif


/*F*
//    Name: ippsSHA256Pack
//          ippsSHA224Pack
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
#if defined (_ENABLE_ALG_SHA256_) || defined (_ENABLE_ALG_SHA224_)
IPPFUN(IppStatus, ippsSHA256Pack,(const IppsSHA256State* pCtx, Ipp8u* pBuffer))
{
   /* test pointers */
   IPP_BAD_PTR2_RET(pCtx, pBuffer);
   /* use aligned context */
   pCtx = (IppsSHA256State*)( IPP_ALIGNED_PTR(pCtx, SHA256_ALIGNMENT) );
   /* test the context */
   IPP_BADARG_RET(idCtxSHA256 !=SHS_ID(pCtx), ippStsContextMatchErr);

   CopyBlock(pCtx, pBuffer, sizeof(IppsSHA256State));
   return ippStsNoErr;
}
#endif

#if defined (_ENABLE_ALG_SHA224_)
IPPFUN(IppStatus, ippsSHA224Pack,(const IppsSHA224State* pCtx, Ipp8u* pBuffer))
{
   return ippsSHA256Pack(pCtx, pBuffer);
}
#endif

/*F*
//    Name: ippsSHA256Unpack
//          ippsSHA224Unpack
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
#if defined (_ENABLE_ALG_SHA256_) || defined (_ENABLE_ALG_SHA224_)
IPPFUN(IppStatus, ippsSHA256Unpack,(const Ipp8u* pBuffer, IppsSHA256State* pCtx))
{
   /* test pointers */
   IPP_BAD_PTR2_RET(pCtx, pBuffer);
   /* use aligned context */
   pCtx = (IppsSHA256State*)( IPP_ALIGNED_PTR(pCtx, SHA256_ALIGNMENT) );

   CopyBlock(pBuffer, pCtx, sizeof(IppsSHA256State));
   return ippStsNoErr;
}
#endif

#if defined (_ENABLE_ALG_SHA224_)
IPPFUN(IppStatus, ippsSHA224Unpack,(const Ipp8u* pBuffer, IppsSHA224State* pCtx))
{
   return ippsSHA256Unpack(pBuffer, pCtx);
}
#endif


/*F*
//    Name: ippsSHA256Duplicate
//          ippsSHA224Duplicate
//
// Purpose: Clone SHA256 state.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSrcState == NULL
//                            pDstState == NULL
//    ippStsContextMatchErr   pSrcState->idCtx != idCtxSHA256
//                            pDstState->idCtx != idCtxSHA256
//    ippStsNoErr             no errors
//
// Parameters:
//    pSrcState   pointer to the source SHA256 state
//    pDstState   pointer to the target SHA256 state
//
// Note:
//    pDstState may to be uninitialized by ippsSHA256Init()
//
*F*/
#if defined (_ENABLE_ALG_SHA256_) || defined (_ENABLE_ALG_SHA224_)
IPPFUN(IppStatus, ippsSHA256Duplicate,(const IppsSHA256State* pSrcState, IppsSHA256State* pDstState))
{
   /* test state pointers */
   IPP_BAD_PTR2_RET(pSrcState, pDstState);
   /* use aligned context */
   pSrcState = (IppsSHA256State*)( IPP_ALIGNED_PTR(pSrcState, SHA256_ALIGNMENT) );
   pDstState = (IppsSHA256State*)( IPP_ALIGNED_PTR(pDstState, SHA256_ALIGNMENT) );
   /* test states ID */
   IPP_BADARG_RET(idCtxSHA256 !=SHS_ID(pSrcState), ippStsContextMatchErr);
   //IPP_BADARG_RET(idCtxSHA256 !=SHS_ID(pDstState), ippStsContextMatchErr);

   /* copy state */
   CopyBlock(pSrcState, pDstState, sizeof(IppsSHA256State));

   return ippStsNoErr;
}
#endif

#if defined (_ENABLE_ALG_SHA224_)
IPPFUN(IppStatus, ippsSHA224Duplicate,(const IppsSHA224State* pSrcState, IppsSHA224State* pDstState))
{
   return ippsSHA256Duplicate(pSrcState, pDstState);
}
#endif


/*F*
//    Name: ippsSHA256Update
//          ippsSHA224Update
//
// Purpose: Updates intermadiate digest based on input stream.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSrc == NULL
//                            pState == NULL
//    ippStsContextMatchErr   pState->idCtx != idCtxSHA256
//    ippStsLengthErr         len <0
//    ippStsNoErr             no errors
//
// Parameters:
//    pSrc        pointer to the input stream
//    len         input stream length
//    pState      pointer to the SHA256 state
//
*F*/
#if defined (_ENABLE_ALG_SHA256_) || defined (_ENABLE_ALG_SHA224_)
IPPFUN(IppStatus, ippsSHA256Update,(const Ipp8u* pSrc, int len, IppsSHA256State* pState))
{
   /* test state pointer and ID */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsSHA256State*)( IPP_ALIGNED_PTR(pState, SHA256_ALIGNMENT) );
   /* test state ID */
   IPP_BADARG_RET(idCtxSHA256 !=SHS_ID(pState), ippStsContextMatchErr);

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

      /* select processing function */
#if 0
      cpHashProc updateFunc = UpdateSHA256;
      #if (_IPP>=_IPP_P8) || (_IPP32E>=_IPP32E_Y8)
      if( IsFeatureEnabled(SHA_NI_ENABLED) )
         updateFunc = UpdateSHA256ni;
      #endif
#endif
      cpHashProc updateFunc;
      #if (_SHA_NI_ENABLING_==_FEATURE_ON_)
      updateFunc = UpdateSHA256ni;
      #else
         #if (_SHA_NI_ENABLING_==_FEATURE_TICKTOCK_)
         if( IsFeatureEnabled(SHA_NI_ENABLED) )
            updateFunc = UpdateSHA256ni;
         else
         #endif
            updateFunc = UpdateSHA256;
      #endif

      lenLo += len;

      /* if non empty internal buffer filling */
      if(n) {
         /* copy from input stream to the internal buffer as match as possible */
         processingLen = IPP_MIN(len, (MBS_SHA256 - SHS_INDX(pState)));
         CopyBlock(pSrc, pBuffer+n, processingLen);

         pSrc += processingLen;
         len  -= processingLen;
         SHS_INDX(pState) = n += processingLen;

         /* update digest if buffer full */
         if( MBS_SHA256 == n) {
            updateFunc(pHash, pBuffer, MBS_SHA256, SHA256_cnt);
            SHS_INDX(pState) = 0;
         }
      }

      /* main message part processing */
      processingLen = len & ~(MBS_SHA256-1);
      if(processingLen) {
         updateFunc(pHash, pSrc, processingLen, SHA256_cnt);
         pSrc += processingLen;
         len  -= processingLen;
      }

      /* store rest of message into the internal buffer */
      if(len) {
         CopyBlock(pSrc, pBuffer, len);
         SHS_INDX(pState) += len;
      }

      SHS_LENL(pState) = lenLo;
   }

   return ippStsNoErr;
}
#endif

#if defined (_ENABLE_ALG_SHA224_)
IPPFUN(IppStatus, ippsSHA224Update,(const Ipp8u* pSrc, int len, IppsSHA224State* pState))
{
   return ippsSHA256Update(pSrc, len, pState);
}
#endif


/*
// Compute digest
*/
#if defined (_ENABLE_ALG_SHA256_) || defined (_ENABLE_ALG_SHA224_)
void ComputeDigestSHA256(Ipp32u* pHash, const IppsSHA256State* pState)
{
   const Ipp8u* stateBuff = SHS_BUFF(pState);
   int stateBuffLen = SHS_INDX(pState);

   /* local buffer and it length */
   Ipp8u buffer[MBS_SHA256*2];
   int bufferLen = stateBuffLen < (MBS_SHA1-(int)sizeof(Ipp64u))? MBS_SHA256 : MBS_SHA256*2; 

   /* select processing  function */
#if 0
   cpHashProc updateFunc = UpdateSHA256;
   #if (_IPP>=_IPP_P8) || (_IPP32E>=_IPP32E_Y8)
   if( IsFeatureEnabled(SHA_NI_ENABLED) )
      updateFunc = UpdateSHA256ni;
   #endif
#endif
   cpHashProc updateFunc;
   #if (_SHA_NI_ENABLING_==_FEATURE_ON_)
   updateFunc = UpdateSHA256ni;
   #else
      #if (_SHA_NI_ENABLING_==_FEATURE_TICKTOCK_)
      if( IsFeatureEnabled(SHA_NI_ENABLED) )
         updateFunc = UpdateSHA256ni;
      else
      #endif
         updateFunc = UpdateSHA256;
   #endif

   /* copy rest of message into internal buffer */
   CopyBlock(stateBuff, buffer, stateBuffLen);

   /* padd message */
   buffer[stateBuffLen++] = 0x80;
   PaddBlock(0, buffer+stateBuffLen, bufferLen-stateBuffLen-sizeof(Ipp64u));

   /* message length representation */
   {
      Ipp64u lo = SHS_LENL(pState);      /* message length in bytes */
      lo = LSL64(lo,3);                  /* message length in bits */
      ((Ipp64u*)(buffer+bufferLen))[-1] = ENDIANNESS64(lo);
   }

   /* copmplete hash computation */
   updateFunc(pHash, buffer, bufferLen, SHA256_cnt);

   /* convert hash into big endian */
   pHash[0] = ENDIANNESS32(pHash[0]);
   pHash[1] = ENDIANNESS32(pHash[1]);
   pHash[2] = ENDIANNESS32(pHash[2]);
   pHash[3] = ENDIANNESS32(pHash[3]);
   pHash[4] = ENDIANNESS32(pHash[4]);
   pHash[5] = ENDIANNESS32(pHash[5]);
   pHash[6] = ENDIANNESS32(pHash[6]);
   pHash[7] = ENDIANNESS32(pHash[7]);
}
#endif


/*F*
//    Name: ippsSHA256GetTag
//          ippsSHA224GetTag
//
// Purpose: Compute digest based on current state.
//          Note, that futher digest update is possible
//
// Returns:                Reason:
//    ippStsNullPtrErr        pTag == NULL
//                            pState == NULL
//    ippStsContextMatchErr   pState->idCtx != idCtxSHA256
//    ippStsLengthErr         max_SHA_digestLen < tagLen <1
//    ippStsNoErr             no errors
//
// Parameters:
//    pTag        address of the output digest
//    tagLen      length of digest
//    pState      pointer to the SHS state
//
*F*/
#if defined (_ENABLE_ALG_SHA256_)
IPPFUN(IppStatus, ippsSHA256GetTag,(Ipp8u* pTag, Ipp32u tagLen, const IppsSHA256State* pState))
{
   /* test state pointer and ID */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsSHA256State*)( IPP_ALIGNED_PTR(pState, SHA256_ALIGNMENT) );
   /* test state ID */
   IPP_BADARG_RET(idCtxSHA256 !=SHS_ID(pState), ippStsContextMatchErr);

   /* test digest pointer */
   IPP_BAD_PTR1_RET(pTag);
   IPP_BADARG_RET((tagLen<1)||(sizeof(DigestSHA256)<tagLen), ippStsLengthErr);

   {
      DigestSHA256 digest;

      CopyBlock(SHS_HASH(pState), digest, sizeof(DigestSHA256));
      ComputeDigestSHA256(digest, pState);
      CopyBlock(digest, pTag, tagLen);

      return ippStsNoErr;
   }
}
#endif

#if defined (_ENABLE_ALG_SHA224_)
IPPFUN(IppStatus, ippsSHA224GetTag,(Ipp8u* pTag, Ipp32u tagLen, const IppsSHA224State* pState))
{
   /* test state pointer and ID */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsSHA224State*)( IPP_ALIGNED_PTR(pState, SHA256_ALIGNMENT) );
   /* test state ID */
   IPP_BADARG_RET(idCtxSHA256 !=SHS_ID(pState), ippStsContextMatchErr);

   /* test digest pointer */
   IPP_BAD_PTR1_RET(pTag);
   IPP_BADARG_RET((tagLen<1)||(sizeof(DigestSHA224)<tagLen), ippStsLengthErr);

   {
      DigestSHA256 digest;

      CopyBlock(SHS_HASH(pState), digest, sizeof(DigestSHA256));
      ComputeDigestSHA256(digest, pState);
      CopyBlock(digest, pTag, tagLen);

      return ippStsNoErr;
   }
}
#endif


/*F*
//    Name: ippsSHA256Final
//          ippsSHA224Final
//
// Purpose: Stop message digesting and return digest.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pDigest == NULL
//                            pState == NULL
//    ippStsContextMatchErr   pState->idCtx != idCtxSHA256
//    ippStsNoErr             no errors
//
// Parameters:
//    pMD         address of the output digest
//    pState      pointer to the SHA256 state
//
*F*/
#if defined (_ENABLE_ALG_SHA256_)
IPPFUN(IppStatus, ippsSHA256Final,(Ipp8u* pMD, IppsSHA256State* pState))
{
   /* test state pointer and ID */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsSHA256State*)( IPP_ALIGNED_PTR(pState, SHA256_ALIGNMENT) );
   /* test state ID */
   IPP_BADARG_RET(idCtxSHA256 !=SHS_ID(pState), ippStsContextMatchErr);

   /* test digest pointer */
   IPP_BAD_PTR1_RET(pMD);

   ComputeDigestSHA256(SHS_HASH(pState), pState);
   CopyBlock(SHS_HASH(pState), pMD, sizeof(DigestSHA256));
   InitSHA256(SHA256_IV, pState);

   return ippStsNoErr;
}
#endif

#if defined (_ENABLE_ALG_SHA224_)
IPPFUN(IppStatus, ippsSHA224Final,(Ipp8u* pMD, IppsSHA224State* pState))
{
   /* test state pointer and ID */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsSHA224State*)( IPP_ALIGNED_PTR(pState, SHA256_ALIGNMENT) );
   /* test state ID */
   IPP_BADARG_RET(idCtxSHA256 !=SHS_ID(pState), ippStsContextMatchErr);

   /* test digest pointer */
   IPP_BAD_PTR1_RET(pMD);

   ComputeDigestSHA256(SHS_HASH(pState), pState);
   CopyBlock(SHS_HASH(pState), pMD, sizeof(DigestSHA224));
   InitSHA256(SHA224_IV, pState);

   return ippStsNoErr;
}
#endif


#if defined (_ENABLE_ALG_SHA256_) || defined (_ENABLE_ALG_SHA224_)
IppStatus cpSHA256MessageDigest(DigestSHA256 hash, const Ipp8u* pMsg, int msgLen, const DigestSHA256 IV)
{
   /* test digest pointer */
   IPP_BAD_PTR1_RET(hash);
   /* test message length */
   IPP_BADARG_RET((msgLen<0), ippStsLengthErr);
   /* test message pointer */
   IPP_BADARG_RET((msgLen && !pMsg), ippStsNullPtrErr);

   {
      /* message length in the multiple MBS and the rest */
      int msgLenBlks = msgLen & (-MBS_SHA256);
      int msgLenRest = msgLen - msgLenBlks;

      /* select processing function */
#if 0
      cpHashProc updateFunc = UpdateSHA256;
      #if (_IPP>=_IPP_P8) || (_IPP32E>=_IPP32E_Y8)
      if( IsFeatureEnabled(SHA_NI_ENABLED) )
         updateFunc = UpdateSHA256ni;
      #endif
#endif
      cpHashProc updateFunc;
      #if (_SHA_NI_ENABLING_==_FEATURE_ON_)
      updateFunc = UpdateSHA256ni;
      #else
         #if (_SHA_NI_ENABLING_==_FEATURE_TICKTOCK_)
         if( IsFeatureEnabled(SHA_NI_ENABLED) )
            updateFunc = UpdateSHA256ni;
         else
         #endif
            updateFunc = UpdateSHA256;
      #endif

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
         updateFunc(hash, pMsg, msgLenBlks, SHA256_cnt);

      /* process message padding */
      {
         #define MREP_SIZE_SHA256   (sizeof(Ipp64u))
         Ipp8u buffer[MBS_SHA256*2];
         int bufferLen = msgLenRest < (int)(MBS_SHA256-MREP_SIZE_SHA256)? MBS_SHA256 : MBS_SHA256*2;

         /* message bitlength representation */
         Ipp64u msgLenBits = (Ipp64u)msgLen*8;
         msgLenBits = ENDIANNESS64(msgLenBits);

         /* copy end of message */
         CopyBlock(pMsg+msgLen-msgLenRest, buffer, msgLenRest);

         /* end of message bit */
         buffer[msgLenRest++] = 0x80;

         /* padd buffer */
         PaddBlock(0, buffer+msgLenRest, bufferLen-msgLenRest-MREP_SIZE_SHA256);
         /* copy message bitlength representation */
         ((Ipp64u*)(buffer+bufferLen))[-1] = msgLenBits;

         updateFunc(hash, buffer, bufferLen, SHA256_cnt);
         #undef MREP_SIZE_SHA256
      }

      /* swap hash bytes */
      hash[0] = ENDIANNESS32(hash[0]);
      hash[1] = ENDIANNESS32(hash[1]);
      hash[2] = ENDIANNESS32(hash[2]);
      hash[3] = ENDIANNESS32(hash[3]);
      hash[4] = ENDIANNESS32(hash[4]);
      hash[5] = ENDIANNESS32(hash[5]);
      hash[6] = ENDIANNESS32(hash[6]);
      hash[7] = ENDIANNESS32(hash[7]);

      return ippStsNoErr;
   }
}
#endif

/*F*
//    Name: ippsSHA256MessageDigest,
//          ippsSHA224MessageDigest
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
#if defined (_ENABLE_ALG_SHA256_)
IPPFUN(IppStatus, ippsSHA256MessageDigest,(const Ipp8u* pMsg, int msgLen, Ipp8u* pMD))
{
   /* test digest pointer */
   IPP_BAD_PTR1_RET(pMD);

   {
      DigestSHA256 hash;
      IppStatus sts = cpSHA256MessageDigest(hash, pMsg, msgLen, SHA256_IV);
      if(ippStsNoErr==sts)
         CopyBlock(hash, pMD, IPP_SHA256_DIGEST_BITSIZE/BYTESIZE);
      return sts;
   }
}
#endif

#if defined (_ENABLE_ALG_SHA224_)
IPPFUN(IppStatus, ippsSHA224MessageDigest,(const Ipp8u* pMsg, int msgLen, Ipp8u* pMD))
{
   /* test digest pointer */
   IPP_BAD_PTR1_RET(pMD);

   {
      DigestSHA256 hash;
      IppStatus sts = cpSHA256MessageDigest(hash, pMsg, msgLen, SHA224_IV);
      if(ippStsNoErr==sts)
         CopyBlock(hash, pMD, IPP_SHA224_DIGEST_BITSIZE/BYTESIZE);
      return sts;
   }
}
#endif
