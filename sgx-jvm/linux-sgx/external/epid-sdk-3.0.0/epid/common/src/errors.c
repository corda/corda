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
 * \brief Error reporting implementation.
 */

#include <stddef.h>

#include "epid/common/stdtypes.h"
#include "epid/common/errors.h"

/// Record mapping status code to string
struct ErrorTextEntry {
  /// error code
  EpidStatus value;
  /// string associated with error code
  char const* text;
};

/// Mapping of status codes to strings
static const struct ErrorTextEntry kEnumToText[] = {
    {kEpidNoErr, "no error"},
    {kEpidErr, "unspecified error"},
    {kEpidSigInvalid, "invalid signature"},
    {kEpidSigRevokedInGroupRl, "signature revoked in GroupRl"},
    {kEpidSigRevokedInPrivRl, "signature revoked in PrivRl"},
    {kEpidSigRevokedInSigRl, "signature revoked in SigRl"},
    {kEpidSigRevokedInVerifierRl, "signature revoked in VerifierRl"},
    {kEpidNotImpl, "not implemented"},
    {kEpidBadArgErr, "bad arguments"},
    {kEpidNoMemErr, "could not allocate memory"},
    {kEpidMemAllocErr, "insufficient memory provided"},
    {kEpidMathErr, "internal math error"},
    {kEpidDivByZeroErr, "attempt to divide by zero"},
    {kEpidUnderflowErr, "underflow"},
    {kEpidHashAlgorithmNotSupported, "unsupported hash algorithm type"},
    {kEpidRandMaxIterErr, "reached max iteration for random number generation"},
    {kEpidDuplicateErr, "argument would add duplicate entry"},
    {kEpidInconsistentBasenameSetErr,
     "the set basename is inconsistent with supplied parameters"}};

char const* EpidStatusToString(EpidStatus e) {
  size_t i = 0;
  const size_t num_entries = sizeof(kEnumToText) / sizeof(kEnumToText[0]);
  for (i = 0; i < num_entries; i++) {
    if (e == kEnumToText[i].value) {
      return kEnumToText[i].text;
    }
  }
  return "unknown error";
}
