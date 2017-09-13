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

#include <string.h>
#include <stdlib.h>

/*
 * Runtime implementation of __strlen_chk.
 *
 * See
 *   http://gcc.gnu.org/onlinedocs/gcc/Object-Size-Checking.html
 *   http://gcc.gnu.org/ml/gcc-patches/2004-09/msg02055.html
 * for details.
 *
 * This strlen check is called if _FORTIFY_SOURCE is defined and
 * greater than 0.
 *
 * This test is designed to detect code such as:
 *
 * int main() {
 *   char buf[10];
 *   memcpy(buf, "1234567890", sizeof(buf));
 *   size_t len = strlen(buf); // segfault here with _FORTIFY_SOURCE
 *   printf("%d\n", len);
 *   return 0;
 * }
 *
 * or anytime strlen reads beyond an object boundary.
 */
extern "C" size_t __strlen_chk(const char *s, size_t s_len) {
    size_t ret = strlen(s);

    if (__predict_false(ret >= s_len)) {
        /* TODO: add runtime error massage */
        abort();
    }

    return ret;
}
