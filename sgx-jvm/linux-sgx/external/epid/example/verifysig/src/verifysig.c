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
 * \brief Signature verification implementation.
 */

#include "src/verifysig.h"

#include <stdlib.h>

#include "util/buffutil.h"
#include "util/envutil.h"

bool IsCaCertAuthorizedByRootCa(void const* data, size_t size) {
  // Implementation of this function is out of scope of the sample.
  // In an actual implementation Issuing CA certificate must be validated
  // with CA Root certificate before using it in parse functions.
  (void)data;
  (void)size;
  return true;
}

/// Authenticate and allocate revocation list
/*!  Utility function to authenticate revocation list and allocate a
  buffer to contain the parsed result
  \note caller is responsible for freeing free the memory allocated
 */
EpidStatus AuthenticateAndAllocateRl(void const* buf, size_t len,
                                     EpidCaCertificate const* cert,
                                     EpidFileType file_type, const char* name,
                                     void** new_rl, size_t* rl_len);

EpidStatus Verify(EpidSignature const* sig, size_t sig_len, void const* msg,
                  size_t msg_len, void const* basename, size_t basename_len,
                  void const* signed_priv_rl, size_t signed_priv_rl_size,
                  void const* signed_sig_rl, size_t signed_sig_rl_size,
                  void const* signed_grp_rl, size_t signed_grp_rl_size,
                  VerifierRl const* ver_rl, size_t ver_rl_size,
                  void const* signed_pub_key, size_t signed_pub_key_size,
                  EpidCaCertificate const* cacert, HashAlg hash_alg,
                  VerifierPrecomp* precomp, bool is_precomp_init) {
  EpidStatus result = kEpidErr;
  VerifierCtx* ctx = NULL;

  PrivRl* priv_rl = NULL;
  size_t priv_rl_size = 0;
  SigRl* sig_rl = NULL;
  size_t sig_rl_size = 0;
  GroupRl* grp_rl = NULL;
  size_t grp_rl_size = 0;

  do {
    GroupPubKey pub_key = {0};
    // authenticate and extract group public key
    result = EpidParseGroupPubKeyFile(signed_pub_key, signed_pub_key_size,
                                      cacert, &pub_key);
    if (kEpidNoErr != result) {
      break;
    }

    if (is_precomp_init && precomp) {
      // create verifier
      result = EpidVerifierCreate(&pub_key, precomp, &ctx);
      if (kEpidNoErr != result) {
        break;
      }
    } else {
      // create verifier
      result = EpidVerifierCreate(&pub_key, NULL, &ctx);
      if (kEpidNoErr != result) {
        break;
      }

      // initialize pre-computation blob
      result = EpidVerifierWritePrecomp(ctx, precomp);
      if (kEpidNoErr != result) {
        break;
      }
    }

    // set hash algorithm used for signing
    result = EpidVerifierSetHashAlg(ctx, hash_alg);
    if (kEpidNoErr != result) {
      break;
    }

    if (signed_priv_rl) {
      result = AuthenticateAndAllocateRl(signed_priv_rl, signed_priv_rl_size,
                                         cacert, kPrivRlFile, "PrivRl",
                                         (void**)&priv_rl, &priv_rl_size);
      if (kEpidNoErr != result) {
        break;
      }
      // set private key based revocation list
      result = EpidVerifierSetPrivRl(ctx, priv_rl, priv_rl_size);
      if (kEpidNoErr != result) {
        break;
      }
    }

    if (signed_sig_rl) {
      result = AuthenticateAndAllocateRl(signed_sig_rl, signed_sig_rl_size,
                                         cacert, kSigRlFile, "SigRl",
                                         (void**)&sig_rl, &sig_rl_size);
      if (kEpidNoErr != result) {
        break;
      }
      // set signature based revocation list
      result = EpidVerifierSetSigRl(ctx, sig_rl, sig_rl_size);
      if (kEpidNoErr != result) {
        break;
      }
    }

    if (signed_grp_rl) {
      result = AuthenticateAndAllocateRl(signed_grp_rl, signed_grp_rl_size,
                                         cacert, kGroupRlFile, "GroupRl",
                                         (void**)&grp_rl, &grp_rl_size);
      if (kEpidNoErr != result) {
        break;
      }
      // set group based revocation list
      result = EpidVerifierSetGroupRl(ctx, grp_rl, grp_rl_size);
      if (kEpidNoErr != result) {
        break;
      }
    }

    if (ver_rl) {
      // set verifier based revocation list
      result = EpidVerifierSetVerifierRl(ctx, ver_rl, ver_rl_size);
      if (kEpidNoErr != result) {
        break;
      }
    }

    // verify signature
    result =
        EpidVerify(ctx, sig, sig_len, msg, msg_len, basename, basename_len);
    if (kEpidNoErr != result) {
      break;
    }
  } while (0);

  // delete verifier
  EpidVerifierDelete(&ctx);

  if (priv_rl) free(priv_rl);
  if (sig_rl) free(sig_rl);
  if (grp_rl) free(grp_rl);

  return result;
}

EpidStatus AuthenticateAndAllocateRl(void const* buf, size_t len,
                                     EpidCaCertificate const* cert,
                                     EpidFileType file_type, const char* name,
                                     void** new_rl, size_t* rl_len) {
  typedef EpidStatus (*ParseFuncType)(void const* buf, size_t len,
                                      EpidCaCertificate const* cert,
                                      unsigned char* rl, size_t* rl_len);
  EpidStatus result = kEpidErr;
  void* parsed_rl = NULL;
  ParseFuncType ParseFunc = NULL;

  if (!buf || !cert || !new_rl || !rl_len || !name) {
    return kEpidBadArgErr;
  }

  switch (file_type) {
    case kPrivRlFile:
      ParseFunc = (ParseFuncType)&EpidParsePrivRlFile;
      break;
    case kSigRlFile:
      ParseFunc = (ParseFuncType)&EpidParseSigRlFile;
      break;
    case kGroupRlFile:
      ParseFunc = (ParseFuncType)&EpidParseGroupRlFile;
      break;
    default:
      return kEpidBadArgErr;
  }

  do {
    size_t parsed_len = 0;

    // authenticate and determine space needed for RL
    result = ParseFunc(buf, len, cert, NULL, &parsed_len);
    if (kEpidSigInvalid == result) {
      // authentication failure
      break;
    }
    if (kEpidNoErr != result) {
      break;
    }
    parsed_rl = AllocBuffer(parsed_len);
    if (!parsed_rl) {
      result = kEpidMemAllocErr;
      break;
    }

    // fill the rl
    result = ParseFunc(buf, len, cert, parsed_rl, &parsed_len);

    if (kEpidNoErr != result) {
      break;
    }

    *rl_len = parsed_len;
    *new_rl = parsed_rl;
  } while (0);

  if (kEpidNoErr != result) {
    if (parsed_rl) free(parsed_rl);
  }

  return result;
}
