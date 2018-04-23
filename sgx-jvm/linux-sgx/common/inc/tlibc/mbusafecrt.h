//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for full license information. 
//

/***
*   mbusafecrt.h - public declarations for SafeCRT lib
*

*
*   Purpose:
*       This file contains the public declarations SafeCRT
*       functions ported to MacOS. These are the safe versions of
*       functions standard functions banned by SWI
*

****/

/* shields! */

#ifndef MBUSAFECRT_H
#define MBUSAFECRT_H
#include <string.h>
#include <stdarg.h>
#include <wchar.h>
typedef wchar_t WCHAR;

#ifdef __cplusplus
    extern "C" {
#endif

extern errno_t strcat_s( char* ioDest, size_t inDestBufferSize, const char* inSrc );
extern errno_t wcscat_s( WCHAR* ioDest, size_t inDestBufferSize, const WCHAR* inSrc );

extern errno_t strncat_s( char* ioDest, size_t inDestBufferSize, const char* inSrc, size_t inCount );
extern errno_t wcsncat_s( WCHAR* ioDest, size_t inDestBufferSize, const WCHAR* inSrc, size_t inCount );

extern errno_t strcpy_s( char* outDest, size_t inDestBufferSize, const char* inSrc );
extern errno_t wcscpy_s( WCHAR* outDest, size_t inDestBufferSize, const WCHAR* inSrc );

extern errno_t strncpy_s( char* outDest, size_t inDestBufferSize, const char* inSrc, size_t inCount );
extern errno_t wcsncpy_s( WCHAR* outDest, size_t inDestBufferSize, const WCHAR* inSrc, size_t inCount );

extern char* strtok_s( char* inString, const char* inControl, char** ioContext );
extern WCHAR* wcstok_s( WCHAR* inString, const WCHAR* inControl, WCHAR** ioContext );

extern size_t wcsnlen( const WCHAR* inString, size_t inMaxSize );

extern errno_t _itoa_s( int inValue, char* outBuffer, size_t inDestBufferSize, int inRadix );
extern errno_t _itow_s( int inValue, WCHAR* outBuffer, size_t inDestBufferSize, int inRadix );

extern errno_t _ltoa_s( long inValue, char* outBuffer, size_t inDestBufferSize, int inRadix );
extern errno_t _ltow_s( long inValue, WCHAR* outBuffer, size_t inDestBufferSize, int inRadix );

extern errno_t _ultoa_s( unsigned long inValue, char* outBuffer, size_t inDestBufferSize, int inRadix );
extern errno_t _ultow_s( unsigned long inValue, WCHAR* outBuffer, size_t inDestBufferSize, int inRadix );

extern errno_t _i64toa_s( long long inValue, char* outBuffer, size_t inDestBufferSize, int inRadix );
extern errno_t _i64tow_s( long long inValue, WCHAR* outBuffer, size_t inDestBufferSize, int inRadix );

extern errno_t _ui64toa_s( unsigned long long inValue, char* outBuffer, size_t inDestBufferSize, int inRadix );
extern errno_t _ui64tow_s( unsigned long long inValue, WCHAR* outBuffer, size_t inDestBufferSize, int inRadix );

extern int sprintf_s( char *string, size_t sizeInBytes, const char *format, ... );
extern int swprintf_s( WCHAR *string, size_t sizeInWords, const WCHAR *format, ... );

extern int _snprintf_s( char *string, size_t sizeInBytes, size_t count, const char *format, ... );
extern int _snwprintf_s( WCHAR *string, size_t sizeInWords, size_t count, const WCHAR *format, ... );

extern int _vsprintf_s( char* string, size_t sizeInBytes, const char* format, va_list arglist );
extern int _vsnprintf_s( char* string, size_t sizeInBytes, size_t count, const char* format, va_list arglist );

extern int _vswprintf_s( WCHAR* string, size_t sizeInWords, const WCHAR* format, va_list arglist );
extern int _vsnwprintf_s( WCHAR* string, size_t sizeInWords, size_t count, const WCHAR* format, va_list arglist );

extern errno_t memcpy_s( void * dst, size_t sizeInBytes, const void * src, size_t count );
extern errno_t memmove_s( void * dst, size_t sizeInBytes, const void * src, size_t count );

#ifdef __cplusplus
    }
#endif

#endif	/* MBUSAFECRT_H */
