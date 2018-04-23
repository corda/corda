/** dropt_string.c
  *
  * String routines for dropt.
  *
  * Copyright (c) 2006-2012 James D. Lin <jameslin@cal.berkeley.edu>
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

#ifdef _MSC_VER
    #include <tchar.h>
#endif

#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <ctype.h>
#include <wctype.h>
#include <stdio.h>
#include <assert.h>

#if __STDC_VERSION__ >= 199901L
    #include <stdint.h>
#else
    /* Compatibility junk for things that don't yet support ISO C99. */
    #if defined _MSC_VER || defined __BORLANDC__
        #ifndef va_copy
            #define va_copy(dest, src) (dest = (src))
        #endif
    #else
        #ifndef va_copy
            #error Unsupported platform.  va_copy is not defined.
        #endif
    #endif

    #ifndef SIZE_MAX
        #define SIZE_MAX ((size_t) -1)
    #endif
#endif

#include "dropt_string.h"

#ifndef MAX
#define MAX(x, y) (((x) > (y)) ? (x) : (y))
#endif

#ifndef MIN
#define MIN(x, y) (((x) < (y)) ? (x) : (y))
#endif

#ifdef DROPT_DEBUG_STRING_BUFFERS
    enum { default_stringstream_buffer_size = 1 };
    #define GROWN_STRINGSTREAM_BUFFER_SIZE(oldSize, minAmount) \
        ((oldSize) + (minAmount))
#else
    enum { default_stringstream_buffer_size = 256 };
    #define GROWN_STRINGSTREAM_BUFFER_SIZE(oldSize, minAmount) \
        MAX((oldSize) * 2, (oldSize) + (minAmount))
#endif


#ifndef DROPT_NO_STRING_BUFFERS
struct dropt_stringstream
{
    dropt_char* string; /* The string buffer. */
    size_t maxSize;     /* Size of the string buffer, in dropt_char-s, including space for NUL. */
    size_t used;        /* Number of elements used in the string buffer, excluding NUL. */
};
#endif


/** dropt_safe_malloc
  *
  *     A version of malloc that checks for integer overflow.
  *
  * PARAMETERS:
  *     IN numElements : The number of elements to allocate.
  *     IN elementSize : The size of each element, in bytes.
  *
  * RETURNS:
  *     A pointer to the allocated memory.
  *     Returns NULL if numElements is 0.
  *     Returns NULL on error.
  */
void*
dropt_safe_malloc(size_t numElements, size_t elementSize)
{
    return dropt_safe_realloc(NULL, numElements, elementSize);
}


/** dropt_safe_realloc
  *
  *     Wrapper around realloc to check for integer overflow.
  *
  * PARAMETERS:
  *     IN/OUT p       : A pointer to the memory block to resize.
  *                      If NULL, a new memory block of the specified size
  *                        will be allocated.
  *     IN numElements : The number of elements to allocate.
  *                      If 0, frees p.
  *     IN elementSize : The size of each element, in bytes.
  *
  * RETURNS:
  *     A pointer to the allocated memory.
  *     Returns NULL if numElements is 0.
  *     Returns NULL on error.
  */
void*
dropt_safe_realloc(void* p, size_t numElements, size_t elementSize)
{
    size_t numBytes;

    /* elementSize shouldn't legally be 0, but we check for it in case a
     * caller got the argument order wrong.
     */
    if (numElements == 0 || elementSize == 0)
    {
        /* The behavior of realloc(p, 0) is implementation-defined.  Let's
         * enforce a particular behavior.
         */
        free(p);

        assert(elementSize != 0);
        return NULL;
    }

    numBytes = numElements * elementSize;
    if (numBytes / elementSize != numElements)
    {
        /* Overflow. */
        return NULL;
    }

    return realloc(p, numBytes);
}


/** dropt_strdup
  *
  *     Duplicates a string.
  *
  * PARAMETERS:
  *     IN s : A NUL-terminated string to duplicate.
  *
  * RETURNS:
  *     The duplicated string.  The caller is responsible for calling
  *       free() on it when no longer needed.
  *     Returns NULL on error.
  */
dropt_char*
dropt_strdup(const dropt_char* s)
{
    return dropt_strndup(s, SIZE_MAX);
}


/** dropt_strndup
  *
  *     Duplicates the first n characters of a string.
  *
  * PARAMETERS:
  *     IN s : The string to duplicate.
  *     IN n : The maximum number of dropt_char-s to copy, excluding the
  *              NUL-terminator.
  *
  * RETURNS:
  *     The duplicated string, which is always NUL-terminated.  The caller
  *       is responsible for calling free() on it when no longer needed.
  *     Returns NULL on error.
  */
dropt_char*
dropt_strndup(const dropt_char* s, size_t n)
{
    dropt_char* copy;
    size_t len = 0;

    assert(s != NULL);

    while (len < n && s[len] != DROPT_TEXT_LITERAL('\0'))
    {
        len++;
    }

    if (len + 1 < len)
    {
        /* This overflow check shouldn't be strictly necessary.  len can be
         * at most SIZE_MAX, so SIZE_MAX + 1 can wrap around to 0, but
         * dropt_safe_malloc will return NULL for a 0-sized allocation.
         * However, favor defensive paranoia.
         */
        return NULL;
    }

    copy = dropt_safe_malloc(len + 1 /* NUL */, sizeof *copy);
    if (copy != NULL)
    {
        memcpy(copy, s, len * sizeof *copy);
        copy[len] = DROPT_TEXT_LITERAL('\0');
    }

    return copy;
}


/** dropt_stricmp
  *
  *     Compares two NUL-terminated strings ignoring case differences.  Not
  *       recommended for non-ASCII strings.
  *
  * PARAMETERS:
  *     IN s, t : The strings to compare.
  *
  * RETURNS:
  *     0 if the strings are equivalent,
  *     < 0 if s is lexically less than t,
  *     > 0 if s is lexically greater than t.
  */
int
dropt_stricmp(const dropt_char* s, const dropt_char* t)
{
    assert(s != NULL);
    assert(t != NULL);
    return dropt_strnicmp(s, t, SIZE_MAX);
}


/** dropt_strnicmp
  *
  *     Compares the first n characters of two strings, ignoring case
  *       differences.  Not recommended for non-ASCII strings.
  *
  * PARAMETERS:
  *     IN s, t : The strings to compare.
  *     IN n    : The maximum number of dropt_char-s to compare.
  *
  * RETURNS:
  *     0 if the strings are equivalent,
  *     < 0 if s is lexically less than t,
  *     > 0 if s is lexically greater than t.
  */
int
dropt_strnicmp(const dropt_char* s, const dropt_char* t, size_t n)
{
    assert(s != NULL);
    assert(t != NULL);

    if (s == t) { return 0; }

    while (n--)
    {
        if (*s == DROPT_TEXT_LITERAL('\0') && *t == DROPT_TEXT_LITERAL('\0'))
        {
            break;
        }
        else if (*s == *t || dropt_tolower(*s) == dropt_tolower(*t))
        {
            s++;
            t++;
        }
        else
        {
            return (dropt_tolower(*s) < dropt_tolower(*t))
                   ? -1
                   : +1;
        }
    }

    return 0;
}


#ifndef DROPT_NO_STRING_BUFFERS
/** dropt_vsnprintf
  *
  *     vsnprintf wrapper to provide ISO C99-compliant behavior.
  *
  * PARAMETERS:
  *     OUT s     : The destination buffer.  May be NULL if n is 0.
  *                 If non-NULL, always NUL-terminated.
  *     IN n      : The size of the destination buffer, measured in
  *                   dropt_char-s.
  *     IN format : printf-style format specifier.  Must not be NULL.
  *     IN args   : Arguments to insert into the formatted string.
  *
  * RETURNS:
  *     The number of characters that would be written to the destination
  *       buffer if it's sufficiently large, excluding the NUL-terminator.
  *     Returns -1 on error.
  */
int
dropt_vsnprintf(dropt_char* s, size_t n, const dropt_char* format, va_list args)
{
#if __STDC_VERSION__ >= 199901L || __GNUC__
    /* ISO C99-compliant.
     *
     * As far as I can tell, gcc's implementation of vsnprintf has always
     * matched the behavior required by the C99 standard (which is to
     * return the necessary buffer size).
     *
     * Note that this won't work with wchar_t because there is no true,
     * standard wchar_t equivalent of snprintf.  swprintf comes close but
     * doesn't return the necessary buffer size (and the standard does not
     * provide a guaranteed way to test if truncation occurred), and its
     * format string can't be used interchangeably with snprintf.
     *
     * It's simpler not to support wchar_t on non-Windows platforms.
     */
    assert(format != NULL);
    return vsnprintf(s, n, format, args);
#elif defined __BORLANDC__
    /* Borland's compiler neglects to NUL-terminate. */
    int ret;
    assert(format != NULL);
    ret = vsnprintf(s, n, format, args);
    if (n != 0) { s[n - 1] = DROPT_TEXT_LITERAL('\0'); }
    return ret;
#elif defined _MSC_VER
    /* _vsntprintf and _vsnprintf_s on Windows don't have C99 semantics;
     * they return -1 if truncation occurs.
     */
    va_list argsCopy;
    int ret;

    assert(format != NULL);

    va_copy(argsCopy, args);
    ret = _vsctprintf(format, argsCopy);
    va_end(argsCopy);

    if (n != 0)
    {
        assert(s != NULL);

    #if _MSC_VER >= 1400
        (void) _vsntprintf_s(s, n, _TRUNCATE, format, args);
    #else
        /* This version doesn't necessarily NUL-terminate.  Sigh. */
        (void) _vsnprintf(s, n, format, args);
        s[n - 1] = DROPT_TEXT_LITERAL('\0');
    #endif
    }

    return ret;

#else
    #error Unsupported platform.  dropt_vsnprintf unimplemented.
    return -1;
#endif
}


/** See dropt_vsnprintf. */
int
dropt_snprintf(dropt_char* s, size_t n, const dropt_char* format, ...)
{
    int ret;
    va_list args;
    va_start(args, format);
    ret = dropt_vsnprintf(s, n, format, args);
    va_end(args);
    return ret;
}


/** dropt_vasprintf
  *
  *     Allocates a formatted string with vprintf semantics.
  *
  * PARAMETERS:
  *     IN format : printf-style format specifier.  Must not be NULL.
  *     IN args   : Arguments to insert into the formatted string.
  *
  * RETURNS:
  *     The formatted string, which is always NUL-terminated.  The caller
  *       is responsible for calling free() on it when no longer needed.
  *     Returns NULL on error.
  */
dropt_char*
dropt_vasprintf(const dropt_char* format, va_list args)
{
    dropt_char* s = NULL;
    int len;
    va_list argsCopy;
    assert(format != NULL);

    va_copy(argsCopy, args);
    len = dropt_vsnprintf(NULL, 0, format, argsCopy);
    va_end(argsCopy);

    if (len >= 0)
    {
        size_t n = len + 1 /* NUL */;
        s = dropt_safe_malloc(n, sizeof *s);
        if (s != NULL)
        {
            dropt_vsnprintf(s, n, format, args);
        }
    }

    return s;
}


/** See dropt_vasprintf. */
dropt_char*
dropt_asprintf(const dropt_char* format, ...)
{
    dropt_char* s;

    va_list args;
    va_start(args, format);
    s = dropt_vasprintf(format, args);
    va_end(args);

    return s;
}


/** dropt_ssopen
  *
  *     Constructs a new dropt_stringstream.
  *
  * RETURNS:
  *     An initialized dropt_stringstream.  The caller is responsible for
  *       calling either dropt_ssclose() or dropt_ssfinalize() on it when
  *       no longer needed.
  *     Returns NULL on error.
  */
dropt_stringstream*
dropt_ssopen(void)
{
    dropt_stringstream* ss = malloc(sizeof *ss);
    if (ss != NULL)
    {
        ss->used = 0;
        ss->maxSize = default_stringstream_buffer_size;
        ss->string = dropt_safe_malloc(ss->maxSize, sizeof *ss->string);
        if (ss->string == NULL)
        {
            free(ss);
            ss = NULL;
        }
        else
        {
            ss->string[0] = DROPT_TEXT_LITERAL('\0');
        }
    }
    return ss;
}


/** dropt_ssclose
  *
  *     Destroys a dropt_stringstream.
  *
  * PARAMETERS:
  *     IN/OUT ss : The dropt_stringstream.
  */
void
dropt_ssclose(dropt_stringstream* ss)
{
    if (ss != NULL)
    {
        free(ss->string);
        free(ss);
    }
}


/** dropt_ssgetfreespace
  *
  * RETURNS:
  *     The amount of free space in the dropt_stringstream's internal
  *       buffer, measured in dropt_char-s.  Space used for the
  *       NUL-terminator is considered free. (The amount of free space
  *       therefore is always positive.)
  */
static size_t
dropt_ssgetfreespace(const dropt_stringstream* ss)
{
    assert(ss != NULL);
    assert(ss->maxSize > 0);
    assert(ss->maxSize > ss->used);
    return ss->maxSize - ss->used;
}


/** dropt_ssresize
  *
  *     Resizes a dropt_stringstream's internal buffer.  If the requested
  *     size is less than the amount of buffer already in use, the buffer
  *     will be shrunk to the minimum size necessary.
  *
  * PARAMETERS:
  *     IN/OUT ss : The dropt_stringstream.
  *     IN n      : The desired buffer size, in dropt_char-s.
  *
  * RETURNS:
  *     The new size of the dropt_stringstream's buffer in dropt_char-s,
  *       including space for a terminating NUL.
  */
static size_t
dropt_ssresize(dropt_stringstream* ss, size_t n)
{
    assert(ss != NULL);

    /* Don't allow shrinking if it will truncate the string. */
    if (n < ss->maxSize) { n = MAX(n, ss->used + 1 /* NUL */); }

    /* There should always be a buffer to point to. */
    assert(n > 0);

    if (n != ss->maxSize)
    {
        dropt_char* p = dropt_safe_realloc(ss->string, n, sizeof *ss->string);
        if (p != NULL)
        {
            ss->string = p;
            ss->maxSize = n;
            assert(ss->maxSize > 0);
         }
    }
    return ss->maxSize;
}


/** dropt_ssclear
  *
  *     Clears and re-initializes a dropt_stringstream.
  *
  * PARAMETERS:
  *     IN/OUT ss : The dropt_stringstream
  */
void
dropt_ssclear(dropt_stringstream* ss)
{
    assert(ss != NULL);

    ss->string[0] = DROPT_TEXT_LITERAL('\0');
    ss->used = 0;

    dropt_ssresize(ss, default_stringstream_buffer_size);
}


/** dropt_ssfinalize
  *
  *     Finalizes a dropt_stringstream; returns the contained string and
  *     destroys the dropt_stringstream.
  *
  * PARAMETERS:
  *     IN/OUT ss : The dropt_stringstream.
  *
  * RETURNS:
  *     The dropt_stringstream's string, which is always NUL-terminated.
  *       Note that the caller assumes ownership of the returned string and
  *       is responsible for calling free() on it when no longer needed.
  */
dropt_char*
dropt_ssfinalize(dropt_stringstream* ss)
{
    dropt_char* s;
    assert(ss != NULL);

    /* Shrink to fit. */
    dropt_ssresize(ss, 0);

    s = ss->string;
    ss->string = NULL;

    dropt_ssclose(ss);

    return s;
}


/** dropt_ssgetstring
  *
  * PARAMETERS:
  *     IN ss : The dropt_stringstream.
  *
  * RETURNS:
  *     The dropt_stringstream's string, which is always NUL-terminated.
  *       The returned string will no longer be valid if further operations
  *       are performed on the dropt_stringstream or if the
  *       dropt_stringstream is closed.
  */
const dropt_char*
dropt_ssgetstring(const dropt_stringstream* ss)
{
    assert(ss != NULL);
    return ss->string;
}


/** dropt_vssprintf
  *
  *     Appends a formatted string with vprintf semantics to a
  *     dropt_stringstream.
  *
  * PARAMETERS:
  *     IN/OUT ss : The dropt_stringstream.
  *     IN format : printf-style format specifier.  Must not be NULL.
  *     IN args   : Arguments to insert into the formatted string.
  *
  * RETURNS:
  *     The number of characters written to the dropt_stringstream,
  *       excluding the NUL-terminator.
  *     Returns a negative value on error.
  */
int
dropt_vssprintf(dropt_stringstream* ss, const dropt_char* format, va_list args)
{
    int n;
    va_list argsCopy;
    assert(ss != NULL);
    assert(format != NULL);

    va_copy(argsCopy, args);
    n = dropt_vsnprintf(NULL, 0, format, argsCopy);
    va_end(argsCopy);

    if (n > 0)
    {
        size_t available = dropt_ssgetfreespace(ss);
        if ((unsigned int) n >= available)
        {
            /* It's possible that newSize < ss->maxSize if
             * GROWN_STRINGSTREAM_BUFFER_SIZE overflows, but it should be
             * safe since we'll recompute the available space.
             */
            size_t newSize = GROWN_STRINGSTREAM_BUFFER_SIZE(ss->maxSize, n);
            dropt_ssresize(ss, newSize);
            available = dropt_ssgetfreespace(ss);
        }
        assert(available > 0); /* Space always is reserved for NUL. */

        /* snprintf's family of functions return the number of characters
         * that would be output with a sufficiently large buffer, excluding
         * NUL.
         */
        n = dropt_vsnprintf(ss->string + ss->used, available, format, args);

        /* We couldn't allocate enough space. */
        if ((unsigned int) n >= available) { n = -1; }

        if (n > 0) { ss->used += n; }
    }
    return n;
}


/** See dropt_vssprintf. */
int
dropt_ssprintf(dropt_stringstream* ss, const dropt_char* format, ...)
{
    int n;

    va_list args;
    va_start(args, format);
    n = dropt_vssprintf(ss, format, args);
    va_end(args);

    return n;
}
#endif /* DROPT_NO_STRING_BUFFERS */
