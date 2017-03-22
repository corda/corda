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
//   Author(s): Alexey Korchuganov
//              Anatoly Pluzhnikov
//              Igor Astakhov
//              Dmitry Kozhaev
// 
//   Created: 27-Jul-1999 20:27
// 
*/

#ifndef __OWNDEFS_H__
#define __OWNDEFS_H__

#if defined( _VXWORKS )
  #include <vxWorks.h>
  #undef NONE
#endif

#include "ippdefs.h"

#if defined(__INTEL_COMPILER) || defined(_MSC_VER)
  #define __INLINE static __inline
#elif defined( __GNUC__ )
  #define __INLINE static __inline__
#else
  #define __INLINE static
#endif

#if defined(__INTEL_COMPILER)
 #define __RESTRICT restrict
#elif !defined( __RESTRICT )
 #define __RESTRICT
#endif

#if defined( IPP_W32DLL )
  #if defined( _MSC_VER ) || defined( __INTEL_COMPILER )
    #define IPPFUN(type,name,arg) __declspec(dllexport) type __STDCALL name arg
  #else
    #define IPPFUN(type,name,arg)                extern type __STDCALL name arg
  #endif
#else
  #define   IPPFUN(type,name,arg)                extern type __STDCALL name arg
#endif


/* structure represeting 128 bit unsigned integer type */

typedef struct{
  Ipp64u low;
  Ipp64u high;
}Ipp128u;

#define _IPP_PX 0    /* pure C-code ia32                              */
#define _IPP_M5 1    /* Quark (Pentium) - x86+x87 ia32                */
#define _IPP_M6 2    /* Pentium MMX - MMX ia32                        */
#define _IPP_A6 4    /* Pentium III - SSE ia32                        */
#define _IPP_W7 8    /* Pentium 4 - SSE2 ia32                         */
#define _IPP_T7 16   /* Pentium with x64 support (Nocona) - SSE3 ia32 */
#define _IPP_V8 32   /* Merom - SSSE3 ia32                            */
#define _IPP_P8 64   /* Penryn - SSE4.1 + tick for SSE4.2 ia32        */
#define _IPP_G9 128  /* SandyBridge (GSSE) - AVX ia32                 */
#define _IPP_H9 256  /* Haswell (AVX2) ia32                           */
#define _IPP_I0 512  /* KNL (AVX-512) ia32                            */
#define _IPP_S0 1024 /* SkyLake Xeon (AVX-512) ia32                   */

#define _IPPXSC_PX 0
#define _IPPXSC_S1 1
#define _IPPXSC_S2 2
#define _IPPXSC_C2 4

#define _IPPLRB_PX 0
#define _IPPLRB_B1 1
#define _IPPLRB_B2 2

#define _IPP64_PX  _IPP_PX
#define _IPP64_I7 64

#define _IPP32E_PX _IPP_PX /* pure C-code x64                              */
#define _IPP32E_M7 32      /* Pentium with x64 support (Nocona) - SSE3 x64 */
#define _IPP32E_U8 64      /* Merom - SSSE3 x64                            */
#define _IPP32E_Y8 128     /* Penryn - SSE4.1 + tick for SSE4.2 x64        */
#define _IPP32E_E9 256     /* SandyBridge (GSSE) - AVX x64                 */
#define _IPP32E_L9 512     /* Haswell (AVX2) x64                           */
#define _IPP32E_N0 1024    /* KNL (AVX-512) x64                            */
#define _IPP32E_K0 2048    /* SkyLake Xeon (AVX-512) x64                   */

#define _IPPLP32_PX _IPP_PX
#define _IPPLP32_S8 1      /* old Atom (SSSE3+movbe) (Silverthorne) ia32   */

#define _IPPLP64_PX _IPP_PX
#define _IPPLP64_N8 1      /* old Atom (SSSE3+movbe) (Silverthorne) x64    */

#if defined(__INTEL_COMPILER) || (_MSC_VER >= 1300)
    #define __ALIGN8  __declspec (align(8))
    #define __ALIGN16 __declspec (align(16))
#if !defined( OSX32 )
    #define __ALIGN32 __declspec (align(32))
#else
    #define __ALIGN32 __declspec (align(16))
#endif
    #define __ALIGN64 __declspec (align(64))
#else
    #define __ALIGN8
    #define __ALIGN16
    #define __ALIGN32
    #define __ALIGN64
#endif

#if defined ( _M5 ) /* Quark (Pentium) - x86+x87 ia32                */
  #define _IPP    _IPP_M5
  #define _IPP32E _IPP32E_PX
  #define _IPPLRB _IPPLRB_PX
  #define _IPPLP32 _IPPLP32_PX
  #define _IPPLP64 _IPPLP64_PX

#elif defined ( _M6 ) /* Pentium MMX - MMX ia32                        */
  #define _IPP    _IPP_M6
  #define _IPP32E _IPP32E_PX
  #define _IPPLRB _IPPLRB_PX
  #define _IPPLP32 _IPPLP32_PX
  #define _IPPLP64 _IPPLP64_PX

#elif defined( _A6 ) /* Pentium III - SSE ia32                        */
  #define _IPP    _IPP_A6
  #define _IPP32E _IPP32E_PX
  #define _IPPLRB _IPPLRB_PX
  #define _IPPLP32 _IPPLP32_PX
  #define _IPPLP64 _IPPLP64_PX

#elif defined( _W7 ) /* Pentium 4 - SSE2 ia32                         */
  #define _IPP    _IPP_W7
  #define _IPP32E _IPP32E_PX
  #define _IPPLRB _IPPLRB_PX
  #define _IPPLP32 _IPPLP32_PX
  #define _IPPLP64 _IPPLP64_PX

#elif defined( _T7 ) /* Pentium with x64 support (Nocona) - SSE3 ia32 */
  #define _IPP    _IPP_T7
  #define _IPP32E _IPP32E_PX
  #define _IPPLRB _IPPLRB_PX
  #define _IPPLP32 _IPPLP32_PX
  #define _IPPLP64 _IPPLP64_PX

#elif defined( _V8 ) /* Merom - SSSE3 ia32                            */
  #define _IPP    _IPP_V8
  #define _IPP32E _IPP32E_PX
  #define _IPPLRB _IPPLRB_PX
  #define _IPPLP32 _IPPLP32_PX
  #define _IPPLP64 _IPPLP64_PX

#elif defined( _P8 ) /* Penryn - SSE4.1 + tick for SSE4.2 ia32        */
  #define _IPP    _IPP_P8
  #define _IPP32E _IPP32E_PX
  #define _IPPLRB _IPPLRB_PX
  #define _IPPLP32 _IPPLP32_PX
  #define _IPPLP64 _IPPLP64_PX

#elif defined( _G9 ) /* SandyBridge (GSSE) - AVX ia32                 */
  #define _IPP    _IPP_G9
  #define _IPP32E _IPP32E_PX
  #define _IPPLRB _IPPLRB_PX
  #define _IPPLP32 _IPPLP32_PX
  #define _IPPLP64 _IPPLP64_PX

#elif defined( _H9 ) /* Haswell (AVX2) ia32                           */
  #define _IPP    _IPP_H9
  #define _IPP32E _IPP32E_PX
  #define _IPPLRB _IPPLRB_PX
  #define _IPPLP32 _IPPLP32_PX
  #define _IPPLP64 _IPPLP64_PX

#elif defined( _M7 ) /* Pentium with x64 support (Nocona) - SSE3 x64 */
  #define _IPP    _IPP_PX
  #define _IPP32E _IPP32E_M7
  #define _IPPLRB _IPPLRB_PX
  #define _IPPLP32 _IPPLP32_PX
  #define _IPPLP64 _IPPLP64_PX

#elif defined( _U8 ) /* Merom - SSSE3 x64                            */
  #define _IPP    _IPP_PX
  #define _IPP32E _IPP32E_U8
  #define _IPPLRB _IPPLRB_PX
  #define _IPPLP32 _IPPLP32_PX
  #define _IPPLP64 _IPPLP64_PX

#elif defined( _Y8 ) /* Penryn - SSE4.1 + tick for SSE4.2 x64        */
  #define _IPP    _IPP_PX
  #define _IPP32E _IPP32E_Y8
  #define _IPPLRB _IPPLRB_PX
  #define _IPPLP32 _IPPLP32_PX
  #define _IPPLP64 _IPPLP64_PX

#elif defined( _E9 ) /* SandyBridge (GSSE) - AVX x64                 */
  #define _IPP    _IPP_PX
  #define _IPP32E _IPP32E_E9
  #define _IPPLRB _IPPLRB_PX
  #define _IPPLP32 _IPPLP32_PX
  #define _IPPLP64 _IPPLP64_PX

#elif defined( _L9 ) /* Haswell (AVX2) x64                           */
  #define _IPP    _IPP_PX
  #define _IPP32E _IPP32E_L9
  #define _IPPLRB _IPPLRB_PX
  #define _IPPLP32 _IPPLP32_PX
  #define _IPPLP64 _IPPLP64_PX

#elif defined( _N0 ) /* KNL (AVX-512) x64                            */
  #define _IPP    _IPP_PX
  #define _IPP32E _IPP32E_N0
  #define _IPPLRB _IPPLRB_PX
  #define _IPPLP32 _IPPLP32_PX
  #define _IPPLP64 _IPPLP64_PX

#elif defined( _K0 ) /* SkyLake Xeon (AVX-512) x64                   */
  #define _IPP    _IPP_PX
  #define _IPP32E _IPP32E_K0
  #define _IPPLRB _IPPLRB_PX
  #define _IPPLP32 _IPPLP32_PX
  #define _IPPLP64 _IPPLP64_PX

#elif defined( _B2 ) /* KNC (MIC)                                    */
  #define _IPP    _IPP_PX
  #define _IPP32E _IPP32E_PX
  #define _IPPLRB _IPPLRB_B2
  #define _IPPLP32 _IPPLP32_PX
  #define _IPPLP64 _IPPLP64_PX

#elif defined( _S8 ) /* old Atom (SSSE3+movbe) (Silverthorne) ia32   */
  #define _IPP    _IPP_V8
  #define _IPP32E _IPP32E_PX
  #define _IPPLRB _IPPLRB_PX
  #define _IPPLP32 _IPPLP32_S8
  #define _IPPLP64 _IPPLP64_PX

#elif defined( _N8 ) /* old Atom (SSSE3+movbe) (Silverthorne) x64    */
  #define _IPP    _IPP_PX
  #define _IPP32E _IPP32E_U8
  #define _IPPLRB _IPPLRB_PX
  #define _IPPLP32 _IPPLP32_PX
  #define _IPPLP64 _IPPLP64_N8

#else
  #define _IPP    _IPP_PX
  #define _IPP32E _IPP32E_PX
  #define _IPPLRB _IPPLRB_PX
  #define _IPPLP32 _IPPLP32_PX
  #define _IPPLP64 _IPPLP64_PX

#endif


#define _IPP_ARCH_IA32    1
#define _IPP_ARCH_IA64    2
#define _IPP_ARCH_EM64T   4
#define _IPP_ARCH_XSC     8
#define _IPP_ARCH_LRB     16
#define _IPP_ARCH_LP32    32
#define _IPP_ARCH_LP64    64
#define _IPP_ARCH_LRB2    128

#if defined ( _ARCH_IA32 )
  #define _IPP_ARCH    _IPP_ARCH_IA32

#elif defined( _ARCH_EM64T )
  #define _IPP_ARCH    _IPP_ARCH_EM64T

#elif defined( _ARCH_LRB2 )
  #define _IPP_ARCH    _IPP_ARCH_LRB2

#elif defined( _ARCH_LP32 )
  #define _IPP_ARCH    _IPP_ARCH_LP32

#elif defined( _ARCH_LP64 )
  #define _IPP_ARCH    _IPP_ARCH_LP64

#else
  #if defined(_M_AMD64) || defined(__x86_64) || defined(__x86_64__)
    #define _IPP_ARCH    _IPP_ARCH_EM64T

  #else
    #define _IPP_ARCH    _IPP_ARCH_IA32

  #endif
#endif

#if ((_IPP_ARCH == _IPP_ARCH_IA32) || (_IPP_ARCH == _IPP_ARCH_LP32))
__INLINE
Ipp32s IPP_INT_PTR( const void* ptr )  {
    union {
        void*   Ptr;
        Ipp32s  Int;
    } dd;
    dd.Ptr = (void*)ptr;
    return dd.Int;
}

__INLINE
Ipp32u IPP_UINT_PTR( const void* ptr )  {
    union {
        void*   Ptr;
        Ipp32u  Int;
    } dd;
    dd.Ptr = (void*)ptr;
    return dd.Int;
}
#elif ((_IPP_ARCH == _IPP_ARCH_EM64T) || (_IPP_ARCH == _IPP_ARCH_LRB2) || (_IPP_ARCH == _IPP_ARCH_LP64))
__INLINE
Ipp64s IPP_INT_PTR( const void* ptr )  {
    union {
        void*   Ptr;
        Ipp64s  Int;
    } dd;
    dd.Ptr = (void*)ptr;
    return dd.Int;
}

__INLINE
Ipp64u IPP_UINT_PTR( const void* ptr )  {
    union {
        void*    Ptr;
        Ipp64u   Int;
    } dd;
    dd.Ptr = (void*)ptr;
    return dd.Int;
}
#else
  #define IPP_INT_PTR( ptr )  ( (long)(ptr) )
  #define IPP_UINT_PTR( ptr ) ( (unsigned long)(ptr) )
#endif

#define IPP_ALIGN_TYPE(type, align) ((align)/sizeof(type)-1)
#define IPP_BYTES_TO_ALIGN(ptr, align) ((-(IPP_INT_PTR(ptr)&((align)-1)))&((align)-1))
#define IPP_ALIGNED_PTR(ptr, align) (void*)( (unsigned char*)(ptr) + (IPP_BYTES_TO_ALIGN( ptr, align )) )

#define IPP_ALIGNED_SIZE(size, align) (((size)+(align)-1)&~((align)-1))

#define IPP_MALLOC_ALIGNED_BYTES   64
#define IPP_MALLOC_ALIGNED_8BYTES   8
#define IPP_MALLOC_ALIGNED_16BYTES 16
#define IPP_MALLOC_ALIGNED_32BYTES 32

#define IPP_ALIGNED_ARRAY(align,arrtype,arrname,arrlength)\
 char arrname##AlignedArrBuff[sizeof(arrtype)*(arrlength)+IPP_ALIGN_TYPE(char, align)];\
 arrtype *arrname = (arrtype*)IPP_ALIGNED_PTR(arrname##AlignedArrBuff,align)

#if defined( __cplusplus )
extern "C" {
#endif

/* /////////////////////////////////////////////////////////////////////////////

           IPP Context Identification

  /////////////////////////////////////////////////////////////////////////// */

#define IPP_CONTEXT( a, b, c, d) \
            (int)(((unsigned)(a) << 24) | ((unsigned)(b) << 16) | \
            ((unsigned)(c) << 8) | (unsigned)(d))

typedef enum {
    idCtxUnknown = 0,
    idCtxFFT_C_16sc,
    idCtxFFT_C_16s,
    idCtxFFT_R_16s,
    idCtxFFT_C_32fc,
    idCtxFFT_C_32f,
    idCtxFFT_R_32f,
    idCtxFFT_C_64fc,
    idCtxFFT_C_64f,
    idCtxFFT_R_64f,
    idCtxDFT_C_16sc,
    idCtxDFT_C_16s,
    idCtxDFT_R_16s,
    idCtxDFT_C_32fc,
    idCtxDFT_C_32f,
    idCtxDFT_R_32f,
    idCtxDFT_C_64fc,
    idCtxDFT_C_64f,
    idCtxDFT_R_64f,
    idCtxDCTFwd_16s,
    idCtxDCTInv_16s,
    idCtxDCTFwd_32f,
    idCtxDCTInv_32f,
    idCtxDCTFwd_64f,
    idCtxDCTInv_64f,
    idCtxFFT2D_C_32fc,
    idCtxFFT2D_R_32f,
    idCtxDFT2D_C_32fc,
    idCtxDFT2D_R_32f,
    idCtxFFT2D_R_32s,
    idCtxDFT2D_R_32s,
    idCtxDCT2DFwd_32f,
    idCtxDCT2DInv_32f,
    idCtxMoment64f,
    idCtxMoment64s,
    idCtxRandUni_8u,
    idCtxRandUni_16s,
    idCtxRandUni_32f,
    idCtxRandUni_64f,
    idCtxRandGauss_8u,
    idCtxRandGauss_16s,
    idCtxRandGauss_32f,
    idCtxRandGauss_64f,
    idCtxWTFwd_32f,
    idCtxWTFwd_8u32f,
    idCtxWTFwd_8s32f,
    idCtxWTFwd_16u32f,
    idCtxWTFwd_16s32f,
    idCtxWTFwd2D_32f_C1R,
    idCtxWTInv2D_32f_C1R,
    idCtxWTFwd2D_32f_C3R,
    idCtxWTInv2D_32f_C3R,
    idCtxWTInv_32f,
    idCtxWTInv_32f8u,
    idCtxWTInv_32f8s,
    idCtxWTInv_32f16u,
    idCtxWTInv_32f16s,
    idCtxMDCTFwd_32f,
    idCtxMDCTInv_32f,
    idCtxMDCTFwd_16s,
    idCtxFIRBlock_32f,
    idCtxFDP_32f,
    idCtxRLMS_32f       = IPP_CONTEXT( 'L', 'M', 'S', '1'),
    idCtxRLMS32f_16s    = IPP_CONTEXT( 'L', 'M', 'S', 0 ),
    idCtxIIRAR_32f      = IPP_CONTEXT( 'I', 'I', '0', '1'),
    idCtxIIRBQ_32f      = IPP_CONTEXT( 'I', 'I', '0', '2'),
    idCtxIIRAR_32fc     = IPP_CONTEXT( 'I', 'I', '0', '3'),
    idCtxIIRBQ_32fc     = IPP_CONTEXT( 'I', 'I', '0', '4'),
    idCtxIIRAR32f_16s   = IPP_CONTEXT( 'I', 'I', '0', '5'),
    idCtxIIRBQ32f_16s   = IPP_CONTEXT( 'I', 'I', '0', '6'),
    idCtxIIRAR32fc_16sc = IPP_CONTEXT( 'I', 'I', '0', '7'),
    idCtxIIRBQ32fc_16sc = IPP_CONTEXT( 'I', 'I', '0', '8'),
    idCtxIIRAR32s_16s   = IPP_CONTEXT( 'I', 'I', '0', '9'),
    idCtxIIRBQ32s_16s   = IPP_CONTEXT( 'I', 'I', '1', '0'),
    idCtxIIRAR32sc_16sc = IPP_CONTEXT( 'I', 'I', '1', '1'),
    idCtxIIRBQ32sc_16sc = IPP_CONTEXT( 'I', 'I', '1', '2'),
    idCtxIIRAR_64f      = IPP_CONTEXT( 'I', 'I', '1', '3'),
    idCtxIIRBQ_64f      = IPP_CONTEXT( 'I', 'I', '1', '4'),
    idCtxIIRAR_64fc     = IPP_CONTEXT( 'I', 'I', '1', '5'),
    idCtxIIRBQ_64fc     = IPP_CONTEXT( 'I', 'I', '1', '6'),
    idCtxIIRAR64f_32f   = IPP_CONTEXT( 'I', 'I', '1', '7'),
    idCtxIIRBQ64f_32f   = IPP_CONTEXT( 'I', 'I', '1', '8'),
    idCtxIIRAR64fc_32fc = IPP_CONTEXT( 'I', 'I', '1', '9'),
    idCtxIIRBQ64fc_32fc = IPP_CONTEXT( 'I', 'I', '2', '0'),
    idCtxIIRAR64f_32s   = IPP_CONTEXT( 'I', 'I', '2', '1'),
    idCtxIIRBQ64f_32s   = IPP_CONTEXT( 'I', 'I', '2', '2'),
    idCtxIIRAR64fc_32sc = IPP_CONTEXT( 'I', 'I', '2', '3'),
    idCtxIIRBQ64fc_32sc = IPP_CONTEXT( 'I', 'I', '2', '4'),
    idCtxIIRAR64f_16s   = IPP_CONTEXT( 'I', 'I', '2', '5'),
    idCtxIIRBQ64f_16s   = IPP_CONTEXT( 'I', 'I', '2', '6'),
    idCtxIIRAR64fc_16sc = IPP_CONTEXT( 'I', 'I', '2', '7'),
    idCtxIIRBQ64fc_16sc = IPP_CONTEXT( 'I', 'I', '2', '8'),
    idCtxIIRBQDF1_32f   = IPP_CONTEXT( 'I', 'I', '2', '9'),
    idCtxIIRBQDF164f_32s= IPP_CONTEXT( 'I', 'I', '3', '0'),
    idCtxFIRSR_32f      = IPP_CONTEXT( 'F', 'I', '0', '1'),
    idCtxFIRSR_32fc     = IPP_CONTEXT( 'F', 'I', '0', '2'),
    idCtxFIRMR_32f      = IPP_CONTEXT( 'F', 'I', '0', '3'),
    idCtxFIRMR_32fc     = IPP_CONTEXT( 'F', 'I', '0', '4'),
    idCtxFIRSR32f_16s   = IPP_CONTEXT( 'F', 'I', '0', '5'),
    idCtxFIRSR32fc_16sc = IPP_CONTEXT( 'F', 'I', '0', '6'),
    idCtxFIRMR32f_16s   = IPP_CONTEXT( 'F', 'I', '0', '7'),
    idCtxFIRMR32fc_16sc = IPP_CONTEXT( 'F', 'I', '0', '8'),
    idCtxFIRSR32s_16s   = IPP_CONTEXT( 'F', 'I', '0', '9'),
    idCtxFIRSR32sc_16sc = IPP_CONTEXT( 'F', 'I', '1', '0'),
    idCtxFIRMR32s_16s   = IPP_CONTEXT( 'F', 'I', '1', '1'),
    idCtxFIRMR32sc_16sc = IPP_CONTEXT( 'F', 'I', '1', '2'),
    idCtxFIRSR_64f      = IPP_CONTEXT( 'F', 'I', '1', '3'),
    idCtxFIRSR_64fc     = IPP_CONTEXT( 'F', 'I', '1', '4'),
    idCtxFIRMR_64f      = IPP_CONTEXT( 'F', 'I', '1', '5'),
    idCtxFIRMR_64fc     = IPP_CONTEXT( 'F', 'I', '1', '6'),
    idCtxFIRSR64f_32f   = IPP_CONTEXT( 'F', 'I', '1', '7'),
    idCtxFIRSR64fc_32fc = IPP_CONTEXT( 'F', 'I', '1', '8'),
    idCtxFIRMR64f_32f   = IPP_CONTEXT( 'F', 'I', '1', '9'),
    idCtxFIRMR64fc_32fc = IPP_CONTEXT( 'F', 'I', '2', '0'),
    idCtxFIRSR64f_32s   = IPP_CONTEXT( 'F', 'I', '2', '1'),
    idCtxFIRSR64fc_32sc = IPP_CONTEXT( 'F', 'I', '2', '2'),
    idCtxFIRMR64f_32s   = IPP_CONTEXT( 'F', 'I', '2', '3'),
    idCtxFIRMR64fc_32sc = IPP_CONTEXT( 'F', 'I', '2', '4'),
    idCtxFIRSR64f_16s   = IPP_CONTEXT( 'F', 'I', '2', '5'),
    idCtxFIRSR64fc_16sc = IPP_CONTEXT( 'F', 'I', '2', '6'),
    idCtxFIRMR64f_16s   = IPP_CONTEXT( 'F', 'I', '2', '7'),
    idCtxFIRMR64fc_16sc = IPP_CONTEXT( 'F', 'I', '2', '8'),
    idCtxFIRSR_16s      = IPP_CONTEXT( 'F', 'I', '2', '9'),
    idCtxFIRMR_16s      = IPP_CONTEXT( 'F', 'I', '3', '0'),
    idCtxFIRSRStream_16s= IPP_CONTEXT( 'F', 'I', '3', '1'),
    idCtxFIRMRStream_16s= IPP_CONTEXT( 'F', 'I', '3', '2'),
    idCtxFIRSRStream_32f= IPP_CONTEXT( 'F', 'I', '3', '3'),
    idCtxFIRMRStream_32f= IPP_CONTEXT( 'F', 'I', '3', '4'),
    idCtxRLMS32s_16s    = IPP_CONTEXT( 'L', 'M', 'S', 'R'),
    idCtxCLMS32s_16s    = IPP_CONTEXT( 'L', 'M', 'S', 'C'),
    idCtxEncode_JPEG2K,
    idCtxDES            = IPP_CONTEXT( ' ', 'D', 'E', 'S'),
    idCtxBlowfish       = IPP_CONTEXT( ' ', ' ', 'B', 'F'),
    idCtxRijndael       = IPP_CONTEXT( ' ', 'R', 'I', 'J'),
    idCtxSMS4           = IPP_CONTEXT( 'S', 'M', 'S', '4'),
    idCtxTwofish        = IPP_CONTEXT( ' ', ' ', 'T', 'F'),
    idCtxARCFOUR        = IPP_CONTEXT( ' ', 'R', 'C', '4'),
    idCtxRC564          = IPP_CONTEXT( 'R', 'C', '5', '1'),
    idCtxRC5128         = IPP_CONTEXT( 'R', 'C', '5', '2'),
    idCtxSHA1           = IPP_CONTEXT( 'S', 'H', 'S', '1'),
    idCtxSHA224         = IPP_CONTEXT( 'S', 'H', 'S', '3'),
    idCtxSHA256         = IPP_CONTEXT( 'S', 'H', 'S', '2'),
    idCtxSHA384         = IPP_CONTEXT( 'S', 'H', 'S', '4'),
    idCtxSHA512         = IPP_CONTEXT( 'S', 'H', 'S', '5'),
    idCtxMD5            = IPP_CONTEXT( ' ', 'M', 'D', '5'),
    idCtxHMAC           = IPP_CONTEXT( 'H', 'M', 'A', 'C'),
    idCtxDAA            = IPP_CONTEXT( ' ', 'D', 'A', 'A'),
    idCtxBigNum         = IPP_CONTEXT( 'B', 'I', 'G', 'N'),
    idCtxMontgomery     = IPP_CONTEXT( 'M', 'O', 'N', 'T'),
    idCtxPrimeNumber    = IPP_CONTEXT( 'P', 'R', 'I', 'M'),
    idCtxPRNG           = IPP_CONTEXT( 'P', 'R', 'N', 'G'),
    idCtxRSA            = IPP_CONTEXT( ' ', 'R', 'S', 'A'),
    idCtxRSA_PubKey     = IPP_CONTEXT( 'R', 'S', 'A', '0'),
    idCtxRSA_PrvKey1    = IPP_CONTEXT( 'R', 'S', 'A', '1'),
    idCtxRSA_PrvKey2    = IPP_CONTEXT( 'R', 'S', 'A', '2'),
    idCtxDSA            = IPP_CONTEXT( ' ', 'D', 'S', 'A'),
    idCtxECCP           = IPP_CONTEXT( ' ', 'E', 'C', 'P'),
    idCtxECCB           = IPP_CONTEXT( ' ', 'E', 'C', 'B'),
    idCtxECCPPoint      = IPP_CONTEXT( 'P', 'E', 'C', 'P'),
    idCtxECCBPoint      = IPP_CONTEXT( 'P', 'E', 'C', 'B'),
    idCtxDH             = IPP_CONTEXT( ' ', ' ', 'D', 'H'),
    idCtxDLP            = IPP_CONTEXT( ' ', 'D', 'L', 'P'),
    idCtxCMAC           = IPP_CONTEXT( 'C', 'M', 'A', 'C'),
    idCtxRFFT2_8u,
    idCtxHilbert_32f32fc,
    idCtxHilbert_16s32fc,
    idCtxHilbert_16s16sc,
    idCtxTone_16s,
    idCtxTriangle_16s,
    idCtxDFTOutOrd_C_32fc,
    idCtxDFTOutOrd_C_64fc,
    idCtxFFT_C_32sc,
    idCtxFFT_C_32s,
    idCtxFFT_R_32s,
    idCtxFFT_R_16s32s,
    idCtxDecodeProgr_JPEG2K,
    idCtxWarp_MPEG4,
    idCtxQuantInvIntra_MPEG4,
    idCtxQuantInvInter_MPEG4,
    idCtxQuantIntra_MPEG4,
    idCtxQuantInter_MPEG4,
    idCtxAnalysisFilter_SBR_C_32f32fc,
    idCtxAnalysisFilter_SBR_C_32f,
    idCtxAnalysisFilter_SBR_R_32f,
    idCtxSynthesisFilter_SBR_C_32fc32f,
    idCtxSynthesisFilter_SBR_C_32f,
    idCtxSynthesisFilter_SBR_R_32f,
    idCtxSynthesisDownFilter_SBR_C_32fc32f,
    idCtxSynthesisDownFilter_SBR_C_32f,
    idCtxSynthesisDownFilter_SBR_R_32f,
    idCtxVLCEncode,
    idCtxVLCDecode,
    idCtxAnalysisFilter_SBR_C_32s32sc,
    idCtxAnalysisFilter_SBR_R_32s,
    idCtxSynthesisFilter_SBR_C_32sc32s,
    idCtxSynthesisFilter_SBR_R_32s,
    idCtxSynthesisDownFilter_SBR_C_32sc32s,
    idCtxSynthesisDownFilter_SBR_R_32s,
    idCtxSynthesisFilter_PQMF_MP3_32f,
    idCtxAnalysisFilter_PQMF_MP3_32f,
    idCtxResampleRow,
    idCtxAnalysisFilter_SBR_Enc_C_32f32fc,
    idCtxSynthesisFilter_DTS_32f,
    idCtxFilterBilateralGauss_8u,
    idCtxFilterBilateralGaussFast_8u,
    idCtxBGF,
    idCtxPolyGF,
    idCtxRSenc,
    idCtxRSdec,
    idCtxSnow3g        = IPP_CONTEXT( 'S', 'n', 'o', 'w'),
    idCtxSnow3gF8,
    idCtxSnow3gF9,
    idCtxKasumi        = IPP_CONTEXT( 'K', 'a', 's', 'u'),
    idCtxKasumiF8,
    idCtxKasumiF9,
    idCtxResizeHannFilter_8u,
    idCtxResizeLanczosFilter_8u,
    idCtxAESXCBC,
    idCtxAESCCM,
    idCtxAESGCM,
    idCtxMsgCatalog,
    idCtxGFP,
    idCtxGFPE,
    idCtxGFPX,
    idCtxGFPXE,
    idCtxGFPXQX,
    idCtxGFPXQXE,
    idCtxGFPEC,
    idCtxGFPPoint,
    idCtxGFPXEC,
    idCtxGFPXECPoint,
    idCtxPairing,
    idCtxResize_32f,
    idCtxResizeYUV420,
    idCtxResizeYUV422,
    idCtxResize_64f,
    idCtxFilterBilateralBorder,
    idCtxThresholdAdaptiveGauss,
    idCtxHOG,
    idCtxFastN,
    idCtxHash,
    idCtxSM3
} IppCtxId;




/* /////////////////////////////////////////////////////////////////////////////
           Helpers
  /////////////////////////////////////////////////////////////////////////// */

#define IPP_NOERROR_RET()  return ippStsNoErr
#define IPP_ERROR_RET( ErrCode )  return (ErrCode)

#ifdef _IPP_DEBUG

    #define IPP_BADARG_RET( expr, ErrCode )\
                {if (expr) { IPP_ERROR_RET( ErrCode ); }}

#else

    #define IPP_BADARG_RET( expr, ErrCode )

#endif


    #define IPP_BAD_SIZE_RET( n )\
                IPP_BADARG_RET( (n)<=0, ippStsSizeErr )

    #define IPP_BAD_STEP_RET( n )\
                IPP_BADARG_RET( (n)<=0, ippStsStepErr )

    #define IPP_BAD_PTR1_RET( ptr )\
                IPP_BADARG_RET( NULL==(ptr), ippStsNullPtrErr )

    #define IPP_BAD_PTR2_RET( ptr1, ptr2 )\
                {IPP_BAD_PTR1_RET( ptr1 ); IPP_BAD_PTR1_RET( ptr2 )}

    #define IPP_BAD_PTR3_RET( ptr1, ptr2, ptr3 )\
                {IPP_BAD_PTR2_RET( ptr1, ptr2 ); IPP_BAD_PTR1_RET( ptr3 )}

    #define IPP_BAD_PTR4_RET( ptr1, ptr2, ptr3, ptr4 )\
                {IPP_BAD_PTR2_RET( ptr1, ptr2 ); IPP_BAD_PTR2_RET( ptr3, ptr4 )}

    #define IPP_BAD_ISIZE_RET(roi) \
               IPP_BADARG_RET( ((roi).width<=0 || (roi).height<=0), ippStsSizeErr)

/* ////////////////////////////////////////////////////////////////////////// */
/*                              internal messages                             */

#define MSG_LOAD_DLL_ERR (-9700) /* Error at loading of %s library */
#define MSG_NO_DLL       (-9701) /* No DLLs were found in the Waterfall procedure */
#define MSG_NO_SHARED    (-9702) /* No shared libraries were found in the Waterfall procedure */

/* ////////////////////////////////////////////////////////////////////////// */


typedef union { /* double precision */
    Ipp64s  hex;
    Ipp64f   fp;
} IppFP_64f;

typedef union { /* single precision */
    Ipp32s  hex;
    Ipp32f   fp;
} IppFP_32f;


extern const IppFP_32f ippConstantOfNAN_32f;
extern const IppFP_64f ippConstantOfNAN_64f;

extern const IppFP_32f ippConstantOfINF_32f;
extern const IppFP_64f ippConstantOfINF_64f;
extern const IppFP_32f ippConstantOfINF_NEG_32f;
extern const IppFP_64f ippConstantOfINF_NEG_64f;

#define NAN_32F      (ippConstantOfNAN_32f.fp)
#define NAN_64F      (ippConstantOfNAN_64f.fp)
#define INF_32F      (ippConstantOfINF_32f.fp)
#define INF_64F      (ippConstantOfINF_64f.fp)
#define INF_NEG_32F  (ippConstantOfINF_NEG_32f.fp)
#define INF_NEG_64F  (ippConstantOfINF_NEG_64f.fp)

/* ////////////////////////////////////////////////////////////////////////// */

typedef enum {
    ippunreg=-1,
    ippac   = 0,
    ippcc   = 1,
    ippch   = 2,
    ippcp   = 3,
    ippcv   = 4,
    ippdc   = 5,
    ippdi   = 6,
    ippgen  = 7,
    ippi    = 8,
    ippj    = 9,
    ippm    = 10,
    ippr    = 11,
    ipps    = 12,
    ippsc   = 13,
    ippsr   = 14,
    ippvc   = 15,
    ippvm   = 16,
    ippmsdk = 17,
    ippcpepid = 18,
    ippe = 19,
    ipprs = 20,
    ippsq = 21,
    ippnomore
} IppDomain;

int __CDECL ownGetNumThreads( void );
int __CDECL ownGetFeature( Ipp64u MaskOfFeature ); /* the main function of tick-tock dispatcher */

#ifdef _IPP_DYNAMIC
typedef IppStatus (__STDCALL *DYN_RELOAD)( int );
void __CDECL ownRegisterLib( IppDomain, DYN_RELOAD );
void __CDECL ownUnregisterLib( IppDomain );
#endif

/*     the number of threads available for any ipp function that uses OMP;     */
/* at the ippxx.dll loading time is equal to the number of logical processors, */
/*  and can be changed ONLY externally by library user to any desired number   */
/*               by means of ippSetNumThreads() function                       */
#define IPP_GET_NUM_THREADS() ( ownGetNumThreads() )
#define IPP_OMP_NUM_THREADS() num_threads( IPP_GET_NUM_THREADS() )
#define IPP_OMP_LIMIT_MAX_NUM_THREADS(n)  num_threads( IPP_MIN(IPP_GET_NUM_THREADS(),(n)))


/* ////////////////////////////////////////////////////////////////////////// */

/* Define NULL pointer value */
#ifndef NULL
#ifdef  __cplusplus
#define NULL    0
#else
#define NULL    ((void *)0)
#endif
#endif

#define UNREFERENCED_PARAMETER(p) (p)=(p)

#if defined( _IPP_MARK_LIBRARY )
static char G[] = {73, 80, 80, 71, 101, 110, 117, 105, 110, 101, 243, 193, 210, 207, 215};
#endif


#define STR2(x)           #x
#define STR(x)       STR2(x)
#define MESSAGE( desc )\
     message(__FILE__ "(" STR(__LINE__) "):" #desc)

/*
// endian definition
*/
#define IPP_LITTLE_ENDIAN  (0)
#define IPP_BIG_ENDIAN     (1)

#if defined( _IPP_LE )
   #define IPP_ENDIAN IPP_LITTLE_ENDIAN

#elif defined( _IPP_BE )
   #define IPP_ENDIAN IPP_BIG_ENDIAN

#else
   #if defined( __ARMEB__ )
     #define IPP_ENDIAN IPP_BIG_ENDIAN

   #else
     #define IPP_ENDIAN IPP_LITTLE_ENDIAN

   #endif
#endif


/* ////////////////////////////////////////////////////////////////////////// */

/* intrinsics */
#if (_IPP >= _IPP_A6) || (_IPP32E >= _IPP32E_M7)
    #if defined(__INTEL_COMPILER) || (_MSC_VER >= 1300)
        #if (_IPP == _IPP_A6)
            #include "xmmintrin.h"
        #elif (_IPP == _IPP_W7)
            #if defined(__INTEL_COMPILER)
              #include "emmintrin.h"
            #else
              #undef _W7
              #include "emmintrin.h"
              #define _W7
            #endif
            #define _mm_loadu _mm_loadu_si128
        #elif (_IPP == _IPP_T7) || (_IPP32E == _IPP32E_M7)
            #if defined(__INTEL_COMPILER)
                #include "pmmintrin.h"
                #define _mm_loadu _mm_lddqu_si128
            #elif (_MSC_FULL_VER >= 140050110)
                #include "intrin.h"
                #define _mm_loadu _mm_lddqu_si128
            #elif (_MSC_FULL_VER < 140050110)
                #include "emmintrin.h"
                #define _mm_loadu _mm_loadu_si128
            #endif
        #elif (_IPP == _IPP_V8) || (_IPP32E == _IPP32E_U8)
            #if defined(__INTEL_COMPILER)
                #include "tmmintrin.h"
                #define _mm_loadu _mm_lddqu_si128
            #elif (_MSC_FULL_VER >= 140050110)
                #include "intrin.h"
                #define _mm_loadu _mm_lddqu_si128
            #elif (_MSC_FULL_VER < 140050110)
                #include "emmintrin.h"
                #define _mm_loadu _mm_loadu_si128
            #endif
        #elif (_IPP == _IPP_P8) || (_IPP32E == _IPP32E_Y8)
            #if defined(__INTEL_COMPILER)
                #include "smmintrin.h"
                #define _mm_loadu _mm_lddqu_si128
            #elif (_MSC_FULL_VER >= 140050110)
                #include "intrin.h"
                #define _mm_loadu _mm_lddqu_si128
            #elif (_MSC_FULL_VER < 140050110)
                #include "emmintrin.h"
                #define _mm_loadu _mm_loadu_si128
            #endif
        #elif (_IPP >= _IPP_G9) || (_IPP32E >= _IPP32E_E9)
            #if defined(__INTEL_COMPILER)
                #include "immintrin.h"
                #define _mm_loadu _mm_lddqu_si128
            #elif (_MSC_FULL_VER >= 160021003)
                #include "immintrin.h"
                #define _mm_loadu _mm_lddqu_si128
            #endif
        #endif
    #endif
#elif (_IPPLP32 >= _IPPLP32_S8) || (_IPPLP64 >= _IPPLP64_N8)
    #if defined(__INTEL_COMPILER)
        #include "tmmintrin.h"
        #define _mm_loadu _mm_lddqu_si128
    #elif (_MSC_FULL_VER >= 140050110)
        #include "intrin.h"
        #define _mm_loadu _mm_lddqu_si128
    #elif (_MSC_FULL_VER < 140050110)
        #include "emmintrin.h"
        #define _mm_loadu _mm_loadu_si128
    #endif
#elif (_IPPLRB >= _IPPLRB_B2)
    #if defined(__INTEL_COMPILER) || defined(_REF_LIB)
        #include "immintrin.h"
    #endif
#endif

// **** intrinsics for bit casting ****
#if defined(__INTEL_COMPILER)
extern unsigned int      __intel_castf32_u32(float val);
extern float             __intel_castu32_f32(unsigned int val);
extern unsigned __int64  __intel_castf64_u64(double val);
extern double            __intel_castu64_f64(unsigned __int64 val);
 #define __CAST_32f32u(val) __intel_castf32_u32((Ipp32f)val)
 #define __CAST_32u32f(val) __intel_castu32_f32((Ipp32u)val)
 #define __CAST_64f64u(val) __intel_castf64_u64((Ipp64f)val)
 #define __CAST_64u64f(val) __intel_castu64_f64((Ipp64u)val)
#else
 #define __CAST_32f32u(val) ( *((Ipp32u*)&val) )
 #define __CAST_32u32f(val) ( *((Ipp32f*)&val) )
 #define __CAST_64f64u(val) ( *((Ipp64u*)&val) )
 #define __CAST_64u64f(val) ( *((Ipp64f*)&val) )
#endif


// short names for vector registers casting
#define _pd2ps _mm_castpd_ps
#define _ps2pd _mm_castps_pd
#define _pd2pi _mm_castpd_si128
#define _pi2pd _mm_castsi128_pd
#define _ps2pi _mm_castps_si128
#define _pi2ps _mm_castsi128_ps

#define _ypd2ypi _mm256_castpd_si256
#define _ypi2ypd _mm256_castsi256_pd
#define _yps2ypi _mm256_castps_si256
#define _ypi2yps _mm256_castsi256_ps
#define _ypd2yps _mm256_castpd_ps
#define _yps2ypd _mm256_castps_pd

#define _yps2ps _mm256_castps256_ps128
#define _ypi2pi _mm256_castsi256_si128
#define _ypd2pd _mm256_castpd256_pd128
#define _ps2yps _mm256_castps128_ps256
#define _pi2ypi _mm256_castsi128_si256
#define _pd2ypd _mm256_castpd128_pd256


#if defined(__INTEL_COMPILER)
#define __IVDEP ivdep
#else
#define __IVDEP message("message :: 'ivdep' is not defined")
#endif
//usage: #pragma __IVDEP

/* //////////////////////////////////////////////////////////////////////////
  _IPP_DATA shoul be defined only:
    - if compile not merged library
    - only for 1 CPU for merged library to avoid data duplication
*/
#if defined( _MERGED_BLD ) && ( defined(_G9) || defined(_E9) ) /* compile data only for g9 and e9 CPU */
  #define _IPP_DATA 1
#elif !defined( _MERGED_BLD ) /* compile data if it isn't merged library */
  #define _IPP_DATA 1
#endif


#if defined( __cplusplus )
}
#endif

#endif /* __OWNDEFS_H__ */

