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
class PartialMerkleTree$PartialTree;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace crypto {

class PartialMerkleTree$PartialTree : public net::corda::Any {
public:
    

    PartialMerkleTree$PartialTree() = default;

    explicit PartialMerkleTree$PartialTree(proton::codec::decoder &decoder) {
        
    }
};

}
}
}
}

net::corda::TypeRegistration Registration20("net.corda:qoLLhCY17PZwVNR6EWxfBg==", [](proton::codec::decoder &decoder) { return new net::corda::core::crypto::PartialMerkleTree$PartialTree(decoder); }); // NOLINT(cert-err58-cpp)

#endif