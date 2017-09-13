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
 * \brief Member unit tests.
 */
#include <cstring>
#include <vector>
#include "gtest/gtest.h"

#include "epid/common-testhelper/prng-testhelper.h"
#include "epid/member/unittests/member-testhelper.h"

extern "C" {
#include "epid/member/api.h"
#include "epid/member/src/context.h"
}
bool operator==(MemberPrecomp const& lhs, MemberPrecomp const& rhs) {
  return 0 == std::memcmp(&lhs, &rhs, sizeof(lhs));
}
namespace {
//////////////////////////////////////////////////////////////////////////
// EpidMemberDelete Tests
TEST_F(EpidMemberTest, DeleteWorksGivenNullMemberCtx) {
  EpidMemberDelete(nullptr);
  MemberCtx* member_ctx = nullptr;
  EpidMemberDelete(&member_ctx);
}
TEST_F(EpidMemberTest, DeleteNullsMemberCtx) {
  MemberCtx* ctx = nullptr;
  Prng my_prng;
  EpidMemberCreate(nullptr, &this->kMemberPrivateKey, &this->kMemberPrecomp,
                   &Prng::Generate, &my_prng, &ctx);
  EpidMemberDelete(&ctx);
  EXPECT_EQ(nullptr, ctx);
}
//////////////////////////////////////////////////////////////////////////
// EpidMemberCreate Tests
// test that create fails if any mandatory parameters are NULL
TEST_F(EpidMemberTest, CreateFailsGivenNullParameters) {
  MemberCtx* member_ctx = nullptr;
  Prng my_prng;
  EXPECT_EQ(
      kEpidBadArgErr,
      EpidMemberCreate(nullptr, &this->kMemberPrivateKey, &this->kMemberPrecomp,
                       &Prng::Generate, &my_prng, &member_ctx));
  EpidMemberDelete(&member_ctx);

  EXPECT_EQ(
      kEpidBadArgErr,
      EpidMemberCreate(&this->kGroupPublicKey, nullptr, &this->kMemberPrecomp,
                       &Prng::Generate, &my_prng, &member_ctx));
  EpidMemberDelete(&member_ctx);

  EXPECT_EQ(
      kEpidBadArgErr,
      EpidMemberCreate(&this->kGroupPublicKey, &this->kMemberPrivateKey,
                       &this->kMemberPrecomp, nullptr, &my_prng, &member_ctx));
  EpidMemberDelete(&member_ctx);

  EXPECT_EQ(kEpidBadArgErr,
            EpidMemberCreate(&this->kGroupPublicKey, &this->kMemberPrivateKey,
                             &this->kMemberPrecomp, &Prng::Generate, &my_prng,
                             nullptr));
  EpidMemberDelete(nullptr);
}

// test that create succeeds with valid parameters
TEST_F(EpidMemberTest, CreateSucceedsGivenValidParameters) {
  MemberCtx* member_ctx = nullptr;
  Prng my_prng;

  // pass the whole list of parameters
  EXPECT_EQ(kEpidNoErr,
            EpidMemberCreate(&this->kGroupPublicKey, &this->kMemberPrivateKey,
                             &this->kMemberPrecomp, &Prng::Generate, &my_prng,
                             &member_ctx));
  EpidMemberDelete(&member_ctx);

  // pass the whole list of parameters but member_precomp
  EXPECT_EQ(kEpidNoErr,
            EpidMemberCreate(&this->kGroupPublicKey, &this->kMemberPrivateKey,
                             nullptr, &Prng::Generate, &my_prng, &member_ctx));
  EpidMemberDelete(&member_ctx);
}
// test that create succeeds with valid IKGF given parameters
TEST_F(EpidMemberTest, CreateSucceedsGivenValidParametersUsingIKGFData) {
  const GroupPubKey grp_public_key = {
#include "epid/common-testhelper/testdata/ikgf/groupa/pubkey.inc"
  };
  const PrivKey mbr_private_key = {
#include "epid/common-testhelper/testdata/ikgf/groupa/member0/mprivkey.inc"
  };

  const MemberPrecomp mbr_precomp = {
#include "epid/common-testhelper/testdata/ikgf/groupa/member0/mprecomp.inc"
  };

  MemberCtx* member_ctx = nullptr;
  Prng my_prng;

  // pass the whole list of parameters
  EXPECT_EQ(kEpidNoErr,
            EpidMemberCreate(&grp_public_key, &mbr_private_key, &mbr_precomp,
                             &Prng::Generate, &my_prng, &member_ctx));
  EpidMemberDelete(&member_ctx);

  // pass the whole list of parameters but member_precomp
  EXPECT_EQ(kEpidNoErr,
            EpidMemberCreate(&grp_public_key, &mbr_private_key, nullptr,
                             &Prng::Generate, &my_prng, &member_ctx));
  EpidMemberDelete(&member_ctx);
}

TEST_F(EpidMemberTest, CreateFailsForInvalidGroupPubKey) {
  MemberCtx* member_ctx = nullptr;
  Prng my_prng;
  GroupPubKey gpk_h1 = this->kGroupPublicKey;
  gpk_h1.h1.x.data.data[0]++;
  EXPECT_EQ(
      kEpidBadArgErr,
      EpidMemberCreate(&gpk_h1, &this->kMemberPrivateKey, &this->kMemberPrecomp,
                       &Prng::Generate, &my_prng, &member_ctx));
  EpidMemberDelete(&member_ctx);
  GroupPubKey gpk_h2 = this->kGroupPublicKey;
  gpk_h2.h2.x.data.data[0]++;
  EXPECT_EQ(
      kEpidBadArgErr,
      EpidMemberCreate(&gpk_h2, &this->kMemberPrivateKey, &this->kMemberPrecomp,
                       &Prng::Generate, &my_prng, &member_ctx));
  EpidMemberDelete(&member_ctx);
  GroupPubKey gpk_w = this->kGroupPublicKey;
  gpk_w.w.x[0].data.data[0]++;
  EXPECT_EQ(
      kEpidBadArgErr,
      EpidMemberCreate(&gpk_w, &this->kMemberPrivateKey, &this->kMemberPrecomp,
                       &Prng::Generate, &my_prng, &member_ctx));
  EpidMemberDelete(&member_ctx);
}
TEST_F(EpidMemberTest, CreateFailsForInvalidPrivateKey) {
  MemberCtx* member_ctx = nullptr;
  Prng my_prng;
  PrivKey pk_A = this->kMemberPrivateKey;
  pk_A.A.x.data.data[0]++;
  EXPECT_EQ(
      kEpidBadArgErr,
      EpidMemberCreate(&this->kGroupPublicKey, &pk_A, &this->kMemberPrecomp,
                       &Prng::Generate, &my_prng, &member_ctx));
  EpidMemberDelete(&member_ctx);
}

//////////////////////////////////////////////////////////////////////////
// EpidMemberSetHashAlg
TEST_F(EpidMemberTest, SetHashAlgFailsGivenNullPtr) {
  EXPECT_EQ(kEpidBadArgErr, EpidMemberSetHashAlg(nullptr, kSha256));
}
TEST_F(EpidMemberTest, SetHashAlgCanSetValidAlgorithm) {
  Prng my_prng;
  MemberCtxObj member_ctx(this->kGroupPublicKey, this->kMemberPrivateKey,
                          &Prng::Generate, &my_prng);
  EXPECT_EQ(kEpidNoErr, EpidMemberSetHashAlg(member_ctx, kSha256));
  EXPECT_EQ(kEpidNoErr, EpidMemberSetHashAlg(member_ctx, kSha384));
  EXPECT_EQ(kEpidNoErr, EpidMemberSetHashAlg(member_ctx, kSha512));
}
TEST_F(EpidMemberTest, SetHashAlgFailsForNonSupportedAlgorithm) {
  Prng my_prng;
  MemberCtxObj member_ctx(this->kGroupPublicKey, this->kMemberPrivateKey,
                          &Prng::Generate, &my_prng);
  EXPECT_EQ(kEpidBadArgErr, EpidMemberSetHashAlg(member_ctx, kSha512_256));
  EXPECT_EQ(kEpidBadArgErr, EpidMemberSetHashAlg(member_ctx, kSha3_256));
  EXPECT_EQ(kEpidBadArgErr, EpidMemberSetHashAlg(member_ctx, kSha3_384));
  EXPECT_EQ(kEpidBadArgErr, EpidMemberSetHashAlg(member_ctx, kSha3_512));
  EXPECT_EQ(kEpidBadArgErr, EpidMemberSetHashAlg(member_ctx, (HashAlg)-1));
}
//////////////////////////////////////////////////////////////////////////
// EpidRegisterBaseName
TEST_F(EpidMemberTest, RegisterBaseNameFailsGivenNullPtr) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  std::vector<uint8_t> basename = {'_', 'b', 'a', 's', 'e', 'n', 'a', 'm', 'e'};
  EXPECT_EQ(kEpidBadArgErr,
            EpidRegisterBaseName(member, nullptr, basename.size()));
  EXPECT_EQ(kEpidBadArgErr,
            EpidRegisterBaseName(nullptr, basename.data(), basename.size()));
}
TEST_F(EpidMemberTest, RegisterBaseNameFailsGivenDuplicateBaseName) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  std::vector<uint8_t> basename = {'d', 'b', 'a', 's', 'e', 'n', 'a', 'm', 'e'};
  EXPECT_EQ(kEpidNoErr,
            EpidRegisterBaseName(member, basename.data(), basename.size()));
  EXPECT_EQ(kEpidDuplicateErr,
            EpidRegisterBaseName(member, basename.data(), basename.size()));
}
TEST_F(EpidMemberTest, RegisterBaseNameFailsGivenInvalidBaseName) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  std::vector<uint8_t> basename = {};
  std::vector<uint8_t> basename2 = {'b', 's', 'n'};
  EXPECT_EQ(kEpidBadArgErr,
            EpidRegisterBaseName(member, basename.data(), basename.size()));
  EXPECT_EQ(kEpidBadArgErr, EpidRegisterBaseName(member, basename2.data(), 0));
}
TEST_F(EpidMemberTest, RegisterBaseNameSucceedsGivenUniqueBaseName) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  std::vector<uint8_t> basename = {'b', 's', 'n', '0', '1'};
  EXPECT_EQ(kEpidNoErr,
            EpidRegisterBaseName(member, basename.data(), basename.size()));
}
//////////////////////////////////////////////////////////////////////////
// EpidMemberWritePrecomp
TEST_F(EpidMemberTest, MemberWritePrecompFailsGivenNullPointer) {
  MemberPrecomp precomp;
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  MemberCtx* ctx = member;
  EXPECT_EQ(kEpidBadArgErr, EpidMemberWritePrecomp(nullptr, &precomp));
  EXPECT_EQ(kEpidBadArgErr, EpidMemberWritePrecomp(ctx, nullptr));
}
TEST_F(EpidMemberTest, MemberWritePrecompSucceedGivenValidArgument) {
  MemberPrecomp precomp;
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  MemberCtx* ctx = member;
  EXPECT_EQ(kEpidNoErr, EpidMemberWritePrecomp(ctx, &precomp));
  MemberPrecomp expected_precomp = this->kMemberPrecomp;
  EXPECT_EQ(expected_precomp, precomp);

  MemberCtxObj member2(this->kGroupPublicKey, this->kMemberPrivateKey,
                       &Prng::Generate, &my_prng);
  MemberCtx* ctx2 = member2;
  EXPECT_EQ(kEpidNoErr, EpidMemberWritePrecomp(ctx2, &precomp));
  EXPECT_EQ(expected_precomp, precomp);
}
TEST_F(EpidMemberTest, DefaultHashAlgIsSha512) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);
  MemberCtx* ctx = member;
  EXPECT_EQ(kSha512, ctx->hash_alg);
}

}  // namespace
