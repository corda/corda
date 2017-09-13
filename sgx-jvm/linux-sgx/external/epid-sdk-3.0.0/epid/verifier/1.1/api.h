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
#ifndef EPID_VERIFIER_1_1_API_H_
#define EPID_VERIFIER_1_1_API_H_

#include <stddef.h>
#include "epid/common/stdtypes.h"
#include "epid/common/1.1/types.h"
#include "epid/common/errors.h"
#include "epid/verifier/api.h"

#ifdef __cplusplus
extern "C"{
#endif

/*!
 * \file
 * \brief Intel(R) EPID SDK verifier Intel(R) EPID 1.1 API.
 */

/// Intel(R) EPID 1.1 Verifier functionality
/*!
  \defgroup Epid11VerifierModule EPID 1.1 support

  To verify signatures coming from member devices that belong to an
  Intel&reg; EPID 1.1 group, you need to use Intel&reg; EPID 1.1
  verifier APIs.

  If you are acting as a verifier for both Intel&reg; EPID 1.1 and 2.0
  members, you can determine if you need version 1.1 or 2.0
  verification by checking the Intel&reg; EPID version field in the
  group public key file (see ::EpidParseFileHeader). You can also
  check the version in other binary issuer material, such as the
  GroupRL and SigRL.

  The 1.1 verifier APIs take a verifier context as input. Each
  verifier context (::Epid11VerifierCtx) represents a verifier for a
  single group.

  The Intel&reg; EPID 1.1 specification does not provide hash algorithm
  selection and verifier blacklist revocation. Therefore, APIs such as
  ::EpidVerifierSetHashAlg and ::EpidVerifierSetVerifierRl are not
  available.

  You can find the Intel&reg; EPID 1.1 API headers in the 1.1
  directories, for example, `epid/verifier/1.1/api.h`.

  Intel&reg; EPID 1.1 APIs and data structures are indicated with the
  "Epid11" prefix. For example, the Intel&reg; EPID 1.1 version of
  ::EpidParseGroupPubKeyFile is called ::Epid11ParseGroupPubKeyFile,
  and the Intel&reg; EPID 1.1 version of `GroupRl` is `Epid11GroupRl`.

  \ingroup EpidVerifierModule
 @{
*/

/// Internal context of Intel(R) EPID 1.1 verifier.
typedef struct Epid11VerifierCtx Epid11VerifierCtx;

/// Intel(R) EPID 1.1 Pre-computed verifier settings.
/*!
 Serialized form of the information about a verifier that remains stable for
 a given set of keys.

 This API supports Intel(R) EPID 1.1 verification.

 \note e12 = 0 implies that this data is not valid
 \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
support</b></a>
 */
#pragma pack(1)
typedef struct Epid11VerifierPrecomp {
  Epid11GroupId gid;    ///< group ID
  Epid11GtElemStr e12;  ///< an element in GT
  Epid11GtElemStr e22;  ///< an element in GT
  Epid11GtElemStr e2w;  ///< an element in GT
} Epid11VerifierPrecomp;
#pragma pack()

/// Creates a new Intel(R) EPID 1.1 verifier context.
/*!
 Must be called to create the verifier context that is used by
 other "Verifier" APIs.

 Allocates memory for the context, then initialize it.

 Epid11VerifierDelete() must be called to safely release the member context.

 \param[in] pub_key
 The group certificate.
 \param[in] precomp
 Optional pre-computed data. If NULL the value is computed internally and is
 readable using Epid11VerifierWritePrecomp().
 \param[out] ctx
 Newly constructed verifier context.

 \returns ::EpidStatus

 \note
 If the result is not ::kEpidNoErr the content of ctx is undefined.

 \see Epid11VerifierDelete
 \see Epid11VerifierWritePrecomp
 \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
support</b></a>
 */
EpidStatus Epid11VerifierCreate(Epid11GroupPubKey const* pub_key,
                                Epid11VerifierPrecomp const* precomp,
                                Epid11VerifierCtx** ctx);

/// Deletes an existing Intel(R) EPID 1.1 verifier context.
/*!
 Must be called to safely release a verifier context created using
 Epid11VerifierCreate().

 De-initializes the context, frees memory used by the context, and sets the
 context pointer to NULL.

 \param[in,out] ctx
 The verifier context. Can be NULL.

 \see Epid11VerifierCreate
 \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
support</b></a>
 */
void Epid11VerifierDelete(Epid11VerifierCtx** ctx);

/// Serializes the pre-computed Intel(R) EPID 1.1 verifier settings.
/*!

 \param[in] ctx
 The verifier context.
 \param[out] precomp
 The Serialized pre-computed verifier settings.

 \returns ::EpidStatus

 \note
 If the result is not ::kEpidNoErr the content of precomp is undefined.

 \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
support</b></a>
 */
EpidStatus Epid11VerifierWritePrecomp(Epid11VerifierCtx const* ctx,
                                      Epid11VerifierPrecomp* precomp);

/// Sets the Intel(R) EPID 1.1 private key based revocation list.
/*!
 The caller is responsible to for ensuring the revocation list is authorized,
 e.g signed by the issuer. The caller is also responsible checking the version
 of the revocation list. The call will fail if trying to set an older version
 of the revocation list than was last set.

 This API supports Intel(R) EPID 1.1 verification.

 \attention
 The memory pointed to by priv_rl will be accessed directly by the verifier
 until a new list is set or the verifier is destroyed. Do not modify the
 contents of this memory. The behavior of subsequent operations that rely on
 the revocation list will be undefined if the memory is modified.

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

 \see Epid11VerifierCreate
 \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
support</b></a>
 */
EpidStatus Epid11VerifierSetPrivRl(Epid11VerifierCtx* ctx,
                                   Epid11PrivRl const* priv_rl,
                                   size_t priv_rl_size);

/// Sets the Intel(R) EPID 1.1 signature based revocation list.
/*!
 The caller is responsible to for ensuring the revocation list is authorized,
 e.g signed by the issuer. The caller is also responsible checking the version
 of the revocation list. The call will fail if trying to set an older version
 of the revocation list than was last set.

 This API supports Intel(R) EPID 1.1 verification.

 \attention
 The memory pointed to by sig_rl will be accessed directly by the verifier
 until a new list is set or the verifier is destroyed. Do not modify the
 contents of this memory. The behavior of subsequent operations that rely on
 the revocation list will be undefined if the memory is modified.

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

 \see Epid11VerifierCreate
 \see SdkOverview_11Verifier \see <a
 href="group___epid11_verifier_module.html#details"><b>EPID 1.1
 support</b></a>
 */
EpidStatus Epid11VerifierSetSigRl(Epid11VerifierCtx* ctx,
                                  Epid11SigRl const* sig_rl,
                                  size_t sig_rl_size);

/// Sets the Intel(R) EPID 1.1 group based revocation list.
/*!
 The caller is responsible to for ensuring the revocation list is authorized,
 e.g signed by the issuer. The caller is also responsible checking the version
 of the revocation list. The call will fail if trying to set an older version
 of the revocation list than was last set.

 This API supports Intel(R) EPID 1.1 verification.

 \attention
 The memory pointed to by grp_rl will be accessed directly by the verifier
 until a new list is set or the verifier is destroyed. Do not modify the
 contents of this memory. The behavior of subsequent operations that rely on
 the revocation list will be undefined if the memory is modified.

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

 \see Epid11VerifierCreate
 \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
support</b></a>
 */
EpidStatus Epid11VerifierSetGroupRl(Epid11VerifierCtx* ctx,
                                    Epid11GroupRl const* grp_rl,
                                    size_t grp_rl_size);

/// Sets the basename to be used by a verifier.
/*!
 This API allows setting a zero length base name.

 \warning
 Not all members in the Intel(R) EPID 1.1 ecosystem may support zero length
 basenames. They may interpret a zero length basename as random base.

 \param[in, out] ctx
 The verifier context.
 \param[in] basename
 The basename. Pass NULL for random base.
 \param[in] basename_len
 Number of bytes in basename buffer. Must be 0 if basename is NULL.

 \returns ::EpidStatus

 \see Epid11VerifierCreate

*/
EpidStatus Epid11VerifierSetBasename(Epid11VerifierCtx* ctx,
                                     void const* basename, size_t basename_len);

/// Verifies an Intel(R) EPID 1.1  signature and checks revocation status.
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

 \note
 If the result is not ::kEpidNoErr or one of the values listed above the
 verify should de considered to have failed.

 \see Epid11VerifierCreate
 \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
support</b></a>
 */
EpidStatus Epid11Verify(Epid11VerifierCtx const* ctx,
                        Epid11Signature const* sig, size_t sig_len,
                        void const* msg, size_t msg_len);

/// Determines if two Intel(R) EPID 1.1 signatures are linked.
/*!
 The Intel(R) EPID scheme allows signatures to be linked. If basename
 option is specified when signing signatures with the same basename will be
 linkable. This linking capability allows the verifier, or anyone, to know
 whether two Intel(R) EPID signatures are generated by the same member.

 This API supports Intel(R) EPID 1.1 verification.

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
 The input signatures should be verified using Epid11VerifyBasicSig() before
 invocation. Behavior is undefined if either of the signatures cannot be
 verified.

 \see Epid11VerifyBasicSig
 \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
support</b></a>
 */
bool Epid11AreSigsLinked(Epid11BasicSignature const* sig1,
                         Epid11BasicSignature const* sig2);

/// Verifies an Intel(R) EPID 1.1 member signature without revocation checks.
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
 This function should be used in conjunction with Epid11NrVerify() and
 Epid11CheckPrivRlEntry().

 \note
 If the result is not ::kEpidNoErr the verify should be considered to have
 failed.

 \see Epid11VerifierCreate
 \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
support</b></a>
 */
EpidStatus Epid11VerifyBasicSig(Epid11VerifierCtx const* ctx,
                                Epid11BasicSignature const* sig,
                                void const* msg, size_t msg_len);

/// Verifies the non-revoked proof for a single Intel(R) EPID 1.1 signature
/// based revocation list entry.
/*!
 Used in constrained environments where, due to limited memory, it may not
 be possible to process through a large and potentially unbounded revocation
 list.

 This API supports Intel(R) EPID 1.1 verification.

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
 Sig should be verified using Epid11VerifyBasicSig() before invocation. Behavior
 is undefined if sig cannot be verified.

 \note
 This function should be used in conjunction with Epid11VerifyBasicSig() and
 Epid11CheckPrivRlEntry().

 \note
 If the result is not ::kEpidNoErr the verify should de considered to have
 failed.

 \see Epid11VerifierCreate
 \see Epid11VerifyBasicSig
 \see Epid11CheckPrivRlEntry
 \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
support</b></a>
 */
EpidStatus Epid11NrVerify(Epid11VerifierCtx const* ctx,
                          Epid11BasicSignature const* sig, void const* msg,
                          size_t msg_len, Epid11SigRlEntry const* sigrl_entry,
                          Epid11NrProof const* proof);

/// Verifies an Intel(R) EPID 1.1 signature has not been revoked in the
/// private key based revocation list.
/*!
 Used in constrained environments where, due to limited memory, it may not
 be possible to process through a large and potentially unbounded revocation
 list.

 This API supports Intel(R) EPID 1.1 verification.

 \param[in] ctx
 The verifier context.
 \param[in] sig
 The basic signature.
 \param[in] f
 The private key based revocation list entry.

 \note
 Sig should be verified using Epid11VerifyBasicSig() before invocation. Behavior
 is undefined if sig cannot be verified.

 \note
 This function should be used in conjunction with Epid11NrVerify() and
 Epid11VerifyBasicSig().

 \note
 If the result is not ::kEpidNoErr the verify should de considered to have
 failed.

 \returns ::EpidStatus
 \see Epid11VerifierCreate
 \see Epid11NrVerify
 \see Epid11VerifyBasicSig
 \see <a href="group___epid11_verifier_module.html#details"><b>EPID 1.1
support</b></a>
 */
EpidStatus Epid11CheckPrivRlEntry(Epid11VerifierCtx const* ctx,
                                  Epid11BasicSignature const* sig,
                                  FpElemStr const* f);

#ifdef __cplusplus
};
#endif

/*! @} */
#endif  // EPID_VERIFIER_1_1_API_H_
