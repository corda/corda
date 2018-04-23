/*
   Copyright 2010-2016 Intel Corporation

   This software is licensed to you in accordance
   with the agreement between you and Intel Corporation.

   Alternatively, you can use this file in compliance
   with the Apache license, Version 2.


   Apache License, Version 2.0

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

/**                                                                            
********************************************************************************
**
**    @file typedefs.h
**
**    @brief  Contains common type declarations used throughout the code
**
**    @author Ranjit Narjala
**
********************************************************************************
*/   

#ifndef _TYPEDEFS_H_
#define _TYPEDEFS_H_

#include <stdint.h>
#include <wchar.h>

#ifdef _WIN32
#include <windows.h>
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef uint8_t			UINT8;
typedef uint16_t		UINT16;
typedef uint32_t		UINT32;
typedef int8_t			INT8;
typedef int16_t			INT16;
typedef int32_t			INT32;
typedef void *			PVOID;

#ifdef _WIN32
typedef wchar_t			FILECHAR;
#else
typedef char			TCHAR;
typedef char			FILECHAR;
#define __declspec(x)
#endif /* _WIN32 */

#ifndef IN
#define IN		// Defines an input parameter
#endif

#ifndef OUT
#define OUT		// Defines an output parameter
#endif

#ifndef INOUT
#define INOUT	// Defines an input/output parameter
#endif

#ifndef TRUE
#define TRUE  1     // True value for a BOOLEAN
#endif
#ifndef FALSE
#define FALSE 0     // False value for a BOOLEAN
#endif 

#ifdef __cplusplus
} // extern "C"
#endif

#ifndef NULL
#ifdef  __cplusplus
// Define NULL pointer value under C++
#define NULL    0
#else
// Define NULL pointer value non-C++
#define NULL    ((void *)0)
#endif
#endif

#endif // _TYPEDEFS_H
