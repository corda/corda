////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_TRANSACTIONS_COMPONENTGROUP_H
#define NET_CORDA_CORE_TRANSACTIONS_COMPONENTGROUP_H

#include "corda.h"

// Pre-declarations to speed up processing and avoid circular header dependencies.
namespace net {
namespace corda {
namespace core {
namespace utilities {
class OpaqueBytes;
}
}
}
}

// End of pre-declarations.

namespace net {
namespace corda {
namespace core {
namespace transactions {

class ComponentGroup {
public:
    std::list<net::corda::ptr<net::corda::core::utilities::OpaqueBytes>> components;
    int32_t group_index;

    explicit ComponentGroup(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.transactions.ComponentGroup", descriptor(), 2);
        net::corda::Parser::read_to(decoder, components);
        net::corda::Parser::read_to(decoder, group_index);
    }

    const std::string descriptor() { return "net.corda:wKH9HLUz+O8Y2A4oOnl0yQ=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif