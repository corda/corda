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
 * \brief EpidRequestJoin implementation.
 */

#include <epid/member/api.h>
#include <string.h>
#include "epid/common/src/epid2params.h"
#include "epid/common/math/finitefield.h"
#include "epid/common/math/ecgroup.h"

#pragma pack(1)
/// Storage for values to create commitment in Sign and Verify algorithms
typedef struct JoinPCommitValues {
  BigNumStr p;     ///< Intel(R) EPID 2.0 parameter p
  G1ElemStr g1;    ///< Intel(R) EPID 2.0 parameter g1
  G2ElemStr g2;    ///< Intel(R) EPID 2.0 parameter g2
  G1ElemStr h1;    ///< Group public key value h1
  G1ElemStr h2;    ///< Group public key value h2
  G2ElemStr w;     ///< Group public key value w
  G1ElemStr F;     ///< Variable F computed in algorithm
  G1ElemStr R;     ///< Variable R computed in algorithm
  IssuerNonce NI;  ///< Nonce
} JoinPCommitValues;
#pragma pack()

/// Handle SDK Error with Break
#define BREAK_ON_EPID_ERROR(ret) \
  if (kEpidNoErr != (ret)) {     \
    break;                       \
  }

EpidStatus EpidRequestJoin(GroupPubKey const* pub_key, IssuerNonce const* ni,
                           FpElemStr const* f, BitSupplier rnd_func,
                           void* rnd_param, HashAlg hash_alg,
                           JoinRequest* join_request) {
  EpidStatus sts;
  static const BigNumStr one = {
      {{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}}};
  BigNumStr r_str;
  JoinPCommitValues commit_values;
  Epid2Params_* params = NULL;
  FfElement* r_el = NULL;
  FfElement* f_el = NULL;
  FfElement* c_el = NULL;
  FfElement* cf_el = NULL;
  FfElement* s_el = NULL;
  EcPoint* f_pt = NULL;
  EcPoint* r_pt = NULL;
  EcPoint* h1_pt = NULL;

  if (!pub_key || !ni || !f || !rnd_func || !join_request) {
    return kEpidBadArgErr;
  }
  if (kSha256 != hash_alg && kSha384 != hash_alg && kSha512 != hash_alg) {
    return kEpidBadArgErr;
  }

  do {
    sts = CreateEpid2Params(&params);
    BREAK_ON_EPID_ERROR(sts);
    if (!params->Fp || !params->G1) {
      sts = kEpidBadArgErr;
      break;
    }
    sts = NewFfElement(params->Fp, &r_el);
    BREAK_ON_EPID_ERROR(sts);
    sts = NewFfElement(params->Fp, &f_el);
    BREAK_ON_EPID_ERROR(sts);
    sts = NewFfElement(params->Fp, &c_el);
    BREAK_ON_EPID_ERROR(sts);
    sts = NewFfElement(params->Fp, &cf_el);
    BREAK_ON_EPID_ERROR(sts);
    sts = NewFfElement(params->Fp, &s_el);
    BREAK_ON_EPID_ERROR(sts);
    sts = NewEcPoint(params->G1, &f_pt);
    BREAK_ON_EPID_ERROR(sts);
    sts = NewEcPoint(params->G1, &h1_pt);
    BREAK_ON_EPID_ERROR(sts);
    sts = NewEcPoint(params->G1, &r_pt);
    BREAK_ON_EPID_ERROR(sts);

    sts = ReadFfElement(params->Fp, (uint8_t const*)f, sizeof(*f), f_el);
    BREAK_ON_EPID_ERROR(sts);
    sts = ReadEcPoint(params->G1, (uint8_t*)&pub_key->h1, sizeof(pub_key->h1),
                      h1_pt);
    BREAK_ON_EPID_ERROR(sts);

    // Step 1. The member chooses a random integer r from [1, p-1].
    sts = FfGetRandom(params->Fp, &one, rnd_func, rnd_param, r_el);
    BREAK_ON_EPID_ERROR(sts);
    sts = WriteFfElement(params->Fp, r_el, (uint8_t*)&r_str, sizeof(r_str));

    // Step 2. The member computes F = G1.sscmExp(h1, f).
    sts = EcExp(params->G1, h1_pt, (BigNumStr const*)f, f_pt);
    BREAK_ON_EPID_ERROR(sts);

    // Step 3. The member computes R = G1.sscmExp(h1, r).
    sts = EcExp(params->G1, h1_pt, (BigNumStr const*)&r_str, r_pt);
    BREAK_ON_EPID_ERROR(sts);

    // Step 4. The member computes c = Fp.hash(p || g1 || g2 || h1 || h2 || w ||
    // F || R || NI). Refer to Section 7.1 for hash operation over a prime
    // field.
    sts = WriteBigNum(params->p, sizeof(commit_values.p),
                      (uint8_t*)&commit_values.p);
    BREAK_ON_EPID_ERROR(sts);
    sts = WriteEcPoint(params->G1, params->g1, (uint8_t*)&commit_values.g1,
                       sizeof(commit_values.g1));
    BREAK_ON_EPID_ERROR(sts);
    sts = WriteEcPoint(params->G2, params->g2, (uint8_t*)&commit_values.g2,
                       sizeof(commit_values.g2));
    BREAK_ON_EPID_ERROR(sts);
    commit_values.h1 = pub_key->h1;
    commit_values.h2 = pub_key->h2;
    commit_values.w = pub_key->w;
    sts = WriteEcPoint(params->G1, f_pt, (uint8_t*)&commit_values.F,
                       sizeof(commit_values.F));
    BREAK_ON_EPID_ERROR(sts);
    sts = WriteEcPoint(params->G1, r_pt, (uint8_t*)&commit_values.R,
                       sizeof(commit_values.R));
    BREAK_ON_EPID_ERROR(sts);
    commit_values.NI = *ni;
    sts = FfHash(params->Fp, (uint8_t*)&commit_values, sizeof(commit_values),
                 hash_alg, c_el);
    BREAK_ON_EPID_ERROR(sts);

    // Step 5. The member computes s = (r + c * f) mod p.
    sts = FfMul(params->Fp, c_el, f_el, cf_el);
    BREAK_ON_EPID_ERROR(sts);
    sts = FfAdd(params->Fp, r_el, cf_el, s_el);
    BREAK_ON_EPID_ERROR(sts);

    // Step 6. The output join request is (F, c, s).
    sts = WriteFfElement(params->Fp, c_el, (uint8_t*)&join_request->c,
                         sizeof(join_request->c));
    BREAK_ON_EPID_ERROR(sts);
    sts = WriteFfElement(params->Fp, s_el, (uint8_t*)&join_request->s,
                         sizeof(join_request->s));
    BREAK_ON_EPID_ERROR(sts);
    sts = WriteEcPoint(params->G1, f_pt, (uint8_t*)&join_request->F,
                       sizeof(join_request->F));
    BREAK_ON_EPID_ERROR(sts);

    sts = kEpidNoErr;
  } while (0);
  DeleteEcPoint(&h1_pt);
  DeleteEcPoint(&r_pt);
  DeleteEcPoint(&f_pt);
  DeleteFfElement(&s_el);
  DeleteFfElement(&cf_el);
  DeleteFfElement(&c_el);
  DeleteFfElement(&f_el);
  DeleteFfElement(&r_el);
  DeleteEpid2Params(&params);
  return sts;
}

// implements section 3.2.2 "Validation of Private Key" from
// Intel(R) EPID 2.0 Spec
bool EpidIsPrivKeyInGroup(GroupPubKey const* pub_key, PrivKey const* priv_key) {
  bool result;

  // Intel(R) EPID Parameters
  Epid2Params_* params = NULL;
  PairingState* ps = NULL;

  // private key
  EcPoint* a_pt = NULL;    // an element in G1
  FfElement* x_el = NULL;  // an integer between [1, p-1]
  FfElement* f_el = NULL;  // an integer between [1, p-1]

  // public key
  EcPoint* h1_pt = NULL;  // an element in G1
  EcPoint* h2_pt = NULL;  // an element in G1
  EcPoint* w_pt = NULL;   // an element in G2

  // local variables
  EcPoint* t1_pt = NULL;    // an element in G2
  EcPoint* t2_pt = NULL;    // an element in G1
  FfElement* t3_el = NULL;  // an element in GT
  FfElement* t4_el = NULL;  // an element in GT

  if (!pub_key || !priv_key) {
    return false;
  }

  do {
    EpidStatus sts;
    EcGroup* G1 = NULL;
    EcGroup* G2 = NULL;
    FiniteField* GT = NULL;
    FiniteField* Fp = NULL;
    BigNumStr t_str = {0};

    sts = CreateEpid2Params(&params);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    G1 = params->G1;
    G2 = params->G2;
    GT = params->GT;
    Fp = params->Fp;

    sts = WriteBigNum(params->t, sizeof(t_str), &t_str);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    sts = NewPairingState(G1, G2, GT, &t_str, params->neg, &ps);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    // Load private key
    sts = NewEcPoint(G1, &a_pt);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    sts = ReadEcPoint(G1, &priv_key->A, sizeof(priv_key->A), a_pt);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    sts = NewFfElement(Fp, &x_el);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    sts = ReadFfElement(Fp, &priv_key->x, sizeof(priv_key->x), x_el);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    sts = NewFfElement(Fp, &f_el);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    sts = ReadFfElement(Fp, &priv_key->f, sizeof(priv_key->f), f_el);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    // Load public key
    sts = NewEcPoint(G1, &h1_pt);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    sts = ReadEcPoint(G1, &pub_key->h1, sizeof(pub_key->h1), h1_pt);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    sts = NewEcPoint(G1, &h2_pt);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    sts = ReadEcPoint(G1, &pub_key->h2, sizeof(pub_key->h2), h2_pt);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    sts = NewEcPoint(G2, &w_pt);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    sts = ReadEcPoint(G2, &pub_key->w, sizeof(pub_key->w), w_pt);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    // local variables
    sts = NewEcPoint(G2, &t1_pt);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    sts = NewEcPoint(G1, &t2_pt);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    sts = NewFfElement(GT, &t3_el);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    sts = NewFfElement(GT, &t4_el);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    // Step 1. The member verifies that the gid in the public key matches the
    //         gid in the private key.
    if (0 != memcmp(&pub_key->gid, &priv_key->gid, sizeof(priv_key->gid))) {
      result = false;
      break;
    }

    // Step 2. The member computes t1 = G2.sscmExp(g2, x).
    sts = EcSscmExp(G2, params->g2, (BigNumStr const*)&priv_key->x, t1_pt);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    // Step 3. The member computes t1 = G2.mul(t1, w).
    sts = EcMul(G2, t1_pt, w_pt, t1_pt);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    // Step 4. The member computes t3 = pairing(A, t1).
    sts = Pairing(ps, t3_el, a_pt, t1_pt);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    // Step 5. The member computes t2 = G1.sscmExp(h1, f).
    sts = EcSscmExp(G1, h1_pt, (BigNumStr const*)&priv_key->f, t2_pt);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    // Step 6. The member computes t2 = G1.mul(t2, g1).
    sts = EcMul(G1, t2_pt, params->g1, t2_pt);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    // Step 7. The member computes t4 = pairing(t2, g2).
    sts = WriteBigNum(params->t, sizeof(t_str), &t_str);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    sts = Pairing(ps, t4_el, t2_pt, params->g2);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }

    // Step 8. If GT.isEqual(t3, t4) = false, reports bad private key.
    sts = FfIsEqual(GT, t3_el, t4_el, &result);
    if (kEpidNoErr != sts) {
      result = false;
      break;
    }
  } while (0);

  // local variables
  DeleteFfElement(&t4_el);
  DeleteFfElement(&t3_el);
  DeleteEcPoint(&t2_pt);
  DeleteEcPoint(&t1_pt);

  // public key
  DeleteEcPoint(&w_pt);
  DeleteEcPoint(&h2_pt);
  DeleteEcPoint(&h1_pt);

  // private key
  DeleteFfElement(&f_el);
  DeleteFfElement(&x_el);
  DeleteEcPoint(&a_pt);

  // Intel(R) EPID Parameters
  DeletePairingState(&ps);
  DeleteEpid2Params(&params);

  return result;
}
