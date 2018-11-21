////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CONTRACTS_ATTACHMENTCONSTRAINT_H
#define NET_CORDA_CORE_CONTRACTS_ATTACHMENTCONSTRAINT_H

#include "corda.h"


namespace net {
namespace corda {
namespace core {
namespace contracts {

class AttachmentConstraint {
public:
    

    AttachmentConstraint() = default;

    explicit AttachmentConstraint(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "interface net.corda.core.contracts.AttachmentConstraint", descriptor(), 0);
        
    }

    virtual const std::string descriptor() { return "net.corda:Mgf6/s2AjkZaT8/bU9nNSQ=="; }
};

}
}
}
}

#endif