////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CRYPTO_PARTIALMERKLETREE_PARTIALTREE_H
#define NET_CORDA_CORE_CRYPTO_PARTIALMERKLETREE_PARTIALTREE_H

#include "corda.h"

// Pre-declarations to speed up processing and avoid circular header dependencies.
namespace net {
namespace corda {
namespace core {
namespace crypto {
class IncludedLeaf;
class Leaf;
class Node;
}
}
}
}

// End of pre-declarations.

namespace net {
namespace corda {
namespace core {
namespace crypto {

class PartialMerkleTree$PartialTree {
public:
    

    explicit PartialMerkleTree$PartialTree(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.crypto.PartialMerkleTree$PartialTree", descriptor(), 0);
        
    }

    const std::string descriptor() { return "net.corda:qoLLhCY17PZwVNR6EWxfBg=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif