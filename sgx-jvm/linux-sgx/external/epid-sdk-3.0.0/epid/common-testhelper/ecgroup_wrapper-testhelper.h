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
 * \brief EcGroup C++ wrapper interface.
 */
#ifndef EPID_COMMON_TESTHELPER_ECGROUP_WRAPPER_TESTHELPER_H_
#define EPID_COMMON_TESTHELPER_ECGROUP_WRAPPER_TESTHELPER_H_

#include <memory>
#include <vector>

extern "C" {
#include "epid/common/math/ecgroup.h"
}

#include "epid/common-testhelper/finite_field_wrapper-testhelper.h"

/*!
Wrapper class to provide Resource Allocation is Initialization handling
for EcGroup
*/
class EcGroupObj {
 public:
  /// constructor
  EcGroupObj();
  /// copy constructor
  EcGroupObj(EcGroupObj const& other);
  /// assignment operator
  EcGroupObj& operator=(EcGroupObj const& other);
  /// Create a EcGroup
  explicit EcGroupObj(FiniteFieldObj* ff, FfElement const* a,
                      FfElement const* b, FfElement const* x,
                      FfElement const* y, BigNum const* order,
                      BigNum const* cofactor);
  /// Destroy the EcGroup
  ~EcGroupObj();
  /// cast operator to get the pointer to the stored EcGroup
  operator EcGroup*();
  /// const cast operator to get the pointer to the stored EcGroup
  operator const EcGroup*() const;
  /// Get the underlying pointer
  EcGroup* get();
  /// Get the underlying pointer
  EcGroup const* getc() const;
  /// Get maximum size of group element
  size_t GetElementMaxSize() const;

 private:
  struct State;
  std::unique_ptr<State> state_;
};

#endif  // EPID_COMMON_TESTHELPER_ECGROUP_WRAPPER_TESTHELPER_H_
