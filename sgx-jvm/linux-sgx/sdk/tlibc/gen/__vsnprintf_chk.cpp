/*
 * Copyright (C) 2012 The Android Open Source Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>

/*
 * Runtime implementation of __builtin____vsnprintf_chk.
 *
 * See
 *   http://gcc.gnu.org/onlinedocs/gcc/Object-Size-Checking.html
 *   http://gcc.gnu.org/ml/gcc-patches/2004-09/msg02055.html
 * for details.
 *
 */
extern "C" int __vsnprintf_chk(
        char *dest,
        size_t supplied_size,
        int /*flags*/,
        size_t dest_len_from_compiler,
        const char *format,
        va_list va)
{
    if (__predict_false(supplied_size > dest_len_from_compiler)) {
        /* TODO: add runtime error massage */
        abort();
    }

    return vsnprintf(dest, supplied_size, format, va);
}

/*
 * Runtime implementation of __builtin____snprintf_chk.
 *
 * See
 *   http://gcc.gnu.org/onlinedocs/gcc/Object-Size-Checking.html
 *   http://gcc.gnu.org/ml/gcc-patches/2004-09/msg02055.html
 * for details.
 *
 */
extern "C" int __snprintf_chk(
        char *dest,
        size_t supplied_size,
        int flags,
        size_t dest_len_from_compiler,
        const char *format, ...)
{
    va_list va;
    int retval;

    va_start(va, format);
    retval = __vsnprintf_chk(dest, supplied_size, flags,
                             dest_len_from_compiler, format, va);
    va_end(va);

    return retval;
}
