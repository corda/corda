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
//     Internal Definitions of Block Cipher Tools
// 
// 
*/

#if !defined(_PC_TOOL_H)
#define _CP_TOOL_H

/* copy data block */
__INLINE void CopyBlock(const void* pSrc, void* pDst, cpSize numBytes)
{
   const Ipp8u* s  = (Ipp8u*)pSrc;
   Ipp8u* d  = (Ipp8u*)pDst;
   cpSize k;
   for(k=0; k<numBytes; k++ )
      d[k] = s[k];
}
__INLINE void CopyBlock8(const void* pSrc, void* pDst)
{
   int k;
   for(k=0; k<8; k++ )
      ((Ipp8u*)pDst)[k] = ((Ipp8u*)pSrc)[k];
}
__INLINE void CopyBlock16(const void* pSrc, void* pDst)
{
   int k;
   for(k=0; k<16; k++ )
      ((Ipp8u*)pDst)[k] = ((Ipp8u*)pSrc)[k];
}
__INLINE void CopyBlock24(const void* pSrc, void* pDst)
{
   int k;
   for(k=0; k<24; k++ )
      ((Ipp8u*)pDst)[k] = ((Ipp8u*)pSrc)[k];
}
__INLINE void CopyBlock32(const void* pSrc, void* pDst)
{
   int k;
   for(k=0; k<32; k++ )
      ((Ipp8u*)pDst)[k] = ((Ipp8u*)pSrc)[k];
}

/*
// padding data block
*/
__INLINE void PaddBlock(Ipp8u paddingByte, void* pDst, cpSize numBytes)
{
   Ipp8u* d  = (Ipp8u*)pDst;
   cpSize k;
   for(k=0; k<numBytes; k++ )
      d[k] = paddingByte;
}

#if !((_IPP>=_IPP_W7) || (_IPP32E>=_IPP32E_M7))
__INLINE void PurgeBlock(void* pDst, int len)
{
   int n;
   for(n=0; n<len; n++) ((Ipp8u*)pDst)[n] = 0;
}
#else
void PurgeBlock(void* pDst, int len);
#endif

/* fill block */
__INLINE void FillBlock16(Ipp8u filler, const void* pSrc, void* pDst, int len)
{
   int n;
   for(n=0; n<len; n++) ((Ipp8u*)pDst)[n] = ((Ipp8u*)pSrc)[n];
   for(; n<16; n++) ((Ipp8u*)pDst)[n] = filler;
}

#if 0
void FillBlock8 (Ipp8u filler, const void* pSrc, void* pDst, int len);
void FillBlock16(Ipp8u filler, const void* pSrc, void* pDst, int len);
void FillBlock24(Ipp8u filler, const void* pSrc, void* pDst, int len);
void FillBlock32(Ipp8u filler, const void* pSrc, void* pDst, int len);
#endif

/* xor block */
__INLINE void XorBlock(const void* pSrc1, const void* pSrc2, void* pDst, int len)
{
   const Ipp8u* p1 = (const Ipp8u*)pSrc1;
   const Ipp8u* p2 = (const Ipp8u*)pSrc2;
   Ipp8u* d  = (Ipp8u*)pDst;
   int k;
   for(k=0; k<len; k++)
      d[k] = (Ipp8u)(p1[k] ^p2[k]);
}
__INLINE void XorBlock8(const void* pSrc1, const void* pSrc2, void* pDst)
{
   const Ipp8u* p1 = (const Ipp8u*)pSrc1;
   const Ipp8u* p2 = (const Ipp8u*)pSrc2;
   Ipp8u* d  = (Ipp8u*)pDst;
   int k;
   for(k=0; k<8; k++ )
      d[k] = (Ipp8u)(p1[k] ^p2[k]);
}
__INLINE void XorBlock16(const void* pSrc1, const void* pSrc2, void* pDst)
{
   const Ipp8u* p1 = (const Ipp8u*)pSrc1;
   const Ipp8u* p2 = (const Ipp8u*)pSrc2;
   Ipp8u* d  = (Ipp8u*)pDst;
   int k;
   for(k=0; k<16; k++ )
      d[k] = (Ipp8u)(p1[k] ^p2[k]);
}
__INLINE void XorBlock24(const void* pSrc1, const void* pSrc2, void* pDst)
{
   const Ipp8u* p1 = (const Ipp8u*)pSrc1;
   const Ipp8u* p2 = (const Ipp8u*)pSrc2;
   Ipp8u* d  = (Ipp8u*)pDst;
   int k;
   for(k=0; k<24; k++ )
      d[k] = (Ipp8u)(p1[k] ^p2[k]);
}
__INLINE void XorBlock32(const void* pSrc1, const void* pSrc2, void* pDst)
{
   const Ipp8u* p1 = (const Ipp8u*)pSrc1;
   const Ipp8u* p2 = (const Ipp8u*)pSrc2;
   Ipp8u* d  = (Ipp8u*)pDst;
   int k;
   for(k=0; k<32; k++ )
      d[k] = (Ipp8u)(p1[k] ^p2[k]);
}


/* compare (equivalence) */
__INLINE int EquBlock(const void* pSrc1, const void* pSrc2, int len)
{
   const Ipp8u* p1 = (const Ipp8u*)pSrc1;
   const Ipp8u* p2 = (const Ipp8u*)pSrc2;
   int k;
   int isEqu;
   for(k=0, isEqu=1; k<len && isEqu; k++)
      isEqu = (p1[k] == p2[k]);
   return isEqu;
}


#if 0
int  TestPadding(Ipp8u filler, void* pSrc, int len);
#endif

/* addition functions for CTR mode of diffenent block ciphers */
__INLINE void StdIncrement(Ipp8u* pCounter, int blkSize, int numSize)
{
   int maskPosition = (blkSize-numSize)/8;
   Ipp8u mask = (Ipp8u)( 0xFF >> (blkSize-numSize)%8 );

   /* save crytical byte */
   Ipp8u save  = (Ipp8u)( pCounter[maskPosition] & ~mask );

   int len = BITS2WORD8_SIZE(blkSize);
   Ipp32u carry = 1;
   for(; (len>maskPosition) && carry; len--) {
      Ipp32u x = pCounter[len-1] + carry;
      pCounter[len-1] = (Ipp8u)x;
      carry = (x>>8) & 0xFF;
   }

   /* update crytical byte */
   pCounter[maskPosition] &= mask;
   pCounter[maskPosition] |= save;
}

/* vb */
__INLINE void ompStdIncrement64( void* pInitCtrVal, void* pCurrCtrVal,
                                int ctrNumBitSize, int n )
{
    int    k;
    Ipp64u cntr;
    Ipp64u temp;
    Ipp64s item;

  #if( IPP_ENDIAN == IPP_LITTLE_ENDIAN )
    for( k = 0; k < 8; k++ )
        ( ( Ipp8u* )&cntr )[k] = ( ( Ipp8u* )pInitCtrVal )[7 - k];
  #else
    for( k = 0; k < 8; k++ )
        ( ( Ipp8u* )&cntr )[k] = ( ( Ipp8u* )pInitCtrVal )[k];
  #endif

    if( ctrNumBitSize == 64 )
    {
        cntr += ( Ipp64u )n;
    }
    else
    {
        /* gres: Ipp64u mask = ( Ipp64u )0xFFFFFFFFFFFFFFFF >> ( 64 - ctrNumBitSize ); */
        Ipp64u mask = CONST_64(0xFFFFFFFFFFFFFFFF) >> ( 64 - ctrNumBitSize );
        Ipp64u save = cntr & ( ~mask );
        Ipp64u bndr = ( Ipp64u )1 << ctrNumBitSize;

        temp = cntr & mask;
        cntr = temp + ( Ipp64u )n;

        if( cntr > bndr )
        {
            item = ( Ipp64s )n - ( Ipp64s )( bndr - temp );

            while( item > 0 )
            {
                cntr  = ( Ipp64u )item;
                item -= ( Ipp64s )bndr;
            }
        }

        cntr = save | ( cntr & mask );
    }

  #if( IPP_ENDIAN == IPP_LITTLE_ENDIAN )
    for( k = 0; k < 8; k++ )
        ( ( Ipp8u* )pCurrCtrVal )[7 - k] = ( ( Ipp8u* )&cntr )[k];
  #else
    for( k = 0; k < 8; k++ )
        ( ( Ipp8u* )pCurrCtrVal )[k] = ( ( Ipp8u* )&cntr )[k];
  #endif
}


/* vb */
__INLINE void ompStdIncrement128( void* pInitCtrVal, void* pCurrCtrVal,
                                 int ctrNumBitSize, int n )
{
    int    k;
    Ipp64u low;
    Ipp64u hgh;
    Ipp64u flag;
    Ipp64u mask = CONST_64(0xFFFFFFFFFFFFFFFF);
    Ipp64u save;

  #if( IPP_ENDIAN == IPP_LITTLE_ENDIAN )
    for( k = 0; k < 8; k++ )
    {
        ( ( Ipp8u* )&low )[k] = ( ( Ipp8u* )pInitCtrVal )[15 - k];
        ( ( Ipp8u* )&hgh )[k] = ( ( Ipp8u* )pInitCtrVal )[7 - k];
    }
  #else
    for( k = 0; k < 8; k++ )
    {
        ( ( Ipp8u* )&low )[k] = ( ( Ipp8u* )pInitCtrVal )[8 + k];
        ( ( Ipp8u* )&hgh )[k] = ( ( Ipp8u* )pInitCtrVal )[k];
    }
  #endif

    if( ctrNumBitSize == 64 )
    {
        low += ( Ipp64u )n;
    }
    else if( ctrNumBitSize < 64 )
    {
        Ipp64u bndr;
        Ipp64u cntr;
        Ipp64s item;

        mask >>= ( 64 - ctrNumBitSize );
        save   = low & ( ~mask );
        cntr   = ( low & mask ) + ( Ipp64u )n;

        if( ctrNumBitSize < 31 )
        {
            bndr = ( Ipp64u )1 << ctrNumBitSize;

            if( cntr > bndr )
            {
                item = ( Ipp64s )( ( Ipp64s )n - ( ( Ipp64s )bndr -
                ( Ipp64s )( low & mask ) ) );

                while( item > 0 )
                {
                    cntr  = ( Ipp64u )item;
                    item -= ( Ipp64s )bndr;
                }
            }
        }

        low = save | ( cntr & mask );
    }
    else
    {
        flag = ( low >> 63 );

        if( ctrNumBitSize != 128 )
        {
            mask >>= ( 128 - ctrNumBitSize );
            save   = hgh & ( ~mask );
            hgh   &= mask;
        }

        low += ( Ipp64u )n;

        if( flag != ( low >> 63 ) ) hgh++;

        if( ctrNumBitSize != 128 )
        {
            hgh = save | ( hgh & mask );
        }
    }

  #if( IPP_ENDIAN == IPP_LITTLE_ENDIAN )
    for( k = 0; k < 8; k++ )
    {
        ( ( Ipp8u* )pCurrCtrVal )[15 - k] = ( ( Ipp8u* )&low )[k];
        ( ( Ipp8u* )pCurrCtrVal )[7 - k]  = ( ( Ipp8u* )&hgh )[k];
    }
  #else
    for( k = 0; k < 8; k++ )
    {
        ( ( Ipp8u* )pCurrCtrVal )[8 + k] = ( ( Ipp8u* )&low )[k];
        ( ( Ipp8u* )pCurrCtrVal )[k]     = ( ( Ipp8u* )&hgh )[k];
    }
  #endif
}


/* vb */
__INLINE void ompStdIncrement192( void* pInitCtrVal, void* pCurrCtrVal,
                                int ctrNumBitSize, int n )
{
    int    k;
    Ipp64u low;
    Ipp64u mdl;
    Ipp64u hgh;
    Ipp64u flag;
    Ipp64u mask = CONST_64(0xFFFFFFFFFFFFFFFF);
    Ipp64u save;

  #if( IPP_ENDIAN == IPP_LITTLE_ENDIAN )
    for( k = 0; k < 8; k++ )
    {
        ( ( Ipp8u* )&low )[k] = ( ( Ipp8u* )pInitCtrVal )[23 - k];
        ( ( Ipp8u* )&mdl )[k] = ( ( Ipp8u* )pInitCtrVal )[15 - k];
        ( ( Ipp8u* )&hgh )[k] = ( ( Ipp8u* )pInitCtrVal )[7  - k];
    }
  #else
    for( k = 0; k < 8; k++ )
    {
        ( ( Ipp8u* )&low )[k] = ( ( Ipp8u* )pInitCtrVal )[16 + k];
        ( ( Ipp8u* )&mdl )[k] = ( ( Ipp8u* )pInitCtrVal )[8  + k];
        ( ( Ipp8u* )&hgh )[k] = ( ( Ipp8u* )pInitCtrVal )[k];
    }
  #endif

    if( ctrNumBitSize == 64 )
    {
        low += ( Ipp64u )n;
    }
    else if( ctrNumBitSize == 128 )
    {
        flag = ( low >> 63 );
        low += ( Ipp64u )n;
        if( flag != ( low >> 63 ) ) mdl++;
    }
    else if( ctrNumBitSize == 192 )
    {
        flag = ( low >> 63 );
        low += ( Ipp64u )n;

        if( flag != ( low >> 63 ) )
        {
            flag = ( mdl >> 63 );
            mdl++;
            if( flag != ( mdl >> 63 ) ) hgh++;
        }
    }
    else if( ctrNumBitSize < 64 )
    {
        Ipp64u bndr;
        Ipp64u cntr;
        Ipp64s item;

        mask >>= ( 64 - ctrNumBitSize );
        save   = low & ( ~mask );
        cntr   = ( low & mask ) + ( Ipp64u )n;

        if( ctrNumBitSize < 31 )
        {
            bndr = ( Ipp64u )1 << ctrNumBitSize;

            if( cntr > bndr )
            {
                item = ( Ipp64s )( ( Ipp64s )n - ( ( Ipp64s )bndr -
                    ( Ipp64s )( low & mask ) ) );

                while( item > 0 )
                {
                    cntr  = ( Ipp64u )item;
                    item -= ( Ipp64s )bndr;
                }
            }
        }

        low = save | ( cntr & mask );
    }
    else if( ctrNumBitSize < 128 )
    {
        flag   = ( low >> 63 );
        mask >>= ( 128 - ctrNumBitSize );
        save   = mdl & ( ~mask );
        mdl   &= mask;
        low   += ( Ipp64u )n;
        if( flag != ( low >> 63 ) ) mdl++;
        mdl    = save | ( mdl & mask );
    }
    else
    {
        flag   = ( low >> 63 );
        mask >>= ( 192 - ctrNumBitSize );
        save   = hgh & ( ~mask );
        hgh   &= mask;
        low   += ( Ipp64u )n;

        if( flag != ( low >> 63 ) )
        {
            flag = ( mdl >> 63 );
            mdl++;
            if( flag != ( mdl >> 63 ) ) hgh++;
        }

        hgh    = save | ( hgh & mask );
    }

  #if( IPP_ENDIAN == IPP_LITTLE_ENDIAN )
    for( k = 0; k < 8; k++ )
    {
        ( ( Ipp8u* )pCurrCtrVal )[23 - k] = ( ( Ipp8u* )&low )[k];
        ( ( Ipp8u* )pCurrCtrVal )[15 - k] = ( ( Ipp8u* )&mdl )[k];
        ( ( Ipp8u* )pCurrCtrVal )[7  - k] = ( ( Ipp8u* )&hgh )[k];
    }
  #else
    for( k = 0; k < 8; k++ )
    {
        ( ( Ipp8u* )pCurrCtrVal )[16 + k] = ( ( Ipp8u* )&low )[k];
        ( ( Ipp8u* )pCurrCtrVal )[8  + k] = ( ( Ipp8u* )&mdl )[k];
        ( ( Ipp8u* )pCurrCtrVal )[k]      = ( ( Ipp8u* )&hgh )[k];
    }
  #endif
}


/* vb */
__INLINE void ompStdIncrement256( void* pInitCtrVal, void* pCurrCtrVal,
                                 int ctrNumBitSize, int n )
{
    int    k;
    Ipp64u low;
    Ipp64u mdl;
    Ipp64u mdm;
    Ipp64u hgh;
    Ipp64u flag;
    Ipp64u mask = CONST_64(0xFFFFFFFFFFFFFFFF);
    Ipp64u save;

  #if( IPP_ENDIAN == IPP_LITTLE_ENDIAN )
    for( k = 0; k < 8; k++ )
    {
        ( ( Ipp8u* )&low )[k] = ( ( Ipp8u* )pInitCtrVal )[31 - k];
        ( ( Ipp8u* )&mdl )[k] = ( ( Ipp8u* )pInitCtrVal )[23 - k];
        ( ( Ipp8u* )&mdm )[k] = ( ( Ipp8u* )pInitCtrVal )[15 - k];
        ( ( Ipp8u* )&hgh )[k] = ( ( Ipp8u* )pInitCtrVal )[7  - k];
    }
  #else
    for( k = 0; k < 8; k++ )
    {
        ( ( Ipp8u* )&low )[k] = ( ( Ipp8u* )pInitCtrVal )[24 + k];
        ( ( Ipp8u* )&mdl )[k] = ( ( Ipp8u* )pInitCtrVal )[16 + k];
        ( ( Ipp8u* )&mdm )[k] = ( ( Ipp8u* )pInitCtrVal )[8  + k];
        ( ( Ipp8u* )&hgh )[k] = ( ( Ipp8u* )pInitCtrVal )[k];
    }
  #endif

    if( ctrNumBitSize == 64 )
    {
        low += ( Ipp64u )n;
    }
    else if( ctrNumBitSize == 128 )
    {
        flag = ( low >> 63 );
        low += ( Ipp64u )n;
        if( flag != ( low >> 63 ) ) mdl++;
    }
    else if( ctrNumBitSize == 192 )
    {
        flag = ( low >> 63 );
        low += ( Ipp64u )n;

        if( flag != ( low >> 63 ) )
        {
            flag = ( mdl >> 63 );
            mdl++;
            if( flag != ( mdl >> 63 ) ) hgh++;
        }
    }
    else if( ctrNumBitSize == 256 )
    {
        flag = ( low >> 63 );
        low += ( Ipp64u )n;

        if( flag != ( low >> 63 ) )
        {
            flag = ( mdl >> 63 );
            mdl++;

            if( flag != ( mdl >> 63 ) )
            {
                flag = ( mdm >> 63 );
                mdm++;
                if( flag != ( mdm >> 63 ) ) hgh++;
            }
        }
    }
    else if( ctrNumBitSize < 64 )
    {
        Ipp64u bndr;
        Ipp64u cntr;
        Ipp64s item;

        mask >>= ( 64 - ctrNumBitSize );
        save   = low & ( ~mask );
        cntr   = ( low & mask ) + ( Ipp64u )n;

        if( ctrNumBitSize < 31 )
        {
            bndr = ( Ipp64u )1 << ctrNumBitSize;

            if( cntr > bndr )
            {
                item = ( Ipp64s )( ( Ipp64s )n - ( ( Ipp64s )bndr -
                    ( Ipp64s )( low & mask ) ) );

                while( item > 0 )
                {
                    cntr  = ( Ipp64u )item;
                    item -= ( Ipp64s )bndr;
                }
            }
        }

        low = save | ( cntr & mask );
    }
    else if( ctrNumBitSize < 128 )
    {
        flag   = ( low >> 63 );
        mask >>= ( 128 - ctrNumBitSize );
        save   = mdl & ( ~mask );
        mdl   &= mask;
        low   += ( Ipp64u )n;
        if( flag != ( low >> 63 ) ) mdl++;
        mdl    = save | ( mdl & mask );
    }
    else if( ctrNumBitSize < 192 )
    {
        flag   = ( low >> 63 );
        mask >>= ( 192 - ctrNumBitSize );
        save   = mdm & ( ~mask );
        mdm   &= mask;
        low   += ( Ipp64u )n;

        if( flag != ( low >> 63 ) )
        {
            flag = ( mdl >> 63 );
            mdl++;
            if( flag != ( mdl >> 63 ) ) mdm++;
        }

        mdm    = save | ( mdm & mask );
    }
    else
    {
        flag   = ( low >> 63 );
        mask >>= ( 256 - ctrNumBitSize );
        save   = hgh & ( ~mask );
        hgh   &= mask;
        low   += ( Ipp64u )n;

        if( flag != ( low >> 63 ) )
        {
            flag = ( mdl >> 63 );
            mdl++;

            if( flag != ( mdl >> 63 ) )
            {
                flag = ( mdm >> 63 );
                mdm++;
                if( flag != ( mdm >> 63 ) ) hgh++;
            }
        }

        hgh    = save | ( hgh & mask );
    }

  #if( IPP_ENDIAN == IPP_LITTLE_ENDIAN )
    for( k = 0; k < 8; k++ )
    {
        ( ( Ipp8u* )pCurrCtrVal )[31 - k] = ( ( Ipp8u* )&low )[k];
        ( ( Ipp8u* )pCurrCtrVal )[23 - k] = ( ( Ipp8u* )&mdl )[k];
        ( ( Ipp8u* )pCurrCtrVal )[15 - k] = ( ( Ipp8u* )&mdm )[k];
        ( ( Ipp8u* )pCurrCtrVal )[7  - k] = ( ( Ipp8u* )&hgh )[k];
    }
  #else
    for( k = 0; k < 8; k++ )
    {
        ( ( Ipp8u* )pCurrCtrVal )[24 + k] = ( ( Ipp8u* )&low )[k];
        ( ( Ipp8u* )pCurrCtrVal )[16 + k] = ( ( Ipp8u* )&mdl )[k];
        ( ( Ipp8u* )pCurrCtrVal )[8  + k] = ( ( Ipp8u* )&mdm )[k];
        ( ( Ipp8u* )pCurrCtrVal )[k]      = ( ( Ipp8u* )&hgh )[k];
    }
  #endif
}

#endif /* _CP_TOOL_H */
