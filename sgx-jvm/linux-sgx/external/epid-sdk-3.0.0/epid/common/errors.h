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
#ifndef EPID_COMMON_ERRORS_H_
#define EPID_COMMON_ERRORS_H_
/*!
 * \file
 * \brief Error reporting.
*/

/// Error reporting interface.
/*!
 \defgroup ErrorCodes errors
 This module defines the return status type. It also provides tools for
 interactions with status values, such as converting them to a string.

 \ingroup EpidCommon
  @{
*/

/// Return status for SDK functions.
/*!
  Convention for status values is as follows:
  - Zero indicates "success"
  - Any positive number indicates "success with status"
  - Any negative number indicates "failure"
*/
typedef enum {
  kEpidNoErr = 0,                   //!< no error
  kEpidSigValid = 0,                //!< Signature is valid
  kEpidSigInvalid = 1,              //!< Signature is invalid
  kEpidSigRevokedInGroupRl = 2,     //!< Signature revoked in GroupRl
  kEpidSigRevokedInPrivRl = 3,      //!< Signature revoked in PrivRl
  kEpidSigRevokedInSigRl = 4,       //!< Signature revoked in SigRl
  kEpidSigRevokedInVerifierRl = 5,  //!< Signature revoked in VerifierRl
  kEpidErr = -999,                  //!< unspecified error
  kEpidNotImpl,                     //!< not implemented error
  kEpidBadArgErr,                   //!< incorrect arg to function
  kEpidNoMemErr,                    //!< not enough memory for the operation
  kEpidMemAllocErr,   //!< insufficient memory allocated for operation
  kEpidMathErr,       //!< internal math error
  kEpidDivByZeroErr,  //!< an attempt to divide by zero
  kEpidUnderflowErr,  //!< a value became less than minimum supported level
  kEpidHashAlgorithmNotSupported,  //!< unsupported hash algorithm type
  kEpidRandMaxIterErr,  //!< reached max iteration for random number generation
  kEpidDuplicateErr,    //!< argument would add duplicate entry
  kEpidInconsistentBasenameSetErr,    //!< set basename conflicts with arguments
  kEpidMathQuadraticNonResidueError,  //!< quadratic Non-Residue Error
} EpidStatus;

/// Returns string representation of error code.
/*!
 \param e
 The status value.

 \returns The string describing the status.
*/
char const* EpidStatusToString(EpidStatus e);

/*! @} */
#endif  // EPID_COMMON_ERRORS_H_
