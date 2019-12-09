#pragma once

#include <iosfwd>
#include "include/CordaBytes.h"
#include "include/AMQPBlob.h"

/******************************************************************************/

struct pn_data_t;

/******************************************************************************/

class BlobInspector {
    private :
        amqp::AMQPBlob m_blob;

    public :
        explicit BlobInspector (amqp::CordaBytes &);

        std::string dump();

};

/******************************************************************************/
