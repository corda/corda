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


/* Test Basic Types */

#include "sgx_trts.h"
#include "../Enclave.h"
#include "Enclave_t.h"
#include <limits>
#include <cmath>

/* used to eliminate `unused variable' warning */
#define UNUSED(val) (void)(val)

#define ULP 2

/* used to compare double variables in order to avoid compile warnings */
bool  almost_equal(double x, double y)
{
    /* the machine epsilon has to be scaled to the magnitude of the larger value
       and multiplied by the desired precision in ULPs (units in the last place) */
    return std::abs(x-y) <= std::numeric_limits<double>::epsilon() * std::abs(x+y) * ULP;
}

/* used to compare double variables in order to avoid compile warnings */
bool  almost_equal(float x, float y)
{
    /* the machine epsilon has to be scaled to the magnitude of the larger value
       and multiplied by the desired precision in ULPs (units in the last place) */
    return std::abs(x-y) <= std::numeric_limits<float>::epsilon() * std::abs(x+y) * ULP;
}

/* ecall_type_char:
 *   [char] value passed by App.
 */
void ecall_type_char(char val)
{
    assert(val == 0x12);
#ifndef DEBUG
    UNUSED(val);
#endif
}

/* ecall_type_int:
 *   [int] value passed by App.
 */
void ecall_type_int(int val)
{
    assert(val == 1234);
#ifndef DEBUG
    UNUSED(val);
#endif
}

/* ecall_type_float:
 *   [float] value passed by App.
 */
void ecall_type_float(float val)
{
    assert(almost_equal(val, (float)1234.0));
#ifndef DEBUG
    UNUSED(val);
#endif
}

/* ecall_type_double:
 *   [double] value passed by App.
 */
void ecall_type_double(double val)
{
    assert(almost_equal(val, (double)1234.5678));
#ifndef DEBUG
    UNUSED(val);
#endif
}

/* ecall_type_size_t:
 *   [size_t] value passed by App.
 */
void ecall_type_size_t(size_t val)
{
    assert(val == (size_t)12345678);
#ifndef DEBUG
    UNUSED(val);
#endif
}

/* ecall_type_wchar_t:
 *   [wchar_t] value passed by App.
 */
void ecall_type_wchar_t(wchar_t val)
{
    assert(val == (wchar_t)0x1234);
#ifndef DEBUG
    UNUSED(val);
#endif
}

/* ecall_type_struct:
 *   struct_foo_t is defined in EDL and can be used in ECALL.
 */
void ecall_type_struct(struct struct_foo_t val)
{
    assert(val.struct_foo_0 == 1234);
    assert(val.struct_foo_1 == 5678);
#ifndef DEBUG
    UNUSED(val);
#endif
}

/*
 * ecall_type_enum_union:
 *   enum_foo_t/union_foo_t is defined in EDL 
 *   and can be used in ECALL.
 */
void ecall_type_enum_union(enum enum_foo_t val1, union union_foo_t *val2)
{
    if (sgx_is_outside_enclave(val2, sizeof(union union_foo_t)) != 1)
        abort();
    val2->union_foo_0 = 1;
    val2->union_foo_1 = 2; /* overwrite union_foo_0 */
    assert(val1 == ENUM_FOO_0);
#ifndef DEBUG
    UNUSED(val1);
#endif
}
