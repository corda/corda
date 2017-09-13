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
 * \brief Buffer handling utilities implementation.
 */

#include <stdarg.h>
#include <util/strutil.h>

#include <stdio.h>
#if defined(_MSC_VER) && _MSC_VER < 1900
int vsnprintf(char* outBuf, size_t size, const char* format, va_list ap) {
  int count = -1;

  if (0 != size) {
    count = _vsnprintf_s(outBuf, size, _TRUNCATE, format, ap);
  }
  if (-1 == count) {
    // vsnprintf returns "The number of characters that would have been
    // written if n had been sufficiently large" however _vsnprintf_s
    // returns -1 if the content was truncated.
    // _vscprintf calculates that value
    count = _vscprintf(format, ap);
  }
  return count;
}

int snprintf(char* outBuf, size_t size, const char* format, ...) {
  int count;
  va_list ap;

  va_start(ap, format);
  count = vsnprintf(outBuf, size, format, ap);
  va_end(ap);

  return count;
}
#endif
