.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

CLI UX Guide
============

Command line options
--------------------

Command line utilities should use picocli (http://picocli.info) to provide a unified interface and follow the conventions in the picocli documentation, some of the more important of which are repeated below.

Option names
~~~~~~~~~~~~

* Options should be specified on the command line using a double dash, e.g. ``--parameter``.
* Options that consist of multiple words should be separated via hyphens e.g. ``--my-multiple-word-parameter-name``.

Short names
~~~~~~~~~~~

* Where possible a POSIX style short option should be provided for ease of use (see http://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap12.html#tag_12_02).

  * These should be prefixed with a single hyphen.
  * For example ``-V`` for ``--verbose``, ``-d`` for ``--dev-mode``.
  * Consider adding short options for commands that would be ran regularly as part of troubleshooting/operational processes.
  * Short options should not be used for commands that would be used just once, for example initialising/registration type tasks.

* The picocli interface allows combinations of options without parameters, for example, ```-v`` and ```-d`` can be combined as ``-vd``.

Positional parameters
~~~~~~~~~~~~~~~~~~~~~

* Parameters specified without an option should ideally all be part of a list.

  * For example, in ``java -jar test.jar file1 file2 file3``, the parameters file1, file2 and file3 should be a list of files that are all acted on together (e.g. a list of CorDapps).

* Avoid using positional parameters to mean different things, which involves someone remembering in which order things need to be specified.

  * For example, avoid ``java -jar test.jar configfile1 cordapp1 cordapp2`` where parameter 1 is the config file and any subsequent parameters are the CorDapps.
  * Use ``java -jar test.jar cordapp1 cordapp2 --config-file configfile1`` instead.

Standard options
~~~~~~~~~~~~~~~~

* A ``--help`` option should be provided which details all possible options with a brief description and any short name equivalents. A ``-h`` short option should also be provided.
* A ``--version`` option that should output the version number of the software. A ``-V`` short option should also be provided.
* A ``--logging-level`` option should be provided which specifies the logging level to be used in any logging files. Acceptable values should be ``DEBUG``, ``TRACE``, ``INFO``, ``WARN`` and ``ERROR``.
* ``--verbose`` and ``--log-to-console`` options should be provided (both equivalent) which specifies that logging output should be displayed in the console.
  A ``-v`` short option should also be provided.
* A ``--install-shell-extensions`` option should be provided that creates and installs a bash completion file.


Defaults
~~~~~~~~

* Flags should have sensible defaults.
* Boolean flags should always default to false. Specifying the flag without a parameter should set it to true. For example ``--use-something` should be equal to ``--use-something=true`` and no option should be equal to ``--my-flag=false``.
* Do a bit of work to figure out reasonable defaults. Nobody likes having to set a dozen flags before the tool will cooperate.

Adding a new option
~~~~~~~~~~~~~~~~~~~

* Boolean options should start with is, has or with. For example, ``--is-cheesy``, ``--with-cheese``, ``--has-cheese-on``.
* Any new options must be documented in the docsite and via the ``--help`` screen.
* Never use acronyms in option names and try and make them as descriptive as possible.

Parameter stability
~~~~~~~~~~~~~~~~~~~

* Avoid removing parameters. If, for some reason, a parameter needs to be renamed, add a new parameter with the new name and deprecate the old parameter, or alternatively keep both versions of the parameter.


The ``CordaCliWrapper`` base class
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The ``CordaCliWrapper`` base class from the ``cliutils`` module should be used as a base where practicable, this will provide a set of default options out of the box.
In order to use it, create a class containing your command line options using the syntax provided at (see the `picocli<https://picocli.info/#_options>`_ website for more information)


.. container:: codeset

    .. sourcecode:: kotlin

        class UsefulUtility : CordaCliWrapper(
            "useful-utility", // the alias to be used for this utility in bash. When --install-shell-extensions is run
                              // you will be able to invoke this command by running <useful-utility --opts> from the command line
            "A command line utility that is super useful!" // A description of this utility to be displayed when --help is run
        ) {
            @Option(names = ["--extra-usefulness", "-e"], // A list of the different ways this option can be referenced
                    description = ["Use this option to add extra usefulness"] // Help description to be displayed for this option
            )
            private var extraUsefulness: Boolean = false // This default option will be shown in the help output

            override fun runProgram(): Int { // override this function to run the actual program
                try {
                    // do some stuff
                } catch (KnownException: ex) {
                    return 100 // return a special exit code for known exceptions
                }

                return 0 // this is the exit code to be returned to the system
            }
        }


Then in your ``main()`` method:

.. container:: codeset

    .. sourcecode:: kotlin

        import net.corda.cliutils.start

        fun main(args: Array<String>) {
            UsefulUtility().start(args)
        }



Application behavior
--------------------

* Set exit codes using exitProcess.

  * Zero means success.
  * Other numbers mean errors.

* Setting a unique error code (starting from 1) for each thing that can conceivably break makes your tool shell-scripting friendly.
* Make sure all exit codes are documented with recommended remedies where applicable.
* Your ``--help`` text or other docs should ideally include examples. Writing examples is also a good way to find out if your program requires a dozen flags to do anything.
* Don’t print logging output to the console unless the user requested it via a ``-–verbose`` flag (conventionally shortened to ``-v``). Logs should be either suppressed or saved to a text file during normal usage, except for errors, which are always OK to print.
* Don't print stack traces to the console. Stack traces can be added to logging files, but the user should see as meaningful error description as possible.