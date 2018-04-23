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
#ifndef EPID_COMMON_STDTYPES_H_
#define EPID_COMMON_STDTYPES_H_

/*!
 * \file
 * \brief C99 standard data types.
 */

#include <stdint.h>  // Fixed-width integer types

#ifndef __cplusplus
#ifndef _Bool
/// C99 standard name for bool
#define _Bool char
/// Boolean type
typedef char bool;
/// integer constant 1
#define true 1
/// integer constant 0
#define false 0
#endif  //
#endif  // ifndef __cplusplus

#endif  // EPID_COMMON_STDTYPES_H_
