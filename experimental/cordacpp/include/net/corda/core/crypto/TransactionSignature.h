////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CRYPTO_TRANSACTIONSIGNATURE_H
#define NET_CORDA_CORE_CRYPTO_TRANSACTIONSIGNATURE_H

#include "corda.h"
#include "net/corda/core/crypto/DigitalSignature.h"

namespace java {
namespace security {
class PublicKey;
}
}
namespace net {
namespace corda {
namespace core {
namespace crypto {
class PartialMerkleTree;
class SignatureMetadata;
class DigitalSignature;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace crypto {

class TransactionSignature : public net::corda::core::crypto::DigitalSignature {
public:
    net::corda::ptr<java::security::PublicKey> by;
    proton::binary bytes;
    net::corda::ptr<net::corda::core::crypto::PartialMerkleTree> partial_merkle_tree;
    net::corda::ptr<net::corda::core::crypto::SignatureMetadata> signature_metadata;

    TransactionSignature() = default;

    explicit TransactionSignature(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.crypto.TransactionSignature", descriptor(), 4);
        net::corda::Parser::read_to(decoder, by);
        net::corda::Parser::read_to(decoder, bytes);
        if (decoder.next_type() != proton::NULL_TYPE) net::corda::Parser::read_to(decoder, partial_merkle_tree); else decoder.next();
        net::corda::Parser::read_to(decoder, signature_metadata);
    }

    virtual const std::string descriptor() { return "net.corda:JDgI4T6c+qDdhNXY0kFjiQ=="; }
};

}
}
}
}

#endif