Code style guide
================

This document explains the coding style used in the R3 prototyping repository. You will be expected to follow these
recommendations when submitting patches for review. Please take the time to read them and internalise them, to save
time during code review.

What follows are *recommendations* and not *rules*. They are in places intentionally vague, so use your good judgement
when interpreting them.

.. note:: Parts of the codebase may not follow this style guide yet. If you see a place that doesn't, please fix it!

1. General style
################

We use the standard Java coding style from Sun, adapted for Kotlin in ways that should be fairly intuitive.

We aim for line widths of no more than 120 characters. That is wide enough to avoid lots of pointless wrapping but
narrow enough that with a widescreen monitor and a 12 point fixed width font (like Menlo) you can fit two files
next to each other. This is not a rigidly enforced rule and if wrapping a line would be excessively awkward, let it
overflow. Overflow of a few characters here and there isn't a big deal: the goal is general convenience.

Code is vertically dense, blank lines in methods are used sparingly. This is so more code can fit on screen at once.

Each file has a copyright notice at the top. Copy it from the existing files if you create a new one. We do not mark
classes with @author Javadoc annotations.

In Kotlin code, KDoc is used rather than JavaDoc. It's very similar except it uses Markdown for formatting instead
of HTML tags.

We target Java 8 and use the latest Java APIs whenever convenient. We use ``java.time.Instant`` to represent timestamps
and ``java.nio.file.Path`` to represent file paths.

We use spaces and not tabs.

Never apply any design pattern religiously. There are no silver bullets in programming and if something is fashionable,
that doesn't mean it's always better. In particular:

* Use functional programming patterns like map, filter, fold only where it's genuinely more convenient. Never be afraid
  to use a simple imperative construct like a for loop or a mutable counter if that results in more direct, English-like
  code.
* Use immutability when you don't anticipate very rapid or complex changes to the content. Immutability can help avoid
  bugs, but over-used it can make code that has to adjust fields of an immutable object (in a clone) hard to read and
  stress the garbage collector. When such code becomes a widespread pattern it can lead to code that is just generically
  slow but without hotspots.
* The tradeoffs between various thread safety techniques are complex, subtle, and no technique is always superior to
  the others. Our code uses a mix of locks, worker threads and messaging depending on the situation.

2. Comments
###########

We like them as long as they add detail that is missing from the code. Comments that simply repeat the story already
told by the code are best deleted. Comments should:

* Explain what the code is doing at a higher level than is obtainable from just examining the statement and
  surrounding code.
* Explain why certain choices were made and the tradeoffs considered.
* Explain how things can go wrong, which is a detail often not easily seen just by reading the code.
* Use good grammar with capital letters and full stops. This gets us in the right frame of mind for writing real
  explanations of things.

When writing code, imagine that you have an intelligent colleague looking over your shoulder asking you questions
as you go. Think about what they might ask, and then put your answers in the code.

Don’t be afraid of redundancy, many people will start reading your code in the middle with little or no idea of what
it’s about, eg, due to a bug or a need to introduce a new feature. It’s OK to repeat basic facts or descriptions in
different places if that increases the chance developers will see something important.

API docs: all public methods, constants and classes should have doc comments in either JavaDoc or KDoc. API docs should:

* Explain what the method does in words different to how the code describes it.
* Always have some text, annotation-only JavaDocs don’t render well. Write “Returns a blah blah blah” rather
  than “@returns blah blah blah” if that's the only content (or leave it out if you have nothing more to say than the
  code already says).
* Illustrate with examples when you might want to use the method or class. Point the user at alternatives if this code
  is not always right.
* Make good use of {@link} annotations.

Bad JavaDocs look like this:

.. sourcecode:: java

   /** @return the size of the Bloom filter. */
   public int getBloomFilterSize() {
       return block;
   }

Good JavaDocs look like this:

.. sourcecode:: java

   /**
    * Returns the size of the current {@link BloomFilter} in bytes. Larger filters have
    * lower false positive rates for the same number of inserted keys and thus lower privacy,
    * but bandwidth usage is also correspondingly reduced.
    */
   public int getBloomFilterSize() { ... }

We use C-style (``/** */``) comments for API docs and we use C++ style comments (``//``) for explanations that are
only intended to be viewed by people who read the code.

3. Threading
############

Classes that are thread safe should be annotated with the ``@ThreadSafe`` annotation. The class or method comments
should describe how threads are expected to interact with your code, unless it's obvious because the class is
(for example) a simple immutable data holder.

Code that supports callbacks or event listeners should always accept an ``Executor`` argument that defaults to
``MoreExecutors.directThreadExecutor()`` (i.e. the calling thread) when registering the callback. This makes it easy
to integrate the callbacks with whatever threading environment the calling code expects, e.g. serialised onto a single
worker thread if necessary, or run directly on the background threads used by the class if the callback is thread safe
and doesn't care in what context it's invoked.

In the prototyping code it's OK to use synchronised methods i.e. with an exposed lock when the use of locking is quite
trivial. If the synchronisation in your code is getting more complex, consider the following:

1. Is the complexity necessary? At this early stage, don't worry too much about performance or scalability, as we're
   exploring the design space rather than making an optimal implementation of a design that's already nailed down.
2. Could you simplify it by making the data be owned by a dedicated, encapsulated worker thread? If so, remember to
   think about flow control and what happens if a work queue fills up: the actor model can often be useful but be aware
   of the downsides and try to avoid explicitly defining messages, prefer to send closures onto the worker thread
   instead.
3. If you use an explicit lock and the locking gets complex, and *always* if the class supports callbacks, use the
   cycle detecting locks from the Guava library.
4. Can you simplify some things by using thread-safe collections like ``CopyOnWriteArrayList`` or ``ConcurrentHashMap``?
   These data structures are more expensive than their non-thread-safe equivalents but can be worth it if it lets us
   simplify the code.

Immutable data structures can be very useful for making it easier to reason about multi-threaded code. Kotlin makes it
easy to define these via the "data" attribute, which auto-generates a copy() method. That lets you create clones of
an immutable object with arbitrary fields adjusted in the clone. But if you can't use the data attribute for some
reason, for instance, you are working in Java or because you need an inheritance heirarchy, then consider that making
a class fully immutable may result in very awkward code if there's ever a need to make complex changes to it. If in
doubt, ask. Remember, never apply any design pattern religiously.

4. Assertions and errors
########################

We use them liberally and we use them at runtime, in production. That means we avoid the "assert" keyword in Java,
and instead prefer to use the ``check()`` or ``require()`` functions in Kotlin (for an ``IllegalStateException`` or
``IllegalArgumentException`` respectively), or the Guava ``Preconditions.check`` method from Java.

We define new exception types liberally. We prefer not to provide English language error messages in exceptions at
the throw site, instead we define new types with any useful information as fields, with a toString() method if
really necessary. In other words, don't do this:

.. sourcecode:: java

   throw new Exception("The foo broke")

instead do this

.. sourcecode:: java

   class FooBrokenException extends Exception {}
   throw new FooBrokenException()

The latter is easier to catch and handle if later necessary, and the type name should explain what went wrong.

Note that Kotlin does not require exception types to be declared in method prototypes like Java does.

