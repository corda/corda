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
 * \brief FiniteField C++ wrapper interface.
 */
#ifndef EPID_COMMON_TESTHELPER_FINITE_FIELD_WRAPPER_TESTHELPER_H_
#define EPID_COMMON_TESTHELPER_FINITE_FIELD_WRAPPER_TESTHELPER_H_

#include <memory>
#include <vector>

extern "C" {
#include "epid/common/math/finitefield.h"
}

class FfElementObj;

/*!
Wrapper class to provide Resource Allocation is Initialization handling
for FiniteField
*/
class FiniteFieldObj {
 public:
  /// constructor
  FiniteFieldObj();
  /// copy constructor
  FiniteFieldObj(FiniteFieldObj const& other);
  /// assignment operator
  FiniteFieldObj& operator=(FiniteFieldObj const& other);
  /// Create a FiniteField
  explicit FiniteFieldObj(BigNumStr const& prime);
  /// Create a FiniteField
  FiniteFieldObj(FiniteFieldObj const& ground_field,
                 FfElementObj const& ground_element, int degree);
  /// Create a FiniteField
  FiniteFieldObj(FiniteFieldObj const& ground_field,
                 BigNumStr const* irr_polynomial, int degree);
  /// Destroy the FiniteField
  ~FiniteFieldObj();
  /// cast operator to get the pointer to the stored FiniteField
  operator FiniteField*();
  /// const cast operator to get the pointer to the stored FiniteField
  operator const FiniteField*() const;
  /// Get the underlying pointer
  FiniteField* get();
  /// Get the underlying pointer
  FiniteField const* getc() const;
  /// Get maximum size of field element
  size_t GetElementMaxSize() const;

 private:
  struct State;
  std::unique_ptr<State> state_;
};

#endif  // EPID_COMMON_TESTHELPER_FINITE_FIELD_WRAPPER_TESTHELPER_H_
