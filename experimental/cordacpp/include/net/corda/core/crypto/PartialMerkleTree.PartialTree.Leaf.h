////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CRYPTO_PARTIALMERKLETREE_PARTIALTREE_LEAF_H
#define NET_CORDA_CORE_CRYPTO_PARTIALMERKLETREE_PARTIALTREE_LEAF_H

#include "corda.h"

// Pre-declarations to speed up processing and avoid circular header dependencies.
namespace net {
namespace corda {
namespace core {
namespace crypto {
class SecureHash;
class PartialTree;
}
}
}
}

// End of pre-declarations.

namespace net {
namespace corda {
namespace core {
namespace crypto {

class PartialMerkleTree$PartialTree$Leaf : public net::corda::core::crypto::PartialMerkleTree$PartialTree {
public:
    net::corda::ptr<net::corda::core::crypto::SecureHash> hash;

    explicit PartialMerkleTree$PartialTree$Leaf(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.crypto.PartialMerkleTree$PartialTree$Leaf", descriptor(), 1);
        net::corda::Parser::read_to(decoder, hash);
    }

    const std::string descriptor() { return "net.corda:KhuFP+KmaBglnaD66Yll7A=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif