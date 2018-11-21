////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CRYPTO_PARTIALMERKLETREE_PARTIALTREE_INCLUDEDLEAF_H
#define NET_CORDA_CORE_CRYPTO_PARTIALMERKLETREE_PARTIALTREE_INCLUDEDLEAF_H

#include "corda.h"
#include "net/corda/core/crypto/PartialMerkleTree.PartialTree.h"

namespace net {
namespace corda {
namespace core {
namespace crypto {
class SecureHash;
class PartialMerkleTree$PartialTree;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace crypto {

class PartialMerkleTree$PartialTree$IncludedLeaf : public net::corda::core::crypto::PartialMerkleTree$PartialTree {
public:
    net::corda::ptr<net::corda::core::crypto::SecureHash> hash;

    PartialMerkleTree$PartialTree$IncludedLeaf() = default;

    explicit PartialMerkleTree$PartialTree$IncludedLeaf(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.crypto.PartialMerkleTree$PartialTree$IncludedLeaf", descriptor(), 1);
        net::corda::Parser::read_to(decoder, hash);
    }

    virtual const std::string descriptor() { return "net.corda:K4VxVeBs9tyxPmQo1p4adA=="; }
};

}
}
}
}

#endif