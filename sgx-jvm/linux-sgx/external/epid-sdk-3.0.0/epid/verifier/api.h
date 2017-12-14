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
#ifndef EPID_VERIFIER_API_H_
#define EPID_VERIFIER_API_H_

#include <stddef.h>
#include "epid/common/stdtypes.h"
#include "epid/common/types.h"
#include "epid/common/errors.h"

#ifdef __cplusplus
extern "C"{
#endif

/*!
 * \file
 * \brief Intel(R) EPID SDK verifier API.
 */

/// Verifier functionality
/*!
  \defgroup EpidVerifierModule verifier

  Defines the APIs needed by Intel(R) EPID verifiers. Each verifier
  context (::VerifierCtx) represents a verifier for a single group.

  \ingroup EpidModule
  @{
*/

/// Internal context of verifier.
typedef struct VerifierCtx VerifierCtx;

/// Pre-computed verifier settings.
/*!
 Serialized form of the information about a verifier that remains stable for
 a given set of keys.

 \note e12 = 0 implies that this data is not valid
 */
#pragma pack(1)
typedef struct VerifierPrecomp {
  GroupId gid;     ///< group ID
  GtElemStr e12;   ///< an element in GT
  GtElemStr e22;   ///< an element in GT
  GtElemStr e2w;   ///< an element in GT
  GtElemStr eg12;  ///< an element in GT
} VerifierPrecomp;
#pragma pack()

/// Creates a new verifier context.
/*!
 Must be called to create the verifier context that is used by
 other "Verifier" APIs.

 Allocates memory for the context, then initializes it.

 EpidVerifierDelete() must be called to safely release the member context.


 \param[in] pub_key
 The group certificate.
 \param[in] precomp
 Optional pre-computed data. If NULL the value is computed internally and is
 readable using EpidVerifierWritePrecomp().
 \param[out] ctx
 Newly constructed verifier context.

 \returns ::EpidStatus

 \note
 If the result is not ::kEpidNoErr the content of ctx is undefined.

 \see EpidVerifierDelete
 \see EpidVerifierWritePrecomp
 */
EpidStatus EpidVerifierCreate(GroupPubKey const* pub_key,
                              VerifierPrecomp const* precomp,
                              VerifierCtx** ctx);

/// Deletes an existing verifier context.
/*!
 Must be called to safely release a verifier context created using
 EpidVerifierCreate().

 De-initializes the context, frees memory used by the context, and sets the
 context pointer to NULL.

 \param[in,out] ctx
 The verifier context. Can be NULL.

 \see EpidVerifierCreate
 */
void EpidVerifierDelete(VerifierCtx** ctx);

/// Serializes the pre-computed verifier settings.
/*!
 \param[in] ctx
 The verifier context.
 \param[out] precomp
 The Serialized pre-computed verifier settings.
 \returns ::EpidStatus

 \note
 If the result is not ::kEpidNoErr the content of precomp is undefined.
 */
EpidStatus EpidVerifierWritePrecomp(VerifierCtx const* ctx,
                                    VerifierPrecomp* precomp);

/// Sets the private key based revocation list.
/*!
 The caller is responsible for ensuring the revocation list is authorized,
 e.g signed by the issuer. The caller is also responsible checking the version
 of the revocation list. The call fails if trying to set an older version
 of the revocation list than was last set.

 \attention
 The memory pointed to by priv_rl is accessed directly by the verifier
 until a new list is set or the verifier is destroyed. Do not modify the
 contents of this memory. The behavior of subsequent operations that rely on
 the revocation list is undefined if the memory is modified.

 \attention
 It is the responsibility of the caller to free the memory pointed to by priv_rl
 after the verifier is no longer using it.

 \param[in,out] ctx
 The verifier context.
 \param[in] priv_rl
 The private key based revocation list.
 \param[in] priv_rl_size
 The size of the private key based revocation list in bytes.

 \returns ::EpidStatus

 \note
 If the result is not ::kEpidNoErr the private key based revocation list
 pointed to by the verifier is undefined.

 \see EpidVerifierCreate
 */
EpidStatus EpidVerifierSetPrivRl(VerifierCtx* ctx, PrivRl const* priv_rl,
                                 size_t priv_rl_size);

/// Sets the signature based revocation list.
/*!
 The caller is responsible for ensuring the revocation list is authorized,
 e.g signed by the issuer. The caller is also responsible checking the version
 of the revocation list. The call fails if trying to set an older version
 of the revocation list than was last set.

 \attention
 The memory pointed to by sig_rl is accessed directly by the verifier
 until a new list is set or the verifier is destroyed. Do not modify the
 contents of this memory. The behavior of subsequent operations that rely on
 the revocation list is undefined if the memory is modified.

 \attention
 It is the responsibility of the caller to free the memory pointed to by sig_rl
 after the verifier is no longer using it.

 \param[in,out] ctx
 The verifier context.
 \param[in] sig_rl
 The signature based revocation list.
 \param[in] sig_rl_size
 The size of the signature based revocation list in bytes.

 \returns ::EpidStatus

 \note
 If the result is not ::kEpidNoErr the signature based revocation list pointed
 to by the verifier is undefined.

 \see EpidVerifierCreate
 */
EpidStatus EpidVerifierSetSigRl(VerifierCtx* ctx, SigRl const* sig_rl,
                                size_t sig_rl_size);

/// Sets the group based revocation list.
/*!
 The caller is responsible for ensuring the revocation list is authorized,
 e.g signed by the issuer. The caller is also responsible checking the version
 of the revocation list. The call fails if trying to set an older version
 of the revocation list than was last set.

 \attention
 The memory pointed to by grp_rl is accessed directly by the verifier
 until a new list is set or the verifier is destroyed. Do not modify the
 contents of this memory. The behavior of subsequent operations that rely on
 the revocation list is undefined if the memory is modified.

 \attention
 It is the responsibility of the caller to free the memory pointed to by grp_rl
 after the verifier is no longer using it.

 \param[in,out] ctx
 The verifier context.
 \param[in] grp_rl
 The group based revocation list.
 \param[in] grp_rl_size
 The size of the group based revocation list in bytes.

 \returns ::EpidStatus

 \note
 If the result is not ::kEpidNoErr the group based revocation list pointed
 to by the verifier is undefined.

 \see EpidVerifierCreate
 */
EpidStatus EpidVerifierSetGroupRl(VerifierCtx* ctx, GroupRl const* grp_rl,
                                  size_t grp_rl_size);

/// Sets the verifier revocation list.
/*!

 The caller is responsible for ensuring the revocation list is
 authorized. The caller is also responsible for checking the version
 of the revocation list. The call fails if trying to set an older
 version of the same revocation list than was last set.

 Once ::EpidVerifierSetVerifierRl returns, callers are free to release
 the memory pointed to by ver_rl.

 \param[in,out] ctx
 The verifier context.
 \param[in] ver_rl
 The verifier revocation list.
 \param[in] ver_rl_size
 The size of the verifier revocation list in bytes.

 \returns ::EpidStatus

 \note
 If the result is not ::kEpidNoErr the verifier revocation list pointed
 to by the verifier is undefined.

 \see EpidVerifierCreate
 \see EpidBlacklistSig
 \see EpidWriteVerifierRl
 */
EpidStatus EpidVerifierSetVerifierRl(VerifierCtx* ctx, VerifierRl const* ver_rl,
                                     size_t ver_rl_size);

/// Sets the hash algorithm to be used by a verifier.
/*!
 \param[in] ctx
 The verifier context.
 \param[in] hash_alg
 The hash algorithm to use.

 \returns ::EpidStatus

 \note
 If the result is not ::kEpidNoErr, the hash algorithm used by the
 verifier is undefined.

 \see EpidVerifierCreate
 \see ::HashAlg
 */
EpidStatus EpidVerifierSetHashAlg(VerifierCtx* ctx, HashAlg hash_alg);

/// Sets the basename to be used by a verifier.
/*!

  \note
  A successful call to this function will clear the current verifier
  blacklist.

  \param[in, out] ctx
  The verifier context.
  \param[in] basename
  The basename. Pass NULL for random base.
  \param[in] basename_len
  Number of bytes in basename buffer. Must be 0 if basename is NULL.

  \returns ::EpidStatus

  \see EpidVerifierCreate

 */
EpidStatus EpidVerifierSetBasename(VerifierCtx* ctx, void const* basename,
                                   size_t basename_len);

/// Verifies a signature and checks revocation status.
/*!
 \param[in] ctx
 The verifier context.
 \param[in] sig
 The signature.
 \param[in] sig_len
 The size of sig in bytes.
 \param[in] msg
 The message that was signed.
 \param[in] msg_len
 The size of msg in bytes.

 \returns ::EpidStatus

 \retval ::kEpidSigValid
 Signature validated successfully
 \retval ::kEpidSigInvalid
 Signature is invalid
 \retval ::kEpidSigRevokedInGroupRl
 Signature revoked in GroupRl
 \retval ::kEpidSigRevokedInPrivRl
 Signature revoked in PrivRl
 \retval ::kEpidSigRevokedInSigRl
 Signature revoked in SigRl
 \retval ::kEpidSigRevokedInVerifierRl
 Signature revoked in VerifierRl

 \note
 If the result is not ::kEpidNoErr or one of the values listed above the
 verify should be considered to have failed.

 \see EpidVerifierCreate
 \see EpidSignBasic
 \see EpidSign
 */
EpidStatus EpidVerify(VerifierCtx const* ctx, EpidSignature const* sig,
                      size_t sig_len, void const* msg, size_t msg_len);

/// Determines if two signatures are linked.
/*!

  The Intel(R) EPID scheme allows signatures to be linked. If basename
  option is specified when signing, signatures with the same basename
  are linkable. This linking capability allows the verifier, or
  anyone, to know whether two Intel(R) EPID signatures are generated
  by the same member.

 \param[in] sig1
 A basic signature.
 \param[in] sig2
 A basic signature.

 \result bool

 \retval true
 if the signatures were generated by the same member
 \retval false
 if it couldn't be determined if the signatures were generated by
 the same member

 \note
 The input signatures should be verified using EpidVerifyBasicSig() before
 invocation. Behavior is undefined if either of the signatures cannot be
 verified.

 \see EpidVerifyBasicSig
 \see EpidSignBasic
 \see EpidSign
 */
bool EpidAreSigsLinked(BasicSignature const* sig1, BasicSignature const* sig2);

/// Verifies a member signature without revocation checks.
/*!
 Used in constrained environments where, due to limited memory, it may not
 be possible to process through a large and potentially unbounded revocation
 list.

 \param[in] ctx
 The verifier context.
 \param[in] sig
 The basic signature.
 \param[in] msg
 The message that was signed.
 \param[in] msg_len
 The size of msg in bytes.

 \returns ::EpidStatus

 \note
 This function should be used in conjunction with EpidNrVerify() and
 EpidCheckPrivRlEntry().

 \note
 If the result is not ::kEpidNoErr the verify should be considered to have
 failed.

 \see EpidVerifierCreate
 \see EpidSignBasic
 \see EpidSign
 */
EpidStatus EpidVerifyBasicSig(VerifierCtx const* ctx, BasicSignature const* sig,
                              void const* msg, size_t msg_len);

/// Verifies the non-revoked proof for a single signature based revocation list
/// entry.
/*!
 Used in constrained environments where, due to limited memory, it may not
 be possible to process through a large and potentially unbounded revocation
 list.

 \param[in] ctx
 The verifier context.
 \param[in] sig
 The basic signature.
 \param[in] msg
 The message that was signed.
 \param[in] msg_len
 The size of msg in bytes.
 \param[in] sigrl_entry
 The signature based revocation list entry.
 \param[in] proof
 The non-revoked proof.

 \returns ::EpidStatus

 \note
 Sig should be verified using EpidVerifyBasicSig() before invocation. Behavior
 is undefined if sig cannot be verified.

 \note
 This function should be used in conjunction with EpidVerifyBasicSig() and
 EpidCheckPrivRlEntry().

 \note
 If the result is not ::kEpidNoErr, the verification should be
 considered to have failed.

 \see EpidVerifierCreate
 \see EpidVerifyBasicSig
 \see EpidCheckPrivRlEntry
 */
EpidStatus EpidNrVerify(VerifierCtx const* ctx, BasicSignature const* sig,
                        void const* msg, size_t msg_len,
                        SigRlEntry const* sigrl_entry, NrProof const* proof);

/// Verifies a signature has not been revoked in the private key based
/// revocation list.
/*!
 Used in constrained environments where, due to limited memory, it may not
 be possible to process through a large and potentially unbounded revocation
 list.

 \param[in] ctx
 The verifier context.
 \param[in] sig
 The basic signature.
 \param[in] f
 The private key based revocation list entry.

 \note
 Sig should be verified using EpidVerifyBasicSig() before invocation. Behavior
 is undefined if sig cannot be verified.

 \note
 This function should be used in conjunction with EpidNrVerify() and
 EpidVerifyBasicSig().

 \note
 If the result is not ::kEpidNoErr the verify should be considered to have
 failed.

 \returns ::EpidStatus
 \see EpidVerifierCreate
 \see EpidNrVerify
 \see EpidVerifyBasicSig
 */
EpidStatus EpidCheckPrivRlEntry(VerifierCtx const* ctx,
                                BasicSignature const* sig, FpElemStr const* f);

/// Returns the number of bytes required to serialize the verifier blacklist
/*!

  Use this function to determine the buffer size required by
  ::EpidWriteVerifierRl.

  \param[in] ctx
  The verifier context.

  \returns
  Size in bytes required to serialize the verifier blacklist

  \see EpidVerifierCreate
  \see EpidVerifierSetVerifierRl
  \see EpidBlacklistSig
  \see EpidWriteVerifierRl
*/
size_t EpidGetVerifierRlSize(VerifierCtx const* ctx);

/// Serializes the verifier blacklist to a buffer.
/*!

  If the current blacklist is empty or not set a valid empty verifier
  blacklist will be serialized.

  Use ::EpidGetVerifierRlSize to determine the buffer size required to
  serialize the verifier blacklist.

  \param[in] ctx
  The verifier context.
  \param[out] ver_rl
  An existing buffer in which to write the verifier revocation list.
  \param[in] ver_rl_size
  The size of the caller allocated output buffer in bytes.

  \returns ::EpidStatus

  \see EpidVerifierCreate
  \see EpidVerifierSetVerifierRl
  \see EpidBlacklistSig
  \see EpidGetVerifierRlSize
*/
EpidStatus EpidWriteVerifierRl(VerifierCtx const* ctx, VerifierRl* ver_rl,
                               size_t ver_rl_size);

/// Adds a valid name-based signature to the verifier blacklist.
/*!

  If the signature is not valid it will not be added to the blacklist.

  \param[in] ctx
  The verifier context.
  \param[in] sig
  The name-based signature to revoke.
  \param[in] sig_len
  The size of sig in bytes.
  \param[in] msg
  The message that was signed.
  \param[in] msg_len
  The size of msg in bytes.

  \returns ::EpidStatus

  \see EpidVerifierCreate
  \see EpidVerifierSetVerifierRl
  \see EpidWriteVerifierRl
*/
EpidStatus EpidBlacklistSig(VerifierCtx* ctx, EpidSignature const* sig,
                            size_t sig_len, void const* msg, size_t msg_len);

/*! @} */

#ifdef __cplusplus
}
#endif

#endif  // EPID_VERIFIER_API_H_
