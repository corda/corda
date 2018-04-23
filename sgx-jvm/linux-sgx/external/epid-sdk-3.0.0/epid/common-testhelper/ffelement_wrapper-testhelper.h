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
 * \brief FfElement C++ wrapper interface.
 */
#ifndef EPID_COMMON_TESTHELPER_FFELEMENT_WRAPPER_TESTHELPER_H_
#define EPID_COMMON_TESTHELPER_FFELEMENT_WRAPPER_TESTHELPER_H_

#include <memory>
#include <vector>

extern "C" {
#include "epid/common/math/bignum.h"
#include "epid/common/math/finitefield.h"
#include "epid/common/1.1/types.h"
}

class FiniteFieldObj;

/*!
Wrapper class to provide Resource Allocation is Initialization handling
for FfElement
*/
class FfElementObj {
 public:
  /// constructor
  FfElementObj();
  /// copy constructor
  FfElementObj(FfElementObj const& other);
  /// assignment operator
  FfElementObj& operator=(FfElementObj const& other);
  /// Create a FfElement
  explicit FfElementObj(FiniteFieldObj* ff);
  /// Create a FfElement
  FfElementObj(FiniteFieldObj* ff, FpElemStr const& bytes);
  /// Create a FfElement
  FfElementObj(FiniteFieldObj* ff, FqElemStr const& bytes);
  /// Create a FfElement
  FfElementObj(FiniteFieldObj* ff, Fq2ElemStr const& bytes);
  /// Create a FfElement
  FfElementObj(FiniteFieldObj* ff, Fq3ElemStr const& bytes);
  /// Create a FfElement
  FfElementObj(FiniteFieldObj* ff, Fq6ElemStr const& bytes);
  /// Create a FfElement
  FfElementObj(FiniteFieldObj* ff, Fq12ElemStr const& bytes);
  /// Create a FfElement
  FfElementObj(FiniteFieldObj* ff, std::vector<unsigned char> const& bytes);
  /// Create a FfElement
  FfElementObj(FiniteFieldObj* ff, void const* bytes, size_t size);
  /// Destroy the FfElement
  ~FfElementObj();
  /// cast operator to get the pointer to the stored FfElement
  operator FfElement*();
  /// const cast operator to get the pointer to the stored FfElement
  operator const FfElement*() const;
  /// Get the underlying pointer
  FfElement* get();
  /// Get the underlying pointer
  FfElement const* getc() const;
  /// Get element bytes
  std::vector<unsigned char> data() const;

 private:
  void init(FiniteFieldObj* ff, unsigned char const* bytes, size_t size);
  struct State;
  std::unique_ptr<State> state_;
};

#endif  // EPID_COMMON_TESTHELPER_FFELEMENT_WRAPPER_TESTHELPER_H_
