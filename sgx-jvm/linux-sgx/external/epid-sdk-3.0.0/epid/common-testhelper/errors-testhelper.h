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
 * \brief Error handling C++ wrapper interface.
 */
#ifndef EPID_COMMON_TESTHELPER_ERRORS_TESTHELPER_H_
#define EPID_COMMON_TESTHELPER_ERRORS_TESTHELPER_H_

#include <string>
#include <cstdio>
#include <stdexcept>
#include <vector>
#include <initializer_list>

extern "C" {
#include "epid/common/math/bignum.h"
}

/// Macro used to indicate fatal error during unit test run
#define THROW_ON_EPIDERR(actual)                                       \
  if (kEpidNoErr != actual) {                                          \
    printf("%s(%d): error: %s\n", __FILE__, __LINE__, "test defect");  \
    throw std::logic_error(std::string("Failed to call: ") + #actual); \
  }

#endif  // EPID_COMMON_TESTHELPER_ERRORS_TESTHELPER_H_
