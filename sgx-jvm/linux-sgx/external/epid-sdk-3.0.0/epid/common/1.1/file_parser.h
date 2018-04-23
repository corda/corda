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
/// Epid 1.1 issuer material parsing utilities.
/*!
 * \file
 */
#ifndef EPID_COMMON_1_1_FILE_PARSER_H_
#define EPID_COMMON_1_1_FILE_PARSER_H_

#include <stddef.h>

#include "epid/common/1.1/types.h"
#include "epid/common/errors.h"
#include "epid/common/file_parser.h"

/// Parser for 1.1 issuer material
/*!
 \defgroup Epid11FileParserModule EPID 1.1 support

 Defines the APIs needed to parse Intel(R) EPID 1.1 issuer material.

 \ingroup FileParser
 \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
support</b></a>
  @{
*/

/// Extracts group public key from buffer in issuer binary format
/*!

  Extracts the first group public key from a buffer with format of
  Intel(R) EPID 1.1 Group Public Key Certificate Binary File. The
  function validates that the first public key was signed by the
  private key corresponding to the provided CA certificate and the
  size of the input buffer is correct.

  \warning
  It is the responsibility of the caller to authenticate the
  EpidCaCertificate.

  \param[in] buf
  Pointer to buffer containing public key to extract.

  \param[in] len
  The size of buf in bytes.

  \param[in] cert
  The issuing CA public key certificate.

  \param[out] pubkey
  The extracted group public key.

  \returns ::EpidStatus

  \retval ::kEpidSigInvalid
  Parsing failed due to data authentication failure.

  \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
 support</b></a>

 */
EpidStatus Epid11ParseGroupPubKeyFile(void const* buf, size_t len,
                                      EpidCaCertificate const* cert,
                                      Epid11GroupPubKey* pubkey);

/// Extracts private key revocation list from buffer in issuer binary format
/*!

  Extracts the private key revocation list from a buffer with format of
  Intel(R) EPID 1.1 Binary Private Key Revocation List File.  The function
  validates that the revocation list was signed by the private
  key corresponding to the provided CA certificate and the size of the
  input buffer is correct.

  To determine the required size of the revocation list output buffer,
  provide a null pointer for the output buffer.

  \warning
  It is the responsibility of the caller to authenticate the
  EpidCaCertificate.

  \param[in] buf
  Pointer to buffer containing the revocation list to extract.

  \param[in] len
  The size of buf in bytes.

  \param[in] cert
  The issuing CA public key certificate.

  \param[out] rl
  The extracted revocation list.  If Null, rl_len is filled with
  the required output buffer size.

  \param[in,out] rl_len
  The size of rl in bytes.

  \returns ::EpidStatus

  \retval ::kEpidSigInvalid
  Parsing failed due to data authentication failure.

  \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
 support</b></a>

 */
EpidStatus Epid11ParsePrivRlFile(void const* buf, size_t len,
                                 EpidCaCertificate const* cert,
                                 Epid11PrivRl* rl, size_t* rl_len);

/// Extracts signature revocation list from buffer in issuer binary format
/*!

  Extracts the signature based revocation list from a buffer with
  format of Intel(R) EPID 1.1 Binary Signature Revocation List File.  The
  function
  validates that the revocation list was signed by the private key
  corresponding to the provided CA certificate and the size of the
  input buffer is correct.

  To determine the required size of the revocation list output buffer,
  provide a null pointer for the output buffer.

  \warning
  It is the responsibility of the caller to authenticate the
  EpidCaCertificate.

  \param[in] buf
  Pointer to buffer containing the revocation list to extract.

  \param[in] len
  The size of buf in bytes.

  \param[in] cert
  The issuing CA public key certificate.

  \param[out] rl
  The extracted revocation list.  If Null, rl_len is filled with
  the required output buffer size.

  \param[in,out] rl_len
  The size of rl in bytes.

  \returns ::EpidStatus

  \retval ::kEpidSigInvalid
  Parsing failed due to data authentication failure.

  \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
 support</b></a>

 */
EpidStatus Epid11ParseSigRlFile(void const* buf, size_t len,
                                EpidCaCertificate const* cert, Epid11SigRl* rl,
                                size_t* rl_len);

/// Extracts group revocation list from buffer in issuer binary format
/*!

  Extracts the group revocation list from a buffer with format of
  Intel(R) EPID 1.1 Binary Group Certificate Revocation List File.  The function
  validates that the revocation list was signed by the private key
  corresponding to the provided CA certificate and the size of the
  input buffer is correct.

  To determine the required size of the revocation list output buffer,
  provide a null pointer for the output buffer.

  \warning
  It is the responsibility of the caller to authenticate the
  EpidCaCertificate.

  \param[in] buf
  Pointer to buffer containing the revocation list to extract.

  \param[in] len
  The size of buf in bytes.

  \param[in] cert
  The issuing CA public key certificate.

  \param[out] rl
  The extracted revocation list.  If Null, rl_len is filled with
  the required output buffer size.

  \param[in,out] rl_len
  The size of rl in bytes.

  \returns ::EpidStatus

  \retval ::kEpidSigInvalid
  Parsing failed due to data authentication failure.

  \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
 support</b></a>

 */
EpidStatus Epid11ParseGroupRlFile(void const* buf, size_t len,
                                  EpidCaCertificate const* cert,
                                  Epid11GroupRl* rl, size_t* rl_len);

/*!
  @}
*/

#endif  // EPID_COMMON_1_1_FILE_PARSER_H_
