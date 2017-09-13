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
/// Implementation of 1.1 issuer material file parsing utilities.
/*!
 * \file
 */
#include "epid/common/1.1/file_parser.h"
#include <string.h>

#include "epid/common/math/ecdsa.h"
#include "epid/common/src/memory.h"
#include "epid/common/src/file_parser-internal.h"

/// Intel(R) EPID 1.1 Group Public Key binary format
typedef struct Epid11GroupPubKeyCertificate {
  EpidFileHeader header;     ///< Intel(R) EPID binary file header
  Epid11GroupId gid;         ///< group ID
  Epid11G1ElemStr h1;        ///< an element in G1
  Epid11G1ElemStr h2;        ///< an element in G1
  Epid11G2ElemStr w;         ///< an element in G2
  EcdsaSignature signature;  ///< ECDSA Signature on SHA-256 of above values
} Epid11GroupPubKeyCertificate;

/// Parse a file with a revocation list of any type
static EpidStatus Epid11ParseRlFile(void const* buf, size_t len,
                                    EpidCaCertificate const* cert, void* rl,
                                    size_t* rl_len, EpidFileType file_type) {
  size_t min_rl_file_size = 0;
  size_t empty_rl_size = 0;
  size_t rl_entry_size = 0;
  EpidStatus result = kEpidErr;
  EpidFileHeader const* file_header = (EpidFileHeader*)buf;
  void const* buf_rl =
      (void const*)((unsigned char*)buf + sizeof(EpidFileHeader));
  size_t buf_rl_len = 0;
  EcdsaSignature const* signature = NULL;

  if (!buf || !cert || !rl_len) return kEpidBadArgErr;

  switch (file_type) {
    case kPrivRlFile:
      empty_rl_size = sizeof(Epid11PrivRl) - sizeof(((Epid11PrivRl*)0)->f[0]);
      rl_entry_size = sizeof(((Epid11PrivRl*)0)->f[0]);
      min_rl_file_size = sizeof(EpidFileHeader) + sizeof(Epid11PrivRl) -
                         sizeof(((Epid11PrivRl*)0)->f[0]) +
                         sizeof(EcdsaSignature);
      break;
    case kSigRlFile:
      empty_rl_size = sizeof(Epid11SigRl) - sizeof(((Epid11SigRl*)0)->bk[0]);
      rl_entry_size = sizeof(((Epid11SigRl*)0)->bk[0]);
      min_rl_file_size = sizeof(EpidFileHeader) + sizeof(Epid11SigRl) -
                         sizeof(((Epid11SigRl*)0)->bk[0]) +
                         sizeof(EcdsaSignature);
      break;
    case kGroupRlFile:
      empty_rl_size =
          sizeof(Epid11GroupRl) - sizeof(((Epid11GroupRl*)0)->gid[0]);
      rl_entry_size = sizeof(((Epid11GroupRl*)0)->gid[0]);
      min_rl_file_size = sizeof(EpidFileHeader) + sizeof(Epid11GroupRl) -
                         sizeof(((Epid11GroupRl*)0)->gid[0]) +
                         sizeof(EcdsaSignature);
      break;
    default:
      return kEpidErr;
  }

  if (min_rl_file_size > len) return kEpidBadArgErr;

  // Verify that Intel(R) EPID file header in the buffer is correct
  if (0 != memcmp(&file_header->epid_version, &kEpidVersionCode[kEpid1x],
                  sizeof(kEpidVersionCode[kEpid1x]))) {
    return kEpidBadArgErr;
  }
  if (0 != memcmp(&file_header->file_type, &kEpidFileTypeCode[file_type],
                  sizeof(file_header->file_type))) {
    return kEpidBadArgErr;
  }

  // Verify that CA certificate is correct
  result = EpidVerifyCaCertificate(cert);
  if (kEpidNoErr != result) return result;

  // Verify that RL in file buffer contains of integer number of entries
  buf_rl_len = len - sizeof(EpidFileHeader) - sizeof(EcdsaSignature);
  if (0 != ((buf_rl_len - empty_rl_size) % rl_entry_size)) {
    return kEpidBadArgErr;
  }

  signature =
      (EcdsaSignature*)((unsigned char*)buf + len - sizeof(EcdsaSignature));
  // Authenticate signature for buffer
  result = EcdsaVerifyBuffer(buf, len - sizeof(EcdsaSignature),
                             (EcdsaPublicKey*)&cert->pubkey, signature);
  if (kEpidSigValid != result) return result;

  buf_rl_len = len - sizeof(EpidFileHeader) - sizeof(EcdsaSignature);

  // If pointer to output buffer is NULL it should return required size of RL
  if (!rl) {
    *rl_len = buf_rl_len;
    return kEpidNoErr;
  }

  if (*rl_len < buf_rl_len) return kEpidBadArgErr;
  *rl_len = buf_rl_len;

  // Copy revocation list from file buffer to output
  // Memory copy is used to copy a revocation list of variable length
  if (0 != memcpy_S(rl, *rl_len, buf_rl, buf_rl_len)) return kEpidBadArgErr;

  return kEpidNoErr;
}

EpidStatus Epid11ParseGroupPubKeyFile(void const* buf, size_t len,
                                      EpidCaCertificate const* cert,
                                      Epid11GroupPubKey* pubkey) {
  EpidStatus result = kEpidErr;
  Epid11GroupPubKeyCertificate* buf_pubkey = (Epid11GroupPubKeyCertificate*)buf;

  if (!buf || !cert || !pubkey) {
    return kEpidBadArgErr;
  }

  if (sizeof(Epid11GroupPubKeyCertificate) > len) {
    return kEpidBadArgErr;
  }

  // Verify that Intel(R) EPID file header in the buffer is correct
  if (0 != memcmp(&buf_pubkey->header.epid_version, &kEpidVersionCode[kEpid1x],
                  sizeof(buf_pubkey->header.epid_version))) {
    return kEpidBadArgErr;
  }
  if (0 != memcmp(&buf_pubkey->header.file_type,
                  &kEpidFileTypeCode[kGroupPubKeyFile],
                  sizeof(buf_pubkey->header.file_type))) {
    return kEpidBadArgErr;
  }

  // Verify that CA certificate is correct
  result = EpidVerifyCaCertificate(cert);
  if (kEpidNoErr != result) return result;

  // Authenticate signature for buffer
  result = EcdsaVerifyBuffer(
      buf_pubkey, sizeof(Epid11GroupPubKeyCertificate) - sizeof(EcdsaSignature),
      (EcdsaPublicKey*)&cert->pubkey, &buf_pubkey->signature);
  if (kEpidSigValid != result) return result;

  // Copy group public key from the buffer to output
  pubkey->gid = buf_pubkey->gid;
  pubkey->h1 = buf_pubkey->h1;
  pubkey->h2 = buf_pubkey->h2;
  pubkey->w = buf_pubkey->w;

  return kEpidNoErr;
}

EpidStatus Epid11ParsePrivRlFile(void const* buf, size_t len,
                                 EpidCaCertificate const* cert,
                                 Epid11PrivRl* rl, size_t* rl_len) {
  return Epid11ParseRlFile(buf, len, cert, rl, rl_len, kPrivRlFile);
}

EpidStatus Epid11ParseSigRlFile(void const* buf, size_t len,
                                EpidCaCertificate const* cert, Epid11SigRl* rl,
                                size_t* rl_len) {
  return Epid11ParseRlFile(buf, len, cert, rl, rl_len, kSigRlFile);
}

EpidStatus Epid11ParseGroupRlFile(void const* buf, size_t len,
                                  EpidCaCertificate const* cert,
                                  Epid11GroupRl* rl, size_t* rl_len) {
  return Epid11ParseRlFile(buf, len, cert, rl, rl_len, kGroupRlFile);
}
