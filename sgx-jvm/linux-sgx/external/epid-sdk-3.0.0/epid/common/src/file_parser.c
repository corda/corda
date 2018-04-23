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
 *
 * \brief Implementation of issuer material file parsing utilities.
 *
 */
#include "epid/common/file_parser.h"

#include <string.h>

#include "epid/common/math/ecdsa.h"
#include "epid/common/src/memory.h"
#include "epid/common/src/file_parser-internal.h"

const OctStr16 kEpidVersionCode[kNumEpidVersions] = {
    {0x01, 0x00}, {0x02, 0x00},
};

const OctStr16 kEpidFileTypeCode[kNumFileTypes] = {
    {0x00, 0x11}, {0x00, 0x0C}, {0x00, 0x0D}, {0x00, 0x0E},
    {0x00, 0x0F}, {0x00, 0x03}, {0x00, 0x0B}, {0x00, 0x13},
};

/// Intel(R) EPID 2.0 Group Public Key binary format
typedef struct EpidGroupPubKeyCertificate {
  EpidFileHeader header;     ///< Intel(R) EPID binary file header
  GroupId gid;               ///< group ID
  G1ElemStr h1;              ///< an element in G1
  G1ElemStr h2;              ///< an element in G1
  G2ElemStr w;               ///< an element in G2
  EcdsaSignature signature;  ///< ECDSA Signature on SHA-256 of above values
} EpidGroupPubKeyCertificate;

/// Intel(R) EPID version
static const OctStr16 kEpidVersion = {0x02, 0x00};

/// Verify that certificate contains of EC secp256r1 parameters
EpidStatus EpidVerifyCaCertificate(EpidCaCertificate const* cert) {
  // Prime of GF(p) for secp256r1
  static const unsigned char secp256r1_p[] = {
      // 2^256 -2^224 +2^192 +2^96 -1
      0xff, 0xff, 0xff, 0xff, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff, 0xff,
      0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff};

  // Coefficient of E Curve secp256r1
  static const unsigned char secp256r1_a[] = {
      0xff, 0xff, 0xff, 0xff, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff, 0xff,
      0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xfc};

  // Coefficient of E Curve secp256r1
  static const unsigned char secp256r1_b[] = {
      0x5a, 0xc6, 0x35, 0xd8, 0xaa, 0x3a, 0x93, 0xe7, 0xb3, 0xeb, 0xbd,
      0x55, 0x76, 0x98, 0x86, 0xbc, 0x65, 0x1d, 0x06, 0xb0, 0xcc, 0x53,
      0xb0, 0xf6, 0x3b, 0xce, 0x3c, 0x3e, 0x27, 0xd2, 0x60, 0x4b};

  // X coordinate of Base point G of secp256r1
  static const unsigned char secp256r1_gx[] = {
      0x6b, 0x17, 0xd1, 0xf2, 0xe1, 0x2c, 0x42, 0x47, 0xf8, 0xbc, 0xe6,
      0xe5, 0x63, 0xa4, 0x40, 0xf2, 0x77, 0x03, 0x7d, 0x81, 0x2d, 0xeb,
      0x33, 0xa0, 0xf4, 0xa1, 0x39, 0x45, 0xd8, 0x98, 0xc2, 0x96};

  // Y coordinate of Base point G of secp256r1
  static const unsigned char secp256r1_gy[] = {
      0x4f, 0xe3, 0x42, 0xe2, 0xfe, 0x1a, 0x7f, 0x9b, 0x8e, 0xe7, 0xeb,
      0x4a, 0x7c, 0x0f, 0x9e, 0x16, 0x2b, 0xce, 0x33, 0x57, 0x6b, 0x31,
      0x5e, 0xce, 0xcb, 0xb6, 0x40, 0x68, 0x37, 0xbf, 0x51, 0xf5};

  // Order of base point of secp256r1
  static const unsigned char secp256r1_r[] = {
      0xff, 0xff, 0xff, 0xff, 0x00, 0x00, 0x00, 0x00, 0xff, 0xff, 0xff,
      0xff, 0xff, 0xff, 0xff, 0xff, 0xbc, 0xe6, 0xfa, 0xad, 0xa7, 0x17,
      0x9e, 0x84, 0xf3, 0xb9, 0xca, 0xc2, 0xfc, 0x63, 0x25, 0x51,
  };

  if (!cert) return kEpidBadArgErr;

  // Verify that certificate contains of correct file header
  if (0 !=
      memcmp(&cert->header.epid_version, &kEpidVersion, sizeof(kEpidVersion))) {
    return kEpidBadArgErr;
  }
  if (0 != memcmp(&cert->header.file_type,
                  &kEpidFileTypeCode[kIssuingCaPubKeyFile],
                  sizeof(cert->header.file_type))) {
    return kEpidBadArgErr;
  }

  // Verify that certificate contains of EC secp256r1 parameters
  if (0 != memcmp(&cert->prime, secp256r1_p, sizeof(secp256r1_p))) {
    return kEpidBadArgErr;
  }
  if (0 != memcmp(&cert->a, secp256r1_a, sizeof(secp256r1_a))) {
    return kEpidBadArgErr;
  }
  if (0 != memcmp(&cert->b, secp256r1_b, sizeof(secp256r1_b))) {
    return kEpidBadArgErr;
  }
  if (0 != memcmp(&cert->x, secp256r1_gx, sizeof(secp256r1_gx))) {
    return kEpidBadArgErr;
  }
  if (0 != memcmp(&cert->y, secp256r1_gy, sizeof(secp256r1_gy))) {
    return kEpidBadArgErr;
  }
  if (0 != memcmp(&cert->r, secp256r1_r, sizeof(secp256r1_r))) {
    return kEpidBadArgErr;
  }

  return kEpidNoErr;
}

EpidStatus EpidParseFileHeader(void const* buf, size_t len,
                               EpidVersion* epid_version,
                               EpidFileType* file_type) {
  EpidFileHeader* header = (EpidFileHeader*)buf;
  if (!buf || len < sizeof(EpidFileHeader)) return kEpidBadArgErr;

  if (epid_version) {
    if (0 == memcmp((void*)&header->epid_version, &kEpidVersionCode[kEpid1x],
                    sizeof(header->epid_version))) {
      *epid_version = kEpid1x;
    } else if (0 == memcmp((void*)&header->epid_version,
                           &kEpidVersionCode[kEpid2x],
                           sizeof(header->epid_version))) {
      *epid_version = kEpid2x;
    } else {
      // set default value
      *epid_version = kNumEpidVersions;
    }
  }
  if (file_type) {
    if (0 == memcmp((void*)&header->file_type,
                    &kEpidFileTypeCode[kIssuingCaPubKeyFile],
                    sizeof(header->file_type))) {
      *file_type = kIssuingCaPubKeyFile;
    } else if (0 == memcmp((void*)&header->file_type,
                           &kEpidFileTypeCode[kGroupPubKeyFile],
                           sizeof(header->file_type))) {
      *file_type = kGroupPubKeyFile;
    } else if (0 == memcmp((void*)&header->file_type,
                           &kEpidFileTypeCode[kPrivRlFile],
                           sizeof(header->file_type))) {
      *file_type = kPrivRlFile;
    } else if (0 == memcmp((void*)&header->file_type,
                           &kEpidFileTypeCode[kSigRlFile],
                           sizeof(header->file_type))) {
      *file_type = kSigRlFile;
    } else if (0 == memcmp((void*)&header->file_type,
                           &kEpidFileTypeCode[kGroupRlFile],
                           sizeof(header->file_type))) {
      *file_type = kGroupRlFile;
    } else if (0 == memcmp((void*)&header->file_type,
                           &kEpidFileTypeCode[kPrivRlRequestFile],
                           sizeof(header->file_type))) {
      *file_type = kPrivRlRequestFile;
    } else if (0 == memcmp((void*)&header->file_type,
                           &kEpidFileTypeCode[kSigRlRequestFile],
                           sizeof(header->file_type))) {
      *file_type = kSigRlRequestFile;
    } else if (0 == memcmp((void*)&header->file_type,
                           &kEpidFileTypeCode[kGroupRlRequestFile],
                           sizeof(header->file_type))) {
      *file_type = kGroupRlRequestFile;
    } else {
      // set default value
      *file_type = kNumFileTypes;
    }
  }
  return kEpidNoErr;
}

/// Parse a file with a revocation list of any type
static EpidStatus EpidParseRlFile(void const* buf, size_t len,
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
      empty_rl_size = sizeof(PrivRl) - sizeof(((PrivRl*)0)->f[0]);
      rl_entry_size = sizeof(((PrivRl*)0)->f[0]);
      min_rl_file_size = sizeof(EpidFileHeader) + sizeof(PrivRl) -
                         sizeof(((PrivRl*)0)->f[0]) + sizeof(EcdsaSignature);
      break;
    case kSigRlFile:
      empty_rl_size = sizeof(SigRl) - sizeof(((SigRl*)0)->bk[0]);
      rl_entry_size = sizeof(((SigRl*)0)->bk[0]);
      min_rl_file_size = sizeof(EpidFileHeader) + sizeof(SigRl) -
                         sizeof(((SigRl*)0)->bk[0]) + sizeof(EcdsaSignature);
      break;
    case kGroupRlFile:
      empty_rl_size = sizeof(GroupRl) - sizeof(((GroupRl*)0)->gid[0]);
      rl_entry_size = sizeof(((GroupRl*)0)->gid[0]);
      min_rl_file_size = sizeof(EpidFileHeader) + sizeof(GroupRl) -
                         sizeof(((GroupRl*)0)->gid[0]) + sizeof(EcdsaSignature);
      break;
    default:
      return kEpidErr;
  }

  if (min_rl_file_size > len) return kEpidBadArgErr;

  // Verify that Intel(R) EPID file header in the buffer is correct
  if (0 !=
      memcmp(&file_header->epid_version, &kEpidVersion, sizeof(kEpidVersion))) {
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

EpidStatus EpidParseGroupPubKeyFile(void const* buf, size_t len,
                                    EpidCaCertificate const* cert,
                                    GroupPubKey* pubkey) {
  EpidStatus result;
  EpidGroupPubKeyCertificate* buf_pubkey = (EpidGroupPubKeyCertificate*)buf;

  if (!buf || !cert || !pubkey) {
    return kEpidBadArgErr;
  }

  if (sizeof(EpidGroupPubKeyCertificate) > len) {
    return kEpidBadArgErr;
  }

  // Verify that Intel(R) EPID file header in the buffer is correct
  if (0 != memcmp(&buf_pubkey->header.epid_version, &kEpidVersion,
                  sizeof(kEpidVersion))) {
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
      buf_pubkey, sizeof(EpidGroupPubKeyCertificate) - sizeof(EcdsaSignature),
      (EcdsaPublicKey*)&cert->pubkey, &buf_pubkey->signature);
  if (kEpidSigValid != result) return result;

  // Copy public from the buffer to output
  pubkey->gid = buf_pubkey->gid;
  pubkey->h1 = buf_pubkey->h1;
  pubkey->h2 = buf_pubkey->h2;
  pubkey->w = buf_pubkey->w;

  return kEpidNoErr;
}

EpidStatus EpidParsePrivRlFile(void const* buf, size_t len,
                               EpidCaCertificate const* cert, PrivRl* rl,
                               size_t* rl_len) {
  return EpidParseRlFile(buf, len, cert, rl, rl_len, kPrivRlFile);
}

EpidStatus EpidParseSigRlFile(void const* buf, size_t len,
                              EpidCaCertificate const* cert, SigRl* rl,
                              size_t* rl_len) {
  return EpidParseRlFile(buf, len, cert, rl, rl_len, kSigRlFile);
}

EpidStatus EpidParseGroupRlFile(void const* buf, size_t len,
                                EpidCaCertificate const* cert, GroupRl* rl,
                                size_t* rl_len) {
  return EpidParseRlFile(buf, len, cert, rl, rl_len, kGroupRlFile);
}
