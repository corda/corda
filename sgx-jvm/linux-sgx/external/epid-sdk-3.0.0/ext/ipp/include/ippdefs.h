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
//              Intel(R) Integrated Performance Primitives
//              Common Types and Macro Definitions
// 
// 
*/


#ifndef __IPPDEFS_H__
#define __IPPDEFS_H__

#ifdef __cplusplus
extern "C" {
#endif


#if defined( _IPP_PARALLEL_STATIC ) || defined( _IPP_PARALLEL_DYNAMIC )
  #pragma message("Threaded versions of IPP libraries are deprecated and will be removed in one of the future IPP releases. Use the following link for details: https://software.intel.com/sites/products/ipp-deprecated-features-feedback/")
#endif

#if defined (_WIN64)
#define _INTEL_PLATFORM "intel64/"
#elif defined (_WIN32)
#define _INTEL_PLATFORM "ia32/"
#endif

#if !defined( IPPAPI )

  #if defined( IPP_W32DLL ) && (defined( _WIN32 ) || defined( _WIN64 ))
    #if defined( _MSC_VER ) || defined( __ICL )
      #define IPPAPI( type,name,arg ) \
                     __declspec(dllimport)   type __STDCALL name arg;
    #else
      #define IPPAPI( type,name,arg )        type __STDCALL name arg;
    #endif
  #else
    #define   IPPAPI( type,name,arg )        type __STDCALL name arg;
  #endif

#endif

#if (defined( __ICL ) || defined( __ECL ) || defined(_MSC_VER)) && !defined( _PCS ) && !defined( _PCS_GENSTUBS )
  #if( __INTEL_COMPILER >= 1100 ) /* icl 11.0 supports additional comment */
    #if( _MSC_VER >= 1400 )
      #define IPP_DEPRECATED( comment ) __declspec( deprecated ( comment ))
    #else
      #pragma message ("your icl version supports additional comment for deprecated functions but it can't be displayed")
      #pragma message ("because internal _MSC_VER macro variable setting requires compatibility with MSVC7.1")
      #pragma message ("use -Qvc8 switch for icl command line to see these additional comments")
      #define IPP_DEPRECATED( comment ) __declspec( deprecated )
    #endif
  #elif( _MSC_FULL_VER >= 140050727 )&&( !defined( __INTEL_COMPILER )) /* VS2005 supports additional comment */
    #define IPP_DEPRECATED( comment ) __declspec( deprecated ( comment ))
  #elif( _MSC_VER <= 1200 )&&( !defined( __INTEL_COMPILER )) /* VS 6 doesn't support deprecation */
    #define IPP_DEPRECATED( comment )
  #else
    #define IPP_DEPRECATED( comment ) __declspec( deprecated )
  #endif
#elif (defined(__ICC) || defined(__ECC) || defined( __GNUC__ )) && !defined( _PCS ) && !defined( _PCS_GENSTUBS )
  #if defined( __GNUC__ )
    #if __GNUC__ >= 4 && __GNUC_MINOR__ >= 5
      #define IPP_DEPRECATED( message ) __attribute__(( deprecated( message )))
    #else
      #define IPP_DEPRECATED( message ) __attribute__(( deprecated ))
    #endif
  #else
    #define IPP_DEPRECATED( comment ) __attribute__(( deprecated ))
  #endif
#else
  #define IPP_DEPRECATED( comment )
#endif

#if (defined( __ICL ) || defined( __ECL ) || defined(_MSC_VER))
  #if !defined( _IPP_NO_DEFAULT_LIB )
    #if  (( defined( _IPP_PARALLEL_DYNAMIC ) && !defined( _IPP_PARALLEL_STATIC ) && !defined( _IPP_SEQUENTIAL_DYNAMIC ) && !defined( _IPP_SEQUENTIAL_STATIC )) || \
          (!defined( _IPP_PARALLEL_DYNAMIC ) &&  defined( _IPP_PARALLEL_STATIC ) && !defined( _IPP_SEQUENTIAL_DYNAMIC ) && !defined( _IPP_SEQUENTIAL_STATIC )) || \
          (!defined( _IPP_PARALLEL_DYNAMIC ) && !defined( _IPP_PARALLEL_STATIC ) &&  defined( _IPP_SEQUENTIAL_DYNAMIC ) && !defined( _IPP_SEQUENTIAL_STATIC )) || \
          (!defined( _IPP_PARALLEL_DYNAMIC ) && !defined( _IPP_PARALLEL_STATIC ) && !defined( _IPP_SEQUENTIAL_DYNAMIC ) &&  defined( _IPP_SEQUENTIAL_STATIC )))
    #elif (!defined( _IPP_PARALLEL_DYNAMIC ) && !defined( _IPP_PARALLEL_STATIC ) && !defined( _IPP_SEQUENTIAL_DYNAMIC ) && !defined( _IPP_SEQUENTIAL_STATIC ))
      #define _IPP_NO_DEFAULT_LIB
    #else
      #error Illegal combination of _IPP_PARALLEL_DYNAMIC/_IPP_PARALLEL_STATIC/_IPP_SEQUENTIAL_DYNAMIC/_IPP_SEQUENTIAL_STATIC, only one definition can be defined
    #endif
  #endif
#else
  #define _IPP_NO_DEFAULT_LIB
  #if (defined( _IPP_PARALLEL_DYNAMIC ) || defined( _IPP_PARALLEL_STATIC ) || defined(_IPP_SEQUENTIAL_DYNAMIC) || defined(_IPP_SEQUENTIAL_STATIC))
    #pragma message ("defines _IPP_PARALLEL_DYNAMIC/_IPP_PARALLEL_STATIC/_IPP_SEQUENTIAL_DYNAMIC/_IPP_SEQUENTIAL_STATIC do not have any effect in current configuration")
  #endif
#endif

#if !defined( _IPP_NO_DEFAULT_LIB )
  #if defined( _IPP_PARALLEL_STATIC )
    #pragma comment( lib, "libircmt" )
    #pragma comment( lib, "libmmt" )
    #pragma comment( lib, "svml_dispmt" )
    #pragma comment( lib, "libiomp5md" )
  #endif
#endif

#include "ippbase.h"
#include "ipptypes.h"

extern const IppiRect ippRectInfinite;

#ifdef __cplusplus
}
#endif

#endif /* __IPPDEFS_H__ */
