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

#pragma once

#ifndef _LRU_CACHE_H_
#define _LRU_CACHE_H_

#include <assert.h>

#include <list>

/* STL map is implemented as a tree, hence all operations are O(logN)
   STL unordered_map is implemented as hash, hence all operations are O(1)
   http://stackoverflow.com/questions/2196995/is-there-any-advantage-of-using-map-over-unordered-map-in-case-of-trivial-keys
   http://stackoverflow.com/questions/3902644/choosing-between-stdmap-and-stdunordered-map
   */

#include <unordered_map>

/* this hasher code was needed since strict ansi don't allow 'long long' type
 * adding -U__STRICT_ANSI__ to the compilation flags solved the issue
 * leaving this code here for future reference
namespace stlpmtx_std
{
template<> struct hash<uint64_t> {
  size_t operator()(uint64_t x) const { return (size_t)x; }
  };
}
*/

typedef struct _list_node
{
	uint64_t key;
} list_node_t;

typedef struct _map_node
{
	void* data;
	std::list<list_node_t*>::iterator list_it;
} map_node_t;

typedef std::unordered_map<uint64_t, map_node_t*>::iterator map_iterator;
typedef std::list<list_node_t*>::iterator list_iterator;

class lru_cache
{
private:
	std::list<list_node_t*> list;
	std::unordered_map<uint64_t, map_node_t*> map;

	list_iterator m_it; // for get_first and get_next sequence

public:	
	lru_cache();
	~lru_cache();

	void rehash(uint32_t size_);

	bool add(uint64_t key, void* p);
	void* get(uint64_t key);
	void* find(uint64_t key); // only returns the object, do not bump it to the head
	uint32_t size();

	void* get_first();
	void* get_next();
	void* get_last();
	void remove_last();
};

#endif // _LRU_CACHE_H_
