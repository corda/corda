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
 * \brief Hash unit tests.
 */

#include <cstring>
#include <limits>
#include "gtest/gtest.h"

#include "epid/common-testhelper/errors-testhelper.h"

extern "C" {
#include "epid/common/math/hash.h"
}

/// compares Sha256Digest values
bool operator==(Sha256Digest const& lhs, Sha256Digest const& rhs) {
  return 0 == std::memcmp(&lhs, &rhs, sizeof(lhs));
}

namespace {

///////////////////////////////////////////////////////////////////////
// SHA256
TEST(Hash, Sha256MessageDigestFailsGivenNullPtr) {
  char msg[] = "abc";
  Sha256Digest digest;

  EXPECT_EQ(kEpidBadArgErr,
            Sha256MessageDigest(nullptr, sizeof(msg) - 1, &digest));
  EXPECT_EQ(kEpidBadArgErr, Sha256MessageDigest(msg, sizeof(msg) - 1, nullptr));
}

TEST(Hash, Sha256MessageDigestFailsGivenInvalidBufferSize) {
  char msg[] = "abc";
  Sha256Digest digest;

  EXPECT_EQ(
      kEpidBadArgErr,
      Sha256MessageDigest(msg, std::numeric_limits<size_t>::max(), &digest));
#if (SIZE_MAX >= 0x100000001)  // When size_t value allowed to be 0x100000001
  EXPECT_EQ(kEpidBadArgErr, Sha256MessageDigest(msg, 0x100000001, &digest));
#endif
}

TEST(Hash, Sha256MessageDigestComputesCorrectDigest) {
  // Test vectors here are taken from
  // http://csrc.nist.gov/groups/ST/toolkit/documents/Examples/SHA256.pdf

  Sha256Digest digest;

  char msg_abc[] = "abc";
  Sha256Digest digest_abc = {{0xBA, 0x78, 0x16, 0xBF, 0x8F, 0x01, 0xCF, 0xEA,
                              0x41, 0x41, 0x40, 0xDE, 0x5D, 0xAE, 0x22, 0x23,
                              0xB0, 0x03, 0x61, 0xA3, 0x96, 0x17, 0x7A, 0x9C,
                              0xB4, 0x10, 0xFF, 0x61, 0xF2, 0x00, 0x15, 0xAD}};
  EXPECT_EQ(kEpidNoErr,
            Sha256MessageDigest(msg_abc, sizeof(msg_abc) - 1, &digest));
  EXPECT_EQ(digest_abc, digest);

  char msg_long[] = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq";
  Sha256Digest digest_long = {{0x24, 0x8D, 0x6A, 0x61, 0xD2, 0x06, 0x38, 0xB8,
                               0xE5, 0xC0, 0x26, 0x93, 0x0C, 0x3E, 0x60, 0x39,
                               0xA3, 0x3C, 0xE4, 0x59, 0x64, 0xFF, 0x21, 0x67,
                               0xF6, 0xEC, 0xED, 0xD4, 0x19, 0xDB, 0x06, 0xC1}};
  EXPECT_EQ(kEpidNoErr,
            Sha256MessageDigest(msg_long, sizeof(msg_long) - 1, &digest));
  EXPECT_EQ(digest_long, digest);
}

}  // namespace
