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
 * \brief OctString handling utility interface.
 */
#ifndef EPID_COMMON_TESTHELPER_OCTSTR_TESTHELPER_H_
#define EPID_COMMON_TESTHELPER_OCTSTR_TESTHELPER_H_

#include "epid/common/errors.h"
#include "epid/common/types.h"

/// Compares 2 OctStr256
/*!

if A==B, then pResult = 0
if A > B, then pResult = 1
if A < B, then pResult = 2

\param[in] pA
OctStr256 A
\param[in] pB
OctStr256 B
\param[out] pResult
Comparison Result

\returns ::EpidStatus

*/
EpidStatus Cmp_OctStr256(const OctStr256* pA, const OctStr256* pB,
                         unsigned int* pResult);

#endif  // EPID_COMMON_TESTHELPER_OCTSTR_TESTHELPER_H_
