/*
 * Copyright (C) 2011-2017 Intel Corporation. All rights reserved.
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

/* STLport configuration file
 * It is internal STLport header - DO NOT include it directly */

#define _STLP_SGX_CONFIG                1

/* Use new C headers in STLport instead */
#define _STLP_HAS_NO_NEW_C_HEADERS      1

//#define _STLP_USE_NATIVE_STDEXCEPT    1
//#define _STLP_NO_EXCEPTION_HEADER     1

/* Disable wcstombs, wctomb */
#define _STLP_NO_NATIVE_WIDE_STREAMS    1

/* Use separated C/C++ Runtime directories */
#define _STLP_NATIVE_C_INCLUDE_PATH                 tlibc
#define _STLP_NATIVE_CPP_RUNTIME_INCLUDE_PATH       stdc++

/* No I/O operations that involve OCalls allowed */
#define _STLP_NO_IOSTREAMS              1

/* Do not use secure routines in CRT */
#define _CRT_SECURE_NO_DEPRECATE        1

/* Do not support move semantic (C++11) */
#define _STLP_NO_MOVE_SEMANTIC          1
