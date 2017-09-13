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
 * \brief Intel(R) EPID 1.1 Pairing unit tests.
 */

#include <cstring>

#include "gtest/gtest.h"

#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/common-testhelper/1.1/epid_params-testhelper.h"
#include "epid/common-testhelper/ffelement_wrapper-testhelper.h"

extern "C" {
#include "epid/common/math/tatepairing.h"
}

/// compares Epid11GtElemStr values
bool operator==(Epid11GtElemStr const& lhs, Epid11GtElemStr const& rhs) {
  return 0 == std::memcmp(&lhs, &rhs, sizeof(lhs));
}

namespace {

class Epid11PairingTest : public ::testing::Test {
 public:
  virtual void SetUp() { params = new Epid11ParamsObj(); }
  virtual void TearDown() { delete params; }

  static const Epid11G1ElemStr kGaElemStr;
  static const Epid11G2ElemStr kGbElemStr;
  Epid11ParamsObj* params;
};

const Epid11G1ElemStr Epid11PairingTest::kGaElemStr = {
    0x02, 0x5A, 0xC4, 0xC5, 0xCD, 0x7D, 0xAA, 0xFD, 0x26, 0xE5, 0x0B,
    0xA9, 0xB4, 0xE1, 0x72, 0xA1, 0x65, 0x2D, 0x84, 0xAD, 0x34, 0x34,
    0xF8, 0x62, 0x98, 0x6A, 0x15, 0xBE, 0xEA, 0xE3, 0xCC, 0x56, 0x05,
    0x70, 0x5F, 0x4F, 0x11, 0xAF, 0x45, 0xCF, 0x04, 0x1B, 0x96, 0xAD,
    0xEB, 0x26, 0xEE, 0x95, 0x65, 0x4B, 0xD3, 0xD6, 0x5C, 0x13, 0x76,
    0xB7, 0x7A, 0xA1, 0xC6, 0xDA, 0xED, 0x5A, 0x40, 0xCE};

const Epid11G2ElemStr Epid11PairingTest::kGbElemStr = {
    0x02, 0x10, 0x9A, 0xF4, 0x06, 0x32, 0x30, 0x89, 0xCB, 0x95, 0xE9, 0x55,
    0x0E, 0x9D, 0xAF, 0x0E, 0x98, 0xCD, 0xCA, 0xDC, 0xB1, 0xFF, 0xFC, 0xD1,
    0x45, 0x66, 0xBB, 0x86, 0x46, 0x1E, 0x8C, 0x30, 0x04, 0x78, 0x53, 0xE1,
    0x3F, 0x96, 0xC5, 0xE4, 0x15, 0x23, 0x7B, 0x1F, 0x3F, 0x2C, 0xD3, 0x95,
    0x40, 0xBC, 0x7A, 0x31, 0x1F, 0x14, 0x38, 0x9E, 0x1A, 0xA5, 0xD6, 0x63,
    0x10, 0x91, 0xE4, 0xD3, 0x00, 0xB4, 0x02, 0xBC, 0x47, 0xFA, 0xA6, 0x29,
    0x82, 0x0B, 0xB1, 0xD5, 0xFF, 0xF2, 0xE6, 0xB0, 0xC6, 0xAE, 0xE8, 0x7B,
    0x91, 0xD9, 0xEE, 0x66, 0x07, 0x1F, 0xFD, 0xA2, 0xE7, 0x02, 0x66, 0xDD,
    0x05, 0x2E, 0xF8, 0xC6, 0xC1, 0x6A, 0xEF, 0x3C, 0xC1, 0x95, 0xF6, 0x26,
    0xCE, 0x5E, 0x55, 0xD1, 0x64, 0x13, 0x28, 0xB1, 0x18, 0x57, 0xD8, 0x1B,
    0x84, 0xFA, 0xEC, 0x7E, 0x5D, 0x99, 0x06, 0x49, 0x05, 0x73, 0x35, 0xA9,
    0xA7, 0xF2, 0xA1, 0x92, 0x5F, 0x3E, 0x7C, 0xDF, 0xAC, 0xFE, 0x0F, 0xF5,
    0x08, 0xD0, 0x3C, 0xAE, 0xCD, 0x58, 0x00, 0x5F, 0xD0, 0x84, 0x7E, 0xEA,
    0x63, 0x57, 0xFE, 0xC6, 0x01, 0x56, 0xDA, 0xF3, 0x72, 0x61, 0xDA, 0xC6,
    0x93, 0xB0, 0xAC, 0xEF, 0xAA, 0xD4, 0x51, 0x6D, 0xCA, 0x71, 0x1E, 0x06,
    0x73, 0xEA, 0x83, 0xB2, 0xB1, 0x99, 0x4A, 0x4D, 0x4A, 0x0D, 0x35, 0x07};

///////////////////////////////////////////////////////////////////////
// NewEpid11PairingState / DeleteEpid11PairingState

// test that delete works in a "normal" valid case.
TEST_F(Epid11PairingTest, DeleteWorksGivenNewlyCreatedPairingState) {
  Epid11PairingState* ps = nullptr;
  THROW_ON_EPIDERR(NewEpid11PairingState(this->params->G1, this->params->G2,
                                         this->params->GT, &ps));
  EXPECT_NO_THROW(DeleteEpid11PairingState(&ps));
}

// test that delete works if there is nothing to do
TEST_F(Epid11PairingTest, DeleteWorksGivenNullPointer) {
  EXPECT_NO_THROW(DeleteEpid11PairingState(nullptr));
  Epid11PairingState* ps = nullptr;
  EXPECT_NO_THROW(DeleteEpid11PairingState(&ps));
}

// test that new succeeds with valid parameters
TEST_F(Epid11PairingTest, NewSucceedsGivenValidParameters) {
  Epid11PairingState* ps = nullptr;
  EXPECT_EQ(kEpidNoErr,
            NewEpid11PairingState(this->params->G1, this->params->G2,
                                  this->params->GT, &ps));
  DeleteEpid11PairingState(&ps);
}

// test that new fails if any options are NULL
TEST_F(Epid11PairingTest, NewFailsGivenNullParameters) {
  Epid11PairingState* ps = nullptr;
  EXPECT_EQ(kEpidBadArgErr, NewEpid11PairingState(nullptr, this->params->G2,
                                                  this->params->GT, &ps));
  DeleteEpid11PairingState(&ps);
  EXPECT_EQ(kEpidBadArgErr, NewEpid11PairingState(this->params->G1, nullptr,
                                                  this->params->GT, &ps));
  DeleteEpid11PairingState(&ps);
  EXPECT_EQ(
      kEpidBadArgErr,
      NewEpid11PairingState(this->params->G1, this->params->G2, nullptr, &ps));
  DeleteEpid11PairingState(&ps);
  EXPECT_EQ(kEpidBadArgErr,
            NewEpid11PairingState(this->params->G1, this->params->G2,
                                  this->params->GT, nullptr));
}

// test that new checks that G1 is valid
TEST_F(Epid11PairingTest, NewFailsGivenInvalidG1) {
  Epid11PairingState* ps = nullptr;
  EXPECT_EQ(kEpidBadArgErr,
            NewEpid11PairingState(this->params->G2, this->params->G2,
                                  this->params->GT, &ps));
  DeleteEpid11PairingState(&ps);
}

// test that new checks that G2 is valid
TEST_F(Epid11PairingTest, NewFailsGivenInvalidG2) {
  Epid11PairingState* ps = nullptr;
  EXPECT_EQ(kEpidBadArgErr,
            NewEpid11PairingState(this->params->G1, this->params->G1,
                                  this->params->GT, &ps));
  DeleteEpid11PairingState(&ps);
}

// test that new checks that GT is valid
TEST_F(Epid11PairingTest, NewFailsGivenInvalidGT) {
  FiniteFieldObj GFp;
  Epid11PairingState* ps = nullptr;
  EXPECT_EQ(kEpidBadArgErr, NewEpid11PairingState(this->params->G1,
                                                  this->params->G2, GFp, &ps));
  DeleteEpid11PairingState(&ps);
}
///////////////////////////////////////////////////////////////////////
// Pairing
TEST_F(Epid11PairingTest, PairingWorksFromG1AndG2ToGt) {
  Epid11GtElemStr r_expected_str = {
      0x02, 0xE1, 0x84, 0x16, 0x53, 0x10, 0x0E, 0xEC, 0xFB, 0xDE, 0xF3, 0x5E,
      0x2E, 0x26, 0xEE, 0x45, 0x0C, 0xD7, 0x97, 0xA7, 0x35, 0x43, 0x08, 0x5E,
      0x03, 0xB9, 0xFE, 0x91, 0x8A, 0x02, 0x14, 0xB4, 0x07, 0x7F, 0x8A, 0x5E,
      0xFD, 0xE1, 0x83, 0xC9, 0xCE, 0x1C, 0xC9, 0xF1, 0xCC, 0xB0, 0x52, 0x81,
      0xAD, 0x80, 0x2D, 0x13, 0x1C, 0x32, 0xEC, 0xAF, 0xA0, 0x8B, 0x66, 0x05,
      0x0A, 0x89, 0x26, 0xAD, 0x06, 0x75, 0x3B, 0x3B, 0xE5, 0xFB, 0x62, 0x20,
      0xA8, 0xC3, 0x91, 0xC6, 0x26, 0xC6, 0x58, 0x71, 0xB1, 0x85, 0x06, 0xBD,
      0xAE, 0x06, 0x51, 0xF9, 0x86, 0x2A, 0xC1, 0x5A, 0x11, 0xBA, 0x17, 0xE1,
      0x01, 0x4B, 0x22, 0x66, 0xEB, 0xCF, 0x7E, 0x2B, 0xE7, 0x0A, 0xF2, 0x77,
      0x1C, 0xE6, 0x48, 0x8F, 0x3E, 0xD8, 0x7D, 0x71, 0xF1, 0x78, 0x4C, 0x80,
      0x93, 0xF8, 0x08, 0xB7, 0xCB, 0xAF, 0x04, 0xDF, 0x04, 0x5C, 0x19, 0x3C,
      0xD3, 0x29, 0x11, 0xE7, 0xC5, 0x58, 0x68, 0xEA, 0x65, 0xBB, 0x48, 0x5F,
      0x3A, 0x62, 0xD9, 0x62, 0x40, 0x57, 0x53, 0x19, 0x9B, 0xB5, 0x6C, 0x52,
      0x0C, 0x33, 0x27, 0x14, 0x06, 0x6A, 0xAD, 0xB0, 0x38, 0x41, 0xD0, 0xA5,
      0x37, 0x54, 0xC5, 0x3E, 0x3B, 0x5F, 0x1A, 0xAF, 0x75, 0x8F, 0xCA, 0x42,
      0xB9, 0xA6, 0x1E, 0x18, 0xB2, 0x6B, 0x31, 0x7D, 0x5C, 0xC6, 0xE8, 0xDC};

  Epid11GtElemStr r_str = {0};

  FfElementObj r(&this->params->GT);
  EcPointObj ga_elem(&this->params->G1, this->kGaElemStr);
  EcPointObj gb_elem(&this->params->G2, this->kGbElemStr);

  Epid11PairingState* ps = nullptr;
  THROW_ON_EPIDERR(NewEpid11PairingState(this->params->G1, this->params->G2,
                                         this->params->GT, &ps));
  EXPECT_EQ(kEpidNoErr, Epid11Pairing(ps, ga_elem, gb_elem, r));
  DeleteEpid11PairingState(&ps);

  THROW_ON_EPIDERR(WriteFfElement(this->params->GT, r, &r_str, sizeof(r_str)));
  EXPECT_EQ(r_expected_str, r_str);
}

TEST_F(Epid11PairingTest, PairingGivenPointAtInfinityReturns1) {
  Epid11GtElemStr r_expected_str = {0};
  r_expected_str.a[0].a[0].data.data[31] = 1;

  Epid11GtElemStr r_str = {0};

  FfElementObj r(&this->params->GT);
  EcPointObj ga_elem(&this->params->G1);
  EcPointObj gb_elem(&this->params->G2, this->kGbElemStr);

  Epid11PairingState* ps = nullptr;
  THROW_ON_EPIDERR(NewEpid11PairingState(this->params->G1, this->params->G2,
                                         this->params->GT, &ps));
  EXPECT_EQ(kEpidNoErr, Epid11Pairing(ps, ga_elem, gb_elem, r));
  DeleteEpid11PairingState(&ps);

  THROW_ON_EPIDERR(WriteFfElement(this->params->GT, r, &r_str, sizeof(r_str)));
  EXPECT_EQ(r_expected_str, r_str);
}

TEST_F(Epid11PairingTest, PairingFailsOnPointMissmatch) {
  FfElementObj r(&this->params->GT);

  EcPointObj ga_elem(&this->params->G1, this->kGaElemStr);
  EcPointObj gb_elem(&this->params->G2, this->kGbElemStr);

  Epid11PairingState* ps = nullptr;
  THROW_ON_EPIDERR(NewEpid11PairingState(this->params->G1, this->params->G2,
                                         this->params->GT, &ps));
  EXPECT_EQ(kEpidBadArgErr, Epid11Pairing(ps, gb_elem, ga_elem, r));
  DeleteEpid11PairingState(&ps);
}

TEST_F(Epid11PairingTest, PairingFailsOnInvalidPointInG1) {
  FfElementObj r(&this->params->GT);

  EcPointObj ga_elem(&this->params->G3);
  EcPointObj gb_elem(&this->params->G2, this->kGbElemStr);

  Epid11PairingState* ps = nullptr;
  THROW_ON_EPIDERR(NewEpid11PairingState(this->params->G1, this->params->G2,
                                         this->params->GT, &ps));
  EXPECT_EQ(kEpidBadArgErr, Epid11Pairing(ps, gb_elem, ga_elem, r));
  DeleteEpid11PairingState(&ps);
}

}  // namespace
