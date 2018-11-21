////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CRYPTO_PARTIALMERKLETREE_PARTIALTREE_H
#define NET_CORDA_CORE_CRYPTO_PARTIALMERKLETREE_PARTIALTREE_H

#include "corda.h"
namespace net {
namespace corda {
namespace core {
namespace crypto {
class PartialMerkleTree$PartialTree$IncludedLeaf;
class PartialMerkleTree$PartialTree$Leaf;
class PartialMerkleTree$PartialTree$Node;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace crypto {

class PartialMerkleTree$PartialTree {
public:
    

    PartialMerkleTree$PartialTree() = default;

    explicit PartialMerkleTree$PartialTree(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.crypto.PartialMerkleTree$PartialTree", descriptor(), 0);
        
    }

    virtual const std::string descriptor() { return "net.corda:qoLLhCY17PZwVNR6EWxfBg=="; }
};

}
}
}
}

#endif