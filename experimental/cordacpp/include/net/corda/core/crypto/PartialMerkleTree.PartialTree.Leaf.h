////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CRYPTO_PARTIALMERKLETREE_PARTIALTREE_LEAF_H
#define NET_CORDA_CORE_CRYPTO_PARTIALMERKLETREE_PARTIALTREE_LEAF_H

#include "corda.h"
#include "net/corda/core/crypto/PartialMerkleTree.PartialTree.h"

namespace net {
namespace corda {
namespace core {
namespace crypto {
class SecureHash;
class PartialMerkleTree$PartialTree;
class PartialMerkleTree$PartialTree$Leaf;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace crypto {

class PartialMerkleTree$PartialTree$Leaf : public net::corda::core::crypto::PartialMerkleTree$PartialTree {
public:
    net::corda::ptr<net::corda::core::crypto::SecureHash> hash;

    PartialMerkleTree$PartialTree$Leaf() = default;

    explicit PartialMerkleTree$PartialTree$Leaf(proton::codec::decoder &decoder) {
        net::corda::Parser::read_to(decoder, hash);
    }
};

}
}
}
}

net::corda::TypeRegistration Registration31("net.corda:b0I7redabq3docLLQ0qZVg==", [](proton::codec::decoder &decoder) { return new net::corda::core::crypto::PartialMerkleTree$PartialTree$Leaf(decoder); }); // NOLINT(cert-err58-cpp)

#endif