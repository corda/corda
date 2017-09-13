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
 * \brief FiniteField C++ wrapper unit tests.
 */

#include "gtest/gtest.h"

#include "epid/common-testhelper/errors-testhelper.h"
#include "epid/common-testhelper/finite_field_wrapper-testhelper.h"
#include "epid/common-testhelper/ffelement_wrapper-testhelper.h"

extern "C" {
#include "epid/common/math/bignum.h"
#include "epid/common/types.h"
}

namespace {

// Use Test Fixture for SetUp and TearDown
class FiniteFieldObjTest : public ::testing::Test {
 public:
  static const BigNumStr prime_str;
  static const FpElemStr ground_str;
};

/// Intel(R) EPID 2.0 parameter p
const BigNumStr FiniteFieldObjTest::prime_str = {
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, 0xF0, 0xCD, 0x46, 0xE5, 0xF2,
    0x5E, 0xEE, 0x71, 0xA4, 0x9E, 0x0C, 0xDC, 0x65, 0xFB, 0x12, 0x99,
    0x92, 0x1A, 0xF6, 0x2D, 0x53, 0x6C, 0xD1, 0x0B, 0x50, 0x0D};

const FpElemStr FiniteFieldObjTest::ground_str = {
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
};

TEST_F(FiniteFieldObjTest, ObjDefaultConstructedIsNotNull) {
  FiniteFieldObj ff;
  EXPECT_NE(nullptr, (FiniteField*)ff);
}

TEST_F(FiniteFieldObjTest, AssignmentCopiesPointer) {
  FiniteFieldObj ff1;
  FiniteFieldObj ff2;
  EXPECT_NE((FiniteField*)ff1, (FiniteField*)ff2);
  ff1 = ff2;
  EXPECT_EQ((FiniteField*)ff1, (FiniteField*)ff2);
}

TEST_F(FiniteFieldObjTest, CopyConstructorCopiesPointer) {
  FiniteFieldObj ff1;
  FiniteFieldObj ff2(ff1);
  EXPECT_EQ((FiniteField*)ff1, (FiniteField*)ff2);
}

TEST_F(FiniteFieldObjTest, ConstructorDoesNotThrow) {
  FiniteFieldObj ff1;
  FiniteFieldObj ff2(this->prime_str);
  FfElementObj ffe(&ff2, this->ground_str);
  FiniteFieldObj ff3(ff2, ffe, 2);
}

TEST_F(FiniteFieldObjTest, CanCastConstToConstPointer) {
  FiniteFieldObj const ff;
  FiniteField const* ff_ptr = ff;
  (void)ff_ptr;
}

TEST_F(FiniteFieldObjTest, CanGetConstPointerFromConst) {
  FiniteFieldObj const ff;
  FiniteField const* ff_ptr = ff.getc();
  (void)ff_ptr;
}

/*
The following tests are expected to result in
compile time errors (by design)
*/
/*
TEST_F(FiniteFieldObjTest, CannotCastConstToNonConstPointer) {
  FiniteFieldObj const ff;
  FiniteField * ff_ptr = ff;
  (void) ff_ptr;
}

TEST_F(FiniteFieldObjTest, CannotGetNonConstPointerFromConst) {
  FiniteFieldObj const ff;
  FiniteField * ff_ptr = ff.get();
  (void) ff_ptr;
}
*/

TEST_F(FiniteFieldObjTest, CanCastNonConstToConstPointer) {
  FiniteFieldObj ff;
  FiniteField const* ff_ptr = ff;
  (void)ff_ptr;
}

TEST_F(FiniteFieldObjTest, CanGetConstPointerFromNonConst) {
  FiniteFieldObj ff;
  FiniteField const* ff_ptr = ff.getc();
  (void)ff_ptr;
}

TEST_F(FiniteFieldObjTest, CanCastNonConstToNonConstPointer) {
  FiniteFieldObj ff;
  FiniteField* ff_ptr = ff;
  (void)ff_ptr;
}

TEST_F(FiniteFieldObjTest, CanGetNonConstPointerFromNonConst) {
  FiniteFieldObj ff;
  FiniteField* ff_ptr = ff.get();
  (void)ff_ptr;
}

}  // namespace
