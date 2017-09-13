/** dropt_string.h
  *
  * String routines for dropt.
  *
  * Copyright (c) 2006-2010 James D. Lin <jameslin@cal.berkeley.edu>
  *
  * The latest version of this file can be downloaded from:
  * <http://www.taenarum.com/software/dropt/>
  *
  * This software is provided 'as-is', without any express or implied
  * warranty.  In no event will the authors be held liable for any damages
  * arising from the use of this software.
  *
  * Permission is granted to anyone to use this software for any purpose,
  * including commercial applications, and to alter it and redistribute it
  * freely, subject to the following restrictions:
  *
  * 1. The origin of this software must not be misrepresented; you must not
  *    claim that you wrote the original software. If you use this software
  *    in a product, an acknowledgment in the product documentation would be
  *    appreciated but is not required.
  *
  * 2. Altered source versions must be plainly marked as such, and must not be
  *    misrepresented as being the original software.
  *
  * 3. This notice may not be removed or altered from any source distribution.
  */

#ifndef DROPT_STRING_H
#define DROPT_STRING_H

#include <stdarg.h>
#include "dropt.h"

#ifdef __cplusplus
extern "C" {
#endif

#ifdef DROPT_USE_WCHAR
    #define dropt_strlen wcslen
    #define dropt_strcmp wcscmp
    #define dropt_strncmp wcsncmp
    #define dropt_strchr wcschr
    #define dropt_strtol wcstol
    #define dropt_strtoul wcstoul
    #define dropt_strtod wcstod
    #define dropt_tolower towlower
    #define dropt_fputs fputws
#else
    #define dropt_strlen strlen
    #define dropt_strcmp strcmp
    #define dropt_strncmp strncmp
    #define dropt_strchr strchr
    #define dropt_strtol strtol
    #define dropt_strtoul strtoul
    #define dropt_strtod strtod
    #define dropt_tolower tolower
    #define dropt_fputs fputs
#endif

void* dropt_safe_malloc(size_t numElements, size_t elementSize);
void* dropt_safe_realloc(void* p, size_t numElements, size_t elementSize);

dropt_char* dropt_strdup(const dropt_char* s);
dropt_char* dropt_strndup(const dropt_char* s, size_t n);
int dropt_stricmp(const dropt_char* s, const dropt_char* t);
int dropt_strnicmp(const dropt_char* s, const dropt_char* t, size_t n);


#ifndef DROPT_NO_STRING_BUFFERS
typedef struct dropt_stringstream dropt_stringstream;

int dropt_vsnprintf(dropt_char* s, size_t n, const dropt_char* format, va_list args);
int dropt_snprintf(dropt_char* s, size_t n, const dropt_char* format, ...);

dropt_char* dropt_vasprintf(const dropt_char* format, va_list args);
dropt_char* dropt_asprintf(const dropt_char* format, ...);

dropt_stringstream* dropt_ssopen(void);
void dropt_ssclose(dropt_stringstream* ss);

void dropt_ssclear(dropt_stringstream* ss);
dropt_char* dropt_ssfinalize(dropt_stringstream* ss);
const dropt_char* dropt_ssgetstring(const dropt_stringstream* ss);

int dropt_vssprintf(dropt_stringstream* ss, const dropt_char* format, va_list args);
int dropt_ssprintf(dropt_stringstream* ss, const dropt_char* format, ...);
#endif /* DROPT_NO_STRING_BUFFERS */

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* DROPT_STRING_H */
