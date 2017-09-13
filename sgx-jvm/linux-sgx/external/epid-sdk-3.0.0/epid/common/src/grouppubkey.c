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
 * \brief Group public key implementation.
 */
#include "epid/common/src/grouppubkey.h"
#include "epid/common/src/memory.h"

EpidStatus CreateGroupPubKey(GroupPubKey const* pub_key_str, EcGroup* G1,
                             EcGroup* G2, GroupPubKey_** pub_key) {
  EpidStatus result = kEpidErr;
  GroupPubKey_* pubkey = NULL;
  if (!pub_key_str || !G1 || !G2 || !pub_key) {
    return kEpidBadArgErr;
  }
  do {
    pubkey = SAFE_ALLOC(sizeof(GroupPubKey_));
    if (!pubkey) {
      result = kEpidMemAllocErr;
      break;
    }
    result = NewEcPoint(G1, &pubkey->h1);
    if (kEpidNoErr != result) {
      break;
    }
    result =
        ReadEcPoint(G1, &pub_key_str->h1, sizeof(pub_key_str->h1), pubkey->h1);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewEcPoint(G1, &pubkey->h2);
    if (kEpidNoErr != result) {
      break;
    }
    result =
        ReadEcPoint(G1, &pub_key_str->h2, sizeof(pub_key_str->h2), pubkey->h2);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewEcPoint(G2, &pubkey->w);
    if (kEpidNoErr != result) {
      break;
    }
    result =
        ReadEcPoint(G2, &pub_key_str->w, sizeof(pub_key_str->w), pubkey->w);
    if (kEpidNoErr != result) {
      break;
    }
    pubkey->gid = pub_key_str->gid;
    *pub_key = pubkey;
    result = kEpidNoErr;
  } while (0);

  if (kEpidNoErr != result && pubkey) {
    DeleteEcPoint(&pubkey->w);
    DeleteEcPoint(&pubkey->h2);
    DeleteEcPoint(&pubkey->h1);
    SAFE_FREE(pubkey);
  }
  return result;
}

void DeleteGroupPubKey(GroupPubKey_** pub_key) {
  if (pub_key && *pub_key) {
    DeleteEcPoint(&(*pub_key)->w);
    DeleteEcPoint(&(*pub_key)->h2);
    DeleteEcPoint(&(*pub_key)->h1);

    SAFE_FREE(*pub_key);
  }
}
