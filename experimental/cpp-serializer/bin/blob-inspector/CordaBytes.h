#pragma once

#include "string"
#include <fstream>
#include "amqp/AMQPSectionId.h"

/******************************************************************************/

class CordaBytes {
    private :
        amqp::amqp_section_id_t m_encoding;
        size_t m_size;
        char * m_blob;

    public :
        explicit CordaBytes (const std::string &);

        ~CordaBytes() {
            delete [] m_blob;
        }

        const decltype (m_encoding) & encoding() const {
            return m_encoding;
        }

        decltype (m_size) size() const { return m_size; }

        const char * const bytes() const { return m_blob; }
};

/******************************************************************************/
