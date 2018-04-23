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
 * \brief EpidSignBasic implementation.
 */

#include <string.h>  // memset

#include "epid/common/src/stack.h"
#include "epid/member/api.h"
#include "epid/member/src/context.h"

/// Handle SDK Error with Break
#define BREAK_ON_EPID_ERROR(ret) \
  if (kEpidNoErr != (ret)) {     \
    break;                       \
  }

EpidStatus EpidSignBasic(MemberCtx const* ctx, void const* msg, size_t msg_len,
                         void const* basename, size_t basename_len,
                         BasicSignature* sig) {
  EpidStatus result = kEpidErr;
  // Values to be affected by basename
  EcPoint* B = NULL;
  EcPoint* K = NULL;
  EcPoint* R1 = NULL;
  // data from presig
  EcPoint* T = NULL;
  FfElement* a = NULL;
  FfElement* b = NULL;
  FfElement* rx = NULL;
  FfElement* rf = NULL;
  FfElement* ra = NULL;
  FfElement* rb = NULL;
  FfElement* R2 = NULL;

  // final calculatoin data
  FfElement* sx = NULL;
  FfElement* sf = NULL;
  FfElement* sa = NULL;
  FfElement* sb = NULL;
  FfElement* c_hash = NULL;
  // priv key data, need to clear after use
  BigNumStr f_str = {0};
  if (!ctx || !sig) {
    return kEpidBadArgErr;
  }
  if (!msg && (0 != msg_len)) {
    // if message is non-empty it must have both length and content
    return kEpidBadArgErr;
  }
  if (!basename && (0 != basename_len)) {
    // if basename is non-empty it must have both length and content
    return kEpidBadArgErr;
  }
  if (!ctx->epid2_params || !ctx->priv_key || !ctx->epid2_params->G1 ||
      !ctx->epid2_params->GT || !ctx->epid2_params->Fp || !ctx->priv_key->f) {
    return kEpidBadArgErr;
  }

  do {
    PreComputedSignature curr_presig;
    G1ElemStr B_str = {0};
    G1ElemStr K_str = {0};
    CommitValues commit_values = ctx->commit_values;

    // create all required elemnts
    result = NewEcPoint(ctx->epid2_params->G1, &B);
    BREAK_ON_EPID_ERROR(result);
    result = NewEcPoint(ctx->epid2_params->G1, &K);
    BREAK_ON_EPID_ERROR(result);
    result = NewEcPoint(ctx->epid2_params->G1, &R1);
    BREAK_ON_EPID_ERROR(result);

    result = NewEcPoint(ctx->epid2_params->G1, &T);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ctx->epid2_params->GT, &R2);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ctx->epid2_params->Fp, &sx);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ctx->epid2_params->Fp, &sf);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ctx->epid2_params->Fp, &sa);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ctx->epid2_params->Fp, &sb);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ctx->epid2_params->Fp, &c_hash);
    BREAK_ON_EPID_ERROR(result);

    result = NewFfElement(ctx->epid2_params->Fp, &a);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ctx->epid2_params->Fp, &b);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ctx->epid2_params->Fp, &rx);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ctx->epid2_params->Fp, &rf);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ctx->epid2_params->Fp, &ra);
    BREAK_ON_EPID_ERROR(result);
    result = NewFfElement(ctx->epid2_params->Fp, &rb);
    BREAK_ON_EPID_ERROR(result);

    if (StackGetSize(ctx->presigs)) {
      // Use existing pre-computed signature
      if (!StackPopN(ctx->presigs, 1, &curr_presig)) {
        result = kEpidErr;
        break;
      }
    } else {
      // generate a new pre-computed signature
      result = EpidComputePreSig(ctx, &curr_presig);
      BREAK_ON_EPID_ERROR(result);
    }
    // 3.  If the pre-computed signature pre-sigma exists, the member
    //     loads (B, K, T, a, b, rx, rf, ra, rb, R1, R2) from
    //     pre-sigma. Refer to Section 4.4 for the computation of
    //     these values.
    result = ReadEcPoint(ctx->epid2_params->G1, &curr_presig.B,
                         sizeof(curr_presig.B), B);
    BREAK_ON_EPID_ERROR(result);
    result = ReadEcPoint(ctx->epid2_params->G1, &curr_presig.K,
                         sizeof(curr_presig.K), K);
    BREAK_ON_EPID_ERROR(result);
    result = ReadEcPoint(ctx->epid2_params->G1, &curr_presig.T,
                         sizeof(curr_presig.T), T);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(ctx->epid2_params->Fp, &curr_presig.a,
                           sizeof(curr_presig.a), a);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(ctx->epid2_params->Fp, &curr_presig.b,
                           sizeof(curr_presig.b), b);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(ctx->epid2_params->Fp, &curr_presig.rx,
                           sizeof(curr_presig.rx), rx);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(ctx->epid2_params->Fp, &curr_presig.rf,
                           sizeof(curr_presig.rf), rf);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(ctx->epid2_params->Fp, &curr_presig.ra,
                           sizeof(curr_presig.ra), ra);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(ctx->epid2_params->Fp, &curr_presig.rb,
                           sizeof(curr_presig.rb), rb);
    BREAK_ON_EPID_ERROR(result);
    result = ReadEcPoint(ctx->epid2_params->G1, &curr_presig.R1,
                         sizeof(curr_presig.R1), R1);
    BREAK_ON_EPID_ERROR(result);
    result = ReadFfElement(ctx->epid2_params->GT, &curr_presig.R2,
                           sizeof(curr_presig.R2), R2);
    BREAK_ON_EPID_ERROR(result);

    if (basename) {
      // If basename is provided, the member does the following:
      // make sure basename is registered/allowed
      if (!ContainsBasename(ctx->allowed_basenames, basename, basename_len)) {
        result = kEpidBadArgErr;
        break;
      } else {
        // basename valid, can modify parameters
        //   a. The member computes B = G1.hash(bsn).
        result = EcHash(ctx->epid2_params->G1, basename, basename_len,
                        ctx->hash_alg, B);
        BREAK_ON_EPID_ERROR(result);
        //   b. The member computes K = G1.sscmExp(B, f), where B comes
        //      from step a.
        result = WriteFfElement(ctx->epid2_params->Fp, ctx->priv_key->f, &f_str,
                                sizeof(f_str));
        BREAK_ON_EPID_ERROR(result);
        result = EcSscmExp(ctx->epid2_params->G1, B, &f_str, K);
        BREAK_ON_EPID_ERROR(result);
        //   c. The member computes R1 = G1.sscmExp(B, rf), where B comes
        //      from step a.
        result = EcSscmExp(ctx->epid2_params->G1, B,
                           (const BigNumStr*)&curr_presig.rf, R1);
        BREAK_ON_EPID_ERROR(result);
        //   d. The member over-writes the B, K, and R1 values.
      }
    }
    // 5.  The member computes t3 = Fp.hash(p || g1 || g2 || h1 || h2
    //     || w || B || K || T || R1 || R2). Refer to Section 7.1 for
    //     hash operation over a prime field.
    // 6.  The member computes c = Fp.hash(t3 || m).
    result = WriteEcPoint(ctx->epid2_params->G1, B, &B_str, sizeof(B_str));
    BREAK_ON_EPID_ERROR(result);
    result = WriteEcPoint(ctx->epid2_params->G1, K, &K_str, sizeof(K_str));
    BREAK_ON_EPID_ERROR(result);
    result = SetCalculatedCommitValues(&B_str, &K_str, &curr_presig.T, R1,
                                       ctx->epid2_params->G1, R2,
                                       ctx->epid2_params->GT, &commit_values);
    BREAK_ON_EPID_ERROR(result);
    result = CalculateCommitmentHash(&commit_values, ctx->epid2_params->Fp,
                                     ctx->hash_alg, msg, msg_len, c_hash);
    BREAK_ON_EPID_ERROR(result);
    // 7.  The member computes sx = (rx + c * x) mod p.
    result = FfMul(ctx->epid2_params->Fp, c_hash, ctx->priv_key->x, sx);
    BREAK_ON_EPID_ERROR(result);
    result = FfAdd(ctx->epid2_params->Fp, rx, sx, sx);
    // 8.  The member computes sf = (rf + c * f) mod p.
    result = FfMul(ctx->epid2_params->Fp, c_hash, ctx->priv_key->f, sf);
    BREAK_ON_EPID_ERROR(result);
    result = FfAdd(ctx->epid2_params->Fp, rf, sf, sf);
    BREAK_ON_EPID_ERROR(result);
    // 9.  The member computes sa = (ra + c * a) mod p.
    result = FfMul(ctx->epid2_params->Fp, c_hash, a, sa);
    BREAK_ON_EPID_ERROR(result);
    result = FfAdd(ctx->epid2_params->Fp, ra, sa, sa);
    BREAK_ON_EPID_ERROR(result);
    // 10. The member computes sb = (rb + c * b) mod p.
    result = FfMul(ctx->epid2_params->Fp, c_hash, b, sb);
    BREAK_ON_EPID_ERROR(result);
    result = FfAdd(ctx->epid2_params->Fp, rb, sb, sb);
    BREAK_ON_EPID_ERROR(result);
    // 11. The member sets sigma0 = (B, K, T, c, sx, sf, sa, sb).
    result = WriteEcPoint(ctx->epid2_params->G1, B, &sig->B, sizeof(sig->B));
    BREAK_ON_EPID_ERROR(result);
    result = WriteEcPoint(ctx->epid2_params->G1, K, &sig->K, sizeof(sig->K));
    BREAK_ON_EPID_ERROR(result);
    result = WriteEcPoint(ctx->epid2_params->G1, T, &sig->T, sizeof(sig->T));
    BREAK_ON_EPID_ERROR(result);
    result =
        WriteFfElement(ctx->epid2_params->Fp, c_hash, &sig->c, sizeof(sig->c));
    BREAK_ON_EPID_ERROR(result);
    result =
        WriteFfElement(ctx->epid2_params->Fp, sx, &sig->sx, sizeof(sig->sx));
    BREAK_ON_EPID_ERROR(result);
    result =
        WriteFfElement(ctx->epid2_params->Fp, sf, &sig->sf, sizeof(sig->sf));
    BREAK_ON_EPID_ERROR(result);
    result =
        WriteFfElement(ctx->epid2_params->Fp, sa, &sig->sa, sizeof(sig->sa));
    BREAK_ON_EPID_ERROR(result);
    result =
        WriteFfElement(ctx->epid2_params->Fp, sb, &sig->sb, sizeof(sig->sb));
    BREAK_ON_EPID_ERROR(result);
    result = kEpidNoErr;
  } while (0);
  // remove all data
  DeleteEcPoint(&B);
  DeleteEcPoint(&K);
  DeleteEcPoint(&R1);

  DeleteEcPoint(&T);
  DeleteFfElement(&R2);
  DeleteFfElement(&sx);
  DeleteFfElement(&sf);
  DeleteFfElement(&sa);
  DeleteFfElement(&sb);
  DeleteFfElement(&c_hash);
  DeleteFfElement(&a);
  DeleteFfElement(&b);
  DeleteFfElement(&rx);
  DeleteFfElement(&rf);
  DeleteFfElement(&ra);
  DeleteFfElement(&rb);

  return result;
}
