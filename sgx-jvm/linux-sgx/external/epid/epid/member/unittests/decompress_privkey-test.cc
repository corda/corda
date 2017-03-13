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
 * \brief DecompressPrivKey unit tests.
 */
#include <cstring>
#include "gtest/gtest.h"

extern "C" {
#include "epid/member/api.h"
}

#include "epid/member/unittests/member-testhelper.h"

bool operator==(PrivKey const& lhs, PrivKey const& rhs) {
  return 0 == std::memcmp(&lhs, &rhs, sizeof(lhs));
}
namespace {

TEST_F(EpidMemberTest, DecompressPrivKeyFailsGivenNullParameters) {
  auto const& pub_key = this->kGrpXKey;
  auto const& compressed_privkey = this->kGrpXMember9CompressedKey;
  PrivKey priv_key = {};
  EXPECT_EQ(kEpidBadArgErr,
            EpidDecompressPrivKey(nullptr, &compressed_privkey, &priv_key));
  EXPECT_EQ(kEpidBadArgErr,
            EpidDecompressPrivKey(&pub_key, nullptr, &priv_key));
  EXPECT_EQ(kEpidBadArgErr,
            EpidDecompressPrivKey(&pub_key, &compressed_privkey, nullptr));
}

TEST_F(EpidMemberTest, CanDecompressPrivKeyGivenValidCompressedKey) {
  auto const& pub_key = this->kGrpXKey;
  auto const& compressed_privkey = this->kGrpXMember9CompressedKey;
  auto const& expected_decompressed_key = this->kGrpXMember9PrivKey;
  PrivKey priv_key = {};
  EXPECT_EQ(kEpidNoErr,
            EpidDecompressPrivKey(&pub_key, &compressed_privkey, &priv_key));
  EXPECT_EQ(expected_decompressed_key, priv_key);
}

TEST_F(EpidMemberTest, DecompressPrivKeyFailsGivenKeysMissmatch) {
  auto const& pub_key = this->kGrpYKey;
  auto const& compressed_privkey = this->kGrpXMember9CompressedKey;
  PrivKey priv_key = {};
  EXPECT_EQ(kEpidBadArgErr,
            EpidDecompressPrivKey(&pub_key, &compressed_privkey, &priv_key));
}

TEST_F(EpidMemberTest, DecompressPrivKeyFailsGivenInvalidGroupKey) {
  // Test for cases when h1 or w of group public key are invalid.
  // Note h2 of group public key is not used for key decompression.
  auto const& compressed_privkey = this->kGrpXMember9CompressedKey;
  PrivKey priv_key = {};

  auto pub_key_h1 = this->kGrpXKey;
  pub_key_h1.h1.x.data.data[0]++;
  EXPECT_EQ(kEpidBadArgErr,
            EpidDecompressPrivKey(&pub_key_h1, &compressed_privkey, &priv_key));

  auto pub_key_w = this->kGrpXKey;
  pub_key_w.w.x[0].data.data[0]++;
  EXPECT_EQ(kEpidBadArgErr,
            EpidDecompressPrivKey(&pub_key_w, &compressed_privkey, &priv_key));
}

TEST_F(EpidMemberTest, DecompressPrivKeyFailsGivenInvalidCompressedKey) {
  auto const& pub_key = this->kGrpXKey;
  PrivKey priv_key = {};

  auto compressed_privkey_ax = this->kGrpXMember9CompressedKey;
  compressed_privkey_ax.ax.data.data[0]++;
  EXPECT_EQ(kEpidBadArgErr,
            EpidDecompressPrivKey(&pub_key, &compressed_privkey_ax, &priv_key));

  auto compressed_privkey_seed = this->kGrpXMember9CompressedKey;
  compressed_privkey_seed.seed.data[0]++;
  EXPECT_EQ(kEpidBadArgErr, EpidDecompressPrivKey(
                                &pub_key, &compressed_privkey_seed, &priv_key));
}

}  // namespace
