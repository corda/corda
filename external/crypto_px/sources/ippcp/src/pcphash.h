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

#if !defined(_PCP_HASH_H)
#define _PCP_HASH_H


/* messge block size */
#define MBS_SHA1     (64)           /* SHA1 message block size (bytes) */
#define MBS_SHA256   (64)           /* SHA256 and SHA224               */
#define MBS_SHA224   (64)           /* SHA224                          */
#define MBS_SHA512   (128)          /* SHA512 and SHA384               */
#define MBS_SHA384   (128)          /* SHA384                          */
#define MBS_MD5      (64)           /* MD5                             */
#define MBS_HASH_MAX (MBS_SHA512)   /* max message block size (bytes)  */
#define MAX_HASH_SIZE (IPP_SHA512_DIGEST_BITSIZE/8)   /* hash of the max len (bytes) */

/*
// Useful macros
*/
#define SHS_ID(stt)     ((stt)->idCtx)
#define SHS_INDX(stt)   ((stt)->index)
#define SHS_LENL(stt)   ((stt)->msgLenLo)
#define SHS_LENH(stt)   ((stt)->msgLenHi)
#define SHS_BUFF(stt)   ((stt)->msgBuffer)
#define SHS_HASH(stt)   ((stt)->msgHash)

/* initial hash values */
extern const Ipp32u SHA1_IV[];
extern const Ipp32u SHA256_IV[];
extern const Ipp32u SHA224_IV[];
extern const Ipp64u SHA512_IV[];
extern const Ipp64u SHA384_IV[];
extern const Ipp32u MD5_IV[];
extern const Ipp64u SHA512_224_IV[];
extern const Ipp64u SHA512_256_IV[];

/* hash alg additive constants */
extern __ALIGN16 const Ipp32u SHA1_cnt[];
extern __ALIGN16 const Ipp32u SHA256_cnt[];
extern __ALIGN16 const Ipp64u SHA512_cnt[];
extern __ALIGN16 const Ipp32u MD5_cnt[];


/* hash alg attributes */
typedef struct _cpHashAttr {
   int         ivSize;        /* attr: length (bytes) of initial value cpHashIV */
   int         hashSize;      /* attr: length (bytes) of hash */
   int         msgBlkSize;    /* attr: length (bytes) of message block */
   int         msgLenRepSize; /* attr: length (bytes) in representation of processed message length */
   Ipp64u      msgLenMax[2];  /* attr: max message length (bytes) (low high) */
} cpHashAttr;


/* hash value */
typedef Ipp64u cpHash[IPP_SHA512_DIGEST_BITSIZE/BITSIZE(Ipp64u)]; /* hash value */

/* hash update function */
typedef void (*cpHashProc)(void* pHash, const Ipp8u* pMsg, int msgLen, const void* pParam);



/* hash context */
struct _cpHashCtx {
   IppCtxId    idCtx;                     /* hash identifier   */
   IppHashAlgId   algID;                  /* hash algorithm ID */
   Ipp64u      msgLenLo;                  /* length (bytes) of processed message: */
   Ipp64u      msgLenHi;                  /*       low and high parts */
   cpHashProc  hashProc;                  /* hash update function */
   const void* pParam;                    /* optional hashProc's parameter */
   cpHash      hashVal;                   /* intermadiate has value */
   int         buffOffset;                /* current buffer position */
   Ipp8u       msgBuffer[MBS_HASH_MAX];   /* buffer */
};

/* accessors */
#define HASH_CTX_ID(stt)   ((stt)->idCtx)
#define HASH_ALG_ID(stt)   ((stt)->algID)
#define HASH_LENLO(stt)    ((stt)->msgLenLo)
#define HASH_LENHI(stt)    ((stt)->msgLenHi)
#define HASH_FUNC(stt)     ((stt)->hashProc)
#define HASH_FUNC_PAR(stt) ((stt)->pParam)
#define HASH_VALUE(stt)    ((stt)->hashVal)
#define HAHS_BUFFIDX(stt)  ((stt)->buffOffset)
#define HASH_BUFF(stt)     ((stt)->msgBuffer)
#define HASH_VALID_ID(pCtx)   (HASH_CTX_ID((pCtx))==idCtxHash)


/*  hash alg opt argument */
extern const void* cpHashProcFuncOpt[];

/* enabled hash alg */
extern const IppHashAlgId cpEnabledHashAlgID[];

/* hash alg IV (init value) */
extern const Ipp8u* cpHashIV[];

/* hash alg attribute DB */
extern const cpHashAttr cpHashAlgAttr[];

/* IV size helper */
__INLINE int cpHashIvSize(IppHashAlgId algID)
{ return cpHashAlgAttr[algID].ivSize; }

/* hash size helper */
__INLINE int cpHashSize(IppHashAlgId algID)
{ return cpHashAlgAttr[algID].hashSize; }

/* message block size helper */
__INLINE int cpHashMBS(IppHashAlgId algID)
{ return cpHashAlgAttr[algID].msgBlkSize; }

/* maps algID into enabled IppHashAlgId value */
__INLINE IppHashAlgId cpValidHashAlg(IppHashAlgId algID)
{
   /* maps algID into the valid range */
   algID = (((int)ippHashAlg_Unknown < (int)algID) && ((int)algID < (int)ippHashAlg_MaxNo))? algID : ippHashAlg_Unknown;
   return cpEnabledHashAlgID[algID];
}


/* processing functions */
void UpdateSHA1  (void* pHash, const Ipp8u* mblk, int mlen, const void* pParam);
void UpdateSHA256(void* pHash, const Ipp8u* mblk, int mlen, const void* pParam);
void UpdateSHA512(void* pHash, const Ipp8u* mblk, int mlen, const void* pParam);
void UpdateMD5   (void* pHash, const Ipp8u* mblk, int mlen, const void* pParam);

/* general methods */
int cpReInitHash(IppsHashState* pCtx, IppHashAlgId algID);

#endif /* _PCP_HASH_H */
