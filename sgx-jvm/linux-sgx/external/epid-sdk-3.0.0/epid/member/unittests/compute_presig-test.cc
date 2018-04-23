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
 * \brief ComputePreSig unit tests.
 */

#include "gtest/gtest.h"

extern "C" {
#include "epid/member/api.h"
#include "epid/member/src/context.h"
}

#include "epid/member/unittests/member-testhelper.h"
#include "epid/common-testhelper/prng-testhelper.h"
#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/common-testhelper/finite_field_wrapper-testhelper.h"
#include "epid/common-testhelper/ffelement_wrapper-testhelper.h"
#include "epid/common-testhelper/epid_params-testhelper.h"

namespace {

TEST_F(EpidMemberTest, ComputePreSigFailsGivenNullPointer) {
  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  PreComputedSignature presig;
  EXPECT_EQ(kEpidBadArgErr, EpidComputePreSig(nullptr, &presig));
  EXPECT_EQ(kEpidBadArgErr, EpidComputePreSig(member, nullptr));
}

TEST_F(EpidMemberTest,
       ComputePreSigGeneratedPreComputedSignatureCanBeDeserialized) {
  const BigNumStr p_str = {
      {{0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, 0xF0, 0xCD, 0x46, 0xE5, 0xF2, 0x5E,
        0xEE, 0x71, 0xA4, 0x9E, 0x0C, 0xDC, 0x65, 0xFB, 0x12, 0x99, 0x92, 0x1A,
        0xF6, 0x2D, 0x53, 0x6C, 0xD1, 0x0B, 0x50, 0x0D}}};

  Prng my_prng;
  MemberCtxObj member(this->kGroupPublicKey, this->kMemberPrivateKey,
                      this->kMemberPrecomp, &Prng::Generate, &my_prng);

  PreComputedSignature presig;
  EXPECT_EQ(kEpidNoErr, EpidComputePreSig(member, &presig));

  Epid20Params params;
  FiniteFieldObj Fp(p_str);
  FfElementObj Fp_element(&Fp);

  FfElementObj GT_element(&params.GT);
  EcPointObj G1_pt(&params.G1);

  EXPECT_EQ(kEpidNoErr,
            ReadEcPoint(params.G1, &presig.B, sizeof(presig.B), G1_pt));
  EXPECT_EQ(kEpidNoErr,
            ReadEcPoint(params.G1, &presig.K, sizeof(presig.K), G1_pt));
  EXPECT_EQ(kEpidNoErr,
            ReadEcPoint(params.G1, &presig.T, sizeof(presig.T), G1_pt));
  EXPECT_EQ(kEpidNoErr,
            ReadEcPoint(params.G1, &presig.R1, sizeof(presig.R1), G1_pt));
  EXPECT_EQ(kEpidNoErr, ReadFfElement(params.GT, &presig.R2, sizeof(presig.R2),
                                      GT_element));
  EXPECT_EQ(kEpidNoErr,
            ReadFfElement(Fp, &presig.a, sizeof(presig.a), Fp_element));
  EXPECT_EQ(kEpidNoErr,
            ReadFfElement(Fp, &presig.b, sizeof(presig.b), Fp_element));
  EXPECT_EQ(kEpidNoErr,
            ReadFfElement(Fp, &presig.rx, sizeof(presig.rx), Fp_element));
  EXPECT_EQ(kEpidNoErr,
            ReadFfElement(Fp, &presig.rf, sizeof(presig.rf), Fp_element));
  EXPECT_EQ(kEpidNoErr,
            ReadFfElement(Fp, &presig.ra, sizeof(presig.ra), Fp_element));
  EXPECT_EQ(kEpidNoErr,
            ReadFfElement(Fp, &presig.rb, sizeof(presig.rb), Fp_element));
}

}  // namespace
