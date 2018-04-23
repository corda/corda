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
#ifndef EPID_COMMON_SRC_MEMORY_H_
#define EPID_COMMON_SRC_MEMORY_H_

#include <stdlib.h>
#include <string.h>

/*!
 * \file
 * \brief Memory access interface.
 * \addtogroup EpidCommon
 * @{
 */

/// When enabled secrets are wiped out from the memory by EpidFree
#define EPID_ENABLE_EPID_ZERO_MEMORY_ON_FREE

/// Clear information stored in block of memory pointer to by ptr
/*!

  \warning
  This function may be optimized away by some compilers. If it is, you
  should consider using a compiler or operating system specific memory
  sanitization function (e.g. memcpy_s or SecureZeroMemory).

  \param[in] ptr
  pointer to memory block
  \param[in] size
  number of bytes to clear
  */
void EpidZeroMemory(void* ptr, size_t size);

/// Allocates memory of size bytes
/*!
  The content of memory is initialized with zeros.
  Memory must be freed with EpidFree function.

  \param[in] size
  number of bytes to allocate

  \returns pointer to allocated memory.
 */
void* EpidAlloc(size_t size);

/// Reallocates memory allocated by EpidAlloc
/*!
  In case of error NULL pointer is returned and input memory block
  is not changed.
  Memory must be freed with EpidFree function.

  \param[in] ptr
  pointer to memory block to reallocate
  \param[in] new_size
  number of bytes to reallocate for

  \returns pointer to allocated memory.
 */
void* EpidRealloc(void* ptr, size_t new_size);

/// Frees memory allocated by EpidAlloc
/*!
  Clears information stored in the block of memory.

  \param[in] ptr
  pointer to allocated memory block
 */
void EpidFree(void* ptr);

#if !defined(SAFE_ALLOC)
/// Allocates zero initalized block of memory
#define SAFE_ALLOC(size) EpidAlloc(size);
#endif  // !defined(SAFE_ALLOC)
#if !defined(SAFE_FREE)
/// Deallocates space allocated by SAFE_ALLOC() and nulls pointer
#define SAFE_FREE(ptr)   \
  {                      \
    if (NULL != (ptr)) { \
      EpidFree(ptr);     \
      (ptr) = NULL;      \
    }                    \
  }
#endif  // !defined(SAFE_FREE)

#if !defined(SAFE_REALLOC)
/// Changes the size of the memory block pointed to by ptr
#define SAFE_REALLOC(ptr, size) EpidRealloc((ptr), (size))
#endif  // !defined(SAFE_REALLOC)

/// Copies bytes between buffers with security ehancements
/*!
  Copies count bytes from src to dest. If the source and destination
  overlap, the behavior is undefined.

  \param[out] dest
  pointer to the object to copy to
  \param[in] destsz
  max number of bytes to modify in the destination (typically the size
  of the destination object)
  \param[in] src
  pointer to the object to copy from
  \param[in] count
  number of bytes to copy

  \returns zero on success and non-zero value on error.
 */
int memcpy_S(void* dest, size_t destsz, void const* src, size_t count);

/*! @} */
#endif  // EPID_COMMON_SRC_MEMORY_H_
