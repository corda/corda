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
 * \brief Intel(R) EPID 2.0 parameters C++ wrapper interface.
 */
#ifndef EPID_COMMON_TESTHELPER_EPID_PARAMS_TESTHELPER_H_
#define EPID_COMMON_TESTHELPER_EPID_PARAMS_TESTHELPER_H_

#include <memory>
#include "epid/common/types.h"

extern "C" {
#include "epid/common/math/bignum.h"
#include "epid/common/math/finitefield.h"
#include "epid/common/math/ecgroup.h"
}

#include "epid/common-testhelper/ffelement_wrapper-testhelper.h"
#include "epid/common-testhelper/finite_field_wrapper-testhelper.h"
#include "epid/common-testhelper/ecgroup_wrapper-testhelper.h"
#include "epid/common-testhelper/ecpoint_wrapper-testhelper.h"
#include "epid/common-testhelper/bignum_wrapper-testhelper.h"

class Epid20Params {
 public:
  Epid20Params();

  // This class instances are not meant to be copied.
  // Explicitly delete copy constructor and assignment operator.
  Epid20Params(const Epid20Params&) = delete;
  Epid20Params& operator=(const Epid20Params&) = delete;

  virtual ~Epid20Params() {}

  FiniteFieldObj GT;
  EcGroupObj G1;
  EcGroupObj G2;

 private:
  static const BigNumStr q_str_;
  static const FqElemStr beta_str_;
  static const Fq6ElemStr v_str_;

  static const BigNumStr p_str_;
  static const FqElemStr b_str_;
  static const FqElemStr h_str_;
  static const G1ElemStr g1_str_;

  static const Fq2ElemStr xi_str_;
  static const G2ElemStr g2_str_;

  FiniteFieldObj fq;
  FiniteFieldObj fq2;
  FiniteFieldObj fq6;
};

#endif  // EPID_COMMON_TESTHELPER_EPID_PARAMS_TESTHELPER_H_
