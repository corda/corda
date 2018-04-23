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
* \brief Epid11Verify unit tests.
*/

#include "gtest/gtest.h"

extern "C" {
#include "epid/verifier/1.1/api.h"
#include "epid/common/src/endian_convert.h"
}

#include "epid/verifier/1.1/unittests/verifier-testhelper.h"
#include "epid/common-testhelper/1.1/verifier_wrapper-testhelper.h"
#include "epid/common-testhelper/errors-testhelper.h"

namespace {

TEST_F(Epid11VerifierTest, VerifyFailsGivenNullParameters) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  auto sig = this->kSigGrpXMember0Sha256RandbaseMsg0;
  auto msg = this->kMsg0;

  EXPECT_EQ(kEpidBadArgErr,
            Epid11Verify(nullptr, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
  EXPECT_EQ(kEpidBadArgErr, Epid11Verify(verifier, nullptr, sig.size(),
                                         msg.data(), msg.size()));
  EXPECT_EQ(kEpidBadArgErr,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), nullptr, msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyFailsGivenSigLenTooShortForRlCount) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  Epid11VerifierSetSigRl(verifier, (Epid11SigRl const*)this->kSigRl.data(),
                         this->kSigRl.size());
  auto sig = this->kSigGrpXMember0Sha256RandbaseMsg0;
  auto n2 = ntohl(((Epid11SigRl const*)this->kSigRl.data())->n2.data);
  sig.resize(sizeof(Epid11Signature) +
             (n2 - 2) * sizeof(((Epid11Signature*)0)->sigma));
  auto msg = this->kMsg0;

  EXPECT_EQ(kEpidBadArgErr,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyFailsGivenSigLenTooLongForRlCount) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  Epid11VerifierSetSigRl(verifier, (Epid11SigRl const*)this->kSigRl.data(),
                         this->kSigRl.size());
  auto sig = this->kSigGrpXMember0Sha256RandbaseMsg0;
  auto n2 = ntohl(((Epid11SigRl const*)this->kSigRl.data())->n2.data);
  sig.resize(sizeof(Epid11Signature) +
             n2 * sizeof(((Epid11Signature*)0)->sigma));
  auto msg = this->kMsg0;

  EXPECT_EQ(kEpidBadArgErr,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

#if (SIZE_MAX <= 0xFFFFFFFF)  // When size_t value is 32 bit or lower
TEST_F(Epid11VerifierTest, VerifyFailsGivenRlCountTooBig) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  Epid11VerifierSetSigRl(verifier, (Epid11SigRl const*)this->kSigRl.data(),
                         this->kSigRl.size());
  auto sig = this->kSigGrpXMember0Sha256RandbaseMsg0;
  uint32_t n2 = SIZE_MAX / sizeof(Epid11NrProof) + 1;
  uint32_t n2_ = ntohl(n2);
  Epid11Signature* sig_struct = (Epid11Signature*)sig.data();
  sig_struct->n2 = *(OctStr32*)&n2_;
  sig.resize(sizeof(Epid11Signature) + (n2 - 1) * sizeof(Epid11NrProof));
  auto msg = this->kMsg0;

  EXPECT_EQ(kEpidBadArgErr,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}
#endif

/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 1 - We use the following variables T1, T2, R1, R2,
//                  t1, t2 (elements of G1), R4, t3 (elements of GT),
//                  B, K, R3, t5 (elements of G3), c, sx, sy, sa, sb,
//                  salpha, sbeta, nc, nc', nsx, syalpha,
//                  t4 (256-bit big integers), nd (80-bit big integer),
//                  and sf (600-bit big integer).
// This Step is not testable

/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 2 - The verifier reads the verifier pre-computation
//                  blob (gid, e12, e22, e2w) from its storage.
//                  Refer to Section 3.4 for the computation of
//                  these values.
// This Step is not testable

/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 3 - The verifier verifies gid in the public key,
//                  PRIV-RL, and SIG-RL (if provided) and the verifier
//                  pre-computation blob all match.
// This step tested with SetPrivRl, SetSigRl and ReadPrecomp functions tests
/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 4 - The verifier verifies the signatures of PRIV-RL,
//                  SIG-RL (if provided), and Group-RL (if provided)
//                  using IVK.
// This Step is not testable

/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 5 - If Group-RL is provided as input, the verifier
//                  verifies that gid has not been revoked, i.e.,
//                  gid does not match any entry in Group-RL.

TEST_F(Epid11VerifierTest, VerifyRejectsFromGroupRlSingleEntry) {
  auto& pub_key = this->kPubKeyStr;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& grp_rl = this->kGrpRlRevokedGrpXSingleEntry;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;

  Epid11VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(Epid11VerifierSetGroupRl(
      verifier, (Epid11GroupRl const*)grp_rl.data(), grp_rl.size()));
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigRevokedInGroupRl,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyRejectsFromGroupRlFirstEntry) {
  auto& pub_key = this->kPubKeyStr;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& grp_rl = this->kGrpRlRevokedGrpXFirstEntry;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;

  Epid11VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(Epid11VerifierSetGroupRl(
      verifier, (Epid11GroupRl const*)grp_rl.data(), grp_rl.size()));
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigRevokedInGroupRl,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyRejectsFromGroupRlMiddleEntry) {
  auto& pub_key = this->kPubKeyStr;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& grp_rl = this->kGrpRlRevokedGrpXMiddleEntry;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;

  Epid11VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(Epid11VerifierSetGroupRl(
      verifier, (Epid11GroupRl const*)grp_rl.data(), grp_rl.size()));
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigRevokedInGroupRl,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyRejectsFromGroupRlLastEntry) {
  auto& pub_key = this->kPubKeyStr;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& grp_rl = this->kGrpRlRevokedGrpXLastEntry;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;

  Epid11VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(Epid11VerifierSetGroupRl(
      verifier, (Epid11GroupRl const*)grp_rl.data(), grp_rl.size()));
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigRevokedInGroupRl,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 6 - If SIG-RL is provided as input, the verifier
//                  verifies that RLver and n2 values in s match with
//                  the values in SIG-RL. If SIG-RL is not provided
//                  as input, but the input signature is a not basic
//                  signature, the verifier aborts and outputs false.

TEST_F(Epid11VerifierTest, VerifyFailsOnSigRlverNotMatchSigRlRlver) {
  // The verifier verifies that RLver in Sigma and in SigRL
  // match. If mismatch, abort and output "operation failed".
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  auto msg = this->kMsg0;
  auto bsn = this->kBsn0;
  auto sig_rl = this->kGrpXSigRlMember0Bsn0Msg0SingleEntry;
  auto sig_rl_size = sig_rl.size();
  auto sig = this->kGrpXSigRlMember0Bsn0Msg0FirstEntry;
  Epid11SigRl sig_rl_wrong_ver = *(Epid11SigRl const*)sig_rl.data();
  sig_rl_wrong_ver.version.data[0]++;
  THROW_ON_EPIDERR(
      Epid11VerifierSetSigRl(verifier, &sig_rl_wrong_ver, sig_rl_size));
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidBadArgErr,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyFailsOnSigN2NotMatchSigRlN2) {
  // The verifier verifies that n2 in Sigma and in SigRL
  // match. If mismatch, abort and output "operation failed".
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  auto msg = this->kMsg0;
  auto bsn = this->kBsn0;
  auto sig_rl = this->kGrpXSigRlMember0Bsn0Msg0MiddleEntry;
  auto sig = this->kSigGrpXMember0Sha256Bsn0Msg0SingleEntry;
  THROW_ON_EPIDERR(Epid11VerifierSetSigRl(
      verifier, (Epid11SigRl const*)sig_rl.data(), sig_rl.size()));
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidBadArgErr,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyFailsSigIsNotBasicAndSigRlIsNotProvided) {
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  auto msg = this->kMsg0;
  auto bsn = this->kBsn0;
  auto sig = this->kSigGrpXMember0Sha256Bsn0Msg0ThreeEntry;
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidBadArgErr,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 7 - The verifier verifies that G3.isIdentity(B) is false.
//
TEST_F(Epid11VerifierTest, VerifyRejectsIdentityB) {
  auto& pub_key = this->kPubKeyStr;
  Epid11Signature sig = {0};
  sig.sigma0 =
      *(Epid11BasicSignature*)(this->kSigGrpXMember0Sha256Bsn0Msg0.data());
  size_t sig_len = this->kSigGrpXMember0Sha256Bsn0Msg0.size();
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  memset(&sig.sigma0.B, 0, sizeof(sig.sigma0.B));

  Epid11VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigInvalid,
            Epid11Verify(verifier, &sig, sig_len, msg.data(), msg.size()));
}

/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 8 - If bsnSize = 0, the verifier verifies G3.inGroup(B) = true.
//
TEST_F(Epid11VerifierTest, VerifyRejectsBNotInG3) {
  auto& pub_key = this->kPubKeyStr;
  Epid11Signature sig = {0};
  sig.sigma0 =
      *(Epid11BasicSignature*)(this->kSigGrpXMember0Sha256RandbaseMsg0.data());
  size_t sig_len = this->kSigGrpXMember0Sha256RandbaseMsg0.size();
  auto& msg = this->kMsg0;
  sig.sigma0.B.x.data.data[0] = 0xEE;

  Epid11VerifierCtxObj verifier(pub_key);
  EXPECT_EQ(kEpidSigInvalid,
            Epid11Verify(verifier, &sig, sig_len, msg.data(), msg.size()));
}

/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 9 - If bsnSize > 0, the verifier verifies B = G3.hash(bsn).
//
TEST_F(Epid11VerifierTest, VerifyRejectsBNotMatchingBasename) {
  auto& pub_key = this->kPubKeyStr;
  Epid11Signature sig = {0};
  sig.sigma0 =
      *(Epid11BasicSignature*)(this->kSigGrpXMember0Sha256Bsn0Msg0.data());
  size_t sig_len = this->kSigGrpXMember0Sha256Bsn0Msg0.size();
  auto msg = this->kMsg0;
  auto bsn = this->kBsn0;
  bsn.push_back('x');

  Epid11VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigInvalid,
            Epid11Verify(verifier, &sig, sig_len, msg.data(), msg.size()));
}

/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 10 - The verifier verifies G3.inGroup(K) = true.
//
TEST_F(Epid11VerifierTest, VerifyRejectsKNotInG3) {
  auto& pub_key = this->kPubKeyStr;
  Epid11Signature sig = {0};
  sig.sigma0 =
      *(Epid11BasicSignature*)(this->kSigGrpXMember0Sha256RandbaseMsg0.data());
  size_t sig_len = this->kSigGrpXMember0Sha256RandbaseMsg0.size();
  auto& msg = this->kMsg0;
  sig.sigma0.K.x.data.data[0] = 0xEE;

  Epid11VerifierCtxObj verifier(pub_key);
  EXPECT_EQ(kEpidSigInvalid,
            Epid11Verify(verifier, &sig, sig_len, msg.data(), msg.size()));
}

/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 11 - The verifier verifies G1.inGroup(T1) = true.
//
TEST_F(Epid11VerifierTest, VerifyRejectsT1NotInG1) {
  auto& pub_key = this->kPubKeyStr;
  Epid11Signature sig = {0};
  sig.sigma0 =
      *(Epid11BasicSignature*)(this->kSigGrpXMember0Sha256RandbaseMsg0.data());
  size_t sig_len = this->kSigGrpXMember0Sha256RandbaseMsg0.size();
  auto& msg = this->kMsg0;
  sig.sigma0.T1.x.data.data[0] = 0xEE;

  Epid11VerifierCtxObj verifier(pub_key);
  EXPECT_EQ(kEpidSigInvalid,
            Epid11Verify(verifier, &sig, sig_len, msg.data(), msg.size()));
}

/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 12 - The verifier verifies G1.inGroup(T2) = true.
//
TEST_F(Epid11VerifierTest, VerifyRejectsT2NotInG1) {
  auto& pub_key = this->kPubKeyStr;
  Epid11Signature sig = {0};
  sig.sigma0 =
      *(Epid11BasicSignature*)(this->kSigGrpXMember0Sha256RandbaseMsg0.data());
  size_t sig_len = this->kSigGrpXMember0Sha256RandbaseMsg0.size();
  auto& msg = this->kMsg0;
  sig.sigma0.T2.x.data.data[0] = 0xEE;

  Epid11VerifierCtxObj verifier(pub_key);
  EXPECT_EQ(kEpidSigInvalid,
            Epid11Verify(verifier, &sig, sig_len, msg.data(), msg.size()));
}

/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 13 - The verifier verifies sx, sy, sa, sb, salpha, sbeta
//                   in [0, p-1].
//
TEST_F(Epid11VerifierTest, VerifyRejectsSxNotInFp) {
  auto& pub_key = this->kPubKeyStr;
  Epid11Signature sig = {0};
  sig.sigma0 =
      *(Epid11BasicSignature*)(this->kSigGrpXMember0Sha256RandbaseMsg0.data());
  size_t sig_len = this->kSigGrpXMember0Sha256RandbaseMsg0.size();
  auto& msg = this->kMsg0;
  sig.sigma0.sx.data.data[0] = 0xEE;

  Epid11VerifierCtxObj verifier(pub_key);
  EXPECT_EQ(kEpidSigInvalid,
            Epid11Verify(verifier, &sig, sig_len, msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyRejectsSyNotInFp) {
  auto& pub_key = this->kPubKeyStr;
  Epid11Signature sig = {0};
  sig.sigma0 =
      *(Epid11BasicSignature*)(this->kSigGrpXMember0Sha256RandbaseMsg0.data());
  size_t sig_len = this->kSigGrpXMember0Sha256RandbaseMsg0.size();
  auto& msg = this->kMsg0;
  sig.sigma0.sy.data.data[0] = 0xEE;

  Epid11VerifierCtxObj verifier(pub_key);
  EXPECT_EQ(kEpidSigInvalid,
            Epid11Verify(verifier, &sig, sig_len, msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyRejectsSaNotInFp) {
  auto& pub_key = this->kPubKeyStr;
  Epid11Signature sig = {0};
  sig.sigma0 =
      *(Epid11BasicSignature*)(this->kSigGrpXMember0Sha256RandbaseMsg0.data());
  size_t sig_len = this->kSigGrpXMember0Sha256RandbaseMsg0.size();
  auto& msg = this->kMsg0;
  sig.sigma0.sa.data.data[0] = 0xEE;

  Epid11VerifierCtxObj verifier(pub_key);
  EXPECT_EQ(kEpidSigInvalid,
            Epid11Verify(verifier, &sig, sig_len, msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyRejectsSbNotInFp) {
  auto& pub_key = this->kPubKeyStr;
  Epid11Signature sig = {0};
  sig.sigma0 =
      *(Epid11BasicSignature*)(this->kSigGrpXMember0Sha256RandbaseMsg0.data());
  size_t sig_len = this->kSigGrpXMember0Sha256RandbaseMsg0.size();
  auto& msg = this->kMsg0;
  sig.sigma0.sb.data.data[0] = 0xEE;

  Epid11VerifierCtxObj verifier(pub_key);
  EXPECT_EQ(kEpidSigInvalid,
            Epid11Verify(verifier, &sig, sig_len, msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyRejectsSalphaNotInFp) {
  auto& pub_key = this->kPubKeyStr;
  Epid11Signature sig = {0};
  sig.sigma0 =
      *(Epid11BasicSignature*)(this->kSigGrpXMember0Sha256RandbaseMsg0.data());
  size_t sig_len = this->kSigGrpXMember0Sha256RandbaseMsg0.size();
  auto& msg = this->kMsg0;
  sig.sigma0.salpha.data.data[0] = 0xEE;

  Epid11VerifierCtxObj verifier(pub_key);
  EXPECT_EQ(kEpidSigInvalid,
            Epid11Verify(verifier, &sig, sig_len, msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyRejectsSbetaNotInFp) {
  auto& pub_key = this->kPubKeyStr;
  Epid11Signature sig = {0};
  sig.sigma0 =
      *(Epid11BasicSignature*)(this->kSigGrpXMember0Sha256RandbaseMsg0.data());
  size_t sig_len = this->kSigGrpXMember0Sha256RandbaseMsg0.size();
  auto& msg = this->kMsg0;
  sig.sigma0.sbeta.data.data[0] = 0xEE;

  Epid11VerifierCtxObj verifier(pub_key);
  EXPECT_EQ(kEpidSigInvalid,
            Epid11Verify(verifier, &sig, sig_len, msg.data(), msg.size()));
}

/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 14 - The verifier verifies that sf is an (at-most) 593-bit
//                   unsigned integer, in other words, sf < 2^593.
//
TEST_F(Epid11VerifierTest, VerifyRejectsSfMoreThan592Bits) {
  auto& pub_key = this->kPubKeyStr;
  Epid11Signature sig = {0};
  sig.sigma0 =
      *(Epid11BasicSignature*)(this->kSigGrpXMember0Sha256RandbaseMsg0.data());
  size_t sig_len = this->kSigGrpXMember0Sha256RandbaseMsg0.size();
  auto& msg = this->kMsg0;
  memset(&sig.sigma0.sf, 0, sizeof(sig.sigma0.sf));
  sig.sigma0.sf.data[593 / CHAR_BIT] = 1 << ((593 % CHAR_BIT) - 1);

  Epid11VerifierCtxObj verifier(pub_key);
  EXPECT_EQ(kEpidSigInvalid,
            Epid11Verify(verifier, &sig, sig_len, msg.data(), msg.size()));
}

/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 15 - The verifier computes nc = (-c) mod p.
//   4.1.2 step 16 - The verifier computes nc' = (-c) mod p'.
//   4.1.2 step 17 - The verifier computes nsx = (-sx) mod p.
//   4.1.2 step 18 - The verifier computes syalpha = (sy + salpha) mod p.
//   4.1.2 step 19 - The verifier computes R1 = G1.multiexp(h1, sa, h2, sb, T2,
//   nc).
//   4.1.2 step 20 - The verifier computes R2 = G1.multiexp(h1, salpha, h2,
//   sbeta, T2, nsx).
//   4.1.2 step 21 - The verifier computes R3 = G3.multiexp(B, sf, K, nc').
//   4.1.2 step 22 - The verifier computes t1 = G1.multiexp(T1, nsx, g1, c).
//   4.1.2 step 23 - The verifier computes t2 = G1.exp(T1, nc).
//   4.1.2 step 24 - The verifier computes R4 = pairing(t1, g2).
//   4.1.2 step 25 - The verifier computes t3 = pairing(t2, w).
//   4.1.2 step 26 - The verifier computes R4 = GT.mul(R4, t3).
//   4.1.2 step 27 - The verifier compute t3 = GT.multiexp(e12, sf, e22,
//                   syalpha, e2w, sa).
//   4.1.2 step 28 - The verifier compute R4 = GT.mul(R4, t3).
//   4.1.2 step 29 - The verifier compute t4 = Hash(p || g1 || g2 || g3
//                                                  || h1 || h2 || w || B || K
//                                                  || T1 || T2 || R1 || R2
//                                                  || R3 || R4).
// These steps are not testable

/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 30 - The verifier verifies c = H(t4 || nd || mSize || m).
// It is not  practical to test all inputs to this hash
TEST_F(Epid11VerifierTest, VerifyRejectsSigWithMismatchedMsg) {
  auto& pub_key = this->kPubKeyStr;
  auto& sig = this->kSigGrpXMember0Sha256RandbaseMsg0;
  size_t sig_len = this->kSigGrpXMember0Sha256RandbaseMsg0.size();
  auto msg = this->kMsg0;
  msg.push_back('x');

  Epid11VerifierCtxObj verifier(pub_key);
  EXPECT_EQ(kEpidSigInvalid,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(), sig_len,
                         msg.data(), msg.size()));
}

/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 31 - For i = 0, ..., n1-1, the verifier computes
//                   t5 = G3.exp(B, f[i]) and verifies that
//                   G3.isEqual(t5, K) = false.
//
TEST_F(Epid11VerifierTest, VerifyRejectsSigFromPrivRlSingleEntry) {
  auto& pub_key = this->kPubKeyStr;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& priv_rl = this->kGrpXPrivRlSingleEntry;
  auto& sig = this->kSigGrpXRevokedPrivKey000Sha256Bsn0Msg0;
  Epid11VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(Epid11VerifierSetPrivRl(
      verifier, (Epid11PrivRl const*)priv_rl.data(), priv_rl.size()));
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigRevokedInPrivRl,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyRejectsSigFromPrivRlFirstEntry) {
  auto& pub_key = this->kPubKeyStr;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig = this->kSigGrpXRevokedPrivKey000Sha256Bsn0Msg0;
  Epid11VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(Epid11VerifierSetPrivRl(
      verifier, (Epid11PrivRl const*)priv_rl.data(), priv_rl.size()));
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigRevokedInPrivRl,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyRejectsSigFromPrivRlMiddleEntry) {
  auto& pub_key = this->kPubKeyStr;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig = this->kSigGrpXRevokedPrivKey001Sha256Bsn0Msg0;
  Epid11VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(Epid11VerifierSetPrivRl(
      verifier, (Epid11PrivRl const*)priv_rl.data(), priv_rl.size()));
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigRevokedInPrivRl,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyRejectsSigFromPrivRlLastEntry) {
  auto& pub_key = this->kPubKeyStr;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig = this->kSigGrpXRevokedPrivKey002Sha256Bsn0Msg0;
  Epid11VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(Epid11VerifierSetPrivRl(
      verifier, (Epid11PrivRl const*)priv_rl.data(), priv_rl.size()));
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigRevokedInPrivRl,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 32 - For i = 0, ..., n2-1, the verifier verifies
//                   nr-verify(B, K, B[i], K[i], s[i]) = true.
//                   The details of nr-verify will be given in the
//                   next subsection.

TEST_F(Epid11VerifierTest, VerifyRejectsSigFromSigRlSingleEntry) {
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& sig_rl = this->kGrpXSigRlMember0Bsn0Msg0SingleEntry;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0SingleEntry;
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  THROW_ON_EPIDERR(Epid11VerifierSetSigRl(
      verifier, (Epid11SigRl const*)sig_rl.data(), sig_rl.size()));
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigRevokedInSigRl,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyRejectsSigFromSigRlFirstEntry) {
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& sig_rl = this->kGrpXSigRlMember0Bsn0Msg0FirstEntry;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0ThreeEntry;
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  THROW_ON_EPIDERR(Epid11VerifierSetSigRl(
      verifier, (Epid11SigRl const*)sig_rl.data(), sig_rl.size()));
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigRevokedInSigRl,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyRejectsSigFromSigRlMiddleEntry) {
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& sig_rl = this->kGrpXSigRlMember0Bsn0Msg0MiddleEntry;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0ThreeEntry;
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  THROW_ON_EPIDERR(Epid11VerifierSetSigRl(
      verifier, (Epid11SigRl const*)sig_rl.data(), sig_rl.size()));
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigRevokedInSigRl,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyRejectsSigFromSigRlLastEntry) {
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& sig_rl = this->kGrpXSigRlMember0Bsn0Msg0LastEntry;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0ThreeEntry;
  Epid11VerifierCtxObj verifier(this->kPubKeyStr);
  THROW_ON_EPIDERR(Epid11VerifierSetSigRl(
      verifier, (Epid11SigRl const*)sig_rl.data(), sig_rl.size()));
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigRevokedInSigRl,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 33 - If all the above verifications succeed, the
//                   verifier outputs true. If any of the above
//                   verifications fails, the verifier immediately
//                   aborts and outputs false.

TEST_F(Epid11VerifierTest, VerifyAcceptsSigWithBaseNameNoRl) {
  auto& pub_key = this->kPubKeyStr;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;

  Epid11VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigValid,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyAcceptsSigWithBaseNameAllRl) {
  auto& pub_key = this->kPubKeyStr;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& grp_rl = this->kGroupRlEmptyBuf;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig_rl = this->kEmptySigRl;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;

  Epid11VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(Epid11VerifierSetGroupRl(
      verifier, (Epid11GroupRl const*)grp_rl.data(), grp_rl.size()));
  THROW_ON_EPIDERR(Epid11VerifierSetPrivRl(
      verifier, (Epid11PrivRl const*)priv_rl.data(), priv_rl.size()));
  THROW_ON_EPIDERR(Epid11VerifierSetSigRl(
      verifier, (Epid11SigRl const*)sig_rl.data(), sig_rl.size()));
  THROW_ON_EPIDERR(Epid11VerifierSetBasename(verifier, bsn.data(), bsn.size()));

  EXPECT_EQ(kEpidSigValid,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyAcceptsSigWithRandomBaseNameNoRl) {
  auto& pub_key = this->kPubKeyStr;
  auto& sig = this->kSigGrpXMember0Sha256RandbaseMsg0;
  auto& msg = this->kMsg0;

  Epid11VerifierCtxObj verifier(pub_key);
  EXPECT_EQ(kEpidSigValid,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

TEST_F(Epid11VerifierTest, VerifyAcceptsSigWithRandomBaseNameAllRl) {
  auto& pub_key = this->kPubKeyStr;
  auto& msg = this->kMsg0;
  auto& grp_rl = this->kGroupRlEmptyBuf;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig_rl = this->kEmptySigRl;
  auto& sig = this->kSigGrpXMember0Sha256RandbaseMsg0;

  Epid11VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(Epid11VerifierSetGroupRl(
      verifier, (Epid11GroupRl const*)grp_rl.data(), grp_rl.size()));
  THROW_ON_EPIDERR(Epid11VerifierSetPrivRl(
      verifier, (Epid11PrivRl const*)priv_rl.data(), priv_rl.size()));
  THROW_ON_EPIDERR(Epid11VerifierSetSigRl(
      verifier, (Epid11SigRl const*)sig_rl.data(), sig_rl.size()));
  EXPECT_EQ(kEpidSigValid,
            Epid11Verify(verifier, (Epid11Signature const*)sig.data(),
                         sig.size(), msg.data(), msg.size()));
}

}  // namespace
