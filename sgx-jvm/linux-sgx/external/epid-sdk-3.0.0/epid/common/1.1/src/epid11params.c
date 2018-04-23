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
* \brief Intel(R) EPID 1.1 constant parameters implementation.
*/
#include "epid/common/1.1/src/epid11params.h"
#include "epid/common/src/memory.h"
#include "epid/common/math/tatepairing.h"

/// Handle SDK Error with Break
#define BREAK_ON_EPID_ERROR(ret) \
  if (kEpidNoErr != (ret)) {     \
    break;                       \
  }

/// Count of elements in array
#define COUNT_OF(a) (sizeof(a) / sizeof((a)[0]))

/// create a new Finite Field Fqd
static EpidStatus NewFqd(Epid11Params const* params, FiniteField* Fq,
                         FiniteField** Fqd);

/// create a new Finite Field Fqk
EpidStatus NewFqk(Epid11Params const* params, FiniteField* Fq, FiniteField* Fqd,
                  FiniteField** Fqk);

/// create a new Elliptic curve group G1 over Fq
static EpidStatus NewG1(Epid11Params const* params, FiniteField* Fq,
                        EcGroup** G1);

/// create a new Elliptic curve group G2 over Fqd
static EpidStatus NewG2(Epid11Params const* params, FiniteField* Fq,
                        FiniteField* Fqd, EcGroup** G2);

/// create a new Elliptic curve group G3 over Fq'
static EpidStatus NewG3(Epid11Params const* params, FiniteField* Fq_tick,
                        EcGroup** G3);

EpidStatus CreateEpid11Params(Epid11Params_** params) {
  EpidStatus result = kEpidErr;
  Epid11Params_* _params = NULL;
  Epid11Params params_str = {
#include "epid/common/1.1/src/epid11params_tate.inc"
  };

  if (!params) return kEpidBadArgErr;

  do {
    _params = SAFE_ALLOC(sizeof(Epid11Params_));
    if (!_params) {
      result = kEpidMemAllocErr;
      break;
    }

    // BigNum* p;
    result = NewBigNum(sizeof(params_str.p), &_params->p);
    BREAK_ON_EPID_ERROR(result);
    result = ReadBigNum(&params_str.p, sizeof(params_str.p), _params->p);
    BREAK_ON_EPID_ERROR(result);
    // BigNum* p_tick;
    result = NewBigNum(sizeof(params_str.p_tick), &_params->p_tick);
    BREAK_ON_EPID_ERROR(result);
    result = ReadBigNum(&params_str.p_tick, sizeof(params_str.p_tick),
                        _params->p_tick);
    BREAK_ON_EPID_ERROR(result);

    // FiniteField* Fp;
    result = NewFiniteField(&params_str.p, &_params->Fp);
    BREAK_ON_EPID_ERROR(result);
    // FiniteField* Fq;
    result = NewFiniteField(&params_str.q, &_params->Fq);
    BREAK_ON_EPID_ERROR(result);
    // FiniteField* Fp_tick;
    result = NewFiniteField(&params_str.p_tick, &_params->Fp_tick);
    BREAK_ON_EPID_ERROR(result);
    // FiniteField* Fq_tick;
    result = NewFiniteField(&params_str.q_tick, &_params->Fq_tick);
    BREAK_ON_EPID_ERROR(result);
    // FiniteField* Fqd;
    result = NewFqd(&params_str, _params->Fq, &_params->Fqd);
    BREAK_ON_EPID_ERROR(result);

    // EcGroup* G1;
    result = NewG1(&params_str, _params->Fq, &_params->G1);
    BREAK_ON_EPID_ERROR(result);
    // EcGroup* G2;
    result = NewG2(&params_str, _params->Fq, _params->Fqd, &_params->G2);
    BREAK_ON_EPID_ERROR(result);
    // EcGroup* G3;
    result = NewG3(&params_str, _params->Fq_tick, &_params->G3);
    BREAK_ON_EPID_ERROR(result);
    // FiniteField* GT;
    result = NewFqk(&params_str, _params->Fq, _params->Fqd, &_params->GT);
    BREAK_ON_EPID_ERROR(result);

    // EcPoint* g1;
    result = NewEcPoint(_params->G1, &_params->g1);
    BREAK_ON_EPID_ERROR(result);
    result = ReadEcPoint(_params->G1, &params_str.g1, sizeof(params_str.g1),
                         _params->g1);
    BREAK_ON_EPID_ERROR(result);
    // EcPoint* g2;
    result = NewEcPoint(_params->G2, &_params->g2);
    BREAK_ON_EPID_ERROR(result);
    result = ReadEcPoint(_params->G2, &params_str.g2, sizeof(params_str.g2),
                         _params->g2);
    BREAK_ON_EPID_ERROR(result);
    // EcPoint* g3;
    result = NewEcPoint(_params->G3, &_params->g3);
    BREAK_ON_EPID_ERROR(result);
    result = ReadEcPoint(_params->G3, &params_str.g3, sizeof(params_str.g3),
                         _params->g3);
    BREAK_ON_EPID_ERROR(result);

    // Epid11PairingState* pairing_state;
    result = NewEpid11PairingState(_params->G1, _params->G2, _params->GT,
                                   &_params->pairing_state);
    BREAK_ON_EPID_ERROR(result);

    *params = _params;
    result = kEpidNoErr;
  } while (0);

  if (kEpidNoErr != result && _params) {
    DeleteEpid11PairingState(&_params->pairing_state);

    DeleteBigNum(&_params->p);
    DeleteBigNum(&_params->p_tick);
    DeleteEcPoint(&_params->g1);
    DeleteEcPoint(&_params->g2);
    DeleteEcPoint(&_params->g3);

    DeleteFiniteField(&_params->Fp);
    DeleteFiniteField(&_params->Fq);
    DeleteFiniteField(&_params->Fp_tick);
    DeleteFiniteField(&_params->Fq_tick);
    DeleteFiniteField(&_params->Fqd);
    DeleteFiniteField(&_params->GT);

    DeleteEcGroup(&_params->G1);
    DeleteEcGroup(&_params->G2);
    DeleteEcGroup(&_params->G3);
    SAFE_FREE(_params);
  }
  return result;
}

void DeleteEpid11Params(Epid11Params_** params) {
  if (params && *params) {
    DeleteEpid11PairingState(&(*params)->pairing_state);

    DeleteBigNum(&(*params)->p);
    DeleteBigNum(&(*params)->p_tick);
    DeleteEcPoint(&(*params)->g1);
    DeleteEcPoint(&(*params)->g2);
    DeleteEcPoint(&(*params)->g3);

    DeleteFiniteField(&(*params)->Fp);
    DeleteFiniteField(&(*params)->Fq);
    DeleteFiniteField(&(*params)->Fp_tick);
    DeleteFiniteField(&(*params)->Fq_tick);
    DeleteFiniteField(&(*params)->Fqd);
    DeleteFiniteField(&(*params)->GT);

    DeleteEcGroup(&(*params)->G1);
    DeleteEcGroup(&(*params)->G2);
    DeleteEcGroup(&(*params)->G3);

    SAFE_FREE(*params);
  }
}

EpidStatus NewFqd(Epid11Params const* params, FiniteField* Fq,
                  FiniteField** Fqd) {
  if (!params || !Fq || !Fqd) return kEpidBadArgErr;

  return NewFiniteFieldViaPolynomialExtension(Fq, params->coeff, 3, Fqd);
}

EpidStatus NewFqk(Epid11Params const* params, FiniteField* Fq, FiniteField* Fqd,
                  FiniteField** Fqk) {
  EpidStatus result = kEpidNoErr;
  FfElement* qnr = NULL;
  FfElement* neg_qnr = NULL;
  FfElement* ground_element = NULL;
  Fq3ElemStr ground_element_str = {0};

  if (!params || !Fq || !Fqd || !Fqk) return kEpidBadArgErr;

  do {
    result = NewFfElement(Fq, &qnr);
    BREAK_ON_EPID_ERROR(result);

    result = ReadFfElement(Fq, &(params->qnr), sizeof(params->qnr), qnr);
    BREAK_ON_EPID_ERROR(result);

    result = NewFfElement(Fq, &neg_qnr);
    BREAK_ON_EPID_ERROR(result);

    result = FfNeg(Fq, qnr, neg_qnr);
    BREAK_ON_EPID_ERROR(result);

    result = WriteFfElement(Fq, neg_qnr, &ground_element_str.a[0],
                            sizeof(ground_element_str.a[0]));
    BREAK_ON_EPID_ERROR(result);

    result = NewFfElement(Fqd, &ground_element);
    BREAK_ON_EPID_ERROR(result);

    result = ReadFfElement(Fqd, &(ground_element_str),
                           sizeof(ground_element_str), ground_element);
    BREAK_ON_EPID_ERROR(result);

    result = NewFiniteFieldViaBinomalExtension(Fqd, ground_element, 2, Fqk);
    BREAK_ON_EPID_ERROR(result);
  } while (0);

  DeleteFfElement(&qnr);
  DeleteFfElement(&neg_qnr);
  DeleteFfElement(&ground_element);

  return result;
}

EpidStatus NewG1(Epid11Params const* params, FiniteField* Fq, EcGroup** G1) {
  EpidStatus result = kEpidErr;
  EcGroup* ec = NULL;
  FfElement* fq_a = NULL;
  FfElement* fq_b = NULL;
  FfElement* g1_x = NULL;
  FfElement* g1_y = NULL;
  BigNum* order = NULL;
  BigNum* h = NULL;

  if (!params || !Fq || !G1) return kEpidBadArgErr;

  do {
    // Create G1
    // G1 is an elliptic curve group E(Fq).It can be initialized as follows:
    //   1. Set G1 = E(Fq).init(p, q, h, a, b, g1.x, g1.y).
    // a
    result = NewFfElement(Fq, &fq_a);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(Fq, &params->a, sizeof(params->a), fq_a);
    BREAK_ON_EPID_ERROR(result);
    // b
    result = NewFfElement(Fq, &fq_b);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(Fq, &params->b, sizeof(params->b), fq_b);
    BREAK_ON_EPID_ERROR(result);
    // g1.x
    result = NewFfElement(Fq, &g1_x);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(Fq, &params->g1.x, sizeof(params->g1.x), g1_x);
    BREAK_ON_EPID_ERROR(result);
    // g1.y
    result = NewFfElement(Fq, &g1_y);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(Fq, &params->g1.y, sizeof(params->g1.y), g1_y);
    BREAK_ON_EPID_ERROR(result);
    // order
    result = NewBigNum(sizeof(BigNumStr), &order);
    BREAK_ON_EPID_ERROR(result);
    result = ReadBigNum(&params->p, sizeof(params->p), order);
    BREAK_ON_EPID_ERROR(result);
    // h
    result = NewBigNum(sizeof(BigNumStr), &h);
    BREAK_ON_EPID_ERROR(result);
    result = ReadBigNum(&params->h, sizeof(params->h), h);
    BREAK_ON_EPID_ERROR(result);

    result = NewEcGroup(Fq, fq_a, fq_b, g1_x, g1_y, order, h, &ec);
    BREAK_ON_EPID_ERROR(result);
    *G1 = ec;
    result = kEpidNoErr;
  } while (0);

  DeleteBigNum(&h);
  DeleteBigNum(&order);
  DeleteFfElement(&g1_y);
  DeleteFfElement(&g1_x);
  DeleteFfElement(&fq_b);
  DeleteFfElement(&fq_a);

  return result;
}

EpidStatus NewG3(Epid11Params const* params, FiniteField* Fq_dash,
                 EcGroup** G3) {
  EpidStatus result = kEpidErr;
  EcGroup* ec = NULL;
  FfElement* fq_a = NULL;
  FfElement* fq_b = NULL;
  FfElement* g3_x = NULL;
  FfElement* g3_y = NULL;
  BigNum* order = NULL;
  BigNum* h_tick = NULL;

  if (!params || !Fq_dash || !G3) return kEpidBadArgErr;

  do {
    // Create G3
    // G3 is an elliptic curve group E(Fq').It can be initialized as follows:
    //   1. Set G3 = E(Fq').init(p', q', h', a', b', g3.x, g3.y).
    // a'
    result = NewFfElement(Fq_dash, &fq_a);
    BREAK_ON_EPID_ERROR(result);
    result =
        ReadFfElement(Fq_dash, &params->a_tick, sizeof(params->a_tick), fq_a);
    BREAK_ON_EPID_ERROR(result);
    // b'
    result = NewFfElement(Fq_dash, &fq_b);
    BREAK_ON_EPID_ERROR(result);
    result =
        ReadFfElement(Fq_dash, &params->b_tick, sizeof(params->b_tick), fq_b);
    BREAK_ON_EPID_ERROR(result);
    // g3.x
    result = NewFfElement(Fq_dash, &g3_x);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(Fq_dash, &params->g3.x, sizeof(params->g3.x), g3_x);
    BREAK_ON_EPID_ERROR(result);
    // g3.y
    result = NewFfElement(Fq_dash, &g3_y);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(Fq_dash, &params->g3.y, sizeof(params->g3.y), g3_y);
    BREAK_ON_EPID_ERROR(result);
    // order
    result = NewBigNum(sizeof(BigNumStr), &order);
    BREAK_ON_EPID_ERROR(result);
    result = ReadBigNum(&params->p_tick, sizeof(params->p_tick), order);
    BREAK_ON_EPID_ERROR(result);
    // h'
    result = NewBigNum(sizeof(BigNumStr), &h_tick);
    BREAK_ON_EPID_ERROR(result);
    result = ReadBigNum(&params->h_tick, sizeof(params->h_tick), h_tick);
    BREAK_ON_EPID_ERROR(result);

    result = NewEcGroup(Fq_dash, fq_a, fq_b, g3_x, g3_y, order, h_tick, &ec);
    BREAK_ON_EPID_ERROR(result);
    *G3 = ec;
    result = kEpidNoErr;
  } while (0);

  DeleteBigNum(&h_tick);
  DeleteBigNum(&order);
  DeleteFfElement(&g3_y);
  DeleteFfElement(&g3_x);
  DeleteFfElement(&fq_b);
  DeleteFfElement(&fq_a);

  return result;
}

EpidStatus NewG2(Epid11Params const* params, FiniteField* Fq, FiniteField* Fqd,
                 EcGroup** G2) {
  EpidStatus result = kEpidErr;
  EcGroup* ec = NULL;
  FfElement* fq_twista = NULL;
  FfElement* fq_twistb = NULL;
  FfElement* fqd_twista = NULL;
  FfElement* fqd_twistb = NULL;
  FfElement* g2_x = NULL;
  FfElement* g2_y = NULL;
  FfElement* qnr = NULL;
  BigNum* order = NULL;
  BigNum* h = NULL;
  Fq3ElemStr tmp_Fq3_str = {0};

  if (!params || !Fq || !Fqd || !G2) return kEpidBadArgErr;

  do {
    // Create G2
    // G2 is an elliptic curve group E(Fqd).It can be initialized as follows:
    // 2. Set g2.x = (g2.x[0], g2.x[1], g2.x[2]) an element of Fqd
    result = NewFfElement(Fqd, &g2_x);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(Fqd, &params->g2.x, sizeof(params->g2.x), g2_x);
    BREAK_ON_EPID_ERROR(result);
    // 3. Set g2.y = (g2.y[0], g2.y[1], g2.y[2]) an element of Fqd
    result = NewFfElement(Fqd, &g2_y);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(Fqd, &params->g2.y, sizeof(params->g2.y), g2_y);
    BREAK_ON_EPID_ERROR(result);
    // qnr
    result = NewFfElement(Fq, &qnr);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(Fq, &params->qnr, sizeof(params->qnr), qnr);
    BREAK_ON_EPID_ERROR(result);
    // 4. twista = (a * qnr * qnr) mod q
    result = NewFfElement(Fq, &fq_twista);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(Fq, &params->a, sizeof(params->a), fq_twista);
    BREAK_ON_EPID_ERROR(result);
    result = FfMul(Fq, fq_twista, qnr, fq_twista);
    BREAK_ON_EPID_ERROR(result);
    result = FfMul(Fq, fq_twista, qnr, fq_twista);
    BREAK_ON_EPID_ERROR(result);
    // twista = {twista, 0, 0}
    result = WriteFfElement(Fq, fq_twista, &(tmp_Fq3_str.a[0]),
                            sizeof(tmp_Fq3_str.a[0]));
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(Fqd, &fqd_twista);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(Fqd, &tmp_Fq3_str, sizeof(tmp_Fq3_str), fqd_twista);
    BREAK_ON_EPID_ERROR(result);
    // 5. twistb = (b * qnr * qnr * qnr) mod q
    result = NewFfElement(Fq, &fq_twistb);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(Fq, &params->b, sizeof(params->b), fq_twistb);
    BREAK_ON_EPID_ERROR(result);
    result = FfMul(Fq, fq_twistb, qnr, fq_twistb);
    BREAK_ON_EPID_ERROR(result);
    result = FfMul(Fq, fq_twistb, qnr, fq_twistb);
    BREAK_ON_EPID_ERROR(result);
    result = FfMul(Fq, fq_twistb, qnr, fq_twistb);
    BREAK_ON_EPID_ERROR(result);
    // twistb = {twistb, 0, 0}
    result = WriteFfElement(Fq, fq_twistb, &(tmp_Fq3_str.a[0]),
                            sizeof(tmp_Fq3_str.a[0]));
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(Fqd, &fqd_twistb);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(Fqd, &tmp_Fq3_str, sizeof(tmp_Fq3_str), fqd_twistb);
    BREAK_ON_EPID_ERROR(result);
    // order
    result = NewBigNum(3 * sizeof(BigNumStr), &order);
    BREAK_ON_EPID_ERROR(result);
    result = ReadBigNum(&params->orderG2, sizeof(params->orderG2), order);
    BREAK_ON_EPID_ERROR(result);
    // h
    result = NewBigNum(sizeof(BigNumStr), &h);
    BREAK_ON_EPID_ERROR(result);
    result = ReadBigNum(&params->h, sizeof(params->h), h);
    BREAK_ON_EPID_ERROR(result);

    // 6. Set G2 = E(Fqd).init(orderG2, param(Fqd), twista, twistb, g2.x, g2.y)
    result = NewEcGroup(Fqd, fqd_twista, fqd_twistb, g2_x, g2_y, order, h, &ec);
    BREAK_ON_EPID_ERROR(result);
    *G2 = ec;
    result = kEpidNoErr;
  } while (0);

  DeleteBigNum(&h);
  DeleteBigNum(&order);
  DeleteFfElement(&qnr);
  DeleteFfElement(&fqd_twistb);
  DeleteFfElement(&fq_twistb);
  DeleteFfElement(&fqd_twista);
  DeleteFfElement(&fq_twista);
  DeleteFfElement(&g2_y);
  DeleteFfElement(&g2_x);

  return result;
}
