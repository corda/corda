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

#ifndef __CPUDEF_H__
#define __CPUDEF_H__

#include "ippcore.h"

#if defined( __cplusplus )
extern "C" {
#endif

#undef __CDECL
#if defined( _WIN32 ) || defined ( _WIN64 )
  #define __CDECL    __cdecl
#else
  #define __CDECL
#endif


/* Intel CPU informator */

typedef struct {
   int family;
   int stepping;
   int model;
   int type;
   int feature;
   int tlb;
   int cache;
   int mmx;
   int freq;
   int ssx;
   int wni;
   int htt;
   int pni;
   int em64t;
   int mni;
   int phcores;
   int sse41;
   int sse42;
   int ext_family;
   int ext_model;
   int movbe_instr;
   int avx;
   int xsavexgetbv;
} ippIntelCpuId;

int __CDECL ownGetMaskFeatures( Ipp64u* pFeaturesMask );
int __CDECL ownGetFeature( Ipp64u MaskOfFeature );
int __CDECL ipp_is_avx_extension( void );

__INT64  __CDECL ipp_get_pentium_counter (void);
int __CDECL ipp_is_mmx_extension (void);
int __CDECL ipp_is_ssx_extension (void);
int __CDECL ipp_is_wni_extension (void);
int __CDECL ipp_is_htt_extension( void );
int __CDECL ipp_is_pni_extension( void );
int __CDECL ipp_is_mni_extension( void );
int __CDECL ipp_is_sse41_extension( void );
int __CDECL ipp_is_sse42_extension( void );
int __CDECL ipp_is_movbe( void );
int __CDECL ipp_get_cores_on_die( void );
int __CDECL ipp_is_em64t_extension( void );
int __CDECL ipp_has_cpuid ( void );
int __CDECL ipp_has_rdtsc( void );

void __CDECL ipp_get_pentium_ident ( ippIntelCpuId* cpuid );
int  __CDECL ipp_is_GenuineIntel ( void );
int  __CDECL ipp_max_cpuid_input( void );
int  __CDECL ipp_get_cpuid( int regs[4], int valEAX, int valECX );
void __CDECL ipp_get_cache_line_size( int* szCacheLine );

int  __CDECL  ipp_isnan( double x );
int  __CDECL  ipp_finite( double x );
int  __CDECL  ipp_isnan_32f( float x );
int  __CDECL  ipp_finite_32f( float x );
#define ipp_isfinite ipp_finite

unsigned int __CDECL  ipp_control87 ( unsigned int newcw, unsigned int mask );
unsigned int __CDECL  ipp_status87 ( void );
unsigned int __CDECL  ipp_clear87 ( void );

unsigned int  __CDECL  ipp_clear_ssx (void);
/* topology/affinity */

/* here are definitions of the CW bits exactly as x87 and ssx have */

#define IPP_FPU_MASK_RC     0x0c00
#define IPP_FPU_MASK_PC     0x0300
#define IPP_FPU_MASK_RCPC   0x0f00

#define IPP_FPU_RC_NEAR     0x0000
#define IPP_FPU_RC_DOWN     0x0400
#define IPP_FPU_RC_UP       0x0800
#define IPP_FPU_RC_ZERO     0x0c00

#define IPP_FPU_PC_24       0x0000
#define IPP_FPU_PC_53       0x0200
#define IPP_FPU_PC_64       0x0300


unsigned int __CDECL ipp_set_rcpc_fpu( unsigned int newrcpc, unsigned int mask);
void __CDECL ipp_set_cw_fpu( unsigned int cw );

#define IPP_SSX_RC_NEAR     0x0000
#define IPP_SSX_RC_DOWN     0x2000
#define IPP_SSX_RC_UP       0x4000
#define IPP_SSX_RC_ZERO     0x6000
#define IPP_SSX_MASK_RC     0x6000


unsigned int __CDECL ipp_set_rc_ssx( unsigned int newrc );
void __CDECL ipp_set_cw_ssx( unsigned int cw );

/* ================= FPU section ===================== */

/*  Control bits - disable exceptions   */
#define FPU_EXC_MSK         0x003f  /* Exception Masks Mask         */
#define FPU_MSK_INVALID     0x0001  /*  invalid operation           */
#define FPU_MSK_DENORMAL    0x0002  /*  denormalized operand        */
#define FPU_MSK_ZERODIV     0x0004  /*  zero divide                 */
#define FPU_MSK_OVERFLOW    0x0008  /*  overflow                    */
#define FPU_MSK_UNDERFLOW   0x0010  /*  underflow                   */
#define FPU_MSK_INEXACT     0x0020  /*  inexact (precision)         */

/*  Status bits - exceptions    */
#define FPU_EXC_FLG         0x003f  /* Exception Flags Mask         */
#define FPU_FLG_INVALID     0x0001  /*  invalid operation           */
#define FPU_FLG_DENORMAL    0x0002  /*  denormalized operand        */
#define FPU_FLG_ZERODIV     0x0004  /*  zero divide                 */
#define FPU_FLG_OVERFLOW    0x0008  /*  overflow                    */
#define FPU_FLG_UNDERFLOW   0x0010  /*  underflow                   */
#define FPU_FLG_INEXACT     0x0020  /*  inexact (precision)         */

/*  Control bits - rounding control */
#define FPU_RND             0x0c00  /* Rounding Control Mask        */
#define FPU_RND_NEAR        0x0000  /*  near                        */
#define FPU_RND_DOWN        0x0400  /*  down                        */
#define FPU_RND_UP          0x0800  /*  up                          */
#define FPU_RND_CHOP        0x0c00  /*  chop                        */

/*  Control bits - precision control    */
#define FPU_PRC             0x0300  /* Precision Control Mask       */
#define FPU_PRC_64          0x0300  /*  64 bits                     */
#define FPU_PRC_53          0x0200  /*  53 bits                     */
#define FPU_PRC_24          0x0000  /*  24 bits                     */

/*  Control bits - all masks    */
#define FPU_ALL             0x0f3f  /* all masks                    */

/* ============= definition for control/status world ============== */

#define FPU_SET_EXC_MASK(mask) ps_set_cw_fpu(mask,FPU_EXC_MSK)
#define FPU_GET_EXC_MASK()     (ps_set_cw_fpu(0,0) & FPU_EXC_MSK)

#define FPU_GET_EXC_FLAG()     (ps_get_sw_fpu() & FPU_EXC_FLG)

#define FPU_SET_RND_MODE(mode) ps_set_cw_fpu(mode,FPU_RND)
#define FPU_GET_RND_MODE()     (ps_set_cw_fpu(0,0) & FPU_RND)

#define FPU_SET_PRC_MODE(mode) ps_set_cw_fpu(mode,FPU_PRC)
#define FPU_GET_PRC_MODE()     (ps_set_cw_fpu(0,0) & FPU_PRC)

unsigned int __CDECL ps_set_cw_fpu( unsigned int newcw, unsigned int msk);
unsigned int __CDECL ps_get_cw_fpu(void);
unsigned int __CDECL ps_get_sw_fpu(void);
unsigned int __CDECL ps_clear_fpu(void);

/* ======================= SSX section ============================ */

/*  Control bits - disable exceptions   */
#define SSX_EXC_MSK             0x1f80  /* Disabling exception mask     */
#define SSX_MSK_INEXACT         0x1000  /*  precision (inexact)         */
#define SSX_MSK_UNDERFLOW       0x0800  /*  underflow                   */
#define SSX_MSK_OVERFLOW        0x0400  /*  overflow                    */
#define SSX_MSK_ZERODIV         0x0200  /*  divide by zero              */
#define SSX_MSK_DENORMAL        0x0100  /*  denormalized                */
#define SSX_MSK_INVALID         0x0080  /*  invalid operation           */

/*  Status bits - exceptions    */
#define SSX_EXC_FLG             0x003f  /* Exception flags mask         */
#define SSX_FLG_INEXACT         0x0020  /*  precision (inexact)         */
#define SSX_FLG_UNDERFLOW       0x0010  /*  underflow                   */
#define SSX_FLG_OVERFLOW        0x0008  /*  overflow                    */
#define SSX_FLG_ZERODIV         0x0004  /*  divide by zero              */
#define SSX_FLG_DENORMAL        0x0002  /*  denormalized                */
#define SSX_FLG_INVALID         0x0001  /*  invalid operation           */

/*  Control bits - rounding control */
#define SSX_RND                 0x6000  /* Rounding control mask        */
#define SSX_RND_NEAR            0x0000  /*  near                        */
#define SSX_RND_DOWN            0x2000  /*  down                        */
#define SSX_RND_UP              0x4000  /*  up                          */
#define SSX_RND_CHOP            0x6000  /*  chop                        */

/*  Control bits - flush to zero mode   */
#define SSX_FZ                  0x8000  /* Flush to zero mask           */
#define SSX_FZ_ENABLE           0x8000  /*  flush to zero               */
#define SSX_FZ_DISABLE          0x0000  /*  not flush to zero           */

/*  Control bits - denormals are zero mode   */
#define SSX_DAZ                 0x0040  /* denorm. are zero mask        */
#define SSX_DAZ_ENABLE          0x0040  /* denorm. are zero             */
#define SSX_DAZ_DISABLE         0x0000  /* denorm. are not zero         */

#define SSX_ALL                 0xffbf  /* All masks                    */

/* ==================== definition for SSX register =============== */

#define SSX_SET_EXC_MASK(mask) ps_set_ssx(mask,SSX_EXC_MSK)
#define SSX_GET_EXC_MASK()     (ps_get_ssx() & SSX_EXC_MSK)

#define SSX_SET_EXC_FLAG(flag) ps_set_ssx(flag,SSX_EXC_FLG)
#define SSX_GET_EXC_FLAG()     (ps_get_ssx() & SSX_EXC_FLG)

#define SSX_SET_RND_MODE(mode) ps_set_ssx(mode,SSX_RND)
#define SSX_GET_RND_MODE()     (ps_get_ssx() & SSX_RND)

#define SSX_SET_FZ_MODE(mode)  ps_set_ssx(mode,SSX_FZ)
#define SSX_GET_FZ_MODE()      (ps_get_ssx() & SSX_FZ)

#define SSX_SET_DAZ_MODE(mode) ps_set_ssx(mode,SSX_DAZ)
#define SSX_GET_DAZ_MODE()     (ps_get_ssx() & SSX_DAZ)

unsigned int __CDECL ps_set_ssx(unsigned int newssx, unsigned int msk);
unsigned int __CDECL ps_get_ssx(void);
unsigned int __CDECL ipp_tst_daz_ssx(void);

#if defined( __cplusplus )
}
#endif

#endif /* __CPUDEF_H__ */

/* ////////////////////////// End of file "cpudef.h" //////////////////////// */
