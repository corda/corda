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
 * \brief FfElement C++ wrapper unit tests.
 */

#include "gtest/gtest.h"

#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/common-testhelper/ffelement_wrapper-testhelper.h"
#include "epid/common-testhelper/finite_field_wrapper-testhelper.h"

extern "C" {
#include "epid/common/math/bignum.h"
}

namespace {

// Use Test Fixture for SetUp and TearDown
class FfElementObjTest : public ::testing::Test {
 public:
  static FiniteFieldObj ff;
  static const BigNumStr prime_str;

  static const FpElemStr ff_str_1;
  static const FpElemStr ff_str_2;
  static const Fq2ElemStr ff_2_str;
};

/// Intel(R) EPID 2.0 parameter p
const BigNumStr FfElementObjTest::prime_str = {
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, 0xF0, 0xCD, 0x46, 0xE5, 0xF2,
    0x5E, 0xEE, 0x71, 0xA4, 0x9E, 0x0C, 0xDC, 0x65, 0xFB, 0x12, 0x99,
    0x92, 0x1A, 0xF6, 0x2D, 0x53, 0x6C, 0xD1, 0x0B, 0x50, 0x0D};

const FpElemStr FfElementObjTest::ff_str_1 = {
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
};

const FpElemStr FfElementObjTest::ff_str_2 = {
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0xDC, 0x00, 0x00, 0x00, 0x00, 0x00, 0xA4, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
};

const Fq2ElemStr FfElementObjTest::ff_2_str = {
    // 1
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10,
    // 2
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20};

FiniteFieldObj FfElementObjTest::ff(prime_str);

TEST_F(FfElementObjTest, ObjDefaultConstructedIsNotNull) {
  FfElementObj ffe(&ff);
  EXPECT_NE(nullptr, (FfElement*)ffe);
}

TEST_F(FfElementObjTest, AssignmentDoesNotCopyPointer) {
  FfElementObj ffe1(&ff, ff_str_1);
  FfElementObj ffe2(&ff, ff_str_2);
  EXPECT_NE((FfElement*)ffe1, (FfElement*)ffe2);
  ffe1 = ffe2;
  EXPECT_NE((FfElement*)ffe1, (FfElement*)ffe2);
}

TEST_F(FfElementObjTest, CopyConstructorDoesNotCopyPointer) {
  FfElementObj ffe1(&ff, ff_str_1);
  FfElementObj ffe2(ffe1);
  EXPECT_NE((FfElement*)ffe1, (FfElement*)ffe2);
}

TEST_F(FfElementObjTest, CanConstructBinomialElement) {
  FfElementObj ffe1(&ff, ff_str_1);
  FiniteFieldObj ff2(ff, ffe1, 2);
  FfElementObj ff2_e1(&ff2, ff_2_str);
  EXPECT_NE(nullptr, (FfElement*)ff2_e1);
}

TEST_F(FfElementObjTest, CanCastConstToConstPointer) {
  FfElementObj const ffe(&ff);
  FfElement const* ffe_ptr = ffe;
  (void)ffe_ptr;
}

TEST_F(FfElementObjTest, CanGetConstPointerFromConst) {
  FfElementObj const ffe(&ff);
  FfElement const* ffe_ptr = ffe.getc();
  (void)ffe_ptr;
}

/*
The following tests are expected to result in
compile time errors (by design)
*/
/*
TEST_F(FfElementObjTest, CannotCastConstToNonConstPointer) {
  FfElementObj const ffe(&ff);
  FfElement * ffe_ptr = ffe;
  (void) ffe_ptr;
}

TEST_F(FfElementObjTest, CannotGetNonConstPointerFromConst) {
  FfElementObj const ffe(&ff);
  FfElement * ffe_ptr = ffe.get();
  (void) ffe_ptr;
}
*/

TEST_F(FfElementObjTest, CanCastNonConstToConstPointer) {
  FfElementObj ffe(&ff);
  FfElement const* ffe_ptr = ffe;
  (void)ffe_ptr;
}

TEST_F(FfElementObjTest, CanGetConstPointerFromNonConst) {
  FfElementObj ffe(&ff);
  FfElement const* ffe_ptr = ffe.getc();
  (void)ffe_ptr;
}

TEST_F(FfElementObjTest, CanCastNonConstToNonConstPointer) {
  FfElementObj ffe(&ff);
  FfElement* ffe_ptr = ffe;
  (void)ffe_ptr;
}

TEST_F(FfElementObjTest, CanGetNonConstPointerFromNonConst) {
  FfElementObj ffe(&ff);
  FfElement* ffe_ptr = ffe.get();
  (void)ffe_ptr;
}

}  // namespace
