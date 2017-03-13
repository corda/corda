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
 * \brief Environment utilities implementation.
 */

#include <stdio.h>
#include <stdarg.h>
#include "util/envutil.h"

static char const* prog_name = NULL;

void set_prog_name(char const* name) { prog_name = name; }

char const* get_prog_name() { return prog_name; }

int log_error(char const* msg, ...) {
  int result = 0;
  int local_result = 0;
  va_list args;
  va_start(args, msg);
  do {
    local_result = fprintf(stderr, "%s: ", prog_name);
    if (local_result < 0) {
      result = local_result;
      break;
    }
    result += local_result;
    local_result = vfprintf(stderr, msg, args);
    if (local_result < 0) {
      result = local_result;
      break;
    }
    result += local_result;
    local_result = fprintf(stderr, "\n");
    if (local_result < 0) {
      result = local_result;
      break;
    }
    result += local_result;
  } while (0);
  va_end(args);
  return result;
}

int log_msg(char const* msg, ...) {
  int result = 0;
  int local_result = 0;
  va_list args;
  va_start(args, msg);
  do {
    local_result = vfprintf(stdout, msg, args);
    if (local_result < 0) {
      result = local_result;
      break;
    }
    result += local_result;
    local_result = fprintf(stdout, "\n");
    if (local_result < 0) {
      result = local_result;
      break;
    }
    result += local_result;
  } while (0);
  va_end(args);
  return result;
}

int log_fmt(char const* msg, ...) {
  int result = 0;
  va_list args;
  va_start(args, msg);
  result = vfprintf(stdout, msg, args);
  va_end(args);
  return result;
}
