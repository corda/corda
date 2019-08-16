#pragma once

#include <list>
#include <ostream>
#include <iostream>

#include "types.h"
#include "colours.h"

/******************************************************************************
 *
 * Forward declarations
 *
 ******************************************************************************/

namespace amqp::internal::schema {
    template<class T>
    class OrderedTypeNotations;
}

template<class T>
std::ostream & operator << (
        std::ostream &,
        const amqp::internal::schema::OrderedTypeNotations<T> &);

/******************************************************************************
 *
 * OrderedTypeNotation
 *
 ******************************************************************************/

namespace amqp::internal::schema {

    class OrderedTypeNotation {
        public :
            virtual ~OrderedTypeNotation() = default;

            virtual int dependsOn (const OrderedTypeNotation &) const = 0;
    };

}

/******************************************************************************/

namespace amqp::internal::schema {

    template<class T>
    class OrderedTypeNotations {
        private:
            std::list<std::list<uPtr<T>>> m_schemas;

        public :
            typedef decltype(m_schemas.begin()) iterator;

        private:
            void insert (uPtr<T> &&, iterator);
            void insertNewList (uPtr<T> &&);
            void insertNewList (
                    uPtr<T> &&,
                    typename std::list<std::list<uPtr<T>>>::iterator &);

        public :
            void insert(uPtr<T> && ptr);

            friend std::ostream & ::operator << <> (
                    std::ostream &,
                    const amqp::internal::schema::OrderedTypeNotations<T> &);

            decltype (m_schemas.crbegin()) begin() const {
                return m_schemas.crbegin();
            }

            decltype (m_schemas.crend()) end() const {
                return m_schemas.crend();
            }
    };

}

/******************************************************************************/

template<class T>
std::ostream &
operator << (
        std::ostream &stream_,
        const amqp::internal::schema::OrderedTypeNotations<T> &otn_
) {
    int idx1{0};
    for (const auto &i : otn_.m_schemas) {
        stream_ << "level " << ++idx1 << std::endl;
        for (const auto &j : i) {
            stream_ << "    * " << j->name() << std::endl;
        }
        stream_ << std::endl;
    }

    return stream_;
}

/******************************************************************************/

template<class T>
void
amqp::internal::schema::
OrderedTypeNotations<T>::insertNewList(uPtr<T> && ptr) {
    std::list<uPtr<T>> l;
    l.emplace_back (std::move (ptr));
    m_schemas.emplace_back(std::move (l));
}

/******************************************************************************/

/**
 * This could be a bit more space efficient by checking the previous element
 * for dependendies again as its possible we are moving multiple elements "up"
 * but the extra checks probably don't make it worth it.
 */
template<class T>
void
amqp::internal::schema::
OrderedTypeNotations<T>::insertNewList(
        uPtr<T> && ptr,
        typename std::list<std::list<uPtr<T>>>::iterator & here_)
{
    std::list<uPtr<T>> l;
    l.emplace_back (std::move (ptr));
    m_schemas.insert(here_, std::move (l));
}

/******************************************************************************/

template<class T>
void
amqp::internal::schema::
OrderedTypeNotations<T>::insert (uPtr<T> && ptr) {
    return insert (std::move (ptr), m_schemas.begin());
}

/******************************************************************************/

template<class T>
void
amqp::internal::schema::
OrderedTypeNotations<T>::insert (
        uPtr<T> && ptr,
        amqp::internal::schema::OrderedTypeNotations<T>::iterator l_
) {
    /*
     * First we find where this element needs to be added
     */
    amqp::internal::schema::OrderedTypeNotations<T>::iterator insertionPoint { l_ };

    for (auto i = l_ ; i != m_schemas.end() ; ++i) {
        for (const auto & j : *i) {
            /*
             * A score of 0 means no dependencies at all
             * A score of 1 means "j" has a dependency on what's being inserted
             * A score of 2 means what's being inserted depends on "j"
             */
            auto score = j->dependsOn(*ptr);

            if (score == 1) {
                insertionPoint = std::next(i);
            } else if (score == 2) {
                insertionPoint = i;
                goto done;
            }
        }
    }
done:

    /*
     * Now we insert it and work out if anything requires shuffling
     */
    if (insertionPoint == m_schemas.end()) {
        insertNewList (std::move(ptr));
    } else {
        const auto & insertedPtr = insertionPoint->emplace_front (std::move(ptr));

        for (auto j = std::next (insertionPoint->begin()) ; j != insertionPoint->end() ; ) {
            auto toErase = j++;

            auto score { insertedPtr->dependsOn (**toErase) };

            if (score > 0) {
                uPtr<T> tmpPtr{std::move(*toErase)};
                insertionPoint->erase (toErase);
                switch (score) {
                    // Needs to go after the element we're adding
                    case 1: {
                        insert(std::move(tmpPtr), std::next(insertionPoint));
                        break;
                    }
                    // Needs to go before the element we're adding
                    case 2: {
                        insertNewList (std::move(tmpPtr), insertionPoint);
                        break;
                    }
                }

            }
        }
    }
}

/******************************************************************************/
