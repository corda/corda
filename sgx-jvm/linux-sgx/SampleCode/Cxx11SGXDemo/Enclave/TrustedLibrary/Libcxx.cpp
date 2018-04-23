/*
 * Copyright (C) 2011-2017 Intel Corporation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *   * Neither the name of Intel Corporation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

#include <string>
#include <vector>
#include <iterator>
#include <typeinfo>
#include <functional>
#include <algorithm>
#include <unordered_set>
#include <unordered_map>
#include <initializer_list>
#include <tuple>
#include <memory>
#include <atomic>
#include <mutex>
#include <condition_variable>
#include <map>

#include "../Enclave.h"
#include "Enclave_t.h"


// Feature name        : Lambda functions
// Feature description : It is used to create a function object that can capture variables in scope.
// Demo description    : Shows lambda capture options and a some basic usages.
void ecall_lambdas_demo()
{
    // Lambdas capture options:
    int local_var = 0;

    [] { return true; };                     // captures nothing

    [&] { return ++local_var; };             // captures all variable by reference
    [&local_var] { return ++local_var; };    // captures local_var by reference
    [&, local_var] { return local_var; };    // captures all by reference except local_var

    [=] { return local_var; };               // captures all variable by value
    [local_var] { return local_var; };       // captures local_var by value
    [=, &local_var] { return ++local_var; }; // captures all variable by value except local_var

    // Sample usages for lamdbas:
    std::vector< int> v { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
    printf("[Lambdas] Initial array using lambdas: { ");

    // Print the elements in an array using lambdas
    std::for_each(std::begin(v), std::end(v), [](int elem) { printf("%d ", elem); }); //capture specification
    printf("}.\n");

    // Find the first odd number using lambda as an unary predicate when calling find_if.
    auto first_odd_element = std::find_if(std::begin(v), std::end(v), [=](int elem) { return elem % 2 == 1; });

    if (first_odd_element != std::end(v))
        printf("[Lambdas] First odd element in the array is %d. \n", *first_odd_element);
    else
        printf("[Lambdas] No odd element found in the array.\n");

    // Count the even numbers using a lambda function as an unary predicate when calling count_if.
    long long  number_of_even_elements = std::count_if(std::begin(v), std::end(v), [=](int  val) { return val % 2 == 0; });
    printf("[Lambdas] Number of even elements in the array is %lld.\n", number_of_even_elements);

    // Sort the elements of an array using lambdas
    std::sort(std::begin(v), std::end(v), [](int e1, int e2) {return e2 < e1; });

    // Print the elements in an array using lambdas
    printf("[Lambdas] Array after sort: { ");
    std::for_each(std::begin(v), std::end(v), [](int elem) { printf("%d ", elem); });
    printf("}. \n");
    printf("\n"); // end of demo
}


// Feature name        : auto 
// Feature description : It is used for type deduction
// Demo description    : Shows basic usages of auto specifier with different types.

// Helper function for ecall_auto_demo:
void sample_func_auto_demo()
{
    printf("[auto] Function sample_func_auto_demo is called. \n");
}

void ecall_auto_demo()
{
    double local_var = 0.0;

    auto a = 7; // Type of variable a is deduced to be int
    printf("[auto] Type of a is int. typeid = %s.\n", typeid(a).name());

    const auto b1 = local_var, *b2 = &local_var; // auto can be used with modifiers like const or &. 
    printf("[auto] Type of b1 is const double. typeid = %s.\n", typeid(b1).name());
    printf("[auto] Type of b2 is const double*. typeid = %s.\n", typeid(b2).name());
    (void)b1;
    (void)b2;

    auto c = 0, *d = &a; // multiple variable initialization if the deduced type does match 
    printf("[auto] Type of c is int. typeid = %s.\n", typeid(c).name());
    printf("[auto] Type of d is int*. typeid = %s.\n", typeid(d).name());
    (void)c;
    (void)d;

    auto lambda = [] {}; // can be used to define lambdas
    printf("[auto] Type of lambda is [] {}. typeid = %s.\n", typeid(lambda).name());
    (void)lambda;
                        
    auto func = sample_func_auto_demo; // can be used to deduce type of function    
    printf("[auto] Type of func is void(__cdecl*)(void). typeid = %s.\n", typeid(func).name());
    func();

    printf("\n"); // end of demo
}

// Feature name        : decltype
// Feature description : It is used for type deduction
// Demo description    : Shows basic usages of decltype specifier with different types. 
void ecall_decltype_demo()
{
    int a = 0 ;
    decltype(a) b = 0; // create an element of the same type as another element 
    printf("[decltype] Type of b is int. typeid = %s.\n", typeid(b).name());

    double c = 0;
    decltype(a + c) sum = a + c; // deduce type of a sum of elements of different types and create an element of that type.
                                 // most usefull in templates.
    printf("[decltype] Type of sum is double. typeid = %s.\n", typeid(sum).name());
    (void)sum;
    (void)b;
    printf("\n"); // end of demo
}

// Feature name         : enum classes
// Feature description  : A new type of enum that solves problems found in old enum like :
//                        unscoping of enum values and the possibility to compare them with int
// Demo description     : Shows basic usages of enum classes.
void ecall_strongly_typed_enum_demo()
{
    // In enum class the underlying type can be set.  In the case bellow it is char.
    enum class DaysOfWeek : char { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY };

    // initialization of variable of type DaysOfWeek
    DaysOfWeek random_day = DaysOfWeek::MONDAY;
    (void)random_day;

    // In is not mandatory to specify the underlying type.
    enum class Weekend { SATURDAY, SUNDAY };

    // The two enum classes above: days_of_week and weekend ilustrate that it is now possible to have two enum classes with the same values in them.

    // end of demo
}

// Feature name        : Range based for loops
// Feature description : Easy to read way of accessing elements in an container.
// Demo description    : Shows basic usage of range based for loop with c array and vector.
void ecall_range_based_for_loops_demo()
{
    char array_of_letters[] = { 'a','b','c','d' };
    std::vector<char> vector_of_letters = { 'a','b','c','d' };

    printf("[range_based_for_loops] Using range based for loops to print the content of an array: { ");
    for (auto elem : array_of_letters)
        printf("%c ", elem);
    printf("}. \n");

    printf("[range_based_for_loops] Using range based for loops to print the content of an vector: { ");
    for (auto elem : vector_of_letters)
        printf("%c ", elem);
    printf("}.\n");
    
    printf("\n"); // end of demo
}


// Feature name        : static_assert
// Feature description : It is used to make assertions at compile time.
// Demo description    : Shows basic usage of static_assert with compile time operation.
void ecall_static_assert_demo()
{
    static_assert(sizeof(int) < sizeof(double), "Error : sizeof(int) < sizeof(double) ");
    const int a = 0;
    static_assert(a == 0, "Error: value of a is not 0");

    // end of demo
}


// Feature name        : New virtual function controls : override, final, default, and delete
// Feature description : - delete   : a deleted function cannot be inherited
//				         - final    : a final function cannot be overrided in the derived class
//                       - default  : intruction to the compiler to generate a default function
//                       - override : ensures that a virtual function from derived class overrides a function from base
// Demo description : Shows basic usage of new virtual function control.

/* Helper class for ecall_virtual_function_control_demo.*/
class Base
{
public:

    virtual void f_cannot_be_inherited() final {};
    Base(const Base &) = delete;
    Base() = default;
    virtual void f_must_be_overrided() {};
};

/* Helper class for ecall_virtual_function_control_demo.*/
class Derived : Base
{
public:
    /* The code bellow in this comment does not compile.
    The function cannot be override because it is declared with keyword final in base
    virtual double f_cannot_be_inherited() {};
    */

    /*The keyword override assures that the function overrides a base class member*/
    virtual void f_must_be_overrided() override {};
};

void ecall_virtual_function_control_demo()
{
    // The default constructor will be called generated by the compiler with explicit keyword default
    Base a;
    // Trying to use the copy contructor will generate code that does not compile because it is deleted
    // Base b = a;

    // end of demo
}

// Feature name        : Delegating constructors
// Feature description : A class constructors may have common code which can be delegated to a constructor to avoid code repetion
// Demo description    : Shows basic usage of delegating constructors

// Helper class for ecall_delegating_constructors
class DemoDelegatingConstructors
{
    int a, b, c;
public:
    DemoDelegatingConstructors(int param_a, int param_b, int param_c)
    {
        this->a = param_a;
        this->b = param_b;
        this->c = param_c;
        /*common initialization*/
        switch (c)
        {
        case 1:
            printf("[delegating constructors] Called from DemoDelegatingConstructors(int a, int b). \n");
            break;
        case 2:
            printf("[delegating constructors] Called from DemoDelegatingConstructors(int a). \n");
            break;
        default:
            printf("[delegating constructors] Called from DemoDelegatingConstructors(int a, int b, int c).\n");
            break;
        }
    }
    DemoDelegatingConstructors(int param_a, int param_b) : DemoDelegatingConstructors(param_a, param_b, 1) {}
    DemoDelegatingConstructors(int param_a) : DemoDelegatingConstructors(param_a, 0, 2) {}
};

void ecall_delegating_constructors_demo()
{
    DemoDelegatingConstructors a(1, 2, 3);
    DemoDelegatingConstructors b(1, 2);
    DemoDelegatingConstructors c(1);
    
    printf("\n"); // end of demo
}

// Feature name        : std::function
// Feature description : It is used to store and invoke a callable
// Demo description    : Shows basic usage of std::function

// Helper class for ecall_std_function_demo:
void sample_std_function1()
{
    printf("[std_function] calling sample_std_function1\n");
}

void ecall_std_function_demo()
{
    // Example with functions
    std::function<void()> funct = sample_std_function1;
    funct();

    //Example with lambda
    std::function<void()> funct_lambda = [] { printf("[std_function] calling a lambda function\n");  };
    funct_lambda();

    printf("\n"); // end of demo
}

// Feature name        : std::all_of, std::any_of, std::none_of
// Feature description : New C++11 algorithms 
// Demo description    : Shows basic usage of the std::all_of, std::any_of, std::none_of.
void ecall_cxx11_algorithms_demo()
{
    std::vector<int> v = { 0, 1, 2, 3, 4, 5 };
    bool are_all_of = all_of(begin(v), end(v), [](int e) { return e % 2 == 0; });
    printf("[cxx11_algorithms] All elements in  { 0 1 2  3 4 5 } are even is  %s. \n", are_all_of ? "true" : "false");

    bool are_any_of = any_of(begin(v), end(v), [](int e) { return e % 2 == 0; });
    printf("[cxx11_algorithms] Some elements in  { 0 1 2 3 4 5 } are even is  %s. \n", are_any_of ? "true" : "false");

    bool are_none_of = none_of(begin(v), end(v), [](int e) { return e % 2 == 0; });
    printf("[cxx11_algorithms] None elements in  { 0 1 2 3 4 5 } are even is  %s. \n", are_none_of ? "true" : "false");

    printf("\n"); // end of demo
}


// Feature name        : variadic templates
// Feature description : Templates that can have multiple arguments
// Demo description    : Shows basic usage of variadic templates

// Helper template for ecall_variadic_templates_demo:
template<typename T>
T sum(T elem)
{
    return elem;
}

template<typename T, typename... Args>
T sum(T elem1, T elem2, Args... args)
{
    return elem1 + elem2 + sum(args...);
}

void ecall_variadic_templates_demo()
{
    int computed_sum = sum(1, 2, 3, 4, 5);
    printf("[variadic_templates] The sum  of paramters (1, 2, 3, 4, 5) is %d. \n", computed_sum);
    printf("\n"); // end of demo
}

// Feature name        : Substitution failure is not an error (SFINAE)
// Feature description : Describes the case where a substitution error in templates does not cause errors
// Demo description    : Shows basic usage of SFINAE

/*first candidate for substitution*/
template <typename T> void f(typename T::A*) { printf("[sfinae] First candidate for substitution is matched.\n"); }; 

/*second candidate for substitution*/
template <typename T> void f(T) { printf("[sfinae] Second candidate for substitution is matched.\n"); }

void ecall_SFINAE_demo()
{
    f<int>(0x0);   // even if the first canditate substition will fail, the second one will pass
    printf("\n");  // end of demo
}

//Feature name        : Initializer lists
//Feature description : An object of type std::initializer_list<T> is a lightweight proxy object that provides access to an array of objects of type const T.
//Demo description    : Demonstrates the usage of initializer list in the constructor of an object in enclave.
class Number
{
public:
    Number(const std::initializer_list<int> &v) {
        for (auto i : v) {
            elements.push_back(i);
        }
    }

    void print_elements() {
        printf("[initializer_list] The elements of the vector are:");
        for (auto item : elements) {
            printf(" %d", item);
        }
        printf(".\n");
    }
private:
    std::vector<int> elements;
};

void ecall_initializer_list_demo()
{
    printf("[initializer_list] Using initializer list in the constructor. \n");
    Number m = {10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
    m.print_elements();

    printf("\n"); //end of demo
}


// Feature name        : Rvalue references and move semantics;
// Feature description : They are used for memory usage optimazation by eliminating copy operations
// Demo description    : Shows basic usage of rvalue, move constructor, and move operator

// Helper class for ecall_rvalue_demo
class DemoBuffer
{
public:
    unsigned int size = 100;
    char *buffer;

    DemoBuffer(int param_size)
    {
        this->size = param_size;
        buffer = new char[size];
        printf("[rvalue] Called constructor : DemoBuffer(int size).\n");
    }

    // A typical copy constructor  needs to alocate memory for a new copy 
    // Copying an big array is an expensive operation
    DemoBuffer(const DemoBuffer & rhs)
    {
        this->size = rhs.size;
        buffer = new char[rhs.size];
        memcpy(buffer, rhs.buffer, size);
        printf("[rvalue] Called copy constructor : DemoBuffer(const DemoBuffer & rhs).\n");
    }

    // A typical move constructor can reuse the memory pointed by the buffer
    DemoBuffer(DemoBuffer && rhs)
    {
        buffer = rhs.buffer;
        size = rhs.size;
        // reset state of rhs
        rhs.buffer = NULL;
        rhs.size = 0;
        printf("[rvalue] Called move constructor : DemoBuffer(DemoBuffer && rhs).\n");
    }
    ~DemoBuffer()
    {
        delete buffer;
    }

};

// Helper class for ecall_rvalue_demo
DemoBuffer foobar(int a)
{
    DemoBuffer x(100);
    DemoBuffer y(100);

    if (a > 0)
        return x;
    else
        return y;
}
void ecall_rvalue_demo()
{
    // This will call the constructor
    printf("[rvalue] DemoBuffer a(100).\n");
    DemoBuffer a(100);
    
    printf("[rvalue] DemoBuffer foobar(100). \n");
    // Initializing variable d using a temporary object will result in a call to move constructor
    // This is usefull because it reduces the memory cost of the operation.
    DemoBuffer d(foobar(100));

    // This will call the copy constructor. State of a will not change.
    printf("[rvalue] DemoBuffer b(a).\n");
    DemoBuffer b(a);

    printf("[rvalue] DemoBuffer c(std::move(a)).\n");
    // explicitly cast a to an rvalue so that c will be created using move constructor. 
    // State of a is going to be reseted.
    DemoBuffer c(std::move(a));

    printf("\n"); // end of demo
}

// Feature name        : Nullptr
// Feature description : Resolves the issues of converting NULL to integral types 
// Demo description    : Shows basic usage of nullptr

// overload candidate 1
void nullptr_overload_candidate(int i) {
    (void)i;
    printf("[nullptr] called void nullptr_overload_candidate(int i).\n");
}

// overload candidate 2
void nullptr_overload_candidate(int* ptr) {
    (void)ptr;
    printf("[nullptr] called void nullptr_overload_candidate(int* ptr).\n");
}

template<class F, class A>
void Fwd(F f, A a)
{
    f(a);
}

void g(int* i)
{
    (void)i;
    printf("[nullptr] Function %s called\n", __FUNCTION__);
}

// Feature name        :
// Feature description :
// Demo description    :
void ecall_nullptr_demo()
{
    // NULL can be converted to integral types() like int and will call overload candidate 1
    nullptr_overload_candidate(NULL);

    // nullptr can't be converted to integral types() like int and will call overload candidate 2
    nullptr_overload_candidate(nullptr);

    g(NULL);           // Fine
    g(0);              // Fine
    Fwd(g, nullptr);   // Fine
    //Fwd(g, NULL);  // ERROR: No function g(int)

    printf("\n"); // end of demo
}

// Feature name        : Scoped enums
// Feature description :
// Demo description    :
enum class Color { orange, brown, green = 30, blue, red };

void ecall_enum_class_demo()
{
    int n = 0;
    Color color1 = Color::brown;
    switch (color1)
    {
        case Color::orange: printf("[enum class] orange"); break;
        case Color::brown:  printf("[enum class] brown"); break;
        case Color::green:  printf("[enum class] green"); break;
        case Color::blue:   printf("[enum class] blue"); break;
        case Color::red:    printf("[enum class] red"); break;
    }
    // n = color1; // Not allowed: no scoped enum to int conversion
    n = static_cast<int>(color1); // OK, n = 1
    printf(" - int = %d\n", n);

    Color color2 = Color::red;
    switch (color2)
    {
        case Color::orange: printf("[enum class] orange"); break;
        case Color::brown:  printf("[enum class] brown"); break;
        case Color::green:  printf("[enum class] green"); break;
        case Color::blue:   printf("[enum class] blue"); break;
        case Color::red:    printf("[enum class] red"); break;
    }
    n = static_cast<int>(color2); // OK, n = 32
    printf(" - int = %d\n", n);

    Color color3 = Color::green;
    switch (color3)
    {
        case Color::orange: printf("[enum class] orange"); break;
        case Color::brown:  printf("[enum class] brown"); break;
        case Color::green:  printf("[enum class] green"); break;
        case Color::blue:   printf("[enum class] blue"); break;
        case Color::red:    printf("[enum class] red"); break;
    }
    n = static_cast<int>(color3); // OK, n = 30
    printf(" - int = %d\n", n);
    printf("\n");
}

// Feature name        : new container classes
// Feature description : unordered_set, unordered_map, unordered_multiset, and unordered_multimap
// Demo description    : Shows basic usage of new container classes
void ecall_new_container_classes_demo()
{
    // unordered_set
    // container used for fast acces that groups elements in buckets based on their hash

    std::unordered_set<int> set_of_numbers = { 0, 1, 2, 3, 4, 5 };
    const int searchVal = 3;
    std::unordered_set<int>::const_iterator got = set_of_numbers.find(searchVal);

    if (got != set_of_numbers.end())
        printf("[new_container_classes] unordered_set { 0, 1, 2, 3, 4, 5} has value 3.\n");
    else
        printf("[new_container_classes] unordered_set { 0, 1, 2, 3, 4, 5} does not have value 3.\n");

    // unordered_multiset
    // container used for fast acces that groups non unique elements in buckets based on their hash
    std::unordered_multiset<int> multiset_of_numbers = { 0, 1, 2, 3, 3, 3 };
    printf("[new_container_classes] multiset_set { 0, 1, 2, 3, 3, 3}  has %d elements with value %d.\n",
        (int)multiset_of_numbers.count(searchVal), searchVal);

    // unordered_map
    std::unordered_map<std::string, int> grades{ { "A", 10 },{ "B", 8 },{ "C", 7 },{ "D", 5 },{ "E", 3 } };
    printf("[new_container_classes] unordered_map elements: {");
    for (auto pair : grades) {
        printf("[%s %d] ", pair.first.c_str(), pair.second);
    }

    printf("}.\n");

    // unordered_multimap
    std::unordered_multimap<std::string, int> multimap_grades{ { "A", 10 },{ "B", 8 },{ "B", 7 },{ "E", 5 },{ "E", 3 },{ "E",1 } };

    printf("[new_container_classes] unordered_multimap elements: {");
    for (auto pair : multimap_grades) {
        printf("[%s %d] ", pair.first.c_str(), pair.second);
    }
    printf("}.\n");

    printf("\n"); // end of demo
}

// Feature name        : Tuple
// Feature description : Objects that pack elements of multiple types which can be accessed by index
// Demo description    : Shows basic usage of tuple: creation and access
void ecall_tuple_demo()
{
    // Create tuple using std::make_tuple
    char array_of_letters[4] = {'A','B','C','D'};
    std::vector<char> vector_of_letters = { 'A','B','C','D' };
    std::map<char, char> map_of_letters = { {'B','b' } };
    
    // Creating a tuple using a tuple constructor
    std::tuple<int, std::string> tuple_sample_with_constructor(42, "Sample tuple");
    (void)tuple_sample_with_constructor;

    // Creating a tuple using std::make_tuple
    auto tuple_sample = std::make_tuple("<First element of TupleSample>", 1, 7.9, vector_of_letters, array_of_letters, map_of_letters);

    // Access the elements in tupleSample using std::get<index>
    printf("[tuple] show first  element in TupleSample: %s. \n", std::get<0>(tuple_sample));
    printf("[tuple] show second element in TupleSample: %d. \n", std::get<1>(tuple_sample));
    printf("[tuple] show third  element in TupleSample: %f. \n", std::get<2>(tuple_sample));
    
    // Getting vector from a tuple
    std::vector<char> temp_vector = std::get<3>(tuple_sample);
    (void)temp_vector;

    // Getting array from a tuple
    int first_elem_of_array = std::get<4>(tuple_sample)[0];
    (void)first_elem_of_array;

    // Getting map from a tuple
    std::map<char, char> temp_map = std::get<5>(tuple_sample);
    (void)temp_map;
    printf("\n"); // end of demo
}

// Feature name        : new smart pointer
// Feature description : shared_ptr and unique_ptr
// Demo decription     :  Shows basic usage of smart pointers
// Helper class for ecall_shared_ptr_demo
class DemoSmartPtr
{
    std::string smartPointerType;
public:
    DemoSmartPtr(std::string param_smartPointerType)
    {
        printf("[smart_ptr] In construct of object demo_smart_ptr  using %s. \n", param_smartPointerType.c_str());
        this->smartPointerType = param_smartPointerType;
    }
    ~DemoSmartPtr()
    {
        printf("[smart_ptr] In deconstructor of object demo_smart_ptr using %s. \n", smartPointerType.c_str());
    }
};

void ecall_shared_ptr_demo()
{
    // std::shared_ptr is smart pointer that takes ownership of an object using a pointer
    // The object is freed when the last smart_pointer does not point to it.

    // Creating a shared pointer using std::make_shared
    auto shared_ptr = std::make_shared<DemoSmartPtr>("smart_ptr.");  // The constructor of DemoSmartPtr will be called here

    printf("[smart_ptr] shared_ptr reference count = %ld.  \n", shared_ptr.use_count());
    auto shared_ptr2 = shared_ptr;
    printf("[smart_ptr] shared_ptr reference count = %ld incresead after creating another shared pointer.\n", shared_ptr.use_count());
    shared_ptr2.reset();
    printf("[smart_ptr] shared_ptr reference count = %ld decresead after calling releasing ownership. \n", shared_ptr.use_count());

    // std::unique_ptr is smart pointer that takes ownership of an object using a pointer
    // it is different from smart_ptr in the sense that only one unique_ptr can take ownership
    
    std::unique_ptr<DemoSmartPtr> unique_ptr(new DemoSmartPtr("unique_ptr"));
    // When going out of scope both shared_ptr and unique_ptr release the objects they own
    
    // end of demo
}

//Feature name       : atomic
//Feature description: The atomic library provides components for fine-grained atomic operations allowing for lockless concurrent programming.
//                     Each atomic operation is indivisible with regards to any other atomic operation that involves the same object. 
//                     Atomic objects are free of data races.
//Demo description   : Demonstrates the usage of atomic types, objects and functions in enclave.
void ecall_atomic_demo()
{
    printf("[atomic] Atomic types, objects and functions demo.\n");

    printf("[atomic_store] Defining an atomic_char object with an initial value of 5.\n");
    std::atomic_char atc(5);
    printf("[atomic_store] The current value stored in the atomic object is: %d\n", atc.load());
    printf("[atomic_store] Replacing the value of the atomic object with a non-atomic value of 3.\n");
    std::atomic_store<char>(&atc, 3);
    printf("[atomic_store] The new value of the atomic object is: %d.\n", atc.load());

    printf("\n");

    printf("[atomic_store_explicit] Defining an atomic_short object with an initial value of 5.\n");
    std::atomic_short ats(5);
    printf("[atomic_store_explicit] The current value stored in the atomic object is: %d.\n", ats.load());
    printf("[atomic_store_explicit] Replacing the value of the atomic object with a non-atomic value of 3.\n");
    std::atomic_store_explicit<short>(&ats, 3, std::memory_order_seq_cst);
    printf("[atomic_store] The new value of the atomic object is: %d.\n", ats.load());

    printf("\n");

    printf("[atomic_load] Defining an atomic_int object with an initial value of 4.\n");
    std::atomic_int ati1(4);
    printf("[atomic_load] Obtaining the value of the atomic object and saving it in a int variable.\n");
    int val = std::atomic_load(&ati1);
    printf("[atomic_load] The obtained value is %d.\n", val);

    printf("\n");

    printf("[atomic_load_explicit] Defining an atomic_int object with an initial value of 2.\n");
    std::atomic_int ati2(2);
    printf("[atomic_load_explicit] Obtaining the value of the atomic object and saving it in a int variable.\n");
    int val1 = std::atomic_load_explicit(&ati2, std::memory_order_seq_cst);
    printf("[atomic_load_explicit] The obtained value is %d.\n", val1);

    printf("\n");

    printf("[atomic_fetch_add] Defining an atomic_int object with an initial value of 7.\n");
    std::atomic_int ati(7);
    printf("[atomic_fetch_add] The current value stored in the atomic object is: %d.\n", ati.load());
    printf("[atomic_fetch_add] Adding a non-atomic value of 8 to the atomic object.\n");
    std::atomic_fetch_add(&ati, 8);
    printf("[atomic_fetch_add] The new value of the atomic object is: %d.\n", ati.load());

    printf("\n");

    printf("[atomic_fetch_add_explicit] Defining an atomic_uint object with an initial value of 7.\n");
    std::atomic_uint atui(7);
    printf("[atomic_fetch_add_explicit] The current value stored in the atomic object is: %u.\n", atui.load());
    printf("[atomic_fetch_add_explicit] Adding a non-atomic value of 8 to the atomic object.\n");
    std::atomic_fetch_add_explicit<unsigned int>(&atui, 8, std::memory_order_seq_cst);
    printf("[atomic_fetch_add_explicit] The new value of the atomic object is: %u.\n", atui.load());

    printf("\n");

    printf("[atomic_fetch_sub] Defining an atomic_long object with an initial value of 20.\n");
    std::atomic_long atl(20);
    printf("[atomic_fetch_sub] The current value stored in the atomic object is: %ld.\n", atl.load());
    printf("[atomic_fetch_sub] Substracting a non-atomic value of 8 from the value of the atomic object.\n");
    std::atomic_fetch_sub<long>(&atl, 8);
    printf("[atomic_fetch_sub] The new value of the atomic object is: %ld.\n", atl.load());

    printf("\n");

    printf("[atomic_fetch_sub_explicit] Defining an atomic_llong object with an initial value of 20.\n");
    std::atomic_llong atll(20);
    printf("[atomic_fetch_sub_explicit] The current value stored in the atomic object is: %lld.\n", atll.load());
    printf("[atomic_fetch_sub_explicit] Substracting a non-atomic value of 8 from the value of the atomic object.\n");
    std::atomic_fetch_sub_explicit<long long>(&atll, 8, std::memory_order_seq_cst);
    printf("[atomic_fetch_sub_explicit] The new value of the atomic object is: %lld.\n", atll.load());

    printf("\n"); // end of demo
}

//Feature name        : mutex
//Feature description : The mutex class is a synchronization primitive that can be used to protect shared data
//                     from being simultaneously accessed by multiple threads.
//Demo description    : Demonstrates mutex protection when incrementing values in multiple threads.

//Structure used in mutex demo to show the behavior without using a mutex
struct CounterWithoutMutex {
	int value;

	CounterWithoutMutex() : value(0) {}

	void increment() {
		++value;
	}
};

CounterWithoutMutex counter_without_protection;

//E-call used by mutex demo to perform the incrementation using a counter without mutex protection
void ecall_mutex_demo_no_protection()
{
	for (int i = 0; i < 100000; ++i) {
		counter_without_protection.increment();
	}
}

//E-call used by mutex demo to get the final value of the counter from enclave
void ecall_print_final_value_no_protection()
{
	printf("[mutex] Incrementing values in three threads without mutex protection, using a 100000 times loop. \n[mutex]Expected value is 300000. The final value is %d.\n", counter_without_protection.value);
}


//Structure used in mutex demo
struct CounterProtectedByMutex {
    std::mutex mutex;
    int value;

    CounterProtectedByMutex() : value(0) {}

    void increment() {
        //locking the mutex to avoid simultaneous incrementation in different threads
        mutex.lock();
        ++value;
        //unlocking the mutex
        mutex.unlock();
    }
};

CounterProtectedByMutex counter_with_protection;

//E-call used by mutex demo to perform the actual incrementation
void ecall_mutex_demo()
{
    for (int i = 0; i < 100000; ++i) {
        counter_with_protection.increment();
    }
}

//E-call used by mutex demo to get the final value of the counter from enclave
void ecall_print_final_value_mutex_demo()
{
    printf("[mutex] Mutex protection when incrementing a value in 3 threads, using a 100000 times loop. \n[mutex]Expected value is 300000. The final value is %d.\n", counter_with_protection.value);
}

//Feature name       : condition_variable
//Feature description: The condition_variable class is a synchronization primitive that can be used to block a thread, 
//                     or multiple threads at the same time, until another thread both modifies a shared variable (the condition), 
//                     and notifies the condition_variable.
//Demo description   : Demonstrates condition_variable usage in a two threads environment. One thread is used for loading the data and 
//                     the other processes the loaded data. The thread for processing the data waits untill the data is loaded in the 
//                     other thread and gets notified when loading is completed.

//This class is used by condition variable demo
class DemoConditionVariable
{
    std::mutex mtx;
    std::condition_variable cond_var;
    bool data_loaded;
public:
    DemoConditionVariable()
    {
        data_loaded = false;
    }
    void load_data()
    {
        //Simulating loading of the data
        printf("[condition_variable] Loading Data...\n");
		{
			// Locking the data structure
			std::lock_guard<std::mutex> guard(mtx);
			// Setting the flag to true to signal load data completion
			data_loaded = true;
		}
        // Notify to unblock the waiting thread
        cond_var.notify_one();
    }
    bool is_data_loaded()
    {
        return data_loaded;
    }
    void main_task()
    {
        printf("\n");
        printf("[condition_variable] Running condition variable demo.\n");
    
        // Acquire the lock
        std::unique_lock<std::mutex> lck(mtx);
    
        printf("[condition_variable] Waiting for the data to be loaded in the other thread.\n");
        cond_var.wait(lck, std::bind(&DemoConditionVariable::is_data_loaded, this));
        printf("[condition_variable] Processing the loaded data.\n");
        printf("[condition_variable] Done.\n");
    }
};


DemoConditionVariable app;

//E-call used by condition_variable demo - processing thread

void ecall_condition_variable_run()
{
    app.main_task();
}

//E-call used by condifion_variable demo - loader thread
void ecall_condition_variable_load()
{
    app.load_data();
}
