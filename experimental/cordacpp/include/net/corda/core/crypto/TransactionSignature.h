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
class TransactionSignature;
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
        net::corda::Parser::read_to(decoder, by);
        net::corda::Parser::read_to(decoder, bytes);
        if (decoder.next_type() != proton::NULL_TYPE) net::corda::Parser::read_to(decoder, partial_merkle_tree); else decoder.next();
        net::corda::Parser::read_to(decoder, signature_metadata);
    }
};

}
}
}
}

net::corda::TypeRegistration Registration6("net.corda:JDgI4T6c+qDdhNXY0kFjiQ==", [](proton::codec::decoder &decoder) { return new net::corda::core::crypto::TransactionSignature(decoder); }); // NOLINT(cert-err58-cpp)

#endif