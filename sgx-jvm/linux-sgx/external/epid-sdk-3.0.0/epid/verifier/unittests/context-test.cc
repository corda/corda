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
 * \brief VerifierCreate unit tests.
 */

#include <vector>
#include <cstring>

#include "gtest/gtest.h"

extern "C" {
#include "epid/verifier/api.h"
#include "epid/verifier/src/context.h"
#include "epid/common/src/endian_convert.h"
}

#include "epid/verifier/unittests/verifier-testhelper.h"
#include "epid/common-testhelper/verifier_wrapper-testhelper.h"
#include "epid/common-testhelper/errors-testhelper.h"
bool operator==(VerifierPrecomp const& lhs, VerifierPrecomp const& rhs) {
  return 0 == std::memcmp(&lhs, &rhs, sizeof(lhs));
}

bool operator==(OctStr32 const& lhs, OctStr32 const& rhs) {
  return 0 == std::memcmp(&lhs, &rhs, sizeof(lhs));
}
namespace {
//////////////////////////////////////////////////////////////////////////
// EpidVerifierCreate Tests
TEST_F(EpidVerifierTest, CreateFailsGivenNullPointer) {
  VerifierCtx* ctx = nullptr;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierCreate(&this->kPubKeyStr, &this->kVerifierPrecompStr,
                               nullptr));
  EpidVerifierDelete(&ctx);
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierCreate(nullptr, &this->kVerifierPrecompStr, &ctx));
  EpidVerifierDelete(&ctx);
}
TEST_F(EpidVerifierTest, CreateSucceedsGivenNullPrecomp) {
  VerifierCtx* ctx = nullptr;
  EXPECT_EQ(kEpidNoErr, EpidVerifierCreate(&this->kPubKeyStr, nullptr, &ctx));
  EpidVerifierDelete(&ctx);
}
TEST_F(EpidVerifierTest, CreateSucceedsGivenNullPrecompUsingIkgfData) {
  VerifierCtx* ctx = nullptr;
  EXPECT_EQ(kEpidNoErr,
            EpidVerifierCreate(&this->kPubKeyIkgfStr, nullptr, &ctx));
  EpidVerifierDelete(&ctx);
}
TEST_F(EpidVerifierTest, CreateFailsGivenInvalidPubkey) {
  VerifierCtx* ctx = nullptr;
  GroupPubKey pubkey_with_bad_h1 = this->kPubKeyStr;
  pubkey_with_bad_h1.h1.x.data.data[31]++;  // munge h1 so not in G1
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierCreate(&pubkey_with_bad_h1, nullptr, &ctx));
  EpidVerifierDelete(&ctx);
  GroupPubKey pubkey_with_bad_h2 = this->kPubKeyStr;
  pubkey_with_bad_h2.h2.x.data.data[31]++;  // munge h2 so not in G1
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierCreate(&pubkey_with_bad_h2, nullptr, &ctx));
  EpidVerifierDelete(&ctx);
  GroupPubKey pubkey_with_bad_w = this->kPubKeyStr;
  pubkey_with_bad_w.w.x[0].data.data[31]++;  // munge w so not in G2
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierCreate(&pubkey_with_bad_w, nullptr, &ctx));
  EpidVerifierDelete(&ctx);
}
TEST_F(EpidVerifierTest, CreateFailsGivenBadGroupIdInPrecomp) {
  VerifierCtx* ctx = nullptr;
  // tweak GID
  auto verifier_precomp = this->kVerifierPrecompStr;
  verifier_precomp.gid.data[0] = ~verifier_precomp.gid.data[0];
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierCreate(&this->kPubKeyStr, &verifier_precomp, &ctx));
}
//////////////////////////////////////////////////////////////////////////
// EpidVerifierDelete Tests
TEST_F(EpidVerifierTest, DeleteNullsVerifierCtx) {
  VerifierCtx* ctx = nullptr;
  EpidVerifierCreate(&this->kPubKeyStr, nullptr, &ctx);
  EpidVerifierDelete(&ctx);
  EXPECT_EQ(nullptr, ctx);
}
TEST_F(EpidVerifierTest, DeleteWorksGivenNullVerifierCtx) {
  EpidVerifierDelete(nullptr);
  VerifierCtx* ctx = nullptr;
  EpidVerifierDelete(&ctx);
}

//////////////////////////////////////////////////////////////////////////
// EpidVerifierWritePrecomp
TEST_F(EpidVerifierTest, WritePrecompFailsGivenNullPointer) {
  VerifierPrecomp precomp;
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierCtx* ctx = verifier;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierWritePrecomp(nullptr, &precomp));
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierWritePrecomp(ctx, nullptr));
}
TEST_F(EpidVerifierTest, WritePrecompSucceedGivenValidArgument) {
  VerifierPrecomp precomp;
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierCtx* ctx = verifier;
  EXPECT_EQ(kEpidNoErr, EpidVerifierWritePrecomp(ctx, &precomp));
  VerifierPrecomp expected_precomp = this->kVerifierPrecompStr;
  EXPECT_EQ(expected_precomp, precomp);

  VerifierCtxObj verifier2(this->kPubKeyStr);
  VerifierCtx* ctx2 = verifier2;
  EXPECT_EQ(kEpidNoErr, EpidVerifierWritePrecomp(ctx2, &precomp));
  EXPECT_EQ(expected_precomp, precomp);
}
//////////////////////////////////////////////////////////////////////////
// EpidVerifierSetPrivRl
TEST_F(EpidVerifierTest, SetPrivRlFailsGivenNullPointer) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->kPubKeyStr.gid;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetPrivRl(nullptr, &prl, sizeof(prl)));
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetPrivRl(verifier, nullptr, sizeof(prl)));
}

TEST_F(EpidVerifierTest, SetPrivRlFailsGivenZeroSize) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->kPubKeyStr.gid;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetPrivRl(verifier, &prl, 0));
}

// Size parameter must be at least big enough for n1 == 0 case
TEST_F(EpidVerifierTest, SetPrivRlFailsGivenTooSmallSize) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->kPubKeyStr.gid;
  EXPECT_EQ(
      kEpidBadArgErr,
      EpidVerifierSetPrivRl(verifier, &prl, (sizeof(prl) - sizeof(prl.f)) - 1));
  prl.n1 = this->kOctStr32_1;
  EXPECT_EQ(
      kEpidBadArgErr,
      EpidVerifierSetPrivRl(verifier, &prl, (sizeof(prl) - sizeof(prl.f)) - 1));
}

// Size parameter must be cross-checked with n1 value in priv_rl
TEST_F(EpidVerifierTest, SetPrivRlFailsGivenN1TooBigForSize) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->kPubKeyStr.gid;
  prl.n1 = this->kOctStr32_1;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetPrivRl(verifier, &prl, sizeof(prl) - sizeof(prl.f)));
}

TEST_F(EpidVerifierTest, SetPrivRlFailsGivenN1TooSmallForSize) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->kPubKeyStr.gid;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetPrivRl(verifier, &prl, sizeof(prl)));
}

TEST_F(EpidVerifierTest, SetPrivRlPassesGivenDefaultPrivRl) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->kPubKeyStr.gid;
  EXPECT_EQ(kEpidNoErr,
            EpidVerifierSetPrivRl(verifier, &prl, sizeof(prl) - sizeof(prl.f)));
}

TEST_F(EpidVerifierTest, SetPrivRlPassesGivenDefaultPrivRlUsingIkgfData) {
  VerifierCtxObj verifier(this->kPubKeyIkgfStr, this->kVerifierPrecompIkgfStr);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->kPubKeyIkgfStr.gid;
  EXPECT_EQ(kEpidNoErr,
            EpidVerifierSetPrivRl(verifier, &prl, sizeof(prl) - sizeof(prl.f)));
}

TEST_F(EpidVerifierTest, SetPrivRlPassesGivenEmptyPrivRlUsingIkgfData) {
  VerifierCtxObj verifier(this->kPubKeyIkgfStr, this->kVerifierPrecompIkgfStr);

  uint8_t priv_rl_data_n1_zero_ikgf[] = {
#include "epid/common-testhelper/testdata/ikgf/groupa/privrl_empty.inc"
  };
  PrivRl* priv_rl = reinterpret_cast<PrivRl*>(priv_rl_data_n1_zero_ikgf);
  size_t priv_rl_size = sizeof(priv_rl_data_n1_zero_ikgf);
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetPrivRl(verifier, priv_rl, priv_rl_size));
}

TEST_F(EpidVerifierTest, SetPrivRlPassesGivenPrivRlWithSingleElement) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->kPubKeyStr.gid;
  prl.n1 = this->kOctStr32_1;
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetPrivRl(verifier, &prl, sizeof(prl)));
}

TEST_F(EpidVerifierTest, SetPrivRlFailsGivenBadGroupId) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->kPubKeyStr.gid;
  prl.gid.data[0] = ~prl.gid.data[0];
  prl.n1 = this->kOctStr32_1;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetPrivRl(verifier, &prl, sizeof(prl)));
}

TEST_F(EpidVerifierTest,
       SetPrivRlFailsGivenEmptyPrivRlFromDifferentGroupUsingIkgfData) {
  VerifierCtxObj verifier(this->kPubKeyRevGroupIkgfStr);
  auto& priv_rl = this->kEmptyPrivRlIkgf;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetPrivRl(verifier, (PrivRl const*)priv_rl.data(),
                                  priv_rl.size()));
}

TEST_F(EpidVerifierTest, SetPrivRlFailsGivenOldVersion) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  PrivRl prl = {{0}, {0}, {0}, {0}};
  prl.gid = this->kPubKeyStr.gid;
  prl.version = this->kOctStr32_1;
  EXPECT_EQ(kEpidNoErr,
            EpidVerifierSetPrivRl(verifier, &prl, sizeof(prl) - sizeof(prl.f)));
  OctStr32 octstr32_0 = {0x00, 0x00, 0x00, 0x00};
  prl.version = octstr32_0;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetPrivRl(verifier, &prl, sizeof(prl) - sizeof(prl.f)));
}

//////////////////////////////////////////////////////////////////////////
// EpidVerifierSetSigRl
TEST_F(EpidVerifierTest, SetSigRlFailsGivenNullPointer) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  SigRl srl = {{{0}}, {{0}}, {{0}}, {{{{0}, {0}}, {{0}, {0}}}}};
  srl.gid = this->kPubKeyStr.gid;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetSigRl(nullptr, &srl, sizeof(SigRl)));
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetSigRl(verifier, nullptr, sizeof(SigRl)));
}

TEST_F(EpidVerifierTest, SetSigRlFailsGivenZeroSize) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  SigRl srl = {{{0}}, {{0}}, {{0}}, {{{{0}, {0}}, {{0}, {0}}}}};
  srl.gid = this->kPubKeyStr.gid;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetSigRl(verifier, &srl, 0));
}

// Size parameter must be at least big enough for n2 == 0 case
TEST_F(EpidVerifierTest, SetSigRlFailsGivenTooSmallSize) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  SigRl srl = {{{0}}, {{0}}, {{0}}, {{{{0}, {0}}, {{0}, {0}}}}};
  srl.gid = this->kPubKeyStr.gid;
  EXPECT_EQ(
      kEpidBadArgErr,
      EpidVerifierSetSigRl(verifier, &srl, (sizeof(srl) - sizeof(srl.bk)) - 1));
  srl.n2 = this->kOctStr32_1;
  EXPECT_EQ(
      kEpidBadArgErr,
      EpidVerifierSetSigRl(verifier, &srl, (sizeof(srl) - sizeof(srl.bk)) - 1));
}

TEST_F(EpidVerifierTest, SetSigRlFailsGivenN2TooBigForSize) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  SigRl srl = {{{0}}, {{0}}, {{0}}, {{{{0}, {0}}, {{0}, {0}}}}};
  srl.gid = this->kPubKeyStr.gid;
  srl.n2 = this->kOctStr32_1;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetSigRl(verifier, &srl, sizeof(srl) - sizeof(srl.bk)));
}

TEST_F(EpidVerifierTest, SetSigRlFailsGivenN2TooSmallForSize) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  SigRl srl = {{{0}}, {{0}}, {{0}}, {{{{0}, {0}}, {{0}, {0}}}}};
  srl.gid = this->kPubKeyStr.gid;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetSigRl(verifier, &srl, sizeof(srl)));
}

TEST_F(EpidVerifierTest, SetSigRlWorksGivenDefaultSigRl) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierCtx* ctx = verifier;
  SigRl const* sig_rl =
      reinterpret_cast<SigRl const*>(this->kGrp01SigRl.data());
  size_t sig_rl_size = this->kGrp01SigRl.size();
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetSigRl(ctx, sig_rl, sig_rl_size));
}

TEST_F(EpidVerifierTest, SetSigRlWorksGivenDefaultSigRlUsingIkgfData) {
  VerifierCtxObj verifier(this->kPubKeyIkgfStr, this->kVerifierPrecompIkgfStr);
  VerifierCtx* ctx = verifier;
  SigRl const* sig_rl = reinterpret_cast<SigRl const*>(this->kSigRlIkgf.data());
  size_t sig_rl_size = this->kSigRlIkgf.size();
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetSigRl(ctx, sig_rl, sig_rl_size));
}

TEST_F(EpidVerifierTest, SetSigRlWorksGivenSigRlWithNoElements) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);

  uint8_t sig_rl_data_n2_zero[] = {
      // gid
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x2A,
      // version
      0x00, 0x00, 0x00, 0x00,
      // n2
      0x0, 0x00, 0x00, 0x00,
      // not bk's
  };
  SigRl* sig_rl = reinterpret_cast<SigRl*>(sig_rl_data_n2_zero);
  size_t sig_rl_size = sizeof(sig_rl_data_n2_zero);
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetSigRl(verifier, sig_rl, sig_rl_size));
}

TEST_F(EpidVerifierTest, SetSigRlWorksGivenSigRlWithNoElementsUsingIkgfData) {
  VerifierCtxObj verifier(this->kPubKeyIkgfStr, this->kVerifierPrecompIkgfStr);
  auto& sig_rl = this->kEmptySigRlIkgf;
  EXPECT_EQ(kEpidNoErr,
            EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                 sig_rl.size()));
}

TEST_F(EpidVerifierTest, SetSigRlWorksGivenSigRlWithOneElement) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);

  uint8_t sig_rl_data_n2_one[] = {
      // gid
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x2A,
      // version
      0x00, 0x00, 0x00, 0x00,
      // n2
      0x0, 0x00, 0x00, 0x01,
      // one bk
      0x9c, 0xa5, 0xe5, 0xae, 0x5f, 0xae, 0x51, 0x59, 0x33, 0x35, 0x27, 0xd,
      0x8, 0xb1, 0xbe, 0x5d, 0x69, 0x50, 0x84, 0xc5, 0xfe, 0xe2, 0x87, 0xea,
      0x2e, 0xef, 0xfa, 0xee, 0x67, 0xf2, 0xd8, 0x28, 0x56, 0x43, 0xc6, 0x94,
      0x67, 0xa6, 0x72, 0xf6, 0x41, 0x15, 0x4, 0x58, 0x42, 0x16, 0x88, 0x57,
      0x9d, 0xc7, 0x71, 0xd1, 0xc, 0x84, 0x13, 0xa, 0x90, 0x23, 0x18, 0x8, 0xad,
      0x7d, 0xfe, 0xf5, 0xc8, 0xae, 0xfc, 0x51, 0x40, 0xa7, 0xd1, 0x28, 0xc2,
      0x89, 0xb2, 0x6b, 0x4e, 0xb4, 0xc1, 0x55, 0x87, 0x98, 0xbd, 0x72, 0xf9,
      0xcf, 0xd, 0x40, 0x15, 0xee, 0x32, 0xc, 0xf3, 0x56, 0xc5, 0xc, 0x61, 0x9d,
      0x4f, 0x7a, 0xb5, 0x2b, 0x16, 0xa9, 0xa3, 0x97, 0x38, 0xe2, 0xdd, 0x3a,
      0x33, 0xad, 0xf6, 0x7b, 0x68, 0x8b, 0x68, 0xcf, 0xa3, 0xd3, 0x98, 0x37,
      0xce, 0xec, 0xd1, 0xa8, 0xc, 0x8b};
  SigRl* sig_rl = reinterpret_cast<SigRl*>(sig_rl_data_n2_one);
  size_t sig_rl_size = sizeof(sig_rl_data_n2_one);
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetSigRl(verifier, sig_rl, sig_rl_size));
}

TEST_F(EpidVerifierTest, SetSigRlFailsGivenBadGroupId) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  SigRl srl = {{{0}}, {{0}}, {{0}}, {{{{0}, {0}}, {{0}, {0}}}}};
  srl.gid = this->kPubKeyStr.gid;
  srl.gid.data[0] = ~srl.gid.data[0];
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetSigRl(verifier, &srl, sizeof(srl) - sizeof(srl.bk)));
}

TEST_F(EpidVerifierTest,
       SetPrivRlFailsGivenEmptySigRlFromDifferentGroupUsingIkgfData) {
  VerifierCtxObj verifier(this->kPubKeyRevGroupIkgfStr);
  auto& sig_rl = this->kEmptySigRlIkgf;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                 sig_rl.size()));
}

TEST_F(EpidVerifierTest, SetSigRlFailsGivenOldVersion) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  SigRl srl = {{{0}}, {{0}}, {{0}}, {{{{0}, {0}}, {{0}, {0}}}}};
  srl.gid = this->kPubKeyStr.gid;
  srl.version = this->kOctStr32_1;
  EXPECT_EQ(kEpidNoErr,
            EpidVerifierSetSigRl(verifier, &srl, sizeof(srl) - sizeof(srl.bk)));
  OctStr32 octstr32_0 = {0x00, 0x00, 0x00, 0x00};
  srl.version = octstr32_0;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetSigRl(verifier, &srl, sizeof(srl) - sizeof(srl.bk)));
}

//////////////////////////////////////////////////////////////////////////
// EpidVerifierSetGroupRl
TEST_F(EpidVerifierTest, SetGroupRlFailsGivenNullPointer) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  GroupRl grl = {{0}, {0}, {0}};
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetGroupRl(nullptr, &grl, sizeof(grl)));
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetGroupRl(verifier, nullptr, sizeof(grl)));
}

TEST_F(EpidVerifierTest, SetGroupRlFailsGivenSizeZero) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  GroupRl grl = {{0}, {0}, {0}};
  size_t grl_size = 0;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetGroupRl(verifier, &grl, grl_size));
}

TEST_F(EpidVerifierTest, SetGroupRlFailsGivenSizeTooSmall) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  GroupRl grl = {{0}, {0}, {0}};
  size_t grl_size = sizeof(grl) - sizeof(grl.gid[0]);
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetGroupRl(verifier, &grl, grl_size - 1));
}

TEST_F(EpidVerifierTest, SetGroupRlFailsGivenSizeTooLarge) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  GroupRl grl = {{0}, {0}, {0}};
  size_t grl_size = sizeof(grl) - sizeof(grl.gid[0]);
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetGroupRl(verifier, &grl, grl_size + 1));
}

TEST_F(EpidVerifierTest, SetGroupRlFailsGivenN3ZeroAndGroupRLSizeTooBig) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  GroupRl* group_rl = (GroupRl*)this->kGroupRl3GidN0Buf.data();
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetGroupRl(verifier, group_rl,
                                   this->kGroupRl3GidN0Buf.size()));
}

TEST_F(EpidVerifierTest, SetGroupRlFailsGivenN3TooSmall) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  GroupRl* group_rl = (GroupRl*)this->kGroupRl3GidN2Buf.data();
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetGroupRl(verifier, group_rl,
                                   this->kGroupRl3GidN2Buf.size()));
}

TEST_F(EpidVerifierTest, SetGroupRlFailsGivenN3TooLarge) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  GroupRl* group_rl = (GroupRl*)this->kGroupRl3GidN4Buf.data();
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetGroupRl(verifier, group_rl,
                                   this->kGroupRl3GidN4Buf.size()));
}

TEST_F(EpidVerifierTest, SetGroupRlSucceedsGivenEmptyRL) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  GroupRl* empty_grl = (GroupRl*)this->kGroupRlEmptyBuf.data();
  size_t grl_size = this->kGroupRlEmptyBuf.size();
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetGroupRl(verifier, empty_grl, grl_size));
}
TEST_F(EpidVerifierTest, SetGroupRlSucceedsGivenDefaultGroupRLUsingIkgfData) {
  VerifierCtxObj verifier(this->kPubKeyIkgfStr, this->kVerifierPrecompIkgfStr);
  GroupRl* empty_grl = (GroupRl*)this->kGroupRlEmptyBuf.data();
  size_t grl_size = this->kGroupRlEmptyBuf.size();
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetGroupRl(verifier, empty_grl, grl_size));
}
TEST_F(EpidVerifierTest, SetGroupRlSucceedsGivenRLWith3gid) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  GroupRl* group_rl = (GroupRl*)this->kGroupRl3GidBuf.data();
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetGroupRl(verifier, group_rl,
                                               this->kGroupRl3GidBuf.size()));
}

TEST_F(EpidVerifierTest, SetGroupRlFailsGivenOldVersion) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  GroupRl* group_rl = (GroupRl*)this->kGroupRl3GidBuf.data();
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetGroupRl(verifier, group_rl,
                                               this->kGroupRl3GidBuf.size()));
  GroupRl* empty_grl = (GroupRl*)this->kGroupRlEmptyBuf.data();
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetGroupRl(verifier, empty_grl,
                                   this->kGroupRlEmptyBuf.size()));
}
//////////////////////////////////////////////////////////////////////////
// EpidVerifierSetVerifierRl
TEST_F(EpidVerifierTest, SetVerifierRlFailsGivenNullPointer) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierRl ver_rl = {{0}, {{0}, {0}}, {0}, {0}, {{{0}, {0}}}};
  ver_rl.gid = this->kPubKeyStr.gid;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetVerifierRl(nullptr, &ver_rl, sizeof(ver_rl)));
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetVerifierRl(verifier, nullptr, sizeof(ver_rl)));
}

TEST_F(EpidVerifierTest, SetVerifierRlFailsGivenMismatchedBasename) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  std::vector<uint8_t> wrong_bsn = this->kBasename1;
  wrong_bsn[wrong_bsn.size() - 1] ^= 1;

  VerifierCtx* ctx(verifier);
  size_t res_ver_rl_size = this->kGrp01VerRl.size();
  THROW_ON_EPIDERR(
      EpidVerifierSetBasename(ctx, wrong_bsn.data(), wrong_bsn.size()));
  EXPECT_EQ(
      kEpidBadArgErr,
      EpidVerifierSetVerifierRl(
          ctx, (VerifierRl const*)this->kGrp01VerRl.data(), res_ver_rl_size));
}
TEST_F(EpidVerifierTest, SerVerifierRlFailsGivenRandomBase) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  VerifierCtx* ctx(verifier);
  size_t res_ver_rl_size = this->kGrp01VerRl.size();
  THROW_ON_EPIDERR(EpidVerifierSetBasename(ctx, nullptr, 0));
  EXPECT_EQ(
      kEpidInconsistentBasenameSetErr,
      EpidVerifierSetVerifierRl(
          ctx, (VerifierRl const*)this->kGrp01VerRl.data(), res_ver_rl_size));
}
TEST_F(EpidVerifierTest, SetVerifierRlFailsGivenSizeZero) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierRl ver_rl = {{0}, {{0}, {0}}, {0}, {0}, {{{0}, {0}}}};
  ver_rl.gid = this->kPubKeyStr.gid;
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetVerifierRl(verifier, &ver_rl, 0));
}

// Size parameter must be at least equal to minimum value for n4 == 0 case
TEST_F(EpidVerifierTest, SetVerifierRlFailsGivenSizeTooSmall) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierRl ver_rl = {{0}, {{0}, {0}}, {0}, {0}, {{{0}, {0}}}};
  ver_rl.gid = this->kPubKeyStr.gid;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetVerifierRl(
                verifier, &ver_rl, sizeof(ver_rl) - sizeof(ver_rl.K[0]) - 1));
  ver_rl.n4 = this->kOctStr32_1;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetVerifierRl(
                verifier, &ver_rl, sizeof(ver_rl) - sizeof(ver_rl.K[0]) - 1));
}

TEST_F(EpidVerifierTest, SetVerifierRlFailsGivenN4TooBigForSize) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierRl ver_rl = {{0}, {{0}, {0}}, {0}, {0}, {{{0}, {0}}}};
  ver_rl.gid = this->kPubKeyStr.gid;
  ver_rl.n4 = this->kOctStr32_1;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetVerifierRl(verifier, &ver_rl,
                                      sizeof(ver_rl) - sizeof(ver_rl.K[0])));
}

TEST_F(EpidVerifierTest, SetVerifierRlFailsGivenN4TooSmallForSize) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierRl ver_rl = {{0}, {{0}, {0}}, {0}, {0}, {{{0}, {0}}}};
  ver_rl.gid = this->kPubKeyStr.gid;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetVerifierRl(verifier, &ver_rl, sizeof(ver_rl)));
}

TEST_F(EpidVerifierTest, SetVerifierRlWorksGivenDefaultVerifierRl) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierRl const* ver_rl_ptr =
      reinterpret_cast<VerifierRl const*>(this->kGrp01VerRl.data());
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, this->kBasename.data(),
                                           this->kBasename.size()));
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetVerifierRl(verifier, ver_rl_ptr,
                                                  this->kGrp01VerRl.size()));
}
TEST_F(EpidVerifierTest, SetVerifierRlCopiesGivenValidVerifierRl) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierCtx* ctx(verifier);
  VerifierRl* ver_rl_ptr = (VerifierRl*)(this->kGrp01VerRl.data());
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, this->kBasename.data(),
                                           this->kBasename.size()));
  EXPECT_EQ(kEpidNoErr,
            EpidVerifierSetVerifierRl(
                ctx, ver_rl_ptr, this->kGrp01VerRl.size() * sizeof(uint8_t)));
  EXPECT_NE(ver_rl_ptr, ctx->verifier_rl);
}
TEST_F(EpidVerifierTest, SetVerifierRlWorksGivenVerifierRlWithNoElements) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierRl* ver_rl_ptr = (VerifierRl*)(this->kEmptyGrp01VerRl.data());

  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, this->kBasename1.data(),
                                           this->kBasename1.size()));
  EXPECT_EQ(kEpidNoErr,
            EpidVerifierSetVerifierRl(verifier, ver_rl_ptr,
                                      this->kEmptyGrp01VerRl.size()));
}

TEST_F(EpidVerifierTest, SetVerifierRlWorksGivenVerifierRlWithOneElement) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  uint8_t ver_rl_data_n4_one[] = {
      // gid
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x2A,
      // B
      0x41, 0x63, 0xfd, 0x06, 0xb8, 0xb1, 0xa6, 0x32, 0xa5, 0xe3, 0xeb, 0xc4,
      0x40, 0x11, 0x37, 0xc0, 0x62, 0x0d, 0xe1, 0xca, 0xe9, 0x79, 0xad, 0xff,
      0x1d, 0x13, 0xb3, 0xda, 0xa0, 0x10, 0x8a, 0xa8, 0x30, 0x72, 0xa4, 0xe8,
      0x27, 0xb5, 0xad, 0xdb, 0xac, 0x89, 0xd8, 0x37, 0x79, 0xd9, 0x8c, 0xd0,
      0xb3, 0xef, 0x94, 0x17, 0x4f, 0x05, 0x53, 0x4c, 0x4d, 0xf0, 0x77, 0xf7,
      0xb6, 0xaf, 0xb8, 0xfa,
      // version
      0x00, 0x00, 0x00, 0x00,
      // n4
      0x00, 0x00, 0x00, 0x01,
      // k's
      0xdc, 0x41, 0x24, 0xe7, 0xb8, 0xf2, 0x6d, 0xc4, 0x01, 0xf9, 0x5d, 0xf8,
      0xd9, 0x23, 0x32, 0x29, 0x0a, 0xe1, 0xf6, 0xdc, 0xa1, 0xef, 0x52, 0xf7,
      0x3a, 0x3c, 0xe6, 0x7e, 0x3d, 0x0e, 0xe8, 0x86, 0xa9, 0x58, 0xf4, 0xfe,
      0xfa, 0x8b, 0xe4, 0x1c, 0xad, 0x58, 0x5b, 0x1c, 0xc7, 0x54, 0xee, 0x7e,
      0xe7, 0x12, 0x6a, 0x4b, 0x01, 0x63, 0xb4, 0xdb, 0x6e, 0xe7, 0x7a, 0xe9,
      0x62, 0xa5, 0xb4, 0xe3,
  };
  VerifierRl* ver_rl_ptr = reinterpret_cast<VerifierRl*>(ver_rl_data_n4_one);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, this->kBasename.data(),
                                           this->kBasename.size()));
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetVerifierRl(verifier, ver_rl_ptr,
                                                  sizeof(ver_rl_data_n4_one)));
}

TEST_F(EpidVerifierTest, CanSetVerifierRlTwice) {
  VerifierCtxObj verifier(this->kGrpXKey);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, this->kBsn0.data(),
                                           this->kBsn0.size()));
  EXPECT_EQ(kEpidNoErr,
            EpidVerifierSetVerifierRl(
                verifier, reinterpret_cast<VerifierRl const*>(
                              this->kGrpXBsn0VerRlSingleEntry.data()),
                this->kGrpXBsn0VerRlSingleEntry.size()));
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetVerifierRl(
                            verifier, reinterpret_cast<VerifierRl const*>(
                                          this->kGrpXBsn0Sha256VerRl.data()),
                            this->kGrpXBsn0Sha256VerRl.size()));
}

TEST_F(EpidVerifierTest, SetVerifierRlFailsGivenBadGroupId) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierRl ver_rl = {{0}, {{0}, {0}}, {0}, {0}, {{{0}, {0}}}};
  ver_rl.gid = this->kPubKeyStr.gid;
  VerifierRl* valid_ver_rl = (VerifierRl*)(this->kEmptyGrp01VerRl.data());
  ver_rl.B = valid_ver_rl->B;
  ver_rl.gid.data[0] = ~ver_rl.gid.data[0];
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetVerifierRl(verifier, &ver_rl,
                                      sizeof(ver_rl) - sizeof(ver_rl.K[0])));
}

TEST_F(EpidVerifierTest, SetVerifierRlFailsGivenOldVersion) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierRl ver_rl = {{0}, {{0}, {0}}, {0}, {0}, {{{0}, {0}}}};
  VerifierRl* valid_ver_rl = (VerifierRl*)(this->kEmptyGrp01VerRl.data());
  ver_rl.B = valid_ver_rl->B;
  ver_rl.gid = this->kPubKeyStr.gid;
  ver_rl.version = this->kOctStr32_1;
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, this->kBasename1.data(),
                                           this->kBasename1.size()));
  EXPECT_EQ(kEpidNoErr,
            EpidVerifierSetVerifierRl(verifier, &ver_rl,
                                      sizeof(ver_rl) - sizeof(ver_rl.K[0])));
  OctStr32 octstr32_0 = {0x00, 0x00, 0x00, 0x00};
  ver_rl.version = octstr32_0;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetVerifierRl(verifier, &ver_rl,
                                      sizeof(ver_rl) - sizeof(ver_rl.K[0])));
}

//////////////////////////////////////////////////////////////////////////
// EpidGetVerifierRlSize
TEST_F(EpidVerifierTest, GetVerifierRlSizeReturnsZeroGivenNoContext) {
  EXPECT_EQ((size_t)0, EpidGetVerifierRlSize(nullptr));
}
TEST_F(EpidVerifierTest, GetVerifierRlSizeReturnsZeroGivenRandomBase) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  EXPECT_EQ((size_t)0, EpidGetVerifierRlSize(verifier));
}
TEST_F(EpidVerifierTest, GetVerifierRlSizeReturnsSizeOfEmptyOnNoVerRlSet) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, this->kBasename1.data(),
                                           this->kBasename1.size()));
  EXPECT_EQ(sizeof(VerifierRl) - sizeof(((VerifierRl*)0)->K[0]),
            EpidGetVerifierRlSize(verifier));
}
TEST_F(EpidVerifierTest, GetVerifierRlSizeWorksForEmptyVerifierRl) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);

  VerifierRl* ver_rl_ptr = (VerifierRl*)(this->kEmptyGrp01VerRl.data());
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, this->kBasename1.data(),
                                           this->kBasename1.size()));
  THROW_ON_EPIDERR(EpidVerifierSetVerifierRl(verifier, ver_rl_ptr,
                                             this->kEmptyGrp01VerRl.size()));
  EXPECT_EQ(this->kEmptyGrp01VerRl.size(), EpidGetVerifierRlSize(verifier));
}
TEST_F(EpidVerifierTest, GetVerifierRlSizeWorksForShortVerifierRl) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, this->kBasename.data(),
                                           this->kBasename.size()));
  THROW_ON_EPIDERR(EpidVerifierSetVerifierRl(
      verifier, (VerifierRl*)this->kGrp01VerRlOneEntry.data(),
      this->kGrp01VerRlOneEntry.size()));
  EXPECT_EQ(this->kGrp01VerRlOneEntry.size(), EpidGetVerifierRlSize(verifier));
}
TEST_F(EpidVerifierTest, GetVerifierRlSizeWorksForLongVerifierRl) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  std::vector<uint8_t> ver_rl_data_long = {
      // gid
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x2A,
      // B
      0x41, 0x63, 0xfd, 0x06, 0xb8, 0xb1, 0xa6, 0x32, 0xa5, 0xe3, 0xeb, 0xc4,
      0x40, 0x11, 0x37, 0xc0, 0x62, 0x0d, 0xe1, 0xca, 0xe9, 0x79, 0xad, 0xff,
      0x1d, 0x13, 0xb3, 0xda, 0xa0, 0x10, 0x8a, 0xa8, 0x30, 0x72, 0xa4, 0xe8,
      0x27, 0xb5, 0xad, 0xdb, 0xac, 0x89, 0xd8, 0x37, 0x79, 0xd9, 0x8c, 0xd0,
      0xb3, 0xef, 0x94, 0x17, 0x4f, 0x05, 0x53, 0x4c, 0x4d, 0xf0, 0x77, 0xf7,
      0xb6, 0xaf, 0xb8, 0xfa,
      // version
      0x00, 0x00, 0x00, 0x32,
      // n4
      0x00, 0x00, 0x00, 0x32};
  const std::vector<uint8_t> entry = {
      0xdc, 0x41, 0x24, 0xe7, 0xb8, 0xf2, 0x6d, 0xc4, 0x01, 0xf9, 0x5d,
      0xf8, 0xd9, 0x23, 0x32, 0x29, 0x0a, 0xe1, 0xf6, 0xdc, 0xa1, 0xef,
      0x52, 0xf7, 0x3a, 0x3c, 0xe6, 0x7e, 0x3d, 0x0e, 0xe8, 0x86, 0xa9,
      0x58, 0xf4, 0xfe, 0xfa, 0x8b, 0xe4, 0x1c, 0xad, 0x58, 0x5b, 0x1c,
      0xc7, 0x54, 0xee, 0x7e, 0xe7, 0x12, 0x6a, 0x4b, 0x01, 0x63, 0xb4,
      0xdb, 0x6e, 0xe7, 0x7a, 0xe9, 0x62, 0xa5, 0xb4, 0xe3};
  for (uint32_t i = 0; i < ntohl(((VerifierRl*)ver_rl_data_long.data())->n4);
       ++i) {
    for (auto it : entry) {
      ver_rl_data_long.push_back(it);
    }
  }
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, this->kBasename.data(),
                                           this->kBasename.size()));
  THROW_ON_EPIDERR(EpidVerifierSetVerifierRl(
      verifier, (VerifierRl*)ver_rl_data_long.data(), ver_rl_data_long.size()));
  EXPECT_EQ(ver_rl_data_long.size(), EpidGetVerifierRlSize(verifier));
}
//////////////////////////////////////////////////////////////////////////
// EpidWriteVerifierRl
TEST_F(EpidVerifierTest, WriteVerifierRlFailsGivenNullPointer) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, this->kBasename.data(),
                                           this->kBasename.size()));
  VerifierCtx* ctx(verifier);
  VerifierRl res_ver_rl = {0};
  size_t res_ver_rl_size = this->kGrp01VerRl.size();
  THROW_ON_EPIDERR(EpidVerifierSetVerifierRl(
      ctx, (VerifierRl const*)this->kGrp01VerRl.data(), res_ver_rl_size));
  EXPECT_EQ(kEpidBadArgErr,
            EpidWriteVerifierRl(nullptr, &res_ver_rl, res_ver_rl_size));
  EXPECT_EQ(kEpidBadArgErr, EpidWriteVerifierRl(ctx, nullptr, res_ver_rl_size));
}
TEST_F(EpidVerifierTest, WriteVerifierRlFailsGivenInvalidSize) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, this->kBasename.data(),
                                           this->kBasename.size()));
  VerifierCtx* ctx(verifier);
  VerifierRl res_ver_rl = {0};
  size_t res_ver_rl_size = this->kGrp01VerRl.size();
  THROW_ON_EPIDERR(EpidVerifierSetVerifierRl(
      ctx, (VerifierRl const*)this->kGrp01VerRl.data(), res_ver_rl_size));
  EXPECT_EQ(kEpidBadArgErr,
            EpidWriteVerifierRl(ctx, &res_ver_rl, res_ver_rl_size - 1));
  EXPECT_EQ(kEpidBadArgErr,
            EpidWriteVerifierRl(ctx, &res_ver_rl, res_ver_rl_size + 1));
}
TEST_F(EpidVerifierTest, WriteVerifierRlWorksForEmptyVerifierRl) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, this->kBasename1.data(),
                                           this->kBasename1.size()));
  VerifierCtx* ctx(verifier);

  size_t res_ver_rl_size = sizeof(VerifierRl) - sizeof(((VerifierRl*)0)->K[0]);
  std::vector<uint8_t> expected_ver_rl_buf = this->kEmptyGrp01VerRl;
  std::vector<uint8_t> res_ver_rl_buf(res_ver_rl_size);
  VerifierRl* res_ver_rl = (VerifierRl*)res_ver_rl_buf.data();

  THROW_ON_EPIDERR(EpidVerifierSetVerifierRl(
      ctx, (VerifierRl*)this->kEmptyGrp01VerRl.data(), res_ver_rl_size));
  EXPECT_EQ(kEpidNoErr, EpidWriteVerifierRl(ctx, res_ver_rl, res_ver_rl_size));
  EXPECT_EQ(expected_ver_rl_buf, res_ver_rl_buf);
}
TEST_F(EpidVerifierTest, WriteVerifierRlCanSerializeDefaultVerifierRl) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, this->kBasename1.data(),
                                           this->kBasename1.size()));
  VerifierCtx* ctx(verifier);
  size_t res_ver_rl_size = sizeof(VerifierRl) - sizeof(((VerifierRl*)0)->K[0]);

  std::vector<uint8_t> empty_verifier_rl_buf(res_ver_rl_size);
  std::vector<uint8_t> res_ver_rl_buf(res_ver_rl_size);
  VerifierRl* empty_verifier_rl = (VerifierRl*)empty_verifier_rl_buf.data();
  VerifierRl* res_ver_rl = (VerifierRl*)res_ver_rl_buf.data();

  empty_verifier_rl->gid = ctx->pub_key->gid;
  empty_verifier_rl->B =
      ((EpidSignature const*)this->kSigGrp01Member0Sha256Basename1Test1NoSigRl
           .data())
          ->sigma0.B;
  empty_verifier_rl->n4 = {0};
  empty_verifier_rl->version = {0};
  EXPECT_EQ(kEpidNoErr, EpidWriteVerifierRl(ctx, res_ver_rl, res_ver_rl_size));
  EXPECT_EQ(empty_verifier_rl_buf, res_ver_rl_buf);
}
TEST_F(EpidVerifierTest, WriteVerifierRlWorksForNonEmptyVerifierRl) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, this->kBasename.data(),
                                           this->kBasename.size()));
  VerifierCtx* ctx(verifier);
  auto expected_ver_rl = this->kGrp01VerRl;
  std::vector<uint8_t> resultant_ver_rl_buf(this->kGrp01VerRl.size());
  VerifierRl* resultant_ver_rl = (VerifierRl*)resultant_ver_rl_buf.data();

  THROW_ON_EPIDERR(EpidVerifierSetVerifierRl(
      ctx, (VerifierRl const*)this->kGrp01VerRl.data(),
      this->kGrp01VerRl.size()));
  EXPECT_EQ(kEpidNoErr, EpidWriteVerifierRl(ctx, resultant_ver_rl,
                                            resultant_ver_rl_buf.size()));
  EXPECT_EQ(expected_ver_rl, resultant_ver_rl_buf);
}

//////////////////////////////////////////////////////////////////////////
// EpidBlacklistSig
TEST_F(EpidVerifierTest, BlacklistSigFailsGivenNullPointer) {
  VerifierCtxObj verifier(this->kGrpXKey);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  auto sig = this->kSigGrpXMember0Sha256Bsn0Msg0;
  auto msg = this->kMsg0;
  auto bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidBadArgErr,
            EpidBlacklistSig(nullptr, (EpidSignature*)sig.data(), sig.size(),
                             msg.data(), msg.size()));
  EXPECT_EQ(kEpidBadArgErr, EpidBlacklistSig(verifier, nullptr, sig.size(),
                                             msg.data(), msg.size()));
  EXPECT_EQ(kEpidBadArgErr,
            EpidBlacklistSig(verifier, (EpidSignature*)sig.data(), sig.size(),
                             nullptr, 1));
}
TEST_F(EpidVerifierTest, BlacklistSigFailsGivenInvalidSignatureLength) {
  VerifierCtxObj verifier(this->kGrpXKey);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  auto sig = this->kSigGrpXMember0Sha256Bsn0Msg0;
  auto msg = this->kMsg0;
  auto bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidBadArgErr,
            EpidBlacklistSig(verifier, (EpidSignature*)sig.data(), 0,
                             msg.data(), msg.size()));
  EXPECT_EQ(kEpidBadArgErr,
            EpidBlacklistSig(verifier, (EpidSignature*)sig.data(),
                             sig.size() - 1, msg.data(), msg.size()));
  EXPECT_EQ(kEpidBadArgErr,
            EpidBlacklistSig(verifier, (EpidSignature*)sig.data(),
                             sig.size() + 1, msg.data(), msg.size()));
}
TEST_F(EpidVerifierTest, BlacklistSigFailsGivenSigFromDiffGroup) {
  VerifierCtxObj verifier(this->kGrpXKey);
  auto sig = this->kSigGrp01Member0Sha256Basename1Test1NoSigRl;
  auto msg = this->kTest1;
  auto bsn = this->kBasename1;
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidBadArgErr,
            EpidBlacklistSig(verifier, (EpidSignature*)sig.data(), sig.size(),
                             msg.data(), msg.size()));
}
TEST_F(EpidVerifierTest, BlacklistSigFailsGivenSigFromDiffBasename) {
  VerifierCtxObj verifier(this->kGrpXKey);
  auto sig = this->kSigGrpXMember0Sha256Bsn0Msg0;
  auto msg = this->kMsg0;
  auto bsn = this->kBasename1;
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigInvalid,
            EpidBlacklistSig(verifier, (EpidSignature*)sig.data(), sig.size(),
                             msg.data(), msg.size()));
}
TEST_F(EpidVerifierTest, BlacklistSigFailsGivenSigWithDiffHashAlg) {
  VerifierCtxObj verifier(this->kGrpXKey);
  auto sig = this->kSigGrpXMember0Sha256Bsn0Msg0;
  auto msg = this->kMsg0;
  auto bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha384));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigInvalid,
            EpidBlacklistSig(verifier, (EpidSignature*)sig.data(), sig.size(),
                             msg.data(), msg.size()));
}
TEST_F(EpidVerifierTest, BlacklistSigFailsOnSigAlreadyInVerRl) {
  VerifierCtxObj verifier(this->kGrpXKey);
  auto sig = this->kSigGrpXVerRevokedMember0Sha256Bsn0Msg0;
  auto msg = this->kMsg0;
  auto bsn = this->kBsn0;
  auto ver_rl = this->kGrpXBsn0VerRlSingleEntry;
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetVerifierRl(
      verifier, (VerifierRl*)ver_rl.data(), ver_rl.size()));
  EXPECT_EQ(kEpidSigRevokedInVerifierRl,
            EpidBlacklistSig(verifier, (EpidSignature*)sig.data(), sig.size(),
                             msg.data(), msg.size()));
}
TEST_F(EpidVerifierTest, BlacklistSigFailsOnSigRevokedInSigRl) {
  VerifierCtxObj verifier(this->kGrpXKey);
  auto sig = this->kSigGrpXMember0Sha256Bsn0Msg0SingleEntrySigRl;
  auto msg = this->kMsg0;
  auto bsn = this->kBsn0;
  auto sig_rl = this->kGrpXSigRlMember0Sha256Bsn0Msg0OnlyEntry;
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(
      EpidVerifierSetSigRl(verifier, (SigRl*)sig_rl.data(), sig_rl.size()));
  EXPECT_EQ(kEpidSigRevokedInSigRl,
            EpidBlacklistSig(verifier, (EpidSignature*)sig.data(), sig.size(),
                             msg.data(), msg.size()));
}
TEST_F(EpidVerifierTest, BlacklistSigFailsOnSigRevokedInPrivRl) {
  VerifierCtxObj verifier(this->kGrpXKey);
  auto sig = this->kSigGrpXRevokedPrivKey000Sha256Bsn0Msg0;
  auto msg = this->kMsg0;
  auto bsn = this->kBsn0;
  auto priv_rl = this->kGrpXPrivRl;
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(
      EpidVerifierSetPrivRl(verifier, (PrivRl*)priv_rl.data(), priv_rl.size()));
  EXPECT_EQ(kEpidSigRevokedInPrivRl,
            EpidBlacklistSig(verifier, (EpidSignature*)sig.data(), sig.size(),
                             msg.data(), msg.size()));
}
TEST_F(EpidVerifierTest, BlacklistSigWorksForValidSigGivenEmptyBlacklist) {
  VerifierCtxObj verifier(this->kGrpXKey);
  auto sig = this->kSigGrpXMember0Sha256Bsn0Msg0;
  auto msg = this->kMsg0;
  auto bsn = this->kBsn0;
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidNoErr, EpidBlacklistSig(verifier, (EpidSignature*)sig.data(),
                                         sig.size(), msg.data(), msg.size()));

  std::vector<uint8_t> ver_rl_vec(EpidGetVerifierRlSize(verifier));
  VerifierRl* ver_rl = (VerifierRl*)ver_rl_vec.data();
  size_t ver_rl_size = ver_rl_vec.size();

  THROW_ON_EPIDERR(EpidWriteVerifierRl(verifier, ver_rl, ver_rl_size));

  OctStr32 n4_expected = {0x00, 0x00, 0x00, 0x01};
  OctStr32 rlver_expected = {0x00, 0x00, 0x00, 0x01};
  EXPECT_EQ(n4_expected, ver_rl->n4);
  EXPECT_EQ(rlver_expected, ver_rl->version);
  // missing K checks here
}
TEST_F(EpidVerifierTest,
       MultipleBlacklistFollowedBySerializeIncrementsRlVersionByOne) {
  VerifierCtxObj verifier(this->kGrpXKey);
  auto sig = this->kSigGrpXMember0Sha256Bsn0Msg0;
  auto msg = this->kMsg0;
  auto bsn = this->kBsn0;
  auto sig2 = this->kSigGrpXMember1Sha256Bsn0Msg0;
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidNoErr, EpidBlacklistSig(verifier, (EpidSignature*)sig.data(),
                                         sig.size(), msg.data(), msg.size()));
  EXPECT_EQ(kEpidNoErr, EpidBlacklistSig(verifier, (EpidSignature*)sig2.data(),
                                         sig2.size(), msg.data(), msg.size()));
  std::vector<uint8_t> ver_rl_vec(EpidGetVerifierRlSize(verifier));
  VerifierRl* ver_rl = (VerifierRl*)ver_rl_vec.data();
  size_t ver_rl_size = ver_rl_vec.size();

  THROW_ON_EPIDERR(EpidWriteVerifierRl(verifier, ver_rl, ver_rl_size));

  OctStr32 n4_expected = {0x00, 0x00, 0x00, 0x02};
  OctStr32 rlver_expected = {0x00, 0x00, 0x00, 0x01};
  EXPECT_EQ(n4_expected, ver_rl->n4);
  EXPECT_EQ(rlver_expected, ver_rl->version);
  // missing K checks
}
//////////////////////////////////////////////////////////////////////////
// EpidVerifierSetHashAlg
TEST_F(EpidVerifierTest, SetHashAlgFailsGivenNullPointer) {
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetHashAlg(nullptr, kSha256));
}
TEST_F(EpidVerifierTest, SetHashAlgCanSetValidAlgoritm) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetHashAlg(verifier, kSha256));
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetHashAlg(verifier, kSha384));
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetHashAlg(verifier, kSha512));
  // DE2089 - SHA-512/256 Hash Alg is not supported by EpidMemberSetHashAlg
  // EXPECT_EQ(kEpidNoErr, EpidVerifierSetHashAlg(verifier, kSha512_256));
}
TEST_F(EpidVerifierTest, SetHashAlgCanFailForNonSupportedAlgoritms) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetHashAlg(verifier, kSha3_256));
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetHashAlg(verifier, kSha3_384));
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetHashAlg(verifier, kSha3_512));
  EXPECT_EQ(kEpidBadArgErr, EpidVerifierSetHashAlg(verifier, (HashAlg)-1));
}
TEST_F(EpidVerifierTest, DefaultHashAlgIsSha512) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierCtx* ctx = verifier;
  EXPECT_EQ(kSha512, ctx->hash_alg);
}
//////////////////////////////////////////////////////////////////////////
// EpidVerifierSetBasename
TEST_F(EpidVerifierTest, DefaultBasenameIsNull) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierCtx* ctx = verifier;
  EXPECT_EQ(nullptr, ctx->basename_hash);
}
TEST_F(EpidVerifierTest, SetBasenameFailsGivenNullContext) {
  auto& basename = this->kBasename1;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetBasename(nullptr, basename.data(), basename.size()));
}
TEST_F(EpidVerifierTest, SetBasenameFailsGivenNullBasenameAndNonzeroLength) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierCtx* ctx = verifier;
  auto& basename = this->kBasename1;
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerifierSetBasename(ctx, nullptr, basename.size()));
}
TEST_F(EpidVerifierTest, SetBasenameSucceedsGivenValidParameters) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierCtx* ctx = verifier;
  auto& basename = this->kBasename1;
  EXPECT_EQ(kEpidNoErr,
            EpidVerifierSetBasename(ctx, basename.data(), basename.size()));
}
TEST_F(EpidVerifierTest, SetBasenameAcceptsZeroLengthBasename) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierCtx* ctx = verifier;
  EXPECT_EQ(kEpidNoErr, EpidVerifierSetBasename(ctx, "", 0));
}
TEST_F(EpidVerifierTest, SetBasenameResetsBasenameGivenNullBasename) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierCtx* ctx = verifier;
  auto& basename = this->kBasename1;
  THROW_ON_EPIDERR(
      EpidVerifierSetBasename(ctx, basename.data(), basename.size()));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(ctx, nullptr, 0));
  EXPECT_EQ(nullptr, ctx->basename_hash);
}
TEST_F(EpidVerifierTest, SetBasenameResetsVerifierBlacklist) {
  VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  VerifierCtx* ctx = verifier;
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(ctx, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(ctx, this->kBasename.data(),
                                           this->kBasename.size()));
  auto& basename = this->kBasename;
  VerifierRl const* ver_rl_ptr =
      reinterpret_cast<VerifierRl const*>(this->kGrp01VerRl.data());
  THROW_ON_EPIDERR(EpidVerifierSetVerifierRl(verifier, ver_rl_ptr,
                                             this->kGrp01VerRl.size()));
  THROW_ON_EPIDERR(
      EpidVerifierSetBasename(ctx, basename.data(), basename.size()));
  EXPECT_EQ(nullptr, ctx->verifier_rl);
}

}  // namespace
