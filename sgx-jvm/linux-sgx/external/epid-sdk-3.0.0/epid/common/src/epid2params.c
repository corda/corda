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
 * \brief Intel(R) EPID 2.0 constant parameters implementation.
 */
#include "epid/common/src/epid2params.h"
#include "epid/common/src/memory.h"

/// create a new Finite Field Fp
static EpidStatus NewFp(Epid2Params const* param, FiniteField** Fp);
/// create a new Finite Field Fq
static EpidStatus NewFq(Epid2Params const* param, FiniteField** Fq);
/// create a new Finite Field Fq2
static EpidStatus NewFq2(Epid2Params const* param, FiniteField* Fq,
                         FiniteField** Fq2);
/// create a new Finite Field Fq6
EpidStatus NewFq6(Epid2Params const* param, FiniteField* Fq2, FfElement* xi,
                  FiniteField** Fq6);
/// create a new Elliptic curve group G1 over Fq
static EpidStatus NewG1(Epid2Params const* param, FiniteField* Fq,
                        EcGroup** G1);
/// create a new Elliptic curve group G2 over Fq2
static EpidStatus NewG2(Epid2Params const* param, BigNum* p, BigNum* q,
                        FiniteField* Fq, FiniteField* Fq2, EcGroup** G2);
/// create a new Finite Field Fq12
static EpidStatus NewGT(FiniteField* Fq6, FiniteField** GT);
/// create a new pairing state

/// Deallocate Finite Field Fp
static void DeleteFp(FiniteField** Fp);

/// Deallocate Finite Field Fq
static void DeleteFq(FiniteField** Fq);
/// Deallocate Finite Field Fq2
static void DeleteFq2(FiniteField** Fq2);
/// Deallocate Finite Field Fq6
static void DeleteFq6(FiniteField** Fq6);
/// Deallocate Elliptic curve group G1 over Fq
static void DeleteG1(EcGroup** G1);
/// Deallocate Elliptic curve group G2 over Fq2
static void DeleteG2(EcGroup** G2);
/// Deallocate Finite Field Fq12
static void DeleteGT(FiniteField** GT);

EpidStatus CreateEpid2Params(Epid2Params_** params) {
  EpidStatus result = kEpidErr;
  Epid2Params_* internal_param = NULL;
  BigNumStr t_str = {0};
  Epid2Params params_str = {
#include "epid/common/src/epid2params_ate.inc"
  };
  if (!params) {
    return kEpidBadArgErr;
  }
  do {
    internal_param = SAFE_ALLOC(sizeof(Epid2Params_));
    if (!internal_param) {
      result = kEpidMemAllocErr;
      break;
    }
    result = NewBigNum(sizeof(params_str.p), &internal_param->p);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadBigNum(&params_str.p, sizeof(params_str.p), internal_param->p);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewBigNum(sizeof(params_str.q), &internal_param->q);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadBigNum(&params_str.q, sizeof(params_str.q), internal_param->q);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewBigNum(sizeof(params_str.t), &internal_param->t);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadBigNum(&params_str.t, sizeof(params_str.t), internal_param->t);
    if (kEpidNoErr != result) {
      break;
    }
    internal_param->neg = (params_str.neg.data[0]) ? true : false;

    result = NewFp(&params_str, &internal_param->Fp);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFq(&params_str, &internal_param->Fq);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFq2(&params_str, internal_param->Fq, &internal_param->Fq2);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(internal_param->Fq2, &internal_param->xi);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadFfElement(internal_param->Fq2, &params_str.xi,
                           sizeof(params_str.xi), internal_param->xi);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFq6(&params_str, internal_param->Fq2, internal_param->xi,
                    &internal_param->Fq6);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewGT(internal_param->Fq6, &internal_param->GT);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewG1(&params_str, internal_param->Fq, &internal_param->G1);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewEcPoint(internal_param->G1, &internal_param->g1);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadEcPoint(internal_param->G1, &params_str.g1,
                         sizeof(params_str.g1), internal_param->g1);
    if (kEpidNoErr != result) {
      break;
    }
    result =
        NewG2(&params_str, internal_param->p, internal_param->q,
              internal_param->Fq, internal_param->Fq2, &internal_param->G2);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewEcPoint(internal_param->G2, &internal_param->g2);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadEcPoint(internal_param->G2, &params_str.g2,
                         sizeof(params_str.g2), internal_param->g2);
    if (kEpidNoErr != result) {
      break;
    }
    result = WriteBigNum(internal_param->t, sizeof(t_str), &t_str);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewPairingState(internal_param->G1, internal_param->G2,
                             internal_param->GT, &t_str, internal_param->neg,
                             &internal_param->pairing_state);
    if (kEpidNoErr != result) {
      break;
    }
    *params = internal_param;
    result = kEpidNoErr;
  } while (0);
  if (kEpidNoErr != result && internal_param) {
    DeletePairingState(&internal_param->pairing_state);

    DeleteEcPoint(&internal_param->g2);
    DeleteEcPoint(&internal_param->g1);

    DeleteBigNum(&internal_param->p);
    DeleteBigNum(&internal_param->q);
    DeleteBigNum(&internal_param->t);

    DeleteFp(&internal_param->Fp);
    DeleteFq(&internal_param->Fq);
    DeleteFq2(&internal_param->Fq2);
    DeleteFq6(&internal_param->Fq6);
    DeleteGT(&internal_param->GT);

    DeleteG1(&internal_param->G1);
    DeleteG2(&internal_param->G2);

    SAFE_FREE(internal_param);
  }
  return result;
}

void DeleteEpid2Params(Epid2Params_** epid_params) {
  if (epid_params && *epid_params) {
    DeletePairingState(&(*epid_params)->pairing_state);

    DeleteBigNum(&(*epid_params)->p);
    DeleteBigNum(&(*epid_params)->q);
    DeleteBigNum(&(*epid_params)->t);
    DeleteFfElement(&(*epid_params)->xi);
    DeleteEcPoint(&(*epid_params)->g1);
    DeleteEcPoint(&(*epid_params)->g2);

    DeleteFp(&(*epid_params)->Fp);
    DeleteFq(&(*epid_params)->Fq);
    DeleteFq2(&(*epid_params)->Fq2);
    DeleteFq6(&(*epid_params)->Fq6);
    DeleteGT(&(*epid_params)->GT);

    DeleteG1(&(*epid_params)->G1);
    DeleteG2(&(*epid_params)->G2);

    SAFE_FREE(*epid_params);
  }
}

static EpidStatus NewFp(Epid2Params const* param, FiniteField** Fp) {
  EpidStatus result = kEpidErr;
  if (!param || !Fp) {
    return kEpidBadArgErr;
  }
  result = NewFiniteField(&param->p, Fp);
  if (kEpidNoErr != result) {
    return result;
  }
  return kEpidNoErr;
}

static EpidStatus NewFq(Epid2Params const* param, FiniteField** Fq) {
  EpidStatus result = kEpidErr;
  if (!param || !Fq) {
    return kEpidBadArgErr;
  }
  result = NewFiniteField(&param->q, Fq);
  if (kEpidNoErr != result) {
    return result;
  }
  return kEpidNoErr;
}
EpidStatus NewFq2(Epid2Params const* param, FiniteField* Fq,
                  FiniteField** Fq2) {
  EpidStatus result = kEpidErr;
  FiniteField* Ff = NULL;
  FfElement* beta = NULL;
  FfElement* neg_beta = NULL;
  if (!param || !Fq || !Fq2) {
    return kEpidBadArgErr;
  }
  do {
    result = NewFfElement(Fq, &beta);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(Fq, &neg_beta);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadFfElement(Fq, &param->beta, sizeof(param->beta), beta);
    if (kEpidNoErr != result) {
      break;
    }
    result = FfNeg(Fq, beta, neg_beta);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFiniteFieldViaBinomalExtension(Fq, neg_beta, 2, &Ff);
    if (kEpidNoErr != result) {
      break;
    }
    *Fq2 = Ff;
    result = kEpidNoErr;
  } while (0);

  DeleteFfElement(&neg_beta);
  DeleteFfElement(&beta);

  return result;
}
EpidStatus NewFq6(Epid2Params const* param, FiniteField* Fq2, FfElement* xi,
                  FiniteField** Fq6) {
  EpidStatus result = kEpidErr;
  FiniteField* Ff = NULL;
  FfElement* neg_xi = NULL;
  if (!param || !Fq2 || !Fq6) {
    return kEpidBadArgErr;
  }
  do {
    result = NewFfElement(Fq2, &neg_xi);
    if (kEpidNoErr != result) {
      break;
    }
    result = FfNeg(Fq2, xi, neg_xi);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFiniteFieldViaBinomalExtension(Fq2, neg_xi, 3, &Ff);
    if (kEpidNoErr != result) {
      break;
    }
    *Fq6 = Ff;
    result = kEpidNoErr;
  } while (0);

  DeleteFfElement(&neg_xi);

  return result;
}
EpidStatus NewG1(Epid2Params const* param, FiniteField* Fq, EcGroup** G1) {
  EpidStatus result = kEpidErr;
  EcGroup* ec = NULL;
  FfElement* fq_a = NULL;
  FfElement* fq_b = NULL;
  FfElement* g1_x = NULL;
  FfElement* g1_y = NULL;
  BigNum* order = NULL;
  BigNum* cofactor = NULL;
  // h = 1;
  const BigNumStr h1 = {
      {{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01}}};

  if (!param || !Fq || !G1) {
    return kEpidBadArgErr;
  }
  do {
    // Create G1
    // G1 is an elliptic curve group E(Fq).It can be initialized as follows :
    //   1. Set G1 = E(Fq).init(p, q, n = p, h = 1, a = 0, b, g1.x, g1.y).
    // a = 0
    // NewFfelement is Identidy
    result = NewFfElement(Fq, &fq_a);
    if (kEpidNoErr != result) {
      break;
    }
    // b
    result = NewFfElement(Fq, &fq_b);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadFfElement(Fq, &param->b, sizeof(param->b), fq_b);
    if (kEpidNoErr != result) {
      break;
    }
    // g1.x
    result = NewFfElement(Fq, &g1_x);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadFfElement(Fq, &param->g1.x, sizeof(param->g1.x), g1_x);
    if (kEpidNoErr != result) {
      break;
    }
    // g1.y
    result = NewFfElement(Fq, &g1_y);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadFfElement(Fq, &param->g1.y, sizeof(param->g1.y), g1_y);
    if (kEpidNoErr != result) {
      break;
    }
    // order
    result = NewBigNum(sizeof(BigNumStr), &order);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadBigNum(&param->p, sizeof(param->p), order);
    if (kEpidNoErr != result) {
      break;
    }
    // cofactor
    result = NewBigNum(sizeof(BigNumStr), &cofactor);
    if (kEpidNoErr != result) {
      break;
    }

    result = ReadBigNum(&h1, sizeof(h1), cofactor);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewEcGroup(Fq, fq_a, fq_b, g1_x, g1_y, order, cofactor, &ec);
    if (kEpidNoErr != result) {
      break;
    }
    *G1 = ec;
    result = kEpidNoErr;
  } while (0);

  DeleteBigNum(&cofactor);
  DeleteBigNum(&order);
  DeleteFfElement(&g1_y);
  DeleteFfElement(&g1_x);
  DeleteFfElement(&fq_b);
  DeleteFfElement(&fq_a);

  return result;
}
EpidStatus NewG2(Epid2Params const* param, BigNum* p, BigNum* q,
                 FiniteField* Fq, FiniteField* Fq2, EcGroup** G2) {
  EpidStatus result = kEpidErr;
  EcGroup* ec = NULL;
  FfElement* a = NULL;
  FfElement* b = NULL;
  FfElement* fq_param_b = NULL;
  FfElement* x = NULL;
  FfElement* y = NULL;
  BigNum* order = NULL;
  BigNum* cofactor = NULL;
  if (!param || !Fq || !Fq2 || !G2) {
    return kEpidBadArgErr;
  }
  do {
    //   2. Set xi = (xi0, xi1) an element of Fq2.
    //   3. Let b', xi' be a temporary variable in Fq2.
    //   4. Compute xi' = Fq2.inverse(xi).
    //   5. Compute b' = Fq2.mul(xi', b).
    result = NewFfElement(Fq2, &b);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadFfElement(Fq2, &param->xi, sizeof(param->xi), b);
    if (kEpidNoErr != result) {
      break;
    }
    result = FfInv(Fq2, b, b);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(Fq, &fq_param_b);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadFfElement(Fq, &param->b, sizeof(param->b), fq_param_b);
    if (kEpidNoErr != result) {
      break;
    }
    result = FfMul(Fq2, b, fq_param_b, b);  // ??? overflow fq2*fq
    if (kEpidNoErr != result) {
      break;
    }
    //   6. Set g2.x = (g2.x[0], g2.x[1]) an element of Fq2.
    //   7. Set g2.y = (g2.y[0], g2.y[1]) an element of Fq2.
    result = NewFfElement(Fq2, &x);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadFfElement(Fq2, &param->g2.x, sizeof(param->g2.x), x);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(Fq2, &y);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadFfElement(Fq2, &param->g2.y, sizeof(param->g2.y), y);
    if (kEpidNoErr != result) {
      break;
    }
    //   8. set h = 2q - p, aka cofactor
    result = NewBigNum(2 * sizeof(param->q), &cofactor);
    if (kEpidNoErr != result) {
      break;
    }
    result = BigNumAdd(q, q, cofactor);
    if (kEpidNoErr != result) {
      break;
    }
    result = BigNumSub(cofactor, p, cofactor);
    if (kEpidNoErr != result) {
      break;
    }
    //   9. set n = p * h, AKA order
    result = NewBigNum(2 * sizeof(param->q), &order);
    if (kEpidNoErr != result) {
      break;
    }
    result = BigNumMul(p, cofactor, order);
    if (kEpidNoErr != result) {
      break;
    }
    // set a to identity, NewFfElement does it by default
    result = NewFfElement(Fq2, &a);
    if (kEpidNoErr != result) {
      break;
    }
    //   10. Set G2 = E(Fq2).init(p, param(Fq2), n, h, 0, b', g2.x, g2.y)
    result = NewEcGroup(Fq2, a, b, x, y, order, cofactor, &ec);
    if (kEpidNoErr != result) {
      break;
    }
    *G2 = ec;
    result = kEpidNoErr;
  } while (0);

  DeleteBigNum(&cofactor);
  DeleteBigNum(&order);
  DeleteFfElement(&y);
  DeleteFfElement(&x);
  DeleteFfElement(&b);
  DeleteFfElement(&a);
  DeleteFfElement(&fq_param_b);

  return result;
}
EpidStatus NewGT(FiniteField* Fq6, FiniteField** GT) {
  EpidStatus result = kEpidErr;
  FiniteField* Ff = NULL;
  FfElement* v = NULL;
  FfElement* neg_v = NULL;

  const Fq6ElemStr v_str = {
      {{{{{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}}},
         {{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}}}}},
       {{{{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01}}},
         {{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}}}}},
       {{{{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}}},
         {{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}}}}}}};

  if (!Fq6 || !GT) {
    return kEpidBadArgErr;
  }
  do {
    result = NewFfElement(Fq6, &v);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFfElement(Fq6, &neg_v);
    if (kEpidNoErr != result) {
      break;
    }
    result = ReadFfElement(Fq6, &v_str, sizeof(v_str), v);
    if (kEpidNoErr != result) {
      break;
    }
    result = FfNeg(Fq6, v, neg_v);
    if (kEpidNoErr != result) {
      break;
    }
    result = NewFiniteFieldViaBinomalExtension(Fq6, neg_v, 2, &Ff);
    if (kEpidNoErr != result) {
      break;
    }
    *GT = Ff;
    result = kEpidNoErr;
  } while (0);

  DeleteFfElement(&v);
  DeleteFfElement(&neg_v);

  return result;
}
static void DeleteFp(FiniteField** Fp) { DeleteFiniteField(Fp); }
static void DeleteFq(FiniteField** Fq) { DeleteFiniteField(Fq); }
static void DeleteFq2(FiniteField** Fq2) { DeleteFiniteField(Fq2); }
static void DeleteFq6(FiniteField** Fq6) { DeleteFiniteField(Fq6); }
static void DeleteG1(EcGroup** G1) { DeleteEcGroup(G1); }
static void DeleteG2(EcGroup** G2) { DeleteEcGroup(G2); }
static void DeleteGT(FiniteField** GT) { DeleteFiniteField(GT); }
