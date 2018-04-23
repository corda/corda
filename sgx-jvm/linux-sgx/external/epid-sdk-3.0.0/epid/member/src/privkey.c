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
 * \brief Private key implementation.
 */

#include "epid/common/src/memory.h"
#include "epid/member/src/privkey.h"

EpidStatus CreatePrivKey(PrivKey const* priv_key_str, EcGroup* G1,
                         FiniteField* Fp, PrivKey_** priv_key) {
  EpidStatus result = kEpidErr;
  PrivKey_* priv_key_ = NULL;

  // check parameters
  if (!priv_key_str || !G1 || !Fp || !priv_key) return kEpidBadArgErr;

  do {
    priv_key_ = SAFE_ALLOC(sizeof(*priv_key_));

    if (!priv_key_) {
      result = kEpidMemAllocErr;
      break;
    }

    result = NewEcPoint(G1, &priv_key_->A);
    if (kEpidNoErr != result) break;

    result = NewFfElement(Fp, &priv_key_->x);
    if (kEpidNoErr != result) break;

    result = NewFfElement(Fp, &priv_key_->f);
    if (kEpidNoErr != result) break;

    priv_key_->gid = priv_key_str->gid;

    result = ReadEcPoint(G1, &priv_key_str->A, sizeof(priv_key_str->A),
                         priv_key_->A);
    if (kEpidNoErr != result) break;

    result = ReadFfElement(Fp, &priv_key_str->x, sizeof(priv_key_str->x),
                           priv_key_->x);
    if (kEpidNoErr != result) break;

    result = ReadFfElement(Fp, &priv_key_str->f, sizeof(priv_key_str->f),
                           priv_key_->f);
    if (kEpidNoErr != result) break;

    *priv_key = priv_key_;
    result = kEpidNoErr;
  } while (0);

  if (kEpidNoErr != result) {
    DeletePrivKey(&priv_key_);
  }

  return (result);
}

void DeletePrivKey(PrivKey_** priv_key) {
  if (priv_key) {
    if (*priv_key) {
      DeleteEcPoint(&((*priv_key)->A));
      DeleteFfElement(&((*priv_key)->x));
      DeleteFfElement(&((*priv_key)->f));
    }
    SAFE_FREE(*priv_key);
  }
}
