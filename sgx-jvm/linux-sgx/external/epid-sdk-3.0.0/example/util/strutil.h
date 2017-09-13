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
#ifndef EXAMPLE_UTIL_STRUTIL_H_
#define EXAMPLE_UTIL_STRUTIL_H_

#include <stdio.h>
#include <stdarg.h>

// Prior to version 14.0 snprintf was not supported in MSVC
#if defined(_MSC_VER) && _MSC_VER < 1900
int vsnprintf(char* outBuf, size_t size, const char* format, va_list ap);
int snprintf(char* outBuf, size_t size, const char* format, ...);
#endif

#endif  // EXAMPLE_UTIL_STRUTIL_H_
