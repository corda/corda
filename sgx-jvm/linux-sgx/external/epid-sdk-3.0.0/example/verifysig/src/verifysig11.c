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
 * \brief EPID 1.1 signature verification implementation.
 */

#include "src/verifysig11.h"

#include <stdlib.h>

#include "util/buffutil.h"
#include "util/envutil.h"
#include "epid/verifier/1.1/api.h"
#include "epid/common/1.1/file_parser.h"

EpidStatus Verify11(Epid11Signature const* sig, size_t sig_len, void const* msg,
                    size_t msg_len, void const* basename, size_t basename_len,
                    void const* signed_priv_rl, size_t signed_priv_rl_size,
                    void const* signed_sig_rl, size_t signed_sig_rl_size,
                    void const* signed_grp_rl, size_t signed_grp_rl_size,
                    void const* signed_pub_key, size_t signed_pub_key_size,
                    EpidCaCertificate const* cacert,
                    Epid11VerifierPrecomp* verifier_precomp,
                    bool verifier_precomp_is_input) {
  EpidStatus result = kEpidErr;
  Epid11VerifierCtx* ctx = NULL;
  Epid11PrivRl* priv_rl = NULL;
  Epid11SigRl* sig_rl = NULL;
  Epid11GroupRl* grp_rl = NULL;

  do {
    Epid11GroupPubKey pub_key = {0};
    // authenticate and extract group public key
    result = Epid11ParseGroupPubKeyFile(signed_pub_key, signed_pub_key_size,
                                        cacert, &pub_key);
    if (kEpidNoErr != result) {
      break;
    }

    // create verifier
    result = Epid11VerifierCreate(
        &pub_key, verifier_precomp_is_input ? verifier_precomp : NULL, &ctx);
    if (kEpidNoErr != result) {
      break;
    }

    // serialize verifier pre-computation blob
    result = Epid11VerifierWritePrecomp(ctx, verifier_precomp);
    if (kEpidNoErr != result) {
      break;
    }

    // set the basename used for signing
    result = Epid11VerifierSetBasename(ctx, basename, basename_len);
    if (kEpidNoErr != result) {
      break;
    }

    if (signed_priv_rl) {
      // authenticate and determine space needed for RL
      size_t priv_rl_size = 0;
      result = Epid11ParsePrivRlFile(signed_priv_rl, signed_priv_rl_size,
                                     cacert, NULL, &priv_rl_size);
      if (kEpidSigInvalid == result) {
        // authentication failure
        break;
      }
      if (kEpidNoErr != result) {
        break;
      }

      priv_rl = AllocBuffer(priv_rl_size);
      if (!priv_rl) {
        result = kEpidMemAllocErr;
        break;
      }

      // fill the rl
      result = Epid11ParsePrivRlFile(signed_priv_rl, signed_priv_rl_size,
                                     cacert, priv_rl, &priv_rl_size);
      if (kEpidNoErr != result) {
        break;
      }

      // set private key based revocation list
      result = Epid11VerifierSetPrivRl(ctx, priv_rl, priv_rl_size);
      if (kEpidNoErr != result) {
        break;
      }
    }  // if (signed_priv_rl)

    if (signed_sig_rl) {
      // authenticate and determine space needed for RL
      size_t sig_rl_size = 0;
      result = Epid11ParseSigRlFile(signed_sig_rl, signed_sig_rl_size, cacert,
                                    NULL, &sig_rl_size);
      if (kEpidSigInvalid == result) {
        // authentication failure
        break;
      }
      if (kEpidNoErr != result) {
        break;
      }

      sig_rl = AllocBuffer(sig_rl_size);
      if (!sig_rl) {
        result = kEpidMemAllocErr;
        break;
      }

      // fill the rl
      result = Epid11ParseSigRlFile(signed_sig_rl, signed_sig_rl_size, cacert,
                                    sig_rl, &sig_rl_size);
      if (kEpidNoErr != result) {
        break;
      }

      // set signature based revocation list
      result = Epid11VerifierSetSigRl(ctx, sig_rl, sig_rl_size);
      if (kEpidNoErr != result) {
        break;
      }
    }  // if (signed_sig_rl)

    if (signed_grp_rl) {
      // authenticate and determine space needed for RL
      size_t grp_rl_size = 0;
      result = Epid11ParseGroupRlFile(signed_grp_rl, signed_grp_rl_size, cacert,
                                      NULL, &grp_rl_size);
      if (kEpidSigInvalid == result) {
        // authentication failure
        break;
      }
      if (kEpidNoErr != result) {
        break;
      }

      grp_rl = AllocBuffer(grp_rl_size);
      if (!grp_rl) {
        result = kEpidMemAllocErr;
        break;
      }

      // fill the rl
      result = Epid11ParseGroupRlFile(signed_grp_rl, signed_grp_rl_size, cacert,
                                      grp_rl, &grp_rl_size);
      if (kEpidNoErr != result) {
        break;
      }
      // set group revocation list
      result = Epid11VerifierSetGroupRl(ctx, grp_rl, grp_rl_size);
      if (kEpidNoErr != result) {
        break;
      }
    }  // if (signed_grp_rl)

    // verify signature
    result = Epid11Verify(ctx, sig, sig_len, msg, msg_len);
    if (kEpidNoErr != result) {
      break;
    }
  } while (0);

  // delete verifier
  Epid11VerifierDelete(&ctx);

  if (priv_rl) free(priv_rl);
  if (sig_rl) free(sig_rl);
  if (grp_rl) free(grp_rl);

  return result;
}
