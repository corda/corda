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
 * \brief Buffer handling utilities interface.
 */
#ifndef EXAMPLE_UTIL_BUFFUTIL_H_
#define EXAMPLE_UTIL_BUFFUTIL_H_

#include <stddef.h>
#include "util/stdtypes.h"

/// Options controlling how a buffer should be printed.
typedef struct BufferPrintOptions {
  bool show_header;
  bool show_offset;
  bool show_hex;
  bool show_ascii;
  size_t bytes_per_group;
  size_t groups_per_line;
} BufferPrintOptions;

/// Toggle verbose logging
bool ToggleVerbosity();

/// Test if file exists
/*!
  \param[in] filename
  The file path.

  \returns bool
*/
bool FileExists(char const* filename);

/// Get file size
/*!
  \param[in] filename

  The file path.
  \returns size of the file in bytes
*/
size_t GetFileSize(char const* filename);

/// Get file size
/*!
  checks the size against an expected maximum size.
  \param[in] filename

  The file path.
  \param[in] max_size

  the maximum expected size of the file.
  \returns size of the file in bytes
*/
size_t GetFileSize_S(char const* filename, size_t max_size);

/// Allocate a buffer of a fixed size
/*!
  Logs an error message on failure.

  \param[out] buffer
  A pointer to the buffer to allocate.
  \param[in] size
  the requested size of the buffer in bytes.

  \returns
  A pointer to the allocated buffer or NULL if the allocation failed.

*/
void* AllocBuffer(size_t size);

/// Allocate a buffer to hold the content of a file and load
/*!
  Logs an error message on failure.

  \param[in] filename
  The file path.
  \param[out] size
  The allocated size of the buffer in bytes (same as file size).

  \returns
  A pointer to the allocated buffer or NULL if the allocation failed.

  \see ToggleVerbosity()
*/
void* NewBufferFromFile(const char* filename, size_t* size);

/// Read a buffer from a file with logging
/*!

  Verbosity of logging controlled by verbosity state


  \param[in] filename
  The file path.
  \param[in,out] buf
  The buffer.
  \param[in] size
  The size of the buffer in bytes.

  \returns 0 on success, non-zero failure

  \see ToggleVerbosity()
*/
int ReadLoud(char const* filename, void* buf, size_t size);

/// write a buffer from a file with logging
/*!

  Verbosity of logging controlled by verbosity state

  \param[in] buf
  The buffer.
  \param[in] size
  The size of the buffer in bytes.
  \param[in] filename
  The file path.

  \returns 0 on success, non-zero failure

  \see ToggleVerbosity()
*/
int WriteLoud(void* buf, size_t size, char const* filename);

/// print a buffer to standard out using user provided options
/*!
  \param[in] buf
  The buffer.
  \param[in] size
  The size of the buffer in bytes.
  \param[in] opts
  The formatting options.
*/
void PrintBufferOpt(const void* buffer, size_t size, BufferPrintOptions opts);

/// print a buffer to standard out using default options
/*!
  \param[in] buf
  The buffer.
  \param[in] size
  The size of the buffer in bytes.
*/
void PrintBuffer(const void* buffer, size_t size);

#endif  // EXAMPLE_UTIL_BUFFUTIL_H_
