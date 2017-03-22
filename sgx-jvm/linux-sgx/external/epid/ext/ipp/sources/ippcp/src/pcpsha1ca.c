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
//     Digesting message according to SHA1
// 
//  Contents:
//   - ippsSHA1GetSize()
//   - ippsSHA1Init()
//   - ippsSHA1Pack()
//   - ippsSHA1Unpack()
//   - ippsSHA1Duplicate()
//   - ippsSHA1Update()
//   - ippsSHA1GetTag()
//   - ippsSHA1Final()
//     ippsSHA1MessageDigest()
// 
// 
*/

#include "precomp.h"
#include "owncp.h"
#include "pcphash.h"
#include "pcptool.h"


#if !defined (_ENABLE_ALG_SHA1_)
#pragma message("IPP_ALG_HASH_SHA1 disabled")
#else
#pragma message("IPP_ALG_HASH_SHA1 enabled")

/*
// Init SHA1 digest
*/
IppStatus InitSHA1(IppsSHA1State* pState)
{
   /* test state pointer */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsSHA1State*)( IPP_ALIGNED_PTR(pState, SHA1_ALIGNMENT) );

   /* set state ID */
   SHS_ID(pState) = idCtxSHA1;

   /* zeros message length */
   SHS_LENL(pState) = 0;

   /* message buffer is free */
   SHS_INDX(pState) = 0;

   /* setup initial digest */
   SHS_HASH(pState)[0] = SHA1_IV[0];
   SHS_HASH(pState)[1] = SHA1_IV[1];
   SHS_HASH(pState)[2] = SHA1_IV[2];
   SHS_HASH(pState)[3] = SHA1_IV[3];
   SHS_HASH(pState)[4] = SHA1_IV[4];

   return ippStsNoErr;
}


/*F*
//    Name: ippsSHA1GetSize
//
// Purpose: Returns size (bytes) of IppsSHA1State state.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSize == NULL
//    ippStsNoErr             no errors
//
// Parameters:
//    pSize       pointer to state size
//
*F*/
IPPFUN(IppStatus, ippsSHA1GetSize,(int* pSize))
{
   /* test pointer */
   IPP_BAD_PTR1_RET(pSize);

   *pSize = sizeof(IppsSHA1State) +(SHA1_ALIGNMENT-1);

   return ippStsNoErr;
}


/*F*
//    Name: ippsSHA1Init
//
// Purpose: Init SHA1 state.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pState == NULL
//    ippStsNoErr             no errors
//
// Parameters:
//    pState      pointer to the SHA1 state
//
*F*/
IPPFUN(IppStatus, ippsSHA1Init,(IppsSHA1State* pState))
{
   return InitSHA1(pState);
}


/*F*
//    Name: ippsSHA1Pack
//
// Purpose: Copy initialized context to the buffer.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSize == NULL
//                            pCtx == NULL
//    ippStsNoErr             no errors
//
// Parameters:
//    pCtx        pointer hach state
//    pSize       pointer to the packed spec size
//
*F*/
IPPFUN(IppStatus, ippsSHA1Pack,(const IppsSHA1State* pCtx, Ipp8u* pBuffer))
{
   /* test pointers */
   IPP_BAD_PTR2_RET(pCtx, pBuffer);
   /* use aligned context */
   pCtx = (IppsSHA1State*)( IPP_ALIGNED_PTR(pCtx, SHA1_ALIGNMENT) );
   /* test the context */
   IPP_BADARG_RET(idCtxSHA1 !=SHS_ID(pCtx), ippStsContextMatchErr);

   CopyBlock(pCtx, pBuffer, sizeof(IppsSHA1State));
   return ippStsNoErr;
}


/*F*
//    Name: ippsSHA1Unpack
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
IPPFUN(IppStatus, ippsSHA1Unpack,(const Ipp8u* pBuffer, IppsSHA1State* pCtx))
{
   /* test pointers */
   IPP_BAD_PTR2_RET(pCtx, pBuffer);
   /* use aligned context */
   pCtx = (IppsSHA1State*)( IPP_ALIGNED_PTR(pCtx, SHA1_ALIGNMENT) );

   CopyBlock(pBuffer, pCtx, sizeof(IppsSHA1State));
   return ippStsNoErr;
}


/*F*
//    Name: ippsSHA1Duplicate
//
// Purpose: Clone SHA1 state.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSrcState == NULL
//                            pDstState == NULL
//    ippStsContextMatchErr   pSrcState->idCtx != idCtxSHA1
//                            pDstState->idCtx != idCtxSHA1
//    ippStsNoErr             no errors
//
// Parameters:
//    pSrcState   pointer to the source SHA1 state
//    pDstState   pointer to the target SHA1 state
//
// Note:
//    pDstState may to be uninitialized by ippsSHA1Init()
//
*F*/
IPPFUN(IppStatus, ippsSHA1Duplicate,(const IppsSHA1State* pSrcState, IppsSHA1State* pDstState))
{
   /* test state pointers */
   IPP_BAD_PTR2_RET(pSrcState, pDstState);
   /* use aligned context */
   pSrcState = (IppsSHA1State*)( IPP_ALIGNED_PTR(pSrcState, SHA1_ALIGNMENT) );
   pDstState = (IppsSHA1State*)( IPP_ALIGNED_PTR(pDstState, SHA1_ALIGNMENT) );
   /* test states ID */
   IPP_BADARG_RET(idCtxSHA1 !=SHS_ID(pSrcState), ippStsContextMatchErr);
   //IPP_BADARG_RET(idCtxSHA1 !=SHS_ID(pDstState), ippStsContextMatchErr);

   /* copy state */
   CopyBlock(pSrcState, pDstState, sizeof(IppsSHA1State));

   return ippStsNoErr;
}


/*F*
//    Name: ippsSHA1Update
//
// Purpose: Updates intermadiate digest based on input stream.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSrc == NULL
//                            pState == NULL
//    ippStsContextMatchErr   pState->idCtx != idCtxSHA1
//    ippStsLengthErr         len <0
//    ippStsNoErr             no errors
//
// Parameters:
//    pSrc        pointer to the input stream
//    len         input stream length
//    pState      pointer to the SHA1 state
//
*F*/
IPPFUN(IppStatus, ippsSHA1Update,(const Ipp8u* pSrc, int len, IppsSHA1State* pState))
{
   /* test state pointer and ID */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsSHA1State*)( IPP_ALIGNED_PTR(pState, SHA1_ALIGNMENT) );
   /* test state ID */
   IPP_BADARG_RET(idCtxSHA1 !=SHS_ID(pState), ippStsContextMatchErr);

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
      cpHashProc updateFunc = UpdateSHA1;
      #if (_IPP>=_IPP_P8) || (_IPP32E>=_IPP32E_Y8)
      if( IsFeatureEnabled(SHA_NI_ENABLED) )
         updateFunc = UpdateSHA1ni;
      #endif
#endif
      cpHashProc updateFunc;
      #if (_SHA_NI_ENABLING_==_FEATURE_ON_)
      updateFunc = UpdateSHA1ni;
      #else
         #if (_SHA_NI_ENABLING_==_FEATURE_TICKTOCK_)
         if( IsFeatureEnabled(SHA_NI_ENABLED) )
            updateFunc = UpdateSHA1ni;
         else
         #endif
            updateFunc = UpdateSHA1;
      #endif

      lenLo += len;

      /* if non empty internal buffer filling */
      if(n) {
         /* copy from input stream to the internal buffer as match as possible */
         processingLen = IPP_MIN(len, (MBS_SHA1-n));
         CopyBlock(pSrc, pBuffer+n, processingLen);

         pSrc += processingLen;
         len  -= processingLen;
         SHS_INDX(pState) = n += processingLen;

         /* update digest if buffer full */
         if( MBS_SHA1 == n) {
            updateFunc(pHash, pBuffer, MBS_SHA1, SHA1_cnt);
            SHS_INDX(pState) = 0;
         }
      }

      /* main message part processing */
      processingLen = len & ~(MBS_SHA1-1);
      if(processingLen) {
         updateFunc(pHash, pSrc, processingLen, SHA1_cnt);
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


/*
// Compute digest
*/
void ComputeDigestSHA1(Ipp32u* pHash, const IppsSHA1State* pState)
{
   const Ipp8u* stateBuff = SHS_BUFF(pState);
   int stateBuffLen = SHS_INDX(pState);

   /* local buffer and it length */
   Ipp8u buffer[MBS_SHA1*2];
   int bufferLen = stateBuffLen < (MBS_SHA1-(int)sizeof(Ipp64u))? MBS_SHA1 : MBS_SHA1*2; 

   /* select processing function */
#if 0
   cpHashProc updateFunc = UpdateSHA1;
   #if (_IPP>=_IPP_P8) || (_IPP32E>=_IPP32E_Y8)
   if( IsFeatureEnabled(SHA_NI_ENABLED) )
      updateFunc = UpdateSHA1ni;
   #endif
#endif
   cpHashProc updateFunc;
   #if (_SHA_NI_ENABLING_==_FEATURE_ON_)
   updateFunc = UpdateSHA1ni;
   #else
      #if (_SHA_NI_ENABLING_==_FEATURE_TICKTOCK_)
      if( IsFeatureEnabled(SHA_NI_ENABLED) )
         updateFunc = UpdateSHA1ni;
      else
      #endif
         updateFunc = UpdateSHA1;
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
   updateFunc(pHash, buffer, bufferLen, SHA1_cnt);

   /* convert hash into big endian */
   pHash[0] = ENDIANNESS32(pHash[0]);
   pHash[1] = ENDIANNESS32(pHash[1]);
   pHash[2] = ENDIANNESS32(pHash[2]);
   pHash[3] = ENDIANNESS32(pHash[3]);
   pHash[4] = ENDIANNESS32(pHash[4]);
}


/*F*
//    Name: ippsSHA1GetTag
//
// Purpose: Compute digest based on current state.
//          Note, that futher digest update is possible
//
// Returns:                Reason:
//    ippStsNullPtrErr        pTag == NULL
//                            pState == NULL
//    ippStsContextMatchErr   pState->idCtx != idCtxSHA1
//    ippStsLengthErr         max_SHA_digestLen < tagLen <1
//    ippStsNoErr             no errors
//
// Parameters:
//    pTag        address of the output digest
//    tagLen      length of digest
//    pState      pointer to the SHS state
//
*F*/
IPPFUN(IppStatus, ippsSHA1GetTag,(Ipp8u* pTag, Ipp32u tagLen, const IppsSHA1State* pState))
{
   /* test state pointer and ID */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsSHA1State*)( IPP_ALIGNED_PTR(pState, SHA1_ALIGNMENT) );
   /* test state ID */
   IPP_BADARG_RET(idCtxSHA1 !=SHS_ID(pState), ippStsContextMatchErr);

   /* test digest pointer */
   IPP_BAD_PTR1_RET(pTag);
   IPP_BADARG_RET((tagLen<1)||(sizeof(DigestSHA1)<tagLen), ippStsLengthErr);

   {
      DigestSHA1 digest;

      CopyBlock(SHS_HASH(pState), digest, sizeof(DigestSHA1));
      ComputeDigestSHA1(digest, pState);
      CopyBlock(digest, pTag, tagLen);

      return ippStsNoErr;
   }
}


/*F*
//    Name: ippsSHA1Final
//
// Purpose: Stop message digesting and return digest.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pMD == NULL
//                            pState == NULL
//    ippStsContextMatchErr   pState->idCtx != idCtxSHA1
//    ippStsNoErr             no errors
//
// Parameters:
//    pMD         address of the output digest
//    pState      pointer to the SHS state
//
*F*/
IPPFUN(IppStatus, ippsSHA1Final,(Ipp8u* pMD, IppsSHA1State* pState))
{
   /* test state pointer and ID */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsSHA1State*)( IPP_ALIGNED_PTR(pState, SHA1_ALIGNMENT) );
   /* test state ID */
   IPP_BADARG_RET(idCtxSHA1 !=SHS_ID(pState), ippStsContextMatchErr);

   /* test digest pointer */
   IPP_BAD_PTR1_RET(pMD);

   ComputeDigestSHA1(SHS_HASH(pState), pState);
   CopyBlock(SHS_HASH(pState), pMD, sizeof(DigestSHA1));
   InitSHA1(pState);

   return ippStsNoErr;
}


/*F*
//    Name: ippsSHA1MessageDigest
//
// Purpose: Digest of the whole message.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pMsg == NULL
//                            pMD == NULL
//    ippStsLengthErr         len <0
//    ippStsNoErr             no errors
//
// Parameters:
//    pMsg        pointer to the input message
//    len         input message length
//    pMD         address of the output digest
//
*F*/
IPPFUN(IppStatus, ippsSHA1MessageDigest,(const Ipp8u* pMsg, int msgLen, Ipp8u* pMD))
{
   /* test digest pointer */
   IPP_BAD_PTR1_RET(pMD);
   /* test message length */
   IPP_BADARG_RET((msgLen<0), ippStsLengthErr);
   /* test message pointer */
   IPP_BADARG_RET((msgLen && !pMsg), ippStsNullPtrErr);

   {
      /* message length in the multiple MBS and the rest */
      int msgLenBlks = msgLen & (-MBS_SHA1);
      int msgLenRest = msgLen - msgLenBlks;

      /* init hash value */
      DigestSHA1 hash = {0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476, 0xC3D2E1F0};

      /* select processing function */
#if 0
      cpHashProc updateFunc = UpdateSHA1;
      #if (_IPP>=_IPP_P8) || (_IPP32E>=_IPP32E_Y8)
      if( IsFeatureEnabled(SHA_NI_ENABLED) )
         updateFunc = UpdateSHA1ni;
      #endif
#endif
      cpHashProc updateFunc;
      #if (_SHA_NI_ENABLING_==_FEATURE_ON_)
      updateFunc = UpdateSHA1ni;
      #else
         #if (_SHA_NI_ENABLING_==_FEATURE_TICKTOCK_)
         if( IsFeatureEnabled(SHA_NI_ENABLED) )
            updateFunc = UpdateSHA1ni;
         else
         #endif
            updateFunc = UpdateSHA1;
      #endif

      /* process main part of the message */
      if(msgLenBlks)
         updateFunc(hash, pMsg, msgLenBlks, SHA1_cnt);

      /* process message padding */
      {
         #define MREP_SIZE_SHA1  (sizeof(Ipp64u))
         Ipp8u buffer[MBS_SHA1*2];
         int bufferLen = msgLenRest < (int)(MBS_SHA1-MREP_SIZE_SHA1)? MBS_SHA1 : MBS_SHA1*2;

         /* message bitlength representation */
         Ipp64u msgLenBits = (Ipp64u)msgLen*8;
         msgLenBits = ENDIANNESS64(msgLenBits);

         /* copy end of message */
         CopyBlock(pMsg+msgLen-msgLenRest, buffer, msgLenRest);

         /* end of message bit */
         buffer[msgLenRest++] = 0x80;

         /* padd buffer */
         PaddBlock(0, buffer+msgLenRest, bufferLen-msgLenRest-MREP_SIZE_SHA1);
         /* copy message bitlength representation */
         ((Ipp64u*)(buffer+bufferLen))[-1] = msgLenBits;

         updateFunc(hash, buffer, bufferLen, SHA1_cnt);
         #undef MREP_SIZE_SHA1
      }

      /* swap hash bytes */
      ((Ipp32u*)pMD)[0] = ENDIANNESS32(hash[0]);
      ((Ipp32u*)pMD)[1] = ENDIANNESS32(hash[1]);
      ((Ipp32u*)pMD)[2] = ENDIANNESS32(hash[2]);
      ((Ipp32u*)pMD)[3] = ENDIANNESS32(hash[3]);
      ((Ipp32u*)pMD)[4] = ENDIANNESS32(hash[4]);

      return ippStsNoErr;
   }
}

#endif /* _ENABLE_ALG_SHA1_ */
