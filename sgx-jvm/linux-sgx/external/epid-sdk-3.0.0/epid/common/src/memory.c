/*############################################################################
  # Copyright 2016 Intel Corporation
  #
  # Licensed under the Apache License, Version 2.0 (the "License");
  # you may not use this file except in compliance with the License.
  # You may obtain a copy of the License at
  #
  #     http://www.apache.org/licenses/LICENSE-2.0
  #
  # Unless required by applicable law or agreed to in writing, software
  # distributed under the License is distributed on an "AS IS" BASIS,
  # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  # See the License for the specific language governing permissions and
  # limitations under the License.
  ############################################################################*/

/*!
 * \file
 * \brief Memory access implementation.
 */

#include "epid/common/src/memory.h"

#include <string.h>
#include <stdint.h>

/// Maximum size of the destination buffer
#ifndef RSIZE_MAX
#define RSIZE_MAX ((SIZE_MAX) >> 1)
#endif

#ifndef MIN
/// Evaluate to minimum of two values
#define MIN(a, b) ((a) < (b) ? (a) : (b))
#endif  // MIN

/// Copies count of character from dest to src
/*!  \note Implementation follows C11 memcpy_s but with checks always enabled
 */
int memcpy_S(void* dest, size_t destsz, void const* src, size_t count) {
  size_t i;
  if (!dest || destsz > RSIZE_MAX) return -1;
  if (!src || count > RSIZE_MAX || count > destsz ||
      count > (dest > src ? ((uintptr_t)dest - (uintptr_t)src)
                          : ((uintptr_t)src - (uintptr_t)dest))) {
    // zero out dest if error detected
    memset(dest, 0, destsz);
    return -1;
  }

  for (i = 0; i < count; i++) ((uint8_t*)dest)[i] = ((uint8_t*)src)[i];
  return 0;
}

void EpidZeroMemory(void* ptr, size_t size) { memset(ptr, 0, size); }

#if defined(EPID_ENABLE_EPID_ZERO_MEMORY_ON_FREE)

#if !defined(EPID_ALLOC_ALIGN)
/// Alignment constant for EpidAlloc, must be a power of two
#define EPID_ALLOC_ALIGN sizeof(size_t)
#endif  // !defined(EPID_ALLOC_ALIGN)

#pragma pack(1)
/// Allocated memory block information
typedef struct EpidAllocHeader {
  size_t length;  ///< number of bytes memory block is allocated for
  void* ptr;      ///< pointer to whole memory block including EpidAllocHeader
} EpidAllocHeader;
#pragma pack()

#endif  // defined(EPID_ENABLE_EPID_ZERO_MEMORY_ON_FREE)

void* EpidAlloc(size_t size) {
#if defined(EPID_ENABLE_EPID_ZERO_MEMORY_ON_FREE)
  void* ptr = NULL;
  if (size <= 0) return NULL;
  // Allocate memory enough to store size bytes and EpidAllocHeader
  ptr = calloc(1, size + EPID_ALLOC_ALIGN - 1 + sizeof(EpidAllocHeader));
  if (ptr) {
    void* aligned_pointer = (void*)(((uintptr_t)ptr + EPID_ALLOC_ALIGN +
                                     sizeof(EpidAllocHeader) - 1) &
                                    (~(EPID_ALLOC_ALIGN - 1)));
    ((EpidAllocHeader*)aligned_pointer)[-1].length = size;
    ((EpidAllocHeader*)aligned_pointer)[-1].ptr = ptr;
    return aligned_pointer;
  }
  return NULL;
#else  // defined(EPID_ENABLE_EPID_ZERO_MEMORY_ON_FREE)
  return calloc(1, size);
#endif  // defined(EPID_ENABLE_EPID_ZERO_MEMORY_ON_FREE)
}

void* EpidRealloc(void* ptr, size_t new_size) {
#if defined(EPID_ENABLE_EPID_ZERO_MEMORY_ON_FREE)
  void* new_ptr = EpidAlloc(new_size);
  if (!new_ptr) return NULL;
  if (ptr) {
    // Memory copy is used to copy a buffer of variable length
    if (0 != memcpy_S(new_ptr, ((EpidAllocHeader*)new_ptr)[-1].length, ptr,
                      MIN(((EpidAllocHeader*)ptr)[-1].length,
                          ((EpidAllocHeader*)new_ptr)[-1].length))) {
      EpidFree(new_ptr);
      return NULL;
    }
    EpidFree(ptr);
  }
  return new_ptr;
#else   // defined(EPID_ENABLE_EPID_ZERO_MEMORY_ON_FREE)
  return realloc(ptr, new_size);
#endif  // defined(EPID_ENABLE_EPID_ZERO_MEMORY_ON_FREE)
}

void EpidFree(void* ptr) {
#if defined(EPID_ENABLE_EPID_ZERO_MEMORY_ON_FREE)
  if (ptr) {
    EpidZeroMemory(ptr, ((EpidAllocHeader*)ptr)[-1].length);
    free(((EpidAllocHeader*)ptr)[-1].ptr);
  }
#else   // defined(EPID_ENABLE_EPID_ZERO_MEMORY_ON_FREE)
  free(ptr);
#endif  // defined(EPID_ENABLE_EPID_ZERO_MEMORY_ON_FREE)
}
