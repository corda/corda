#pragma once

#include <iosfwd>
#include "CordaBytes.h"

/******************************************************************************/

struct pn_data_t;

/******************************************************************************/

class BlobInspector {
    private :
        pn_data_t * m_data;

    public :
        BlobInspector (CordaBytes &);

        std::string dump();

};

/******************************************************************************/
