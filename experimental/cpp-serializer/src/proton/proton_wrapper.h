#pragma once

/******************************************************************************/

#include <iosfwd>
#include <string>

#include <proton/types.h>
#include <proton/codec.h>

/******************************************************************************/

/**
 * Friendly ostream operator for a pn_data_t type
 */
std::ostream& operator << (std::ostream& stream, pn_data_t * data_);

/******************************************************************************/

namespace proton {

    /**
     * Wrap enter so we automatically move to the first child node rather
     * than starting on an invalid one
     */
    bool pn_data_enter(pn_data_t *);

    void is_list (pn_data_t *);
    void is_ulong (pn_data_t *);
    void is_symbol (pn_data_t *);
    void is_string (pn_data_t *, bool allowNull = false);
    void is_described (pn_data_t *);

    template<typename T>
    T get_symbol (pn_data_t * data_) {
        return T {};
    }

    std::string get_symbol (pn_data_t *);

    bool get_boolean (pn_data_t *);
    std::string get_string (pn_data_t *, bool allowNull = false);

    class auto_enter {
        private :
            pn_data_t * m_data;

        public :
            auto_enter (pn_data_t *, bool next_ = false);
            ~auto_enter();
    };

    class auto_next {
        private :
            pn_data_t * m_data;

        public :
            auto_next (pn_data_t *);
            auto_next (const auto_next &) = delete;

            ~auto_next();
    };

    class auto_list_enter {
        private :
            size_t      m_elements;
            pn_data_t * m_data;

        public :
            auto_list_enter (pn_data_t *, bool next_ = false);
            ~auto_list_enter();

            size_t elements() const;
    };

}

/******************************************************************************/

namespace proton {

    template<typename T>
    T
    readAndNext (pn_data_t * data_, bool tolerateDeviance_ = false) {
        return T{};
    }

}

/******************************************************************************/
