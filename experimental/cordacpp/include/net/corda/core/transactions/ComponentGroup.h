////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_TRANSACTIONS_COMPONENTGROUP_H
#define NET_CORDA_CORE_TRANSACTIONS_COMPONENTGROUP_H

#include "corda.h"
namespace net {
namespace corda {
namespace core {
namespace utilities {
class OpaqueBytes;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace transactions {

class ComponentGroup {
public:
    std::list<net::corda::ptr<net::corda::core::utilities::OpaqueBytes>> components;
    int32_t group_index;

    ComponentGroup() = default;

    explicit ComponentGroup(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.transactions.ComponentGroup", descriptor(), 2);
        net::corda::Parser::read_to(decoder, components);
        net::corda::Parser::read_to(decoder, group_index);
    }

    virtual const std::string descriptor() { return "net.corda:HneSPA89MGhpizVLE3wcOg=="; }
};

}
}
}
}

#endif