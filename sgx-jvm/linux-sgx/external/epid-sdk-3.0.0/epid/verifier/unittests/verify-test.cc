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
 * \brief Verify unit tests.
 */

#include "gtest/gtest.h"

extern "C" {
#include "epid/verifier/api.h"
#include "epid/common/src/endian_convert.h"
}

#include "epid/verifier/unittests/verifier-testhelper.h"
#include "epid/common-testhelper/verifier_wrapper-testhelper.h"
#include "epid/common-testhelper/errors-testhelper.h"

namespace {

/////////////////////////////////////////////////////////////////////////
// Simple Errors

TEST_F(EpidVerifierTest, VerifyFailsGivenNullParameters) {
  VerifierCtxObj verifier(this->kGrp01Key);
  auto& sig = this->kSigGrp01Member0Sha256RandombaseTest0;
  auto& msg = this->kTest0;

  EXPECT_EQ(kEpidBadArgErr,
            EpidVerify(nullptr, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerify(verifier, nullptr, sig.size(), msg.data(), msg.size()));
  EXPECT_EQ(kEpidBadArgErr,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       nullptr, msg.size()));
}

TEST_F(EpidVerifierTest, VerifyFailsGivenSigLenTooShortForRlCount) {
  VerifierCtxObj verifier(this->kGrp01Key);
  EpidVerifierSetSigRl(verifier, (SigRl const*)this->kGrp01SigRl.data(),
                       this->kGrp01SigRl.size());
  auto sig = this->kSigGrp01Member0Sha256RandombaseTest0;
  auto n2 = this->kGrp01SigRlN2;
  sig.resize(sizeof(EpidSignature) +
             (n2 - 2) * sizeof(((EpidSignature*)0)->sigma));
  auto& msg = this->kTest0;

  EXPECT_EQ(kEpidBadArgErr,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyFailsGivenSigLenTooLongForRlCount) {
  VerifierCtxObj verifier(this->kGrp01Key);
  EpidVerifierSetSigRl(verifier, (SigRl const*)this->kGrp01SigRl.data(),
                       this->kGrp01SigRl.size());
  auto sig = this->kSigGrp01Member0Sha256RandombaseTest0;
  auto n2 = this->kGrp01SigRlN2;
  sig.resize(sizeof(EpidSignature) + n2 * sizeof(((EpidSignature*)0)->sigma));
  auto& msg = this->kTest0;

  EXPECT_EQ(kEpidBadArgErr,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

#if (SIZE_MAX <= 0xFFFFFFFF)  // When size_t value is 32 bit or lower
TEST_F(EpidVerifierTest, VerifyFailsGivenRlCountTooBig) {
  VerifierCtxObj verifier(this->kGrp01Key);
  EpidVerifierSetSigRl(verifier, (SigRl const*)this->kGrp01SigRl.data(),
                       this->kGrp01SigRl.size());
  auto sig = this->kSigGrp01Member0Sha256RandombaseTest0;
  uint32_t n2 = SIZE_MAX / sizeof(NrProof) + 1;
  uint32_t n2_ = ntohl(n2);
  EpidSignature* sig_struct = (EpidSignature*)sig.data();
  sig_struct->n2 = *(OctStr32*)&n2_;
  sig.resize(sizeof(EpidSignature) + (n2 - 1) * sizeof(NrProof));
  auto& msg = this->kTest0;

  EXPECT_EQ(kEpidBadArgErr,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}
#endif

/////////////////////////////////////////////////////////////////////
//
//   4.1.2 step 1 - The verifier reads the pre-computed (e12, e22, e2w, eg12).
//                  Refer to Section 3.6 for the computation of these values.
// This Step is not testable

/////////////////////////////////////////////////////////////////////
// Non-Revocation List Reject
//   4.1.2 step 2 - The verifier verifies the basic signature Sigma0 as
//                  follows:

TEST_F(EpidVerifierTest, VerifyRejectsSigWithBNotInG1) {
  // * 4.1.2 step 2.a - The verifier verifies G1.inGroup(B) = true.
  // result must be kEpidSigInvalid
  VerifierCtxObj verifier(this->kGrp01Key);
  auto& msg = this->kTest0;
  size_t size = this->kSigGrp01Member0Sha256RandombaseTest0.size();
  EpidSignature sig = *(
      const EpidSignature*)(this->kSigGrp01Member0Sha256RandombaseTest0.data());
  sig.sigma0.B.x.data.data[31]++;
  EXPECT_EQ(kEpidSigInvalid,
            EpidVerify(verifier, &sig, size, msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigWithBIdentityOfG1) {
  // * 4.1.2 step 2.b - The verifier verifies that G1.isIdentity(B) is false.
  // result must be kEpidSigInvalid
  VerifierCtxObj verifier(this->kGrp01Key);
  auto& msg = this->kTest0;

  EpidSignature sig = *(
      const EpidSignature*)(this->kSigGrp01Member0Sha256RandombaseTest0.data());
  sig.sigma0.B = this->kG1IdentityStr;
  size_t size = this->kSigGrp01Member0Sha256RandombaseTest0.size();
  EXPECT_EQ(kEpidSigInvalid,
            EpidVerify(verifier, &sig, size, msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigWithDiffBaseNameSameHashAlg) {
  // * 4.1.2 step 2.c - If bsn is provided, the verifier verifies
  //                    B = G1.hash(bsn).
  // result must be kEpidSigInvalid
  auto& pub_key = this->kGrpXKey;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBasename1;

  VerifierCtxObj verifier(pub_key);

  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigInvalid,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigWithSameBaseNameDiffHashAlg) {
  // * 4.1.2 step 2.c - If bsn is provided, the verifier verifies
  //                    B = G1.hash(bsn).
  // result must be kEpidSigInvalid
  auto& pub_key = this->kGrpXKey;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha512));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigInvalid,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigWithKNotInG1) {
  // * 4.1.2 step 2.d - The verifier verifies G1.inGroup(K) = true.
  // result must be kEpidSigInvalid
  VerifierCtxObj verifier(this->kGrp01Key);
  auto& msg = this->kTest0;

  EpidSignature sig = *(
      const EpidSignature*)(this->kSigGrp01Member0Sha256RandombaseTest0.data());
  sig.sigma0.K.x.data.data[31]++;
  size_t size = this->kSigGrp01Member0Sha256RandombaseTest0.size();
  EXPECT_EQ(kEpidSigInvalid,
            EpidVerify(verifier, &sig, size, msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigWithTNotInG1) {
  // * 4.1.2 step 2.e - The verifier verifies G1.inGroup(T) = true.
  // result must be kEpidSigInvalid
  VerifierCtxObj verifier(this->kGrp01Key);
  auto& msg = this->kTest0;

  EpidSignature sig = *(
      const EpidSignature*)(this->kSigGrp01Member0Sha256RandombaseTest0.data());
  sig.sigma0.T.x.data.data[31]++;
  size_t size = this->kSigGrp01Member0Sha256RandombaseTest0.size();
  EXPECT_EQ(kEpidSigInvalid,
            EpidVerify(verifier, &sig, size, msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigWithCNotInRange) {
  // * 4.1.2 step 2.f - The verifier verifies c, sx, sf, sa, sb in [0, p-1].
  // result must be kEpidSigInvalid
  VerifierCtxObj verifier(this->kGrp01Key);
  auto& msg = this->kTest0;

  EpidSignature sig = *(
      const EpidSignature*)(this->kSigGrp01Member0Sha256RandombaseTest0.data());
  sig.sigma0.c.data = this->kParamsStr.p.data;
  size_t size = this->kSigGrp01Member0Sha256RandombaseTest0.size();
  EXPECT_EQ(kEpidSigInvalid,
            EpidVerify(verifier, &sig, size, msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigWithSxNotInRange) {
  // * 4.1.2 step 2.f - The verifier verifies c, sx, sf, sa, sb in [0, p-1].
  // result must be kEpidSigInvalid
  VerifierCtxObj verifier(this->kGrp01Key);
  auto& msg = this->kTest0;

  EpidSignature sig = *(
      const EpidSignature*)(this->kSigGrp01Member0Sha256RandombaseTest0.data());
  sig.sigma0.sx.data = this->kParamsStr.p.data;
  size_t size = this->kSigGrp01Member0Sha256RandombaseTest0.size();
  EXPECT_EQ(kEpidSigInvalid,
            EpidVerify(verifier, &sig, size, msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigWithSfNotInRange) {
  // * 4.1.2 step 2.f - The verifier verifies c, sx, sf, sa, sb in [0, p-1].
  // result must be kEpidSigInvalid
  VerifierCtxObj verifier(this->kGrp01Key);
  auto& msg = this->kTest0;

  EpidSignature sig = *(
      const EpidSignature*)(this->kSigGrp01Member0Sha256RandombaseTest0.data());
  sig.sigma0.sf.data = this->kParamsStr.p.data;
  size_t size = this->kSigGrp01Member0Sha256RandombaseTest0.size();
  EXPECT_EQ(kEpidSigInvalid,
            EpidVerify(verifier, &sig, size, msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigWithSaNotInRange) {
  // * 4.1.2 step 2.f - The verifier verifies c, sx, sf, sa, sb in [0, p-1].
  // result must be kEpidSigInvalid
  VerifierCtxObj verifier(this->kGrp01Key);
  auto& msg = this->kTest0;

  EpidSignature sig = *(
      const EpidSignature*)(this->kSigGrp01Member0Sha256RandombaseTest0.data());
  sig.sigma0.sa.data = this->kParamsStr.p.data;
  size_t size = this->kSigGrp01Member0Sha256RandombaseTest0.size();
  EXPECT_EQ(kEpidSigInvalid,
            EpidVerify(verifier, &sig, size, msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigWithSbNotInRange) {
  // * 4.1.2 step 2.f - The verifier verifies c, sx, sf, sa, sb in [0, p-1].
  // result must be kEpidSigInvalid
  VerifierCtxObj verifier(this->kGrp01Key);
  auto& msg = this->kTest0;

  EpidSignature sig = *(
      const EpidSignature*)(this->kSigGrp01Member0Sha256RandombaseTest0.data());
  sig.sigma0.sb.data = this->kParamsStr.p.data;
  size_t size = this->kSigGrp01Member0Sha256RandombaseTest0.size();
  EXPECT_EQ(kEpidSigInvalid,
            EpidVerify(verifier, &sig, size, msg.data(), msg.size()));
}

//   4.1.2 step 2.g - The verifier computes nc = (-c) mod p.
// This Step is not testable

//   4.1.2 step 2.h - The verifier computes nsx = (-sx) mod p.
// This Step is not testable

//   4.1.2 step 2.i - The verifier computes R1 = G1.multiExp(B, sf, K, nc).
// This Step is not testable

//   4.1.2 step 2.j - The verifier computes t1 = G2.multiExp(g2, nsx, w, nc).
// This Step is not testable

//   4.1.2 step 2.k - The verifier computes R2 = pairing(T, t1).
// This Step is not testable

//   4.1.2 step 2.l - The verifier compute t2 = GT.multiExp(e12, sf, e22, sb,
//                    e2w, sa, eg12, c).
// This Step is not testable

//   4.1.2 step 2.m - The verifier compute R2 = GT.mul(R2, t2).
// This Step is not testable

//   4.1.2 step 2.n - The verifier compute t3 = Fp.hash(p || g1 || g2 || h1
//                    || h2 || w || B || K || T || R1 || R2).
//                    Refer to Section 7.1 for hash operation over a prime
//                    field.
// This Step is not testable

TEST_F(EpidVerifierTest, VerifyRejectsSigDifferingOnlyInMsg) {
  // * 4.1.2 step 2.o - The verifier verifies c = Fp.hash(t3 || m).
  // result must be kEpidSigInvalid
  VerifierCtxObj verifier(this->kGrp01Key);
  auto& sig = this->kSigGrp01Member0Sha256RandombaseTest0;

  auto msg = this->kTest0;
  msg[0]++;
  EXPECT_EQ(kEpidSigInvalid,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigDifferingOnlyInBaseName) {
  // * 4.1.2 step 2.o - The verifier verifies c = Fp.hash(t3 || m).
  // result must be kEpidSigInvalid
  VerifierCtxObj verifier(this->kGrp01Key);

  // copy sig data to a local buffer
  auto sig_data = this->kSigGrpXMember0Sha256Bsn0Msg0;
  EpidSignature* sig = (EpidSignature*)sig_data.data();
  // simulate change to basename
  sig->sigma0.B.x.data.data[0] += 1;
  auto msg = this->kTest1;
  EXPECT_EQ(kEpidSigInvalid,
            EpidVerify(verifier, sig, sig_data.size(), msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigDifferingOnlyInGroup) {
  // * 4.1.2 step 2.o - The verifier verifies c = Fp.hash(t3 || m).
  // result must be kEpidSigInvalid
  VerifierCtxObj verifier(this->kGrp01Key);

  // copy sig data to a local buffer
  auto sig_data = this->kSigGrpXMember0Sha256Bsn0Msg0;
  EpidSignature* sig = (EpidSignature*)sig_data.data();
  // simulate change to h1
  sig->sigma0.T.x.data.data[0] += 1;
  auto msg = this->kTest1;
  EXPECT_EQ(kEpidSigInvalid,
            EpidVerify(verifier, sig, sig_data.size(), msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigDifferingOnlyInHashAlg) {
  // * 4.1.2 step 2.o - The verifier verifies c = Fp.hash(t3 || m).
  // result must be kEpidSigInvalid
  VerifierCtxObj verifier(this->kGrp01Key);
  auto& msg = this->kTest0;
  auto& sig = this->kSigGrp01Member0Sha256RandombaseTest0;

  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha512));
  EXPECT_EQ(kEpidSigInvalid,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

//   4.1.2 step 2.p - If any of the above verifications fails, the verifier
//                    aborts and outputs 1.
// This Step is an aggregate of the above steps

/////////////////////////////////////////////////////////////////////
// Group Based Revocation List Reject
//   4.1.2 step 3 - If GroupRL is provided

TEST_F(EpidVerifierTest, VerifyRejectsFromGroupRlSingleEntry) {
  // * 4.1.2 step 3.a - The verifier verifies that gid does not match any entry
  //                    in GroupRL.
  // result must be kEpidSigRevokedInGroupRl
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& grp_rl = this->kGrpRlRevokedGrpXOnlyEntry;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetGroupRl(
      verifier, (GroupRl const*)grp_rl.data(), grp_rl.size()));

  EXPECT_EQ(kEpidSigRevokedInGroupRl,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsFromGroupRlFirstEntry) {
  // * 4.1.2 step 3.a - The verifier verifies that gid does not match any entry
  //                    in GroupRL.
  // result must be kEpidSigRevokedInGroupRl
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& grp_rl = this->kGrpRlRevokedGrpXFirstEntry;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetGroupRl(
      verifier, (GroupRl const*)grp_rl.data(), grp_rl.size()));

  EXPECT_EQ(kEpidSigRevokedInGroupRl,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsFromGroupRlFirstEntryUsingIkgfData) {
  // result must be kEpidSigRevokedInGroupRl
  auto& pub_key = this->kPubKeyRevGroupIkgfStr;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& grp_rl = this->kGrpRlIkgf;
  auto& sig = this->kRevGroupSigMember0Sha256Bsn0Msg0Ikgf;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetGroupRl(
      verifier, (GroupRl const*)grp_rl.data(), grp_rl.size()));

  EXPECT_EQ(kEpidSigRevokedInGroupRl,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsFromGroupRlMiddleEntry) {
  // * 4.1.2 step 3.a - The verifier verifies that gid does not match any entry
  //                    in GroupRL.
  // result must be kEpidSigRevokedInGroupRl
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& grp_rl = this->kGrpRlRevokedGrpXMiddleEntry;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetGroupRl(
      verifier, (GroupRl const*)grp_rl.data(), grp_rl.size()));

  EXPECT_EQ(kEpidSigRevokedInGroupRl,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsFromGroupRlLastEntry) {
  // * 4.1.2 step 3.a - The verifier verifies that gid does not match any entry
  //                    in GroupRL.
  // result must be kEpidSigRevokedInGroupRl
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& grp_rl = this->kGrpRlRevokedGrpXLastEntry;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetGroupRl(
      verifier, (GroupRl const*)grp_rl.data(), grp_rl.size()));

  EXPECT_EQ(kEpidSigRevokedInGroupRl,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

//   4.1.2 step 3.b - If gid matches an entry in GroupRL, aborts and returns 2.
// This Step is an aggregate of the above steps

/////////////////////////////////////////////////////////////////////
// Private Based Revocation List Reject
//   4.1.2 step 4 - If PrivRL is provided

// * 4.1.2 step 4.a - The verifier verifies that gid in the public key and in
//                    PrivRL match. If mismatch, abort and return
//                    "operation failed".
// Not possible, checked in EpidVerifierSetPrivRl

TEST_F(EpidVerifierTest, VerifyRejectsSigFromPrivRlSingleEntry) {
  // * 4.1.2 step 4.b - For i = 0, ?, n1-1,
  //                    the verifier computes t4 =G1.exp(B, f[i])
  //                    and verifies that G1.isEqual(t4, K) = false.
  //                    A faster private-key revocation check algorithm is
  //                    provided in Section 4.5.
  // result must be kEpidSigRevokedInPrivRl
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& priv_rl = this->kGrpXPrivRlRevokedPrivKey000OnlyEntry;
  auto& sig = this->kSigGrpXRevokedPrivKey000Sha256Bsn0Msg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetPrivRl(
      verifier, (PrivRl const*)priv_rl.data(), priv_rl.size()));

  EXPECT_EQ(kEpidSigRevokedInPrivRl,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigFromPrivRlFirstEntry) {
  // * 4.1.2 step 4.b - For i = 0, ?, n1-1,
  //                    the verifier computes t4 =G1.exp(B, f[i])
  //                    and verifies that G1.isEqual(t4, K) = false.
  //                    A faster private-key revocation check algorithm is
  //                    provided in Section 4.5.
  // result must be kEpidSigRevokedInPrivRl
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig = this->kSigGrpXRevokedPrivKey000Sha256Bsn0Msg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetPrivRl(
      verifier, (PrivRl const*)priv_rl.data(), priv_rl.size()));

  EXPECT_EQ(kEpidSigRevokedInPrivRl,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigFromPrivRlFirstEntryUsingIkgfData) {
  // * 4.1.2 step 4.b - For i = 0, ?, n1-1,
  //                    the verifier computes t4 =G1.exp(B, f[i])
  //                    and verifies that G1.isEqual(t4, K) = false.
  //                    A faster private-key revocation check algorithm is
  //                    provided in Section 4.5.
  // result must be kEpidSigRevokedInPrivRl
  auto& pub_key = this->kPubKeyIkgfStr;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& priv_rl = this->kPrivRlIkgf;
  auto& sig = this->kSigRevokedPrivKeySha256Bsn0Msg0Ikgf;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetPrivRl(
      verifier, (PrivRl const*)priv_rl.data(), priv_rl.size()));

  EXPECT_EQ(kEpidSigRevokedInPrivRl,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigFromPrivRlMiddleEntry) {
  // * 4.1.2 step 4.b - For i = 0, ?, n1-1,
  //                    the verifier computes t4 =G1.exp(B, f[i])
  //                    and verifies that G1.isEqual(t4, K) = false.
  //                    A faster private-key revocation check algorithm is
  //                    provided in Section 4.5.
  // result must be kEpidSigRevokedInPrivRl
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig = this->kSigGrpXRevokedPrivKey001Sha256Bsn0Msg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetPrivRl(
      verifier, (PrivRl const*)priv_rl.data(), priv_rl.size()));

  EXPECT_EQ(kEpidSigRevokedInPrivRl,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigFromPrivRlLastEntry) {
  // * 4.1.2 step 4.b - For i = 0, ?, n1-1,
  //                    the verifier computes t4 =G1.exp(B, f[i])
  //                    and verifies that G1.isEqual(t4, K) = false.
  //                    A faster private-key revocation check algorithm is
  //                    provided in Section 4.5.
  // result must be kEpidSigRevokedInPrivRl
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig = this->kSigGrpXRevokedPrivKey002Sha256Bsn0Msg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetPrivRl(
      verifier, (PrivRl const*)priv_rl.data(), priv_rl.size()));

  EXPECT_EQ(kEpidSigRevokedInPrivRl,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyAcceptsSigFromEmptyPrivRlUsingIkgfData) {
  auto& pub_key = this->kPubKeyIkgfStr;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& priv_rl = this->kEmptyPrivRlIkgf;
  auto& sig = this->kSigMember0Sha256Bsn0Msg0NoSigRlIkgf;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetPrivRl(
      verifier, (PrivRl const*)priv_rl.data(), priv_rl.size()));

  EXPECT_EQ(kEpidNoErr, EpidVerify(verifier, (EpidSignature const*)sig.data(),
                                   sig.size(), msg.data(), msg.size()));
}

//   4.1.2 step 4.c - If the above step fails, the verifier aborts and
//                    output 3.
// This Step is an aggregate of the above steps

/////////////////////////////////////////////////////////////////////
// Signature Based Revocation List Reject
//   4.1.2 step 5 - If SigRL is provided

// * 4.1.2 step 5.a - The verifier verifies that gid in the public key and in
//                    SigRL match. If mismatch, abort and return
//                    "operation failed".
// Not possible, checked in EpidVerifierSetSigRl

TEST_F(EpidVerifierTest, VerifyFailsOnSigRlverNotMatchSigRlRlver) {
  // * 4.1.2 step 5.b - The verifier verifies that RLver in Sigma and in SigRL
  //                    match. If mismatch, abort and output "operation failed".
  // result must be "operation failed" (not kEpidSig*)
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& sig_rl = this->kGrpXSigRlVersion2;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;
  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));

  EXPECT_EQ(kEpidBadArgErr,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyFailsOnSigN2NotMatchSigRlN2) {
  // * 4.1.2 step 5.c - The verifier verifies that n2 in Sigma and in SigRL
  //                    match. If mismatch, abort and output "operation failed".
  // result must be "operation failed" (not kEpidSig*)
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& sig_rl = this->kGrpXSigRlMember0Sha256Bsn0Msg0OnlyEntry;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;
  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));

  EXPECT_EQ(kEpidBadArgErr,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigFromSigRlSingleEntry) {
  // * 4.1.2 step 5.d - For i = 0, ..., n2-1, the verifier verifies
  //                    nrVerify(B, K, B[i], K[i], Sigma[i]) = true. The details
  //                    of nrVerify() will be given in the next subsection.
  // result must be kEpidSigRevokedInSigRl
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& sig_rl = this->kGrpXSigRlMember0Sha256Bsn0Msg0OnlyEntry;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0SingleEntrySigRl;
  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));

  EXPECT_EQ(kEpidSigRevokedInSigRl,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigFromSigRlFirstEntry) {
  // * 4.1.2 step 5.d - For i = 0, ..., n2-1, the verifier verifies
  //                    nrVerify(B, K, B[i], K[i], Sigma[i]) = true. The details
  //                    of nrVerify() will be given in the next subsection.
  // result must be kEpidSigRevokedInSigRl
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& sig_rl = this->kGrpXSigRlMember0Sha256Bsn0Msg0FirstEntry;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;
  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));

  EXPECT_EQ(kEpidSigRevokedInSigRl,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigFromSigRlFirstEntryUsingIkgfData) {
  auto& pub_key = this->kPubKeyIkgfStr;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& sig_rl = this->kSigRlIkgf;
  auto& sig = this->kSigRevSigMember0Sha256Bsn0Msg0Ikgf;
  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));

  EXPECT_EQ(kEpidSigRevokedInSigRl,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigFromSigRlMiddleEntry) {
  // * 4.1.2 step 5.d - For i = 0, ..., n2-1, the verifier verifies
  //                    nrVerify(B, K, B[i], K[i], Sigma[i]) = true. The details
  //                    of nrVerify() will be given in the next subsection.
  // result must be kEpidSigRevokedInSigRl
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& sig_rl = this->kGrpXSigRlMember0Sha256Bsn0Msg0MiddleEntry;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;
  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));

  EXPECT_EQ(kEpidSigRevokedInSigRl,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigFromSigRlLastEntry) {
  // * 4.1.2 step 5.d - For i = 0, ..., n2-1, the verifier verifies
  //                    nrVerify(B, K, B[i], K[i], Sigma[i]) = true. The details
  //                    of nrVerify() will be given in the next subsection.
  // result must be kEpidSigRevokedInSigRl
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& sig_rl = this->kGrpXSigRlMember0Sha256Bsn0Msg0LastEntry;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;
  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));

  EXPECT_EQ(kEpidSigRevokedInSigRl,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest,
       RejectsSigFromNonemptySigRlGivenEmptySigRlUsingIkgfData) {
  auto& pub_key = this->kPubKeyIkgfStr;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& sig_rl = this->kEmptySigRlIkgf;
  auto& sig = this->kSigMember0Sha256Bsn0Msg0Ikgf;
  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));

  EXPECT_EQ(kEpidBadArgErr,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyAcceptsSigFromEmptySigRlUsingIkgfData) {
  auto& pub_key = this->kPubKeyIkgfStr;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& sig_rl = this->kEmptySigRlIkgf;
  auto& sig = this->kSigMember0Sha256Bsn0Msg0EmptySigRlIkgf;
  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));

  EXPECT_EQ(kEpidNoErr, EpidVerify(verifier, (EpidSignature const*)sig.data(),
                                   sig.size(), msg.data(), msg.size()));
}

//   4.1.2 step 5.e - If the above step fails, the verifier aborts and
//                    output 4.
// This Step is an aggregate of the above steps

/////////////////////////////////////////////////////////////////////
// Verifier Based Revocation List Reject
//   4.1.2 step 6 - If VerifierRL is provided

// * 4.1.2 step 6.a - The verifier verifies that gid in the public key and in
//                    VerifierRL match. If mismatch, abort and return
//                    "operation failed".
// Not possible, checked in EpidVerifierSetVerifierRl

// * 4.1.2 step 6.b - The verifier verifies that B in the signature and in
//                    VerifierRL match. If mismatch, go to step 7.
// result must be "operation failed" (not kEpidSig*)
// Not possible, checked in EpidVerifierSetVerifierRl

TEST_F(EpidVerifierTest, VerifyRejectsSigFromVerifierRlSingleEntry) {
  // * 4.1.2 step 6.c - For i = 0, ..., n4-1, the verifier verifies that
  //                    K != K[i].
  // result must be kEpidSigRevokedInVerifierRl
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& grp_rl = this->kGrpRl;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig_rl = this->kGrpXSigRl;
  auto& ver_rl = this->kGrpXBsn0VerRlSingleEntry;
  auto& sig = this->kSigGrpXVerRevokedMember0Sha256Bsn0Msg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetGroupRl(
      verifier, (GroupRl const*)grp_rl.data(), grp_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetPrivRl(
      verifier, (PrivRl const*)priv_rl.data(), priv_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetVerifierRl(
      verifier, (VerifierRl const*)ver_rl.data(), ver_rl.size()));

  EXPECT_EQ(kEpidSigRevokedInVerifierRl,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigFromVerifierRlFirstEntry) {
  // * 4.1.2 step 6.c - For i = 0, ..., n4-1, the verifier verifies that
  //                    K != K[i].
  // result must be kEpidSigRevokedInVerifierRl
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& grp_rl = this->kGrpRl;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig_rl = this->kGrpXSigRl;
  auto& ver_rl = this->kGrpXBsn0Sha256VerRl;
  auto& sig = this->kSigGrpXVerRevokedMember0Sha256Bsn0Msg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetGroupRl(
      verifier, (GroupRl const*)grp_rl.data(), grp_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetPrivRl(
      verifier, (PrivRl const*)priv_rl.data(), priv_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetVerifierRl(
      verifier, (VerifierRl const*)ver_rl.data(), ver_rl.size()));

  EXPECT_EQ(kEpidSigRevokedInVerifierRl,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigFromVerifierRlMiddleEntry) {
  // * 4.1.2 step 6.c - For i = 0, ..., n4-1, the verifier verifies that
  //                    K != K[i].
  // result must be kEpidSigRevokedInVerifierRl
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& grp_rl = this->kGrpRl;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig_rl = this->kGrpXSigRl;
  auto& ver_rl = this->kGrpXBsn0Sha256VerRl;
  auto& sig = this->kSigGrpXVerRevokedMember1Sha256Bsn0Msg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetGroupRl(
      verifier, (GroupRl const*)grp_rl.data(), grp_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetPrivRl(
      verifier, (PrivRl const*)priv_rl.data(), priv_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetVerifierRl(
      verifier, (VerifierRl const*)ver_rl.data(), ver_rl.size()));

  EXPECT_EQ(kEpidSigRevokedInVerifierRl,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyRejectsSigFromVerifierRlLastEntry) {
  // * 4.1.2 step 6.c - For i = 0, ..., n4-1, the verifier verifies that
  //                    K != K[i].
  // result must be kEpidSigRevokedInVerifierRl
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& grp_rl = this->kGrpRl;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig_rl = this->kGrpXSigRl;
  auto& ver_rl = this->kGrpXBsn0Sha256VerRl;
  auto& sig = this->kSigGrpXVerRevokedMember2Sha256Bsn0Msg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetGroupRl(
      verifier, (GroupRl const*)grp_rl.data(), grp_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetPrivRl(
      verifier, (PrivRl const*)priv_rl.data(), priv_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetVerifierRl(
      verifier, (VerifierRl const*)ver_rl.data(), ver_rl.size()));

  EXPECT_EQ(kEpidSigRevokedInVerifierRl,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

//   4.1.2 step 6.d - If the above step fails, the verifier aborts and
//                    output 5
// This Step is an aggregate of the above steps

/////////////////////////////////////////////////////////////////////
// Accept
// 4.1.2 step 7 - If all the above verifications succeed, the verifier
//                outputs 0

TEST_F(EpidVerifierTest, VerifyAcceptsSigWithBaseNameNoRlSha256) {
  auto& pub_key = this->kGrpXKey;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyAcceptsSigWithBaseNameAllRlSha256) {
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& grp_rl = this->kGrpRl;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig_rl = this->kGrpXSigRl;
  auto& ver_rl = this->kGrpXBsn0Sha256VerRl;
  auto& sig = this->kSigGrpXMember0Sha256Bsn0Msg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetGroupRl(
      verifier, (GroupRl const*)grp_rl.data(), grp_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetPrivRl(
      verifier, (PrivRl const*)priv_rl.data(), priv_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetVerifierRl(
      verifier, (VerifierRl const*)ver_rl.data(), ver_rl.size()));

  EXPECT_EQ(kEpidSigValid,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyAcceptsSigWithRandomBaseNameNoRlSha256) {
  auto& pub_key = this->kGrpXKey;
  auto& sig = this->kSigGrpXMember0Sha256RandbaseMsg0;
  auto& msg = this->kMsg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  EXPECT_EQ(kEpidSigValid,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyAcceptsSigWithRandomBaseNameAllRlSha256) {
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& grp_rl = this->kGrpRl;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig_rl = this->kGrpXSigRl;
  auto& sig = this->kSigGrpXMember0Sha256RandbaseMsg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetGroupRl(
      verifier, (GroupRl const*)grp_rl.data(), grp_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetPrivRl(
      verifier, (PrivRl const*)priv_rl.data(), priv_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));

  EXPECT_EQ(kEpidSigValid,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest,
       VerifyAcceptsSigWithRandomBaseNameAllRlSha256UsingIkgfData) {
  auto& pub_key = this->kPubKeyIkgfStr;
  auto& msg = this->kMsg0;
  auto& grp_rl = this->kGrpRlIkgf;
  auto& priv_rl = this->kPrivRlIkgf;
  auto& sig_rl = this->kSigRlIkgf;
  auto& sig = this->kSigMember0Sha256RandbaseMsg0Ikgf;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha256));
  THROW_ON_EPIDERR(EpidVerifierSetGroupRl(
      verifier, (GroupRl const*)grp_rl.data(), grp_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetPrivRl(
      verifier, (PrivRl const*)priv_rl.data(), priv_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));

  EXPECT_EQ(kEpidSigValid,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyAcceptsSigWithBaseNameAllRlSha384) {
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& grp_rl = this->kGrpRl;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig_rl = this->kGrpXSigRl;
  auto& ver_rl = this->kGrpXBsn0Sha384VerRl;
  auto& sig = this->kSigGrpXMember0Sha384Bsn0Msg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha384));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetGroupRl(
      verifier, (GroupRl const*)grp_rl.data(), grp_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetPrivRl(
      verifier, (PrivRl const*)priv_rl.data(), priv_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetVerifierRl(
      verifier, (VerifierRl const*)ver_rl.data(), ver_rl.size()));

  EXPECT_EQ(kEpidSigValid,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyAcceptsSigWithRandomBaseNameAllRlSha384) {
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& grp_rl = this->kGrpRl;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig_rl = this->kGrpXSigRl;
  auto& sig = this->kSigGrpXMember0Sha384RandbaseMsg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha384));
  THROW_ON_EPIDERR(EpidVerifierSetGroupRl(
      verifier, (GroupRl const*)grp_rl.data(), grp_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetPrivRl(
      verifier, (PrivRl const*)priv_rl.data(), priv_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));

  EXPECT_EQ(kEpidSigValid,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyAcceptsSigWithBaseNameAllRlSha512) {
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& grp_rl = this->kGrpRl;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig_rl = this->kGrpXSigRl;
  auto& ver_rl = this->kGrpXBsn0Sha512VerRl;
  auto& sig = this->kSigGrpXMember0Sha512Bsn0Msg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha512));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetGroupRl(
      verifier, (GroupRl const*)grp_rl.data(), grp_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetPrivRl(
      verifier, (PrivRl const*)priv_rl.data(), priv_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetVerifierRl(
      verifier, (VerifierRl const*)ver_rl.data(), ver_rl.size()));

  EXPECT_EQ(kEpidSigValid,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, VerifyAcceptsSigWithRandomBaseNameAllRlSha512) {
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& grp_rl = this->kGrpRl;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig_rl = this->kGrpXSigRl;
  auto& sig = this->kSigGrpXMember0Sha512RandbaseMsg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha512));
  THROW_ON_EPIDERR(EpidVerifierSetGroupRl(
      verifier, (GroupRl const*)grp_rl.data(), grp_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetPrivRl(
      verifier, (PrivRl const*)priv_rl.data(), priv_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));

  EXPECT_EQ(kEpidSigValid,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest, DISABLED_VerifyAcceptsSigWithBaseNameAllRlSha512256) {
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& bsn = this->kBsn0;
  auto& grp_rl = this->kGrpRl;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig_rl = this->kGrpXSigRl;
  auto& ver_rl = this->kGrpXBsn0Sha512256VerRl;
  auto& sig = this->kSigGrpXMember0Sha512256Bsn0Msg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha512_256));
  THROW_ON_EPIDERR(EpidVerifierSetBasename(verifier, bsn.data(), bsn.size()));
  THROW_ON_EPIDERR(EpidVerifierSetGroupRl(
      verifier, (GroupRl const*)grp_rl.data(), grp_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetPrivRl(
      verifier, (PrivRl const*)priv_rl.data(), priv_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetVerifierRl(
      verifier, (VerifierRl const*)ver_rl.data(), ver_rl.size()));

  EXPECT_EQ(kEpidSigValid,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

TEST_F(EpidVerifierTest,
       DISABLED_VerifyAcceptsSigWithRandomBaseNameAllRlSha512256) {
  auto& pub_key = this->kGrpXKey;
  auto& msg = this->kMsg0;
  auto& grp_rl = this->kGrpRl;
  auto& priv_rl = this->kGrpXPrivRl;
  auto& sig_rl = this->kGrpXSigRl;
  auto& sig = this->kSigGrpXMember0Sha512256RandbaseMsg0;

  VerifierCtxObj verifier(pub_key);
  THROW_ON_EPIDERR(EpidVerifierSetHashAlg(verifier, kSha512_256));
  THROW_ON_EPIDERR(EpidVerifierSetGroupRl(
      verifier, (GroupRl const*)grp_rl.data(), grp_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetPrivRl(
      verifier, (PrivRl const*)priv_rl.data(), priv_rl.size()));
  THROW_ON_EPIDERR(EpidVerifierSetSigRl(verifier, (SigRl const*)sig_rl.data(),
                                        sig_rl.size()));

  EXPECT_EQ(kEpidSigValid,
            EpidVerify(verifier, (EpidSignature const*)sig.data(), sig.size(),
                       msg.data(), msg.size()));
}

}  // namespace
