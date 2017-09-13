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


#include <cstdlib>
#include <string>

#include "../Enclave.h"
#include "Enclave_t.h"

/*
 * ecall_exception:
 *   throw/catch C++ exception inside the enclave.
 */

void ecall_exception(void)
{
    std::string foo = "foo";
    try {
        throw std::runtime_error(foo);
    }
    catch (std::runtime_error const& e) {
        assert( foo == e.what() );
        std::runtime_error clone("");
        clone = e;
        assert(foo == clone.what() );
    }
    catch (...) {
        assert( false );
    }
}

#include <map>
#include <algorithm>

using namespace std;

/*
 * ecall_map:
 *   Utilize STL <map> in the enclave.
 */
void ecall_map(void)
{
    typedef map<char, int, less<char> > map_t;
    typedef map_t::value_type map_value;
    map_t m;

    m.insert(map_value('a', 1));
    m.insert(map_value('b', 2));
    m.insert(map_value('c', 3));
    m.insert(map_value('d', 4));

    assert(m['a'] == 1);
    assert(m['b'] == 2);
    assert(m['c'] == 3);
    assert(m['d'] == 4);

    assert(m.find('e') == m.end());
    
    return;
}
