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
//     Security Hash Standard
//     Internal Definitions and Internal Functions Prototypes
// 
// 
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
#define MBS_SM3      (64)           /* SM3                             */
#define MBS_HASH_MAX (MBS_SHA512)   /* max message block size (bytes)  */

#define MAX_HASH_SIZE (IPP_SHA512_DIGEST_BITSIZE/8)   /* hash of the max len (bytes) */

/* hold some old definition for a purpose */
typedef Ipp32u DigestSHA1[5];   /* SHA1 digest   */
typedef Ipp32u DigestSHA224[7]; /* SHA224 digest */
typedef Ipp32u DigestSHA256[8]; /* SHA256 digest */
typedef Ipp64u DigestSHA384[6]; /* SHA384 digest */
typedef Ipp64u DigestSHA512[8]; /* SHA512 digest */
typedef Ipp32u DigestMD5[4];    /* MD5 digest */
typedef Ipp32u DigestSM3[8];    /* SM3 digest */

#define   SHA1_ALIGNMENT   ((int)(sizeof(Ipp32u)))
#define SHA224_ALIGNMENT   ((int)(sizeof(Ipp32u)))
#define SHA256_ALIGNMENT   ((int)(sizeof(Ipp32u)))
#define SHA384_ALIGNMENT   ((int)(sizeof(Ipp32u)))
#define SHA512_ALIGNMENT   ((int)(sizeof(Ipp32u)))
#define    MD5_ALIGNMENT   ((int)(sizeof(Ipp32u)))
#define    SM3_ALIGNMENT   ((int)(sizeof(Ipp32u)))


#if defined(_ENABLE_ALG_SHA1_)
struct _cpSHA1 {
   IppCtxId    idCtx;   /* SHA1 identifier               */
   int         index;   /* internal buffer entry (free)  */
   Ipp64u      msgLenLo;   /* message length (bytes)         */
   Ipp8u       msgBuffer[MBS_SHA1]; /* buffer              */
   DigestSHA1  msgHash; /* intermediate digest           */
};
#endif

#if defined(_ENABLE_ALG_SHA256_) || defined(_ENABLE_ALG_SHA224_)
struct _cpSHA256 {
   IppCtxId     idCtx;   /* SHA224 identifier            */
   int          index;   /* internal buffer entry (free) */
   Ipp64u       msgLenLo;  /* message length (bytes)        */
   Ipp8u        msgBuffer[MBS_SHA256]; /* buffer           */
   DigestSHA256 msgHash; /* intermediate digest          */
};
#endif

#if defined(_ENABLE_ALG_SHA512_) || defined(_ENABLE_ALG_SHA384_) || defined(_ENABLE_ALG_SHA512_224_) || defined(_ENABLE_ALG_SHA512_256_)
struct _cpSHA512 {
   IppCtxId     idCtx;   /* SHA384 identifier            */
   int          index;   /* internal buffer entry (free) */
   Ipp64u       msgLenLo;  /* message length (bytes)        */
   Ipp64u       msgLenHi;  /* message length (bytes)        */
   Ipp8u        msgBuffer[MBS_SHA512]; /* buffer           */
   DigestSHA512 msgHash; /* intermediate digest          */
};
#endif

#if defined(_ENABLE_ALG_MD5_)
struct _cpMD5 {
   IppCtxId     idCtx;   /* MD5 identifier                 */
   int          index;   /* internal buffer entry (free)   */
   Ipp64u       msgLenLo;  /* message length (bytes)          */
   Ipp8u        msgBuffer[MBS_MD5]; /* buffer                */
   DigestMD5    msgHash; /* intermediate digest            */
};
#endif

#if defined(_ENABLE_ALG_SM3_)
struct _cpSM3 {
   IppCtxId     idCtx;   /* SM3    identifier            */
   int          index;   /* internal buffer entry (free) */
   Ipp64u       msgLenLo;  /* message length (bits)        */
   Ipp8u        msgBuffer[MBS_SM3]; /* buffer           */
   DigestSM3    msgHash; /* intermediate digest          */
};
#endif

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
extern const Ipp32u SM3_IV[];
extern const Ipp64u SHA512_224_IV[];
extern const Ipp64u SHA512_256_IV[];

/* hash alg additive constants */
extern __ALIGN16 const Ipp32u SHA1_cnt[];
extern __ALIGN16 const Ipp32u SHA256_cnt[];
extern __ALIGN16 const Ipp64u SHA512_cnt[];
extern __ALIGN16 const Ipp32u MD5_cnt[];
extern __ALIGN16 const Ipp32u SM3_cnt[];

/* */


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
/* old some old accessors */
//#define SHS_DGST(stt)      HASH_VALUE((stt))
//#define SHS_BUFF(stt)      HASH_BUFF((stt))


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
void UpdateSM3   (void* pHash, const Ipp8u* mblk, int mlen, const void* pParam);

#if (_IPP>=_IPP_P8) || (_IPP32E>=_IPP32E_Y8)
void UpdateSHA1ni  (void* pHash, const Ipp8u* mblk, int mlen, const void* pParam);
void UpdateSHA256ni(void* pHash, const Ipp8u* mblk, int mlen, const void* pParam);
#endif

/* general methods */
//void cpHashUpdate(const Ipp8u* pSrc, int len, IppsHashState* pCtx, cpHashProc hashFunc, const void* pParam, int mbs);
int cpReInitHash(IppsHashState* pCtx, IppHashAlgId algID);

#endif /* _PCP_HASH_H */
