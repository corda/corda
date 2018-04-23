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
 * \brief Epid11VerifierCreate unit tests.
 */

#include <cstring>

#include "gtest/gtest.h"

extern "C" {
#include "epid/verifier/1.1/api.h"
#include "epid/verifier/1.1/src/context.h"
}

#include "epid/verifier/1.1/unittests/verifier-testhelper.h"
#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/common-testhelper/1.1/verifier_wrapper-testhelper.h"
bool operator==(Epid11VerifierPrecomp const& lhs,
                Epid11VerifierPrecomp const& rhs) {
  return 0 == std::memcmp(&lhs, &rhs, sizeof(lhs));
}
namespace {
//////////////////////////////////////////////////////////////////////////
// Epid11VerifierCreate Tests
TEST_F(Epid11VerifierTest, CreateFailsGivenNullPointer) {
  Epid11VerifierCtx* ctx = nullptr;
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierCreate(&this->kPubKeyStr, &this->kVerifierPrecompStr,
                                 nullptr));
  Epid11VerifierDelete(&ctx);
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierCreate(nullptr, &this->kVerifierPrecompStr, &ctx));
  Epid11VerifierDelete(&ctx);
}
TEST_F(Epid11VerifierTest, CreateSucceedsGivenNullPrecomp) {
  Epid11VerifierCtx* ctx = nullptr;
  EXPECT_EQ(kEpidNoErr, Epid11VerifierCreate(&this->kPubKeyStr, nullptr, &ctx));
  Epid11VerifierDelete(&ctx);
}
TEST_F(Epid11VerifierTest, CreateSucceedsGivenValidPrecomp) {
  Epid11VerifierCtx* ctx = nullptr;
  EXPECT_EQ(kEpidNoErr, Epid11VerifierCreate(&this->kPubKeyStr,
                                             &this->kVerifierPrecompStr, &ctx));
  Epid11VerifierDelete(&ctx);
}
TEST_F(Epid11VerifierTest, CreateFailsGivenInvalidPubkey) {
  Epid11VerifierCtx* ctx = nullptr;
  Epid11GroupPubKey pubkey_with_bad_h1 = this->kPubKeyStr;
  pubkey_with_bad_h1.h1.x.data.data[31]++;  // munge h1 so not in G1
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierCreate(&pubkey_with_bad_h1, nullptr, &ctx));
  Epid11VerifierDelete(&ctx);
  Epid11GroupPubKey pubkey_with_bad_h2 = this->kPubKeyStr;
  pubkey_with_bad_h2.h2.x.data.data[31]++;  // munge h2 so not in G1
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierCreate(&pubkey_with_bad_h2, nullptr, &ctx));
  Epid11VerifierDelete(&ctx);
  Epid11GroupPubKey pubkey_with_bad_w = this->kPubKeyStr;
  pubkey_with_bad_w.w.x[0].data.data[31]++;  // munge w so not in G2
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierCreate(&pubkey_with_bad_w, nullptr, &ctx));
  Epid11VerifierDelete(&ctx);
}
TEST_F(Epid11VerifierTest, CreateFailsGivenBadGroupIdInPrecomp) {
  Epid11VerifierCtx* ctx = nullptr;
  // tweak GID
  auto verifier_precomp = this->kVerifierPrecompStr;
  verifier_precomp.gid.data[0] = ~verifier_precomp.gid.data[0];
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierCreate(&this->kPubKeyStr, &verifier_precomp, &ctx));
}
//////////////////////////////////////////////////////////////////////////
// Epid11VerifierDelete Tests
TEST_F(Epid11VerifierTest, DeleteNullsVerifierCtx) {
  Epid11VerifierCtx* ctx = nullptr;
  THROW_ON_EPIDERR(Epid11VerifierCreate(&this->kPubKeyStr, nullptr, &ctx));
  Epid11VerifierDelete(&ctx);
  EXPECT_EQ(nullptr, ctx);
}
TEST_F(Epid11VerifierTest, DeleteWorksGivenNullVerifierCtx) {
  Epid11VerifierDelete(nullptr);
  Epid11VerifierCtx* ctx = nullptr;
  Epid11VerifierDelete(&ctx);
}

//////////////////////////////////////////////////////////////////////////
// Epid11VerifierWritePrecomp
TEST_F(Epid11VerifierTest, WritePrecompFailsGivenNullPointer) {
  Epid11VerifierPrecomp precomp;
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11VerifierCtx* ctx = verifier;
  EXPECT_EQ(kEpidBadArgErr, Epid11VerifierWritePrecomp(nullptr, &precomp));
  EXPECT_EQ(kEpidBadArgErr, Epid11VerifierWritePrecomp(ctx, nullptr));
}
TEST_F(Epid11VerifierTest, WritePrecompSucceedGivenValidArgument) {
  Epid11VerifierPrecomp precomp;
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11VerifierCtx* ctx = verifier;
  EXPECT_EQ(kEpidNoErr, Epid11VerifierWritePrecomp(ctx, &precomp));
  Epid11VerifierPrecomp expected_precomp = this->kVerifierPrecompStr;
  EXPECT_EQ(expected_precomp, precomp);

  Epid11VerifierCtxObj verifier2(this->kPubKeyStr);
  Epid11VerifierCtx* ctx2 = verifier2;
  EXPECT_EQ(kEpidNoErr, Epid11VerifierWritePrecomp(ctx2, &precomp));
  EXPECT_EQ(expected_precomp, precomp);
}

//////////////////////////////////////////////////////////////////////////
// Epid11VerifierSetPrivRl
TEST_F(Epid11VerifierTest, SetPrivRlFailsGivenNullPointer) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11PrivRl prl = {0};
  prl.gid = this->kPubKeyStr.gid;
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierSetPrivRl(nullptr, &prl, sizeof(prl)));
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierSetPrivRl(verifier, nullptr, sizeof(prl)));
}

TEST_F(Epid11VerifierTest, SetPrivRlFailsGivenZeroSize) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11PrivRl prl = {0};
  prl.gid = this->kPubKeyStr.gid;
  EXPECT_EQ(kEpidBadArgErr, Epid11VerifierSetPrivRl(verifier, &prl, 0));
}

// Size parameter must be at least big enough for n1 == 0 case
TEST_F(Epid11VerifierTest, SetPrivRlFailsGivenTooSmallSize) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11PrivRl prl = {0};
  prl.gid = this->kPubKeyStr.gid;
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierSetPrivRl(verifier, &prl,
                                    (sizeof(prl) - sizeof(prl.f)) - 1));
  prl.n1 = this->kOctStr32_1;
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierSetPrivRl(verifier, &prl,
                                    (sizeof(prl) - sizeof(prl.f)) - 1));
}

// Size parameter must be cross-checked with n1 value in priv_rl
TEST_F(Epid11VerifierTest, SetPrivRlFailsGivenN1TooBigForSize) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11PrivRl prl = {0};
  prl.gid = this->kPubKeyStr.gid;
  prl.n1 = this->kOctStr32_1;
  EXPECT_EQ(kEpidBadArgErr, Epid11VerifierSetPrivRl(
                                verifier, &prl, sizeof(prl) - sizeof(prl.f)));
}

TEST_F(Epid11VerifierTest, SetPrivRlFailsGivenN1TooSmallForSize) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11PrivRl prl = {0};
  prl.gid = this->kPubKeyStr.gid;
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierSetPrivRl(verifier, &prl, sizeof(prl)));
}

TEST_F(Epid11VerifierTest, SetPrivRlPassesGivenDefaultPrivRl) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11PrivRl prl = {0};
  prl.gid = this->kPubKeyStr.gid;
  EXPECT_EQ(kEpidNoErr, Epid11VerifierSetPrivRl(verifier, &prl,
                                                sizeof(prl) - sizeof(prl.f)));
}

TEST_F(Epid11VerifierTest, SetPrivRlPassesGivenPrivRlWithSingleElement) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11PrivRl prl = {0};
  prl.gid = this->kPubKeyStr.gid;
  prl.n1 = this->kOctStr32_1;
  EXPECT_EQ(kEpidNoErr, Epid11VerifierSetPrivRl(verifier, &prl, sizeof(prl)));
}

TEST_F(Epid11VerifierTest, SetPrivRlFailsGivenBadGroupId) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11PrivRl prl = {0};
  prl.gid = this->kPubKeyStr.gid;
  prl.gid.data[0] = ~prl.gid.data[0];
  prl.n1 = this->kOctStr32_1;
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierSetPrivRl(verifier, &prl, sizeof(prl)));
}

TEST_F(Epid11VerifierTest, SetPrivRlFailsGivenOldVersion) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11PrivRl prl = {0};
  prl.gid = this->kPubKeyStr.gid;
  prl.version = this->kOctStr32_1;
  EXPECT_EQ(kEpidNoErr, Epid11VerifierSetPrivRl(verifier, &prl,
                                                sizeof(prl) - sizeof(prl.f)));
  OctStr32 octstr32_0 = {0x00, 0x00, 0x00, 0x00};
  prl.version = octstr32_0;
  EXPECT_EQ(kEpidBadArgErr, Epid11VerifierSetPrivRl(
                                verifier, &prl, sizeof(prl) - sizeof(prl.f)));
}

//////////////////////////////////////////////////////////////////////////
// Epid11VerifierSetSigRl
TEST_F(Epid11VerifierTest, SetSigRlFailsGivenNullPointer) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11SigRl* empty_sig_rl = (Epid11SigRl*)this->kEmptySigRl.data();
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierSetSigRl(nullptr, empty_sig_rl, sizeof(Epid11SigRl)));
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierSetSigRl(verifier, nullptr, sizeof(Epid11SigRl)));
}

TEST_F(Epid11VerifierTest, SetSigRlFailsGivenZeroSize) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11SigRl* empty_sig_rl = (Epid11SigRl*)this->kEmptySigRl.data();
  EXPECT_EQ(kEpidBadArgErr, Epid11VerifierSetSigRl(verifier, empty_sig_rl, 0));
}

// Size parameter must be at least big enough for n2 == 0 case
TEST_F(Epid11VerifierTest, SetSigRlFailsGivenTooSmallSize) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  std::vector<uint8_t> empty_sig_rl_buf(this->kEmptySigRl);
  Epid11SigRl* empty_sig_rl = (Epid11SigRl*)empty_sig_rl_buf.data();
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierSetSigRl(
                verifier, empty_sig_rl,
                (sizeof(*empty_sig_rl) - sizeof(empty_sig_rl->bk)) - 1));
  empty_sig_rl->n2 = this->kOctStr32_1;
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierSetSigRl(
                verifier, empty_sig_rl,
                (sizeof(*empty_sig_rl) - sizeof(empty_sig_rl->bk)) - 1));
}

TEST_F(Epid11VerifierTest, SetSigRlFailsGivenN2TooBigForSize) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  std::vector<uint8_t> empty_sig_rl_buf(this->kEmptySigRl);
  Epid11SigRl* empty_sig_rl = (Epid11SigRl*)empty_sig_rl_buf.data();
  empty_sig_rl->n2 = this->kOctStr32_1;
  EXPECT_EQ(
      kEpidBadArgErr,
      Epid11VerifierSetSigRl(verifier, empty_sig_rl,
                             sizeof(*empty_sig_rl) - sizeof(empty_sig_rl->bk)));
}

TEST_F(Epid11VerifierTest, SetSigRlFailsGivenN2TooSmallForSize) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  std::vector<uint8_t> empty_sig_rl_buf(this->kEmptySigRl);
  Epid11SigRl* empty_sig_rl = (Epid11SigRl*)empty_sig_rl_buf.data();
  EXPECT_EQ(kEpidBadArgErr, Epid11VerifierSetSigRl(verifier, empty_sig_rl,
                                                   sizeof(*empty_sig_rl)));
}

TEST_F(Epid11VerifierTest, SetSigRlWorksGivenSigRlWithNoElements) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  std::vector<uint8_t> empty_sig_rl_buf(this->kEmptySigRl);
  Epid11SigRl* empty_sig_rl = (Epid11SigRl*)empty_sig_rl_buf.data();
  size_t sig_rl_size = empty_sig_rl_buf.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr,
            Epid11VerifierSetSigRl(verifier, empty_sig_rl, sig_rl_size));
}

TEST_F(Epid11VerifierTest, SetSigRlWorksGivenSigRlWithOneElement) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);

  uint8_t sig_rl_data_n2_one[] = {
      // gid
      0x00, 0x00, 0x00, 0x7b,
      // rev
      0x00, 0x00, 0x00, 0x7b,
      // n2
      0x00, 0x00, 0x00, 0x01,
      // bks
      // bk1
      0x67, 0x58, 0xb2, 0x9c, 0xad, 0x61, 0x1f, 0xfb, 0x74, 0x23, 0xea, 0x40,
      0xe9, 0x66, 0x26, 0xb0, 0x43, 0xdc, 0x7e, 0xc7, 0x48, 0x88, 0x56, 0x59,
      0xf3, 0x35, 0x9f, 0xdb, 0xfa, 0xa2, 0x49, 0x51, 0x85, 0x35, 0x42, 0x50,
      0x8e, 0x79, 0x79, 0xc0, 0x6c, 0xcc, 0x39, 0x0b, 0xad, 0x3b, 0x39, 0x33,
      0xae, 0xb2, 0xa1, 0xc5, 0x28, 0x6f, 0x48, 0x3a, 0xd2, 0x63, 0x5d, 0xfb,
      0x1b, 0x1f, 0x8a, 0x63, 0x84, 0xdc, 0x2d, 0xad, 0x3b, 0x98, 0x3f, 0xc3,
      0x8e, 0x18, 0xd7, 0xea, 0x18, 0x50, 0x0c, 0x50, 0x42, 0x77, 0xb2, 0x59,
      0xf5, 0xd5, 0x38, 0xc3, 0x8d, 0x57, 0xf4, 0xe7, 0xb8, 0x74, 0x5a, 0x9e,
      0x32, 0x75, 0xd1, 0xb4, 0xb3, 0x64, 0xbc, 0x23, 0xcd, 0x98, 0x29, 0x7a,
      0x77, 0x51, 0xfc, 0x26, 0x81, 0x41, 0x9b, 0xf6, 0x21, 0xad, 0xc1, 0xd9,
      0xab, 0x30, 0x25, 0x8d, 0x0c, 0x3b, 0x62, 0xe2};
  Epid11SigRl* sig_rl = reinterpret_cast<Epid11SigRl*>(sig_rl_data_n2_one);
  EXPECT_EQ(kEpidNoErr,
            Epid11VerifierSetSigRl(verifier, sig_rl, sizeof(*sig_rl)));
}

TEST_F(Epid11VerifierTest, SetSigRlWorksGivenSigRlWithTwoElement) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11VerifierCtx* ctx = verifier;
  Epid11SigRl const* sig_rl =
      reinterpret_cast<Epid11SigRl const*>(this->kSigRl.data());
  size_t sig_rl_size = this->kSigRl.size() * sizeof(uint8_t);
  EXPECT_EQ(kEpidNoErr, Epid11VerifierSetSigRl(ctx, sig_rl, sig_rl_size));
}

TEST_F(Epid11VerifierTest, SetSigRlFailsGivenBadGroupId) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  std::vector<uint8_t> empty_sig_rl_buf(this->kEmptySigRl);
  Epid11SigRl* empty_sig_rl = (Epid11SigRl*)empty_sig_rl_buf.data();
  empty_sig_rl->gid.data[0] = ~empty_sig_rl->gid.data[0];
  EXPECT_EQ(
      kEpidBadArgErr,
      Epid11VerifierSetSigRl(verifier, empty_sig_rl,
                             sizeof(*empty_sig_rl) - sizeof(empty_sig_rl->bk)));
}

TEST_F(Epid11VerifierTest, SetSigRlFailsGivenOldVersion) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  std::vector<uint8_t> empty_sig_rl_buf(this->kEmptySigRl);
  Epid11SigRl* empty_sig_rl = (Epid11SigRl*)empty_sig_rl_buf.data();
  empty_sig_rl->version = this->kOctStr32_1;
  EXPECT_EQ(kEpidNoErr, Epid11VerifierSetSigRl(
                            verifier, empty_sig_rl,
                            sizeof(*empty_sig_rl) - sizeof(empty_sig_rl->bk)));
  OctStr32 octstr32_0 = {0x00, 0x00, 0x00, 0x00};
  empty_sig_rl->version = octstr32_0;
  EXPECT_EQ(
      kEpidBadArgErr,
      Epid11VerifierSetSigRl(verifier, empty_sig_rl,
                             sizeof(*empty_sig_rl) - sizeof(empty_sig_rl->bk)));
}

//////////////////////////////////////////////////////////////////////////
// Epid11VerifierSetGroupRl
TEST_F(Epid11VerifierTest, SetGroupRlFailsGivenNullPointer) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  std::vector<uint8_t> group_rl(this->kGroupRl3GidBuf);
  Epid11GroupRl* grl = (Epid11GroupRl*)group_rl.data();
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierSetGroupRl(nullptr, grl, group_rl.size()));
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierSetGroupRl(verifier, nullptr, group_rl.size()));
}

TEST_F(Epid11VerifierTest, SetGroupRlFailsGivenSizeZero) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  std::vector<uint8_t> group_rl(this->kGroupRl3GidBuf);
  Epid11GroupRl* grl = (Epid11GroupRl*)group_rl.data();
  EXPECT_EQ(kEpidBadArgErr, Epid11VerifierSetGroupRl(verifier, grl, 0));
}

TEST_F(Epid11VerifierTest, SetGroupRlFailsGivenSizeTooSmall) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  std::vector<uint8_t> group_rl(this->kGroupRl3GidBuf);
  Epid11GroupRl* grl = (Epid11GroupRl*)group_rl.data();
  size_t grl_size = group_rl.size() - sizeof(grl->gid[0]);
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierSetGroupRl(verifier, grl, grl_size - 1));
}

TEST_F(Epid11VerifierTest, SetGroupRlFailsGivenSizeTooLarge) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  std::vector<uint8_t> group_rl(this->kGroupRl3GidBuf);
  Epid11GroupRl* grl = (Epid11GroupRl*)group_rl.data();
  size_t grl_size = group_rl.size() - sizeof(grl->gid[0]);
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierSetGroupRl(verifier, grl, grl_size + 1));
}

TEST_F(Epid11VerifierTest, SetGroupRlFailsGivenN3ZeroAndGroupRLSizeTooBig) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  std::vector<uint8_t> group_rl_3gid_n0_buf(this->kGroupRl3GidBuf);
  group_rl_3gid_n0_buf[7] = 0x00;
  Epid11GroupRl* group_rl = (Epid11GroupRl*)group_rl_3gid_n0_buf.data();
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierSetGroupRl(verifier, group_rl,
                                     group_rl_3gid_n0_buf.size()));
}

TEST_F(Epid11VerifierTest, SetGroupRlFailsGivenN3TooSmall) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  std::vector<uint8_t> group_rl_3gid_n2_buf(this->kGroupRl3GidBuf);
  group_rl_3gid_n2_buf[7] = 0x02;
  Epid11GroupRl* group_rl = (Epid11GroupRl*)group_rl_3gid_n2_buf.data();
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierSetGroupRl(verifier, group_rl,
                                     group_rl_3gid_n2_buf.size()));
}

TEST_F(Epid11VerifierTest, SetGroupRlFailsGivenN3TooLarge) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  std::vector<uint8_t> group_rl_3gid_n4_buf(this->kGroupRl3GidBuf);
  group_rl_3gid_n4_buf[7] = 0x04;
  Epid11GroupRl* group_rl = (Epid11GroupRl*)group_rl_3gid_n4_buf.data();
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierSetGroupRl(verifier, group_rl,
                                     group_rl_3gid_n4_buf.size()));
}

TEST_F(Epid11VerifierTest, SetGroupRlSucceedsGivenEmptyRL) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11GroupRl* empty_grl = (Epid11GroupRl*)this->kGroupRlEmptyBuf.data();
  size_t grl_size = this->kGroupRlEmptyBuf.size();
  EXPECT_EQ(kEpidNoErr,
            Epid11VerifierSetGroupRl(verifier, empty_grl, grl_size));
}
TEST_F(Epid11VerifierTest, SetGroupRlSucceedsGivenRLWith3gid) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11GroupRl* group_rl = (Epid11GroupRl*)this->kGroupRl3GidBuf.data();
  EXPECT_EQ(kEpidNoErr, Epid11VerifierSetGroupRl(verifier, group_rl,
                                                 this->kGroupRl3GidBuf.size()));
}

TEST_F(Epid11VerifierTest, SetGroupRlFailsGivenOldVersion) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11GroupRl* group_rl = (Epid11GroupRl*)this->kGroupRl3GidBuf.data();
  EXPECT_EQ(kEpidNoErr, Epid11VerifierSetGroupRl(verifier, group_rl,
                                                 this->kGroupRl3GidBuf.size()));
  Epid11GroupRl* empty_grl = (Epid11GroupRl*)this->kGroupRlEmptyBuf.data();
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierSetGroupRl(verifier, empty_grl,
                                     this->kGroupRlEmptyBuf.size()));
}

//////////////////////////////////////////////////////////////////////////
// Epid11VerifierSetBasename
TEST_F(Epid11VerifierTest, DefaultBasenameIsNull) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11VerifierCtx* ctx = verifier;
  EXPECT_EQ(nullptr, ctx->basename);
}
TEST_F(Epid11VerifierTest, SetBasenameFailsGivenNullContext) {
  auto& basename = this->kBsn0;
  EXPECT_EQ(kEpidBadArgErr, Epid11VerifierSetBasename(nullptr, basename.data(),
                                                      basename.size()));
}
TEST_F(Epid11VerifierTest, SetBasenameFailsGivenNullBasenameAndNonzeroLength) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11VerifierCtx* ctx = verifier;
  auto& basename = this->kBsn0;
  EXPECT_EQ(kEpidBadArgErr,
            Epid11VerifierSetBasename(ctx, nullptr, basename.size()));
}
TEST_F(Epid11VerifierTest, SetBasenameSucceedsGivenValidParameters) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11VerifierCtx* ctx = verifier;
  auto& basename = this->kBsn0;
  EXPECT_EQ(kEpidNoErr,
            Epid11VerifierSetBasename(ctx, basename.data(), basename.size()));
  EXPECT_EQ(basename.size(), ctx->basename_len);
  EXPECT_EQ(0, memcmp(basename.data(), ctx->basename, ctx->basename_len));
  EXPECT_NE(nullptr, ctx->basename_hash);
}
TEST_F(Epid11VerifierTest, SetBasenameAcceptsZeroLengthBasename) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11VerifierCtx* ctx = verifier;
  EXPECT_EQ(kEpidNoErr, Epid11VerifierSetBasename(ctx, "", 0));
  EXPECT_EQ((size_t)0, ctx->basename_len);
  EXPECT_NE(nullptr, ctx->basename_hash);
}
TEST_F(Epid11VerifierTest, SetBasenameResetsBasenameGivenNullBasename) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr, this->kVerifierPrecompStr);
  Epid11VerifierCtx* ctx = verifier;
  auto& basename = this->kBsn0;
  THROW_ON_EPIDERR(
      Epid11VerifierSetBasename(ctx, basename.data(), basename.size()));
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(ctx, nullptr, 0));
  EXPECT_EQ(nullptr, ctx->basename_hash);
}
}  // namespace
