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
 * \brief Epid issuer material parsing utilities.
 */
#ifndef EPID_COMMON_FILE_PARSER_H_
#define EPID_COMMON_FILE_PARSER_H_

#include <stddef.h>

#include "epid/common/types.h"
#include "epid/common/errors.h"

/// Parser for issuer material
/*!
  \defgroup FileParser fileparser
  Provides an API for parsing buffers formatted according to the
  various IoT Intel(R) EPID binary file formats.

  \ingroup EpidCommon
  @{
*/

/// Recognized Intel(R) EPID versions
typedef enum EpidVersion {
  kEpid1x,           ///< Intel(R) EPID version 1.x
  kEpid2x,           ///< Intel(R) EPID version 2.x
  kNumEpidVersions,  ///< Maximum number of EPID versions
} EpidVersion;

/// Encoding of issuer material Intel(R) EPID versions
extern const OctStr16 kEpidVersionCode[kNumEpidVersions];

/// Recognized Intel(R) EPID file types
typedef enum EpidFileType {
  kIssuingCaPubKeyFile,  ///< IoT Issuing CA public key file
  kGroupPubKeyFile,      ///< Group Public Key Output File Format
  kPrivRlFile,           ///< Binary Private Key Revocation List
  kSigRlFile,            ///< Binary Signature Revocation List
  kGroupRlFile,          ///< Binary Group Revocation List
  kPrivRlRequestFile,    ///< Binary Private Key Revocation Request
  kSigRlRequestFile,     ///< Binary Signature Revocation Request
  kGroupRlRequestFile,   ///< Binary Group Revocation Request
  kNumFileTypes,         ///< Maximum number of file types
} EpidFileType;

/// Encoding of issuer material file types
extern const OctStr16 kEpidFileTypeCode[kNumFileTypes];

#pragma pack(1)
/// Intel(R) EPID binary file header
typedef struct EpidFileHeader {
  OctStr16 epid_version;  ///< Intel(R) EPID Version
  OctStr16 file_type;     ///< File Type
} EpidFileHeader;

/// IoT CA Certificate binary format
typedef struct EpidCaCertificate {
  EpidFileHeader header;     ///< Intel(R) EPID binary file header
  OctStr512 pubkey;          ///< Public Key (Qx, Qy)
  OctStr256 prime;           ///< Prime of GF(p)
  OctStr256 a;               ///< Coefficient of E Curve
  OctStr256 b;               ///< Coefficient of E Curve
  OctStr256 x;               ///< X coordinate of Base point G
  OctStr256 y;               ///< Y coordinate of Base point G
  OctStr256 r;               ///< Order of base point
  EcdsaSignature signature;  ///< ECDSA Signature on SHA-256 of above values
} EpidCaCertificate;
#pragma pack()

/// Extracts Intel(R) EPID Binary Output File header information
/*!
  \param[in] buf
  Pointer to buffer containing Intel(R) EPID Binary Output File to parse.

  \param[in] len
  The size of buf in bytes.

  \param[out] epid_version
  The extracted EPID version or kNumEpidVersions if EPID version is unknown.
  Pass NULL to not extract.

  \param[out] file_type
  The extracted EPID file type or kNumFileTypes if file type is unknown.
  Pass NULL to not extract.

  \returns ::EpidStatus

*/
EpidStatus EpidParseFileHeader(void const* buf, size_t len,
                               EpidVersion* epid_version,
                               EpidFileType* file_type);

/// Extracts group public key from buffer in issuer binary format
/*!

  Extracts the first group public key from a buffer with format of
  Intel(R) EPID 2.0 Group Public Key Certificate Binary File. The
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

 */
EpidStatus EpidParseGroupPubKeyFile(void const* buf, size_t len,
                                    EpidCaCertificate const* cert,
                                    GroupPubKey* pubkey);

/// Extracts private key revocation list from buffer in issuer binary format
/*!

  Extracts the private key revocation list from a buffer with format of
  Binary Private Key Revocation List File.  The function
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

 */
EpidStatus EpidParsePrivRlFile(void const* buf, size_t len,
                               EpidCaCertificate const* cert, PrivRl* rl,
                               size_t* rl_len);

/// Extracts signature revocation list from buffer in issuer binary format
/*!

  Extracts the signature based revocation list from a buffer with
  format of Binary Signature Revocation List File.  The function
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

 */
EpidStatus EpidParseSigRlFile(void const* buf, size_t len,
                              EpidCaCertificate const* cert, SigRl* rl,
                              size_t* rl_len);

/// Extracts group revocation list from buffer in issuer binary format
/*!

  Extracts the group revocation list from a buffer with format of
  Binary Group Certificate Revocation List File.  The function
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

 */
EpidStatus EpidParseGroupRlFile(void const* buf, size_t len,
                                EpidCaCertificate const* cert, GroupRl* rl,
                                size_t* rl_len);

/*!
  @}
*/

#endif  // EPID_COMMON_FILE_PARSER_H_
