.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Shell
=====

The Corda shell is an embedded command line that allows an administrator to control and monitor the node.
Some of its features include:

* Invoking any of the RPCs the node exposes to applications.
* Starting flows.
* View a dashboard of threads, heap usage, VM properties.
* Uploading and downloading zips from the attachment store.
* Issue SQL queries to the underlying database.
* View JMX metrics and monitoring exports.
* UNIX style pipes for both text and objects, an ``egrep`` command and a command for working with columnular data.

.. note:: A future version of Corda will add SSH access to the node.

It is based on the popular `CRaSH`_ shell used in various other projects and supports many of the same features.

The shell may be disabled by passing the ``--no-local-shell`` flag to the node.

Getting help
------------

You can run ``help`` to list the available commands.

The shell has a ``man`` command that can be used to get interactive help on many commands. You can also use the
``--help`` or ``-h`` flags to a command to get info about what switches it supports.

Commands may have subcommands, in the same style as ``git``. In that case running the command by itself will
list the supported subcommands.

Starting flows and performing remote method calls
-------------------------------------------------

**Flows** are the way the ledger is changed. If you aren't familiar with them, please review ":doc:`flow-state-machines`"
first. The ``flow list`` command can be used to list the flows understood by the node, ``flow watch`` shows all the flows
currently running on the node with the result (or error) information in a user friendly way, ``flow start`` can be
used to start flows. The ``flow start`` command takes the class name of a flow, or *any unambiguous substring* and
then the data to be passed to the flow constructor. The unambiguous substring feature is helpful for reducing
the needed typing. If the match is ambiguous the possible matches will be printed out. If a flow has multiple
constructors then the names and types of the arguments will be used to try and determine which to use automatically.
If the match against available constructors is unclear, the reasons each available constructor failed to match
will be printed out. In the case of an ambiguous match, the first applicable will be used.

**RPCs** (remote procedure calls) are commands that can be sent to the node to query it, control it and manage it.
RPCs don't typically do anything that changes the global ledger, but they may change node-specific data in the
database. Each RPC is one method on the ``CordaRPCOps`` interface, and may return a stream of events that will
be shown on screen until you press Ctrl-C. You perform an RPC by using ``run`` followed by the name.

.. raw:: html

   <center><b><a href="api/kotlin/corda/net.corda.core.messaging/-corda-r-p-c-ops/index.html">Documentation of available RPCs</a></b><p></center>

Whichever form of change is used, there is a need to provide *parameters* to either the RPC or the flow
constructor. Because parameters can be any arbitrary Java object graph, we need a convenient syntax to express
this sort of data. The shell uses a syntax called `Yaml`_ to do this.

Data syntax
-----------

Yaml (yet another markup language) is a simple JSON-like way to describe object graphs. It has several features
that make it helpful for our use case, like a lightweight syntax and support for "bare words" which mean you can
often skip the quotes around strings. Here is an example of how this syntax is used:

``flow start CashIssue amount: $1000, issueRef: 1234, recipient: "O=Bank A,L=London,C=GB", notary: "O=Notary Service,OU=corda,L=London,C=GB"``

This invokes a constructor of a flow with the following prototype in the code:

.. container:: codeset

   .. sourcecode:: kotlin

      class CashIssueFlow(val amount: Amount<Currency>,
                          val issueRef: OpaqueBytes,
                          val recipient: Party,
                          val notary: Party) : AbstractCashFlow(progressTracker)

Here, everything after ``CashIssue`` is specifying the arguments to the constructor of a flow. In Yaml, an object
is specified as a set of ``key: value`` pairs and in our form, we separate them by commas. There are a few things
to note about this syntax:

* When a parameter is of type ``Amount<Currency>`` you can write it as either one of the dollar symbol ($),
  pound (£), euro (€) followed by the amount as a decimal, or as the value followed by the ISO currency code
  e.g. "100.12 CHF"
* ``OpaqueBytes`` is filled with the contents of whatever is provided as a string.
* ``Party`` objects are looked up by name.
* Strings do not need to be surrounded by quotes unless they contain a comma or embedded quotes. This makes it
  a lot more convenient to type such strings.

Other types also have sensible mappings from strings. See `the defined parsers`_ for more information.

Nested objects can be created using curly braces, as in ``{ a: 1, b: 2}``. This is helpful when no particular
parser is defined for the type you need, for instance, if an API requires a ``Pair<String, Int>``
which could be represented as ``{ first: foo, second: 123 }``.

The same syntax is also used to specify the parameters for RPCs, accessed via the ``run`` command, like this:

``run registeredFlows``

Attachments
-----------

The shell can be used to upload and download attachments from the node interactively. To learn more, see
the tutorial ":doc:`tutorial-attachments`".

Extending the shell
-------------------

The shell can be extended using commands written in either Java or `Groovy`_ (Groovy is a scripting language that
is Java compatible). Such commands have full access to the node internal APIs and thus can be used to achieve
almost anything.

A full tutorial on how to write such commands is out of scope for this documentation, to learn more please
refer to the `CRaSH`_ documentation. New commands can be placed in the ``shell-commands`` subdirectory in the
node directory. Edits to existing commands will be used automatically, but at this time commands added after the
node has started won't be automatically detected. Commands should be named in all lower case with either a
``.java`` or ``.groovy`` extension.

.. warning:: Commands written in Groovy ignore Java security checks, so have unrestricted access to node and JVM
   internals regardless of any sandboxing that may be in place. Don't allow untrusted users to edit files in the
   shell-commands directory!

Limitations
-----------

The shell will be enhanced over time. The currently known limitations include:

* SSH access is currently not available.
* There is no command completion for flows or RPCs.
* Command history is not preserved across restarts.
* The ``jdbc`` command requires you to explicitly log into the database first.
* Commands placed in the ``shell-commands`` directory are only noticed after the node is restarted.
* The ``jul`` command advertises access to logs, but it doesn't work with the logging framework we're using.

.. _Yaml: http://www.yaml.org/spec/1.2/spec.html
.. _the defined parsers: api/kotlin/corda/net.corda.client.jackson/-jackson-support/index.html
.. _Groovy: http://groovy-lang.org/
.. _CRaSH: http://www.crashub.org/
