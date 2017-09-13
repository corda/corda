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
* \brief Intel(R) EPID 1.1 parameters C++ wrapper interface.
*/
#ifndef EPID_COMMON_TESTHELPER_1_1_EPID_PARAMS_TESTHELPER_H_
#define EPID_COMMON_TESTHELPER_1_1_EPID_PARAMS_TESTHELPER_H_

#include <memory>
#include <vector>

extern "C" {
#include "epid/common/math/bignum.h"
#include "epid/common/math/finitefield.h"
#include "epid/common/math/ecgroup.h"
#include "epid/common/1.1/types.h"
}

#include "epid/common-testhelper/ffelement_wrapper-testhelper.h"
#include "epid/common-testhelper/finite_field_wrapper-testhelper.h"
#include "epid/common-testhelper/ecgroup_wrapper-testhelper.h"
#include "epid/common-testhelper/ecpoint_wrapper-testhelper.h"

class Epid11ParamsObj {
 public:
  Epid11ParamsObj();

  // This class instances are not meant to be copied.
  // Explicitly delete copy constructor and assignment operator.
  Epid11ParamsObj(const Epid11ParamsObj&) = delete;
  Epid11ParamsObj& operator=(const Epid11ParamsObj&) = delete;

  virtual ~Epid11ParamsObj() {}

  FiniteFieldObj GT;
  EcGroupObj G1;
  EcGroupObj G2;
  EcGroupObj G3;

 private:
  static const BigNumStr p_str_;
  static const BigNumStr q_str_;
  static const std::vector<uint8_t> h_str_;
  static const FqElemStr a_str_;
  static const FqElemStr b_str_;
  static const BigNumStr coeffs_str_[3];
  static const FqElemStr qnr_str;

  static const std::vector<uint8_t> orderG2_str;

  static const BigNumStr p1_str_;
  static const BigNumStr q1_str_;
  static const std::vector<uint8_t> h1_str_;
  static const FqElemStr a1_str_;
  static const FqElemStr b1_str_;

  static const Epid11G1ElemStr g1_str_;
  static const Fq3ElemStr g2x_str_;
  static const Fq3ElemStr g2y_str_;
  static const Epid11G3ElemStr g3_str_;
};

#endif  // EPID_COMMON_TESTHELPER_1_1_EPID_PARAMS_TESTHELPER_H_
