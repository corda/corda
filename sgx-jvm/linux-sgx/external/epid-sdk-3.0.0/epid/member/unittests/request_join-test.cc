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
 * \brief RequestJoin unit tests.
 */

#include <memory>
#include "gtest/gtest.h"

extern "C" {
#include "epid/member/api.h"
#include "epid/common/math/ecgroup.h"
#include "epid/common/math/finitefield.h"
#include "epid/common/src/epid2params.h"
}

#include "epid/member/unittests/member-testhelper.h"
#include "epid/common-testhelper/prng-testhelper.h"
#include "epid/common-testhelper/finite_field_wrapper-testhelper.h"
#include "epid/common-testhelper/ffelement_wrapper-testhelper.h"
#include "epid/common-testhelper/epid_params-testhelper.h"
#include "epid/common-testhelper/ecgroup_wrapper-testhelper.h"
#include "epid/common-testhelper/ecpoint_wrapper-testhelper.h"

namespace {

// local constant for RequestJoin tests. This can be hoisted later if needed
// avoids cpplint warning about multiple includes.
const GroupPubKey kPubKey = {
#include "epid/common-testhelper/testdata/grp01/gpubkey.inc"
};

TEST_F(EpidMemberTest, RequestJoinFailsGivenNullParameters) {
  GroupPubKey pub_key = kPubKey;
  IssuerNonce ni;
  FpElemStr f;
  Prng prng;
  BitSupplier rnd_func = Prng::Generate;
  void* rnd_param = &prng;
  JoinRequest join_request;
  EXPECT_EQ(kEpidBadArgErr, EpidRequestJoin(nullptr, &ni, &f, rnd_func,
                                            rnd_param, kSha256, &join_request));
  EXPECT_EQ(kEpidBadArgErr, EpidRequestJoin(&pub_key, nullptr, &f, rnd_func,
                                            rnd_param, kSha256, &join_request));
  EXPECT_EQ(kEpidBadArgErr, EpidRequestJoin(&pub_key, &ni, nullptr, rnd_func,
                                            rnd_param, kSha256, &join_request));
  EXPECT_EQ(kEpidBadArgErr, EpidRequestJoin(&pub_key, &ni, &f, rnd_func,
                                            rnd_param, kSha256, nullptr));
  EXPECT_EQ(kEpidBadArgErr, EpidRequestJoin(&pub_key, &ni, &f, nullptr,
                                            rnd_param, kSha256, &join_request));
}

TEST_F(EpidMemberTest, RequestJoinFailsGivenInvalidGroupKey) {
  Prng prng;
  BitSupplier rnd_func = Prng::Generate;
  void* rnd_param = &prng;
  JoinRequest join_request;
  GroupPubKey pub_key = kPubKey;
  FpElemStr f = {
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff,
  };
  IssuerNonce ni = {
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03,
      0x04, 0x05, 0x06, 0x07, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01,
  };
  pub_key.h1.x.data.data[15] = 0xff;
  Epid20Params params;
  EcPointObj pt(&params.G1);
  ASSERT_NE(kEpidNoErr, ReadEcPoint(params.G1, (uint8_t*)&pub_key.h1,
                                    sizeof(pub_key.h1), pt));
  EXPECT_EQ(kEpidBadArgErr, EpidRequestJoin(&pub_key, &ni, &f, rnd_func,
                                            rnd_param, kSha256, &join_request));
}

TEST_F(EpidMemberTest, RequestJoinFailsGivenInvalidFValue) {
  Prng prng;
  BitSupplier rnd_func = Prng::Generate;
  void* rnd_param = &prng;
  JoinRequest join_request;
  GroupPubKey pub_key = kPubKey;
  FpElemStr f = {
      0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
      0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
      0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x00,
  };
  IssuerNonce ni = {
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03,
      0x04, 0x05, 0x06, 0x07, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01,
  };

  const BigNumStr p = {
      {{0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, 0xF0, 0xCD, 0x46, 0xE5, 0xF2, 0x5E,
        0xEE, 0x71, 0xA4, 0x9E, 0x0C, 0xDC, 0x65, 0xFB, 0x12, 0x99, 0x92, 0x1A,
        0xF6, 0x2D, 0x53, 0x6C, 0xD1, 0x0B, 0x50, 0x0D}}};
  FiniteFieldObj Fp(p);
  FfElementObj el(&Fp);
  ASSERT_NE(kEpidNoErr, ReadFfElement(Fp, (uint8_t*)&f, sizeof(f), el));
  EXPECT_EQ(kEpidBadArgErr, EpidRequestJoin(&pub_key, &ni, &f, rnd_func,
                                            rnd_param, kSha256, &join_request));
}

TEST_F(EpidMemberTest,
       GeneratesValidJoinRequestGivenValidParametersUsingIKGFData) {
  Prng prng;
  BitSupplier rnd_func = Prng::Generate;
  void* rnd_param = &prng;
  JoinRequest join_request;
  FpElemStr f = {
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff,
  };
  IssuerNonce ni = {
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03,
      0x04, 0x05, 0x06, 0x07, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01,
  };
  const GroupPubKey* grp_public_key = reinterpret_cast<const GroupPubKey*>(
      this->kGroupPublicKeyDataIkgf.data());
  EXPECT_EQ(kEpidNoErr, EpidRequestJoin(grp_public_key, &ni, &f, rnd_func,
                                        rnd_param, kSha256, &join_request));
}

TEST_F(EpidMemberTest, GeneratesValidJoinRequestGivenValidParameters) {
  Prng prng;
  BitSupplier rnd_func = Prng::Generate;
  void* rnd_param = &prng;
  JoinRequest join_request;
  GroupPubKey pub_key = kPubKey;
  FpElemStr f = {
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff,
  };
  IssuerNonce ni = {
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03,
      0x04, 0x05, 0x06, 0x07, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01,
  };
  EXPECT_EQ(kEpidNoErr, EpidRequestJoin(&pub_key, &ni, &f, rnd_func, rnd_param,
                                        kSha256, &join_request));
}

TEST_F(EpidMemberTest, GeneratesDiffJoinRequestsOnMultipleCalls) {
  Prng prng;
  BitSupplier rnd_func = Prng::Generate;
  void* rnd_param = &prng;
  JoinRequest join_request1;
  JoinRequest join_request2;
  GroupPubKey pub_key = kPubKey;
  FpElemStr f = {
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff,
  };
  IssuerNonce ni = {
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03,
      0x04, 0x05, 0x06, 0x07, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01,
  };
  prng.set_seed(0x1234);
  EXPECT_EQ(kEpidNoErr, EpidRequestJoin(&pub_key, &ni, &f, rnd_func, rnd_param,
                                        kSha256, &join_request1));
  EXPECT_EQ(kEpidNoErr, EpidRequestJoin(&pub_key, &ni, &f, rnd_func, rnd_param,
                                        kSha256, &join_request2));
  EXPECT_NE(0, memcmp(&join_request1, &join_request2, sizeof(join_request1)));
}

TEST_F(EpidMemberTest, GeneratesDiffJoinRequestsGivenDiffHashAlgs) {
  Prng prng;
  BitSupplier rnd_func = Prng::Generate;
  void* rnd_param = &prng;
  JoinRequest join_request1;
  JoinRequest join_request2;
  GroupPubKey pub_key = kPubKey;
  FpElemStr f = {
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff,
  };
  IssuerNonce ni = {
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03,
      0x04, 0x05, 0x06, 0x07, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01,
  };
  prng.set_seed(0x1234);
  EXPECT_EQ(kEpidNoErr, EpidRequestJoin(&pub_key, &ni, &f, rnd_func, rnd_param,
                                        kSha256, &join_request1));
  prng.set_seed(0x1234);
  EXPECT_EQ(kEpidNoErr, EpidRequestJoin(&pub_key, &ni, &f, rnd_func, rnd_param,
                                        kSha512, &join_request2));
  EXPECT_NE(0, memcmp(&join_request1, &join_request2, sizeof(join_request1)));
}

TEST_F(EpidMemberTest, PrivateKeyValidationFailsGivenNullParameters) {
  EXPECT_FALSE(EpidIsPrivKeyInGroup(&this->kGrpXKey, nullptr));
  EXPECT_FALSE(EpidIsPrivKeyInGroup(nullptr, &this->kGrpXMember9PrivKey));
}

TEST_F(EpidMemberTest, PrivateKeyValidationFailsGivenGroupIDMissmatch) {
  // Check wrong gid for GroupPubKey
  GroupPubKey group_pub_key = this->kGrpXKey;
  group_pub_key.gid.data[0] = group_pub_key.gid.data[0] ^ 0xFF;
  EXPECT_FALSE(
      EpidIsPrivKeyInGroup(&group_pub_key, &this->kGrpXMember9PrivKey));
  // Check wrong gid for PrivKey
  PrivKey priv_key = this->kGrpXMember9PrivKey;
  priv_key.gid.data[sizeof(priv_key.gid.data) - 1] =
      priv_key.gid.data[sizeof(priv_key.gid.data) - 1] ^ 0xFF;
  EXPECT_FALSE(EpidIsPrivKeyInGroup(&this->kGrpXKey, &priv_key));
  // Check wrong gid for both GroupPubKey and PrivKey
  EXPECT_FALSE(EpidIsPrivKeyInGroup(&group_pub_key, &priv_key));
}

TEST_F(EpidMemberTest, PrivateKeyValidationRejectsInvalidPrivKey) {
  // test for invalid key components values (eg. out of range, not in EC group)
  PrivKey priv_key = this->kGrpXMember9PrivKey;
  priv_key.A.x.data.data[0] = 0xFF;
  EXPECT_FALSE(EpidIsPrivKeyInGroup(&this->kGrpXKey, &priv_key));

  priv_key = this->kGrpXMember9PrivKey;
  priv_key.A.y.data.data[0] = 0xFF;
  EXPECT_FALSE(EpidIsPrivKeyInGroup(&this->kGrpXKey, &priv_key));

  priv_key = this->kGrpXMember9PrivKey;
  FpElemStr inv_f = {
      0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
      0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
      0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x00,
  };
  priv_key.f = inv_f;
  EXPECT_FALSE(EpidIsPrivKeyInGroup(&this->kGrpXKey, &priv_key));

  priv_key = this->kGrpXMember9PrivKey;
  priv_key.x.data.data[0] = 0xFF;
  EXPECT_FALSE(EpidIsPrivKeyInGroup(&this->kGrpXKey, &priv_key));
}

TEST_F(EpidMemberTest, PrivateKeyValidationRejectsInvalidGroupKey) {
  // test for invalid key components values (eg. out of range, not in EC group)
  GroupPubKey pub_key = this->kGrpXKey;
  pub_key.h1.x.data.data[0] = 0xFF;
  EXPECT_FALSE(EpidIsPrivKeyInGroup(&pub_key, &this->kGrpXMember9PrivKey));

  pub_key = this->kGrpXKey;
  pub_key.h1.y.data.data[0] = 0xFF;
  EXPECT_FALSE(EpidIsPrivKeyInGroup(&pub_key, &this->kGrpXMember9PrivKey));

  pub_key = this->kGrpXKey;
  pub_key.h2.x.data.data[0] = 0xFF;
  EXPECT_FALSE(EpidIsPrivKeyInGroup(&pub_key, &this->kGrpXMember9PrivKey));

  pub_key = this->kGrpXKey;
  pub_key.h2.y.data.data[0] = 0xFF;
  EXPECT_FALSE(EpidIsPrivKeyInGroup(&pub_key, &this->kGrpXMember9PrivKey));

  pub_key = this->kGrpXKey;
  pub_key.w.x[0].data.data[0] = 0xFF;
  EXPECT_FALSE(EpidIsPrivKeyInGroup(&pub_key, &this->kGrpXMember9PrivKey));

  pub_key = this->kGrpXKey;
  pub_key.w.x[1].data.data[0] = 0xFF;
  EXPECT_FALSE(EpidIsPrivKeyInGroup(&pub_key, &this->kGrpXMember9PrivKey));

  pub_key = this->kGrpXKey;
  pub_key.w.y[0].data.data[0] = 0xFF;
  EXPECT_FALSE(EpidIsPrivKeyInGroup(&pub_key, &this->kGrpXMember9PrivKey));

  pub_key = this->kGrpXKey;
  pub_key.w.y[1].data.data[0] = 0xFF;
  EXPECT_FALSE(EpidIsPrivKeyInGroup(&pub_key, &this->kGrpXMember9PrivKey));
}

TEST_F(EpidMemberTest, PrivateKeyValidationRejectsKeyNotInGroup) {
  EXPECT_FALSE(
      EpidIsPrivKeyInGroup(&this->kGrpYKey, &this->kGrpXMember9PrivKey));
}

TEST_F(EpidMemberTest, PrivateKeyValidationRejectsKeyNotInGroupUsingIKGFData) {
  const GroupPubKey* grp_public_key = reinterpret_cast<const GroupPubKey*>(
      this->kGroupPublicKeyDataIkgf.data());
  const PrivKey mbr_private_key = {
#include "epid/common-testhelper/testdata/ikgf/groupb/member0/mprivkey.inc"
  };
  EXPECT_FALSE(EpidIsPrivKeyInGroup(grp_public_key, &mbr_private_key));
}

TEST_F(EpidMemberTest, PrivateKeyValidationAcceptsKeyInGroup) {
  EXPECT_TRUE(
      EpidIsPrivKeyInGroup(&this->kGrpXKey, &this->kGrpXMember9PrivKey));
}

TEST_F(EpidMemberTest, PrivateKeyValidationAcceptsKeyInGroupUsingIKGFData) {
  const GroupPubKey* grp_public_key = reinterpret_cast<const GroupPubKey*>(
      this->kGroupPublicKeyDataIkgf.data());
  const PrivKey* mbr_private_key =
      reinterpret_cast<const PrivKey*>(this->kMemberPrivateKeyDataIkgf.data());
  EXPECT_TRUE(EpidIsPrivKeyInGroup(grp_public_key, mbr_private_key));
}

}  // namespace
