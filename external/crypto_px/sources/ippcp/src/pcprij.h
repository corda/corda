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

#if !defined(_PCP_RIJ_H)
#define _PCP_RIJ_H


/*
// The GF(256) modular polynomial and elements
*/
#define WPOLY  0x011B
#define BPOLY    0x1B

/*
// Make WORD using 4 arbitrary bytes
*/
#define BYTES_TO_WORD(b0,b1,b2,b3) ( ( ((Ipp32u)((Ipp8u)(b3))) <<24 ) \
                                    |( ((Ipp32u)((Ipp8u)(b2))) <<16 ) \
                                    |( ((Ipp32u)((Ipp8u)(b1))) << 8 ) \
                                    |( ((Ipp32u)((Ipp8u)(b0))) ) )
/*
// Make WORD setting byte in specified position
*/
#define BYTE0_TO_WORD(b)   BYTES_TO_WORD((b), 0,  0,  0)
#define BYTE1_TO_WORD(b)   BYTES_TO_WORD( 0, (b), 0,  0)
#define BYTE2_TO_WORD(b)   BYTES_TO_WORD( 0,  0, (b), 0)
#define BYTE3_TO_WORD(b)   BYTES_TO_WORD( 0,  0,  0, (b))

/*
// Extract byte from specified position n.
// Sure, n=0,1,2 or 3 only
*/
#define EBYTE(w,n) ((Ipp8u)((w) >> (8 * (n))))


/*
// Rijndael's spec
*/
typedef void (*RijnCipher)(const Ipp8u* pInpBlk, Ipp8u* pOutBlk, int nr, const Ipp8u* pKeys, const void* pTbl);

struct _cpRijndael128 {
   IppCtxId    idCtx;         /* Rijndael spec identifier      */
   int         nk;            /* security key length (words)   */
   int         nb;            /* data block size (words)       */
   int         nr;            /* number of rounds              */
   RijnCipher  encoder;       /* encoder/decoder               */
   RijnCipher  decoder;       /* entry point                   */
   Ipp32u*     pEncTbl;       /* expanded S-boxes for          */
   Ipp32u*     pDecTbl;       /* encryption and decryption     */
   Ipp32u      enc_keys[64];  /* array of keys for encryprion  */
   Ipp32u      dec_keys[64];  /* array of keys for decryprion  */
   Ipp32u      aesNI;         /* AES instruction available     */
   Ipp32u      safeInit;      /* SafeInit performed            */
};

/* alignment */
#define RIJ_ALIGNMENT (16)

#define MBS_RIJ128   (128/8)  /* message block size (bytes) */
#define MBS_RIJ192   (192/8)
#define MBS_RIJ256   (256/8)

#define SR          (4)            /* number of rows in STATE data */

#define NB(msgBlks) ((msgBlks)/32) /* message block size (words)     */
                                   /* 4-word for 128-bits data block */
                                   /* 6-word for 192-bits data block */
                                   /* 8-word for 256-bits data block */

#define NK(keybits) ((keybits)/32)  /* key length (words): */
#define NK128 NK(ippRijndaelKey128)/* 4-word for 128-bits security key */
#define NK192 NK(ippRijndaelKey192)/* 6-word for 192-bits security key */
#define NK256 NK(ippRijndaelKey256)/* 8-word for 256-bits security key */

#define NR128_128 (10)  /* number of rounds data: 128 bits key: 128 bits are used */
#define NR128_192 (12)  /* number of rounds data: 128 bits key: 192 bits are used */
#define NR128_256 (14)  /* number of rounds data: 128 bits key: 256 bits are used */
#define NR192_128 (12)  /* number of rounds data: 192 bits key: 128 bits are used */
#define NR192_192 (12)  /* number of rounds data: 192 bits key: 192 bits are used */
#define NR192_256 (14)  /* number of rounds data: 192 bits key: 256 bits are used */
#define NR256_128 (14)  /* number of rounds data: 256 bits key: 128 bits are used */
#define NR256_192 (14)  /* number of rounds data: 256 bits key: 192 bits are used */
#define NR256_256 (14)  /* number of rounds data: 256 bits key: 256 bits are used */

/*
// Useful macros
*/
#define RIJ_ID(ctx)        ((ctx)->idCtx)
#define RIJ_NB(ctx)        ((ctx)->nb)
#define RIJ_NK(ctx)        ((ctx)->nk)
#define RIJ_NR(ctx)        ((ctx)->nr)
#define RIJ_ENCODER(ctx)   ((ctx)->encoder)
#define RIJ_DECODER(ctx)   ((ctx)->decoder)
#define RIJ_ENC_SBOX(ctx)  ((ctx)->pEncTbl)
#define RIJ_DEC_SBOX(ctx)  ((ctx)->pDecTbl)
#define RIJ_EKEYS(ctx)     (Ipp8u*)((ctx)->enc_keys)
#define RIJ_DKEYS(ctx)     (Ipp8u*)((ctx)->dec_keys)
#define RIJ_AESNI(ctx)     ((ctx)->aesNI)
#define RIJ_SAFE_INIT(ctx) ((ctx)->safeInit)

#define RIJ_ID_TEST(ctx)   (RIJ_ID((ctx))==idCtxRijndael)

/*
// Internal functions
*/

void Safe2Encrypt_RIJ128(const Ipp8u* pInpBlk, Ipp8u* pOutBlk, int nr, const Ipp8u* pKeys, const void* pTbl);
void Safe2Decrypt_RIJ128(const Ipp8u* pInpBlk, Ipp8u* pOutBlk, int nr, const Ipp8u* pKeys, const void* pTbl);

void ExpandRijndaelKey(const Ipp8u* pKey, int NK, int NB, int NR, int nKeys,
                       Ipp8u* pEncKeys, Ipp8u* pDecKeys);

#endif /* _PCP_RIJ_H */
