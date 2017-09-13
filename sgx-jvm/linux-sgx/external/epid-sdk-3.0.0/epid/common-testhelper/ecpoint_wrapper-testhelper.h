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
 * \brief EcPoint C++ wrapper interface.
 */
#ifndef EPID_COMMON_TESTHELPER_ECPOINT_WRAPPER_TESTHELPER_H_
#define EPID_COMMON_TESTHELPER_ECPOINT_WRAPPER_TESTHELPER_H_

#include <memory>
#include <vector>

extern "C" {
#include "epid/common/math/bignum.h"
#include "epid/common/math/ecgroup.h"
#include "epid/common/1.1/types.h"
}

class EcGroupObj;

/*!
Wrapper class to provide Resource Allocation is Initialization handling
for EcPoint
*/
class EcPointObj {
 public:
  /// constructor
  EcPointObj();
  /// copy constructor
  EcPointObj(EcPointObj const& other);
  /// assignment operator
  EcPointObj& operator=(EcPointObj const& other);
  /// Create an EcPoint
  explicit EcPointObj(EcGroupObj* group);
  /// Create an EcPoint
  EcPointObj(EcGroupObj* group, G1ElemStr const& bytes);
  /// Create an EcPoint
  EcPointObj(EcGroupObj* group, G2ElemStr const& bytes);
  /// Create an EcPoint
  EcPointObj(EcGroupObj* group, Epid11G2ElemStr const& bytes);
  /// Create an EcPoint
  EcPointObj(EcGroupObj* group, std::vector<unsigned char> const& bytes);
  /// Create an EcPoint
  EcPointObj(EcGroupObj* group, void const* bytes, size_t size);
  /// Destroy the EcPoint
  ~EcPointObj();
  /// cast operator to get the pointer to the stored EcPoint
  operator EcPoint*();
  /// const cast operator to get the pointer to the stored EcPoint
  operator const EcPoint*() const;
  /// Get the underlying pointer
  EcPoint* get();
  /// Get the underlying pointer
  EcPoint const* getc() const;
  /// Get element bytes
  std::vector<unsigned char> data() const;

 private:
  void init(EcGroupObj* group, unsigned char const* bytes, size_t size);
  struct State;
  std::unique_ptr<State> state_;
};

#endif  // EPID_COMMON_TESTHELPER_ECPOINT_WRAPPER_TESTHELPER_H_
