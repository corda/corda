This source tree contains experimental code written by Mike Hearn. It explores a simple DSL for a state transition
model with the following characteristics:

* A blockchain-esque UTXO model is used in which immutable states are consumed as _inputs_ by _transactions_ yielding
  _outputs_. Transactions do not specify the code to run directly: rather, each state contains a pointer to a program
  called a _contract_ which defines a verification function for the transaction. Every state's contract must accept
  for the transaction to be valid.
* The language used is [Kotlin](https://kotlinlang.org/), a new general purpose JVM targeting language from JetBrains.
  It can be thought of as a much simpler Scala, or alternatively, a much more convenient and concise Java.
* Kotlin has some DSL-definition abilities in it. These are used to define some trivial extension for the purposes of
  mapping English requirements to verification logic, and for defining unit tests.
* As a base case, it defines a contract and states for expressing cash claims against an institution, verifying movements
  of those claims between parties and generating transactions to perform those moves (a simple "wallet").
  
A part of this work is to explore to what extent contract logic can expressed more concisely and correctly using the
type and DSL system that Kotlin provides, in order to answer the question: how powerful does a DSL system need to be to
achieve good results?

This code may evolve into a runnable prototype that tests the clarity of thought behind the R3 architectural proposals.

----

# Kotlin in two minutes

Here's a brief description of syntax and features you will see in this code. Kotlin almost always maps directly to Java
so it should not be hard to understand. In some cases the Java equivalents are shown.

    fun foo() = 1234
    fun foo(): Int = 1234
    
Defines a function called foo that returns the single expression 1234. The return type is inferred in the first example.
  
    val x = 1234            in java:  final int x = 1234;
    var y = "a string"      in java:  String y = "a string";
        
Defines an immutable and mutable variable with inferred types.

    data class Foo(val x: Int, val y: String) : Bar()
    
    ... in Java:
    
    public class Foo extends Bar {
        private int x;
        private String y;
        
        public Foo(int x, String y) {
            this.x = s; this.y = y;
        }
        
        public int getX() { return x; }
        public String getY() { return y; }
        
        @Override public boolean equals(Object other) { .... }
        @Override public int hashCode() { .... }
        @Override public String toString() { .... }
    }
    
Defines a final JavaBean that inherits from Bar, with auto-generated getX(), getY() methods, a constructor that takes
both as arguments, equals, hashCode and toString implementations, as well as a useful method called copy() which is a 
way to duplicate an object with one or more fields changed. Kotlin methods can have named arguments with default values, 
so copy is auto-generated as:

    fun copy(x: Int = x, y: String = y) = Foo(x, y)
 
This avoids the tedious Java builder pattern. 

If the "data" modifier is missing then the equals/toString/hashCode/copy methods are not auto-generated. The reason 
is that currently the compiler doesn't always know how to handle inheritance scenarios. However java-style get/set/is
methods are always generated.
    
    class Foo(val v: Int)
    fun List<Foo?>.sum() = filterNotNull().map { it.v }.sum()
    
    ... java equivalent:
    
    public class Foo {
        private int v;
        public Foo(int v) { this.v = v; }
        public int getV() { return v; }
    }
    
    public class FileNameKt {
        public static int sum(List<Foo> list) {
            int c = 0;
            for (Foo foo : list) {
                if (foo != null) c += foo.getV();
            }
            return c;
        }
    }
    
Defines a simple class with an int field. Then defines an _extension function_ on List<Foo?>, which is like a static
utility method but it appears in auto-complete at the right times, and can be used to integrate externally defined
classes e.g. from libraries with language features better. The type Foo? means "a possibly null reference to a Foo". If
the question mark is missing then references are guaranteed to be non-null. Kotlin generates nullability assertions
in the right places and tracks the nullness of types through the code flow. Finally, this example uses functional
programming utilities to express the Java loop in a simpler way. The Kotlin compiler will inline filterNotNull(), map()
and sum() so the actual bytecode generated is pretty similar to the Java, however, in this case an additional collection
is allocated to hold the results of filtering out the null objects.

TIPS:

1. Whenever you see a function call and want to know what it does, you can command/ctrl click on it to treat it like a
   hyperlink and go to the definition.
2. Put the cursor on a function call and press Ctrl-J to pop up the javadoc for it. That's also a fast way to learn
   how the code works.


    object Foo : Bar() {} 
    
    ... in java:
    
    public class Foo {
        public Foo INSTANCE = new Foo();
        private Foo() {}
    }
    
Defines a singleton called Foo.

    inline fun <reified T : Foo> List<Any>.doSomething {
       for (i in this) {
           if (i is T) {
               i.someMethodOfFoo()
           }
       }
    }

This is a more complex example you'll only find in the DSL definition. It can't be easily expressed in Java.

Java does not have reified generics. That means when a generic method or class is compiled, every use of a type
variable is replaced with its bound (or Object, if there is no bound). So you can't access the type inside
the code itself, which is awkward. Kotlin allows you to duck around this limitation in limited circumstances. Here,
we define an extension function that applies to every list that can't contain nulls, which takes a single type
variable Y which must either be Foo, or some subclass of Foo. Then for every item that is of that type, it calls
a method on it. 

Note that the act of testing the type automatically narrows the type of the variable! Put more simply:

    val x: Any = ....
    if (x is String)
        println("it is ${x.length} characters long")
    
    ... in java:
    
    Object x = ....;
    if (x instanceof String) {
        String xAsString = (String) x;
        System.out.println("it is " + xAsString.length() + " characters long");
    }
    
In Kotlin we don't need the type cast immediately after the check.

    sealed class Foo {
        class Bar
        class Baz
    }
    
Defines a class called Foo that has two final inner subclasses. Foo _cannot_ have any other subclasses, so you know
that it's safe to do this:

    val f: Foo = ...
    val result = when (f) {
        is Bar -> 1
        is Baz -> 2
    }

A "when" expression is like a switch in Java, but it can return something. The compiler will flag an error if we didn't
handle all the cases, so if someone adds another case to Foo we'll be sure to update all the places where the type is
switched on.

    infix fun Bar.`something long and wordy`(i: Int): Foo = ....
    val f = bar `something long and wordy` 42
    
    .... in java:
    
    public class BarUtils {
        public static Foo somethingLongAndWordy(Bar b, int i) { ... }
    }
    Foo f = BarUtils.somethingLongAndWordy(bar, 42);
     
Defines an extension function that has spaces in its name, marked such that it can be used "infix". You cannot define
functions with such names in Java, though the JVM allows it. This is purely a syntax tweak but it can make DSLs more
easily read.

Collections: Kotlin uses the ordinary Java collections framework but enhances it with some compiler magic and lots of 
extension functions. Differences are:
 
* List<Foo> is a read-only view of a list of foos that cannot contain nulls.
* MutableList<Foo> is the same thing as a java.util.List<Foo>, in that it lets you add/remove elements.
* Map<Foo, Bar> is a read-only map of (non-null) Foos to Bars 
* List.contains and Map.get in Java takes a parameter of type Object, meaning you can accidentally try and look up
  an entry of the wrong type and the compiler won't stop you. In Kotlin the types are narrowed to catch this error.
* You can write "someMap[someKey]" in Kotlin to do get/put on a map.


    val m = hashMapOf(
        "a" to 1, 
        "b" to 2,
        "c" to 3
    )
    println(m["a"])
    
    in java:
    
    Map<String, Integer> m = new HashMap<>();
    m.put("a", 1);
    m.put("b", 2);
    m.put("c", 3);
    System.out.println(m.get("a"));
    
