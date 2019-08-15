#pragma once

/******************************************************************************/

#include <map>
#include <memory>

/******************************************************************************/

template<typename T>
using uPtr = std::unique_ptr<T>;

template<typename T>
using sPtr = std::shared_ptr<T>;


template<typename T>
using upStrMap_t = std::map<std::string, uPtr<T>>;

template<typename T>
using spStrMap_t = std::map<std::string, sPtr<T>>;

/******************************************************************************/

