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
/// Internal functions of Epid issuer material parsing utilities.
/*!
 * \file
 */
#ifndef EPID_COMMON_SRC_FILE_PARSER_INTERNAL_H_
#define EPID_COMMON_SRC_FILE_PARSER_INTERNAL_H_

#include <stddef.h>

#include "epid/common/file_parser.h"
#include "epid/common/types.h"
#include "epid/common/errors.h"

/// Verifies CA certificate to contain EC secp256r1 parameters
/*!

Verifies that certificate contains EC secp256r1 parameters,
creates static copies of these parameters and compares them with
ones in cert. Also verifies that certificate contains correct file header.

\param[in] cert
The issuing CA public key certificate.

\returns ::EpidStatus

\retval ::kEpidBadArgErr
Verification failed.

*/
EpidStatus EpidVerifyCaCertificate(EpidCaCertificate const* cert);

#endif  // EPID_COMMON_SRC_FILE_PARSER_INTERNAL_H_
