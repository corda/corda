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
 * \brief OctStr2Bnu unit tests.
 *
 * OctStr2Bnu is an internal function used in the IPP implementation of the
 * math libraries. These tests can be omitted if you do not use this function.
 */

#include "epid/common/stdtypes.h"
#include "gtest/gtest.h"

extern "C" {
#include "epid/common/math/src/bignum-internal.h"
}

namespace {

const uint8_t bnstr1[] = {0x01, 0x02, 0x03, 0x04};
const uint8_t bnstr2[] = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
uint32_t bnustr1[sizeof(bnstr1) / sizeof(uint32_t)] = {0x01020304};
uint32_t bnustr2[sizeof(bnstr2) / sizeof(uint32_t)] = {0x05060708, 0x01020304};

TEST(OctStr2Bnu, octstr2bnuFailsGivenNullBnu) {
  int len = OctStr2Bnu(nullptr, bnstr1, sizeof(bnstr1) / sizeof(uint8_t));
  EXPECT_EQ(-1, len);
}
TEST(OctStr2Bnu, octstr2bnuFailsGivenNullOctstr) {
  uint32_t bnustr_res[sizeof(bnstr1) / sizeof(uint32_t)] = {0};
  int len = OctStr2Bnu(bnustr_res, nullptr, sizeof(bnstr1) / sizeof(uint8_t));
  EXPECT_EQ(-1, len);
}
TEST(OctStr2Bnu, octstr2bnuFailsGivenInvalidOctsrtLen) {
  uint32_t bnustr_res[sizeof(bnstr1) / sizeof(uint32_t)] = {0};
  int len = OctStr2Bnu(bnustr_res, bnstr1, -1);
  EXPECT_EQ(-1, len);
  len = OctStr2Bnu(bnustr_res, bnstr1, 0);
  EXPECT_EQ(-1, len);
  len = OctStr2Bnu(bnustr_res, bnstr1, 3);
  EXPECT_EQ(-1, len);
  len = OctStr2Bnu(bnustr_res, bnstr1, 5);
  EXPECT_EQ(-1, len);
}
TEST(OctStr2Bnu, octstr2bnuWorksGivenOctstr1) {
  uint32_t bnustr_res[sizeof(bnstr1) / sizeof(uint32_t)] = {0};
  int len = OctStr2Bnu(bnustr_res, bnstr1, sizeof(bnstr1) / sizeof(uint8_t));
  EXPECT_EQ(1, len);
  EXPECT_EQ(0,
            memcmp(bnustr1, bnustr_res, sizeof(bnustr_res) / sizeof(uint32_t)))
      << "OctStr2Bnu: bnu string result does not match with predefined value\n";
}
TEST(OctStr2Bnu, octstr2bnuWorksGivenOctstr2) {
  uint32_t bnustr_res[sizeof(bnstr2) / sizeof(uint32_t)] = {0};
  int len = OctStr2Bnu(bnustr_res, bnstr2, sizeof(bnstr2) / sizeof(uint8_t));
  EXPECT_EQ(2, len);
  EXPECT_EQ(0,
            memcmp(bnustr2, bnustr_res, sizeof(bnustr_res) / sizeof(uint32_t)))
      << "OctStr2Bnu: bnu string result does not match with predefined value\n";
}
}  // namespace
