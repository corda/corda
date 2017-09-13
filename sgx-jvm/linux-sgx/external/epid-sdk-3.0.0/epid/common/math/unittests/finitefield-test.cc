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
 * \brief FiniteField unit tests.
 */

#include "gtest/gtest.h"

#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/common-testhelper/finite_field_wrapper-testhelper.h"
#include "epid/common-testhelper/ffelement_wrapper-testhelper.h"

extern "C" {
#include "epid/common/math/finitefield.h"
}

#ifndef COUNT_OF
#define COUNT_OF(a) (sizeof(a) / sizeof((a)[0]))
#endif  // COUNT_OF

namespace {
/// Intel(R) EPID 2.0 parameters q, beta, xi and v
BigNumStr q = {{0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, 0xF0, 0xCD, 0x46, 0xE5,
                0xF2, 0x5E, 0xEE, 0x71, 0xA4, 0x9F, 0x0C, 0xDC, 0x65, 0xFB,
                0x12, 0x98, 0x0A, 0x82, 0xD3, 0x29, 0x2D, 0xDB, 0xAE, 0xD3,
                0x30, 0x13}};
FqElemStr beta = {{0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, 0xF0, 0xCD, 0x46, 0xE5,
                   0xF2, 0x5E, 0xEE, 0x71, 0xA4, 0x9F, 0x0C, 0xDC, 0x65, 0xFB,
                   0x12, 0x98, 0x0A, 0x82, 0xD3, 0x29, 0x2D, 0xDB, 0xAE, 0xD3,
                   0x30, 0x12}};
Fq2ElemStr xi = {
    {{{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02}}},
     {{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01}}}}};
Fq6ElemStr v = {
    {{{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
       {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}}},
     {{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01},
       {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}}},
     {{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
       {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}}}}};

FqElemStr qnr = {{0x08, 0x66, 0xA7, 0x67, 0x36, 0x6E, 0x62, 0x71, 0xB7, 0xA6,
                  0x52, 0x94, 0x8F, 0xFB, 0x25, 0x9E, 0xE6, 0x4F, 0x25, 0xE5,
                  0x26, 0x9A, 0x2B, 0x6E, 0x7E, 0xF8, 0xA6, 0x39, 0xAE, 0x46,
                  0xAA, 0x24}};

const BigNumStr coeffs[3] = {
    {{{0x02, 0x16, 0x7A, 0x61, 0x53, 0xDD, 0xF6, 0xE2, 0x89, 0x15, 0xA0, 0x94,
       0xF1, 0xB5, 0xDC, 0x65, 0x21, 0x15, 0x62, 0xE1, 0x7D, 0xC5, 0x43, 0x89,
       0xEE, 0xB4, 0xEF, 0xC8, 0xA0, 0x8E, 0x34, 0x0F}}},

    {{{0x04, 0x82, 0x27, 0xE1, 0xEB, 0x98, 0x64, 0xC2, 0x8D, 0x8F, 0xDD, 0x0E,
       0x82, 0x40, 0xAE, 0xD4, 0x31, 0x63, 0xD6, 0x46, 0x32, 0x16, 0x85, 0x7A,
       0xB7, 0x18, 0x68, 0xB8, 0x17, 0x02, 0x81, 0xA6}}},

    {{{0x06, 0x20, 0x76, 0xE8, 0x54, 0x54, 0x53, 0xB4, 0xA9, 0xD8, 0x44, 0x4B,
       0xAA, 0xFB, 0x1C, 0xFD, 0xAE, 0x15, 0xCA, 0x29, 0x79, 0xA6, 0x24, 0xA4,
       0x0A, 0xF6, 0x1E, 0xAC, 0xED, 0xFB, 0x10, 0x41}}}};

TEST(FiniteField, DeleteWorksGivenNewlyCreatedFiniteField) {
  FiniteField* finitefield = nullptr;
  EpidStatus sts = NewFiniteField(&q, &finitefield);
  EXPECT_EQ(kEpidNoErr, sts);
  EXPECT_NO_THROW(DeleteFiniteField(&finitefield));
}
TEST(FiniteField, DeleteWorksGivenNullPointer) {
  EXPECT_NO_THROW(DeleteFiniteField(nullptr));
  FiniteField* finitefield = nullptr;
  EXPECT_NO_THROW(DeleteFiniteField(&finitefield));
}
TEST(FiniteField, NewFailsGivenNullBigNumStr) {
  FiniteField* finitefield = nullptr;
  EpidStatus sts = NewFiniteField(nullptr, &finitefield);
  EXPECT_EQ(kEpidBadArgErr, sts);
  DeleteFiniteField(&finitefield);
}

TEST(FiniteField, NewFailsGivenNullFiniteField) {
  EpidStatus sts = NewFiniteField(&q, nullptr);
  EXPECT_EQ(kEpidBadArgErr, sts);
}

TEST(FiniteField, NewSucceedsGivenNewlyCreatedBigNumStr) {
  FiniteField* finitefield = nullptr;
  EpidStatus sts = NewFiniteField(&q, &finitefield);
  EXPECT_EQ(kEpidNoErr, sts);
  DeleteFiniteField(&finitefield);
}

// the following test reproduces a bug in IPP.
TEST(FiniteField, DISABLED_NewSucceedsGivenAllFFBigNumStr) {
  const BigNumStr test_prime = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
                                0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
                                0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
                                0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};
  FiniteField* finitefield = nullptr;
  EpidStatus sts = NewFiniteField(&test_prime, &finitefield);
  EXPECT_EQ(kEpidNoErr, sts);
  DeleteFiniteField(&finitefield);
}

TEST(FiniteField, BinomialExtensionFailsGivenNullPointer) {
  FiniteField* binom_ext_finite_field_ptr = nullptr;
  FiniteFieldObj ground_field(q);
  FfElementObj ground_element(&ground_field, beta);
  EXPECT_EQ(kEpidBadArgErr,
            NewFiniteFieldViaBinomalExtension(nullptr, ground_element, 2,
                                              &binom_ext_finite_field_ptr));
  DeleteFiniteField(&binom_ext_finite_field_ptr);
  EXPECT_EQ(kEpidBadArgErr,
            NewFiniteFieldViaBinomalExtension(ground_field, nullptr, 2,
                                              &binom_ext_finite_field_ptr));
  DeleteFiniteField(&binom_ext_finite_field_ptr);
  EXPECT_EQ(kEpidBadArgErr, NewFiniteFieldViaBinomalExtension(
                                ground_field, ground_element, 2, nullptr));
}

TEST(FiniteField, BinomialExtensionFailsGivenBadDegree) {
  FiniteField* binom_ext_finite_field_ptr = nullptr;
  FiniteFieldObj ground_field(q);
  FfElementObj ground_element(&ground_field, beta);
  EXPECT_EQ(kEpidBadArgErr,
            NewFiniteFieldViaBinomalExtension(ground_field, ground_element, 1,
                                              &binom_ext_finite_field_ptr));
  DeleteFiniteField(&binom_ext_finite_field_ptr);
  EXPECT_EQ(kEpidBadArgErr,
            NewFiniteFieldViaBinomalExtension(ground_field, ground_element, 0,
                                              &binom_ext_finite_field_ptr));
  DeleteFiniteField(&binom_ext_finite_field_ptr);
  EXPECT_EQ(kEpidBadArgErr,
            NewFiniteFieldViaBinomalExtension(ground_field, ground_element, -1,
                                              &binom_ext_finite_field_ptr));
  DeleteFiniteField(&binom_ext_finite_field_ptr);
  EXPECT_EQ(kEpidBadArgErr,
            NewFiniteFieldViaBinomalExtension(ground_field, ground_element, -99,
                                              &binom_ext_finite_field_ptr));
  DeleteFiniteField(&binom_ext_finite_field_ptr);
}

TEST(FiniteField, BinomialExtensionCanBuildEpid2GtField) {
  // construct Fq finite field
  FiniteFieldObj fq(q);

  // construct Fq^2 finite field
  FfElementObj neg_beta(&fq);
  THROW_ON_EPIDERR(FfNeg(fq, FfElementObj(&fq, beta), neg_beta));
  FiniteFieldObj fq2(fq, neg_beta, 2);

  // construct Fq^6 finite field
  FfElementObj neg_xi(&fq2);
  THROW_ON_EPIDERR(FfNeg(fq2, FfElementObj(&fq2, xi), neg_xi));
  FiniteFieldObj fq6(fq2, neg_xi, 3);

  // construct Fq^12 finite field
  FfElementObj neg_v(&fq6);
  THROW_ON_EPIDERR(FfNeg(fq6, FfElementObj(&fq6, v), neg_v));
  FiniteFieldObj fq12(fq6, neg_v, 2);

  FiniteField* binom_ext_fq12_ptr = nullptr;
  EXPECT_EQ(kEpidNoErr, NewFiniteFieldViaBinomalExtension(fq6, neg_v, 2,
                                                          &binom_ext_fq12_ptr));
  DeleteFiniteField(&binom_ext_fq12_ptr);
}

TEST(FiniteField, PolynomialExtensionFailsGivenNullPointer) {
  FiniteField* ext_finite_field_ptr = nullptr;
  FiniteFieldObj ground_field(q);
  EXPECT_EQ(kEpidBadArgErr,
            NewFiniteFieldViaPolynomialExtension(
                nullptr, coeffs, COUNT_OF(coeffs), &ext_finite_field_ptr));
  DeleteFiniteField(&ext_finite_field_ptr);
  EXPECT_EQ(kEpidBadArgErr,
            NewFiniteFieldViaPolynomialExtension(ground_field, nullptr, 2,
                                                 &ext_finite_field_ptr));
  DeleteFiniteField(&ext_finite_field_ptr);
  EXPECT_EQ(kEpidBadArgErr, NewFiniteFieldViaPolynomialExtension(
                                ground_field, coeffs, 2, nullptr));
}

TEST(FiniteField, PolynomialExtensionFailsGivenBadDegree) {
  FiniteField* ext_finite_field_ptr = nullptr;
  FiniteFieldObj ground_field(q);
  FfElementObj ground_element(&ground_field, beta);
  EXPECT_EQ(kEpidBadArgErr,
            NewFiniteFieldViaPolynomialExtension(ground_field, coeffs, 0,
                                                 &ext_finite_field_ptr));
  DeleteFiniteField(&ext_finite_field_ptr);
  EXPECT_EQ(kEpidBadArgErr,
            NewFiniteFieldViaPolynomialExtension(ground_field, coeffs, -1,
                                                 &ext_finite_field_ptr));
  DeleteFiniteField(&ext_finite_field_ptr);
  EXPECT_EQ(kEpidBadArgErr,
            NewFiniteFieldViaPolynomialExtension(ground_field, coeffs, -99,
                                                 &ext_finite_field_ptr));
  DeleteFiniteField(&ext_finite_field_ptr);
}

TEST(FiniteField, CanBuildEpid11GtField) {
  // construct Fq finite field
  FiniteFieldObj fq(q);

  // construct Fqd finite field
  FiniteFieldObj fqd(fq, coeffs, COUNT_OF(coeffs));

  // Fqk ground element is {-qnr, 0, 0}
  FfElementObj neg_qnr(&fq);
  THROW_ON_EPIDERR(FfNeg(fq, FfElementObj(&fq, qnr), neg_qnr));
  Fq3ElemStr ground_element_str = {0};
  THROW_ON_EPIDERR(WriteFfElement(fq, neg_qnr, &ground_element_str.a[0],
                                  sizeof(ground_element_str.a[0])));
  FfElementObj ground_element(&fqd, ground_element_str);

  // construct Fqk finite field
  FiniteField* gt_ptr = nullptr;
  EXPECT_EQ(kEpidNoErr,
            NewFiniteFieldViaBinomalExtension(fqd, ground_element, 2, &gt_ptr));
  DeleteFiniteField(&gt_ptr);
}

}  // namespace
