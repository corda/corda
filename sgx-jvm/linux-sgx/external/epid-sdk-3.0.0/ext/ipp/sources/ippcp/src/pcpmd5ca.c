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
//     Digesting message according to MD5
//     (derived from the RSA Data Security, Inc. MD5 Message-Digest Algorithm)
// 
//     Equivalent code is available from RFC 1321.
// 
//  Contents:
//     ippsMD5GetSize()
//     ippsMD5Init()
//     ippsMD5Pack()
//     ippsMD5Unpack()
//     ippsMD5Duplicate()
//     ippsMD5Update()
//     ippsMD5GetTag()
//     ippsMD5Final()
//     ippsMD5MessageDigest()
// 
// 
*/

#include "precomp.h"
#include "owncp.h"
#include "pcphash.h"
#include "pcptool.h"


#if !defined (_ENABLE_ALG_MD5_)
#pragma message("IPP_ALG_HASH_MD5 disabled")
#else
#pragma message("IPP_ALG_HASH_MD5 enabled")

/*
// Init MD5 digest
*/
IppStatus InitMD5(IppsMD5State* pState)
{
   /* test state pointer */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsMD5State*)( IPP_ALIGNED_PTR(pState, MD5_ALIGNMENT) );

   /* set state ID */
   SHS_ID(pState) = idCtxMD5;

   /* zeros message length */
   SHS_LENL(pState) = 0;

   /* message buffer is free */
   SHS_INDX(pState) = 0;

   /* setup initial digest */
   SHS_HASH(pState)[0] = MD5_IV[0];
   SHS_HASH(pState)[1] = MD5_IV[1];
   SHS_HASH(pState)[2] = MD5_IV[2];
   SHS_HASH(pState)[3] = MD5_IV[3];

   return ippStsNoErr;
}


/*F*
//    Name: ippsMD5GetSize
//
// Purpose: Returns size (bytes) of IppsMD5State state.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSize == NULL
//    ippStsNoErr             no errors
//
// Parameters:
//    pSize       pointer to size
//
*F*/
IPPFUN(IppStatus, ippsMD5GetSize,(int* pSize))
{
   /* test pointer */
   IPP_BAD_PTR1_RET(pSize);

   *pSize = sizeof(IppsMD5State) +(MD5_ALIGNMENT-1);

   return ippStsNoErr;
}


/*F*
//    Name: ippsMD5Init
//
// Purpose: Init MD5 state.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pState == NULL
//    ippStsNoErr             no errors
//
// Parameters:
//    pState      pointer to the MD5 state
//
*F*/
IPPFUN(IppStatus, ippsMD5Init,(IppsMD5State* pState))
{
   return InitMD5(pState);
}


/*F*
//    Name: ippsMD5Pack
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
IPPFUN(IppStatus, ippsMD5Pack,(const IppsMD5State* pCtx, Ipp8u* pBuffer))
{
   /* test pointers */
   IPP_BAD_PTR2_RET(pCtx, pBuffer);
   /* use aligned context */
   pCtx = (IppsMD5State*)( IPP_ALIGNED_PTR(pCtx, MD5_ALIGNMENT) );
   /* test the context */
   IPP_BADARG_RET(idCtxMD5 !=SHS_ID(pCtx), ippStsContextMatchErr);

   CopyBlock(pCtx, pBuffer, sizeof(IppsMD5State));
   return ippStsNoErr;
}


/*F*
//    Name: ippsMD5Unpack
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
IPPFUN(IppStatus, ippsMD5Unpack,(const Ipp8u* pBuffer, IppsMD5State* pCtx))
{
   /* test pointers */
   IPP_BAD_PTR2_RET(pCtx, pBuffer);
   /* use aligned context */
   pCtx = (IppsMD5State*)( IPP_ALIGNED_PTR(pCtx, MD5_ALIGNMENT) );

   CopyBlock(pBuffer, pCtx, sizeof(IppsMD5State));
   return ippStsNoErr;
}


/*F*
//    Name: ippsMD5Duplicate
//
// Purpose: Clone MD5 state.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSrcState == NULL
//                            pDstState == NULL
//    ippStsContextMatchErr   pSrcState->idCtx != idCtxMD5
//                            pDstState->idCtx != idCtxMD5
//    ippStsNoErr             no errors
//
// Parameters:
//    pSrcState   pointer to the source MD5 state
//    pDstState   pointer to the target MD5 state
//
// Note:
//    pDstState may to be uninitialized by ippsMD5Init()
//
*F*/
IPPFUN(IppStatus, ippsMD5Duplicate,(const IppsMD5State* pSrcState, IppsMD5State* pDstState))
{
   /* test state pointers */
   IPP_BAD_PTR2_RET(pSrcState, pDstState);
   /* use aligned context */
   pSrcState = (IppsMD5State*)( IPP_ALIGNED_PTR(pSrcState, MD5_ALIGNMENT) );
   pDstState = (IppsMD5State*)( IPP_ALIGNED_PTR(pDstState, MD5_ALIGNMENT) );
   /* test states ID */
   IPP_BADARG_RET(idCtxMD5 !=SHS_ID(pSrcState), ippStsContextMatchErr);

   /* copy state */
   CopyBlock(pSrcState, pDstState, sizeof(IppsMD5State));

   return ippStsNoErr;
}


/*F*
//    Name: ippsMD5Update
//
// Purpose: Updates intermadiate digest based on input stream.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pSrc == NULL
//                            pState == NULL
//    ippStsContextMatchErr   pState->idCtx != idCtxMD5
//    ippStsLengthErr         len <0
//    ippStsNoErr             no errors
//
// Parameters:
//    pSrc        pointer to the input stream
//    len         input stream length
//    pState      pointer to the MD5 state
//
*F*/
IPPFUN(IppStatus, ippsMD5Update,(const Ipp8u* pSrc, int len, IppsMD5State* pState))
{
   /* test state pointer and ID */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsMD5State*)( IPP_ALIGNED_PTR(pState, MD5_ALIGNMENT) );
   /* test state ID */
   IPP_BADARG_RET(idCtxMD5 !=SHS_ID(pState), ippStsContextMatchErr);

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
      lenLo += len;

      /* if non empty internal buffer filling */
      if(n) {
         /* copy from input stream to the internal buffer as match as possible */
         processingLen = IPP_MIN(len, (MBS_MD5 - SHS_INDX(pState)));
         CopyBlock(pSrc, pBuffer+n, processingLen);

         pSrc += processingLen;
         len  -= processingLen;
         SHS_INDX(pState) = n += processingLen;

         /* update digest if buffer full */
         if( MBS_MD5 == n) {
            UpdateMD5(pHash, pBuffer, MBS_MD5, MD5_cnt);
            SHS_INDX(pState) = 0;
         }
      }

      /* main message part processing */
      processingLen = len & ~(MBS_MD5-1);
      if(processingLen) {
         UpdateMD5(pHash, pSrc, processingLen, MD5_cnt);
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
void ComputeDigestMD5(Ipp32u* pHash, const IppsMD5State* pState)
{
   const Ipp8u* stateBuff = SHS_BUFF(pState);
   int stateBuffLen = SHS_INDX(pState);

   /* local buffer and it length */
   Ipp8u buffer[MBS_MD5*2];
   int bufferLen = stateBuffLen < (MBS_MD5-(int)sizeof(Ipp64u))? MBS_MD5 : MBS_MD5*2; 

   /* copy rest of message into internal buffer */
   CopyBlock(stateBuff, buffer, stateBuffLen);

   /* padd message */
   buffer[stateBuffLen++] = 0x80;
   PaddBlock(0, buffer+stateBuffLen, bufferLen-stateBuffLen-sizeof(Ipp64u));

   /* message length representation */
   {
      Ipp64u lo = SHS_LENL(pState);      /* message length in bytes */
      lo = LSL64(lo,3);                  /* message length in bits */
      ((Ipp64u*)(buffer+bufferLen))[-1] = lo;
   }

   /* copmplete hash computation */
   UpdateMD5(pHash, buffer, bufferLen, MD5_cnt);

   /* convert hash into big endian */
   /* is not necessary if little endian */
}


/*F*
//    Name: ippsMD5GetTag
//
// Purpose: Compute digest based on current state.
//          Note, that futher digest update is possible
//
// Returns:                Reason:
//    ippStsNullPtrErr        pTag == NULL
//                            pState == NULL
//    ippStsContextMatchErr   pState->idCtx != idCtxMD5
//    ippStsLengthErr         max_MD5_digestLen < tagLen <1
//    ippStsNoErr             no errors
//
// Parameters:
//    pTag        address of the output digest
//    tagLen      length of digest
//    pState      pointer to the MD5 state
//
*F*/
IPPFUN(IppStatus, ippsMD5GetTag,(Ipp8u* pTag, Ipp32u tagLen, const IppsMD5State* pState))
{
   /* test state pointer and ID */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsMD5State*)( IPP_ALIGNED_PTR(pState, MD5_ALIGNMENT) );
   /* test state ID */
   IPP_BADARG_RET(idCtxMD5 !=SHS_ID(pState), ippStsContextMatchErr);

   /* test digest pointer */
   IPP_BAD_PTR1_RET(pTag);
   IPP_BADARG_RET((tagLen<1)||(sizeof(DigestMD5)<tagLen), ippStsLengthErr);

   {
      DigestMD5 digest;

      CopyBlock(SHS_HASH(pState), digest, sizeof(DigestMD5));
      ComputeDigestMD5(digest, pState);
      CopyBlock(digest, pTag, tagLen);

      return ippStsNoErr;
   }
}


/*F*
//    Name: ippsMD5Final
//
// Purpose: Stop message digesting and return digest.
//
// Returns:                Reason:
//    ippStsNullPtrErr        pMD == NULL
//                            pState == NULL
//    ippStsContextMatchErr   pState->idCtx != idCtxMD5
//    ippStsNoErr             no errors
//
// Parameters:
//    pMD         address of the output digest
//    pState      pointer to the MD5 state
//
*F*/
IPPFUN(IppStatus, ippsMD5Final,(Ipp8u* pMD, IppsMD5State* pState))
{
   /* test state pointer and ID */
   IPP_BAD_PTR1_RET(pState);
   /* use aligned context */
   pState = (IppsMD5State*)( IPP_ALIGNED_PTR(pState, MD5_ALIGNMENT) );
   /* test state ID */
   IPP_BADARG_RET(idCtxMD5 !=SHS_ID(pState), ippStsContextMatchErr);

   /* test digest pointer */
   IPP_BAD_PTR1_RET(pMD);

   ComputeDigestMD5(SHS_HASH(pState), pState);
   CopyBlock(SHS_HASH(pState), pMD, sizeof(DigestMD5));
   InitMD5(pState);

   return ippStsNoErr;
}


/*F*
//    Name: ippsMD5MessageDigest
//
// Purpose: Ddigest of the whole message.
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
IPPFUN(IppStatus, ippsMD5MessageDigest,(const Ipp8u* pMsg, int msgLen, Ipp8u* pMD))
{
   /* test digest pointer */
   IPP_BAD_PTR1_RET(pMD);
   /* test message length */
   IPP_BADARG_RET((msgLen<0), ippStsLengthErr);
   /* test message pointer */
   IPP_BADARG_RET((msgLen && !pMsg), ippStsNullPtrErr);

   {
      /* message length in the multiple MBS and the rest */
      int msgLenBlks = msgLen & (-MBS_MD5);
      int msgLenRest = msgLen - msgLenBlks;

      /* init hash value */
      DigestMD5 hash = {0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476};

      /* process main part of the message */
      if(msgLenBlks)
         UpdateMD5(hash, pMsg, msgLenBlks, MD5_cnt);

      /* process message padding */
      {
         #define MREP_SIZE_MD5  (sizeof(Ipp64u))
         Ipp8u buffer[MBS_MD5*2];
         int bufferLen = msgLenRest < (int)(MBS_MD5-MREP_SIZE_MD5)? (int)MBS_MD5 : (int)(MBS_MD5*2);

         /* message bitlength representation */
         Ipp64u msgLenBits = (Ipp64u)msgLen*8;

         /* copy end of message */
         CopyBlock(pMsg+msgLen-msgLenRest, buffer, msgLenRest);

         /* end of message bit */
         buffer[msgLenRest++] = 0x80;

         /* padd buffer */
         PaddBlock(0, buffer+msgLenRest, bufferLen-msgLenRest-MREP_SIZE_MD5);
         /* copy message bitlength representation */
         ((Ipp64u*)(buffer+bufferLen))[-1] = msgLenBits;

         UpdateMD5(hash, buffer, bufferLen, MD5_cnt);
         #undef MREP_SIZE_MD5
      }

      /* copy hash bytes */
      ((Ipp32u*)pMD)[0] = hash[0];
      ((Ipp32u*)pMD)[1] = hash[1];
      ((Ipp32u*)pMD)[2] = hash[2];
      ((Ipp32u*)pMD)[3] = hash[3];

      return ippStsNoErr;
   }
}

#endif /* _ENABLE_ALG_MD5_ */
