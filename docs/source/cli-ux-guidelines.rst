CLI UX Guide
============

Command line options
--------------------

Command line utilities should use picocli (http://picocli.info) to provide a unified interface and follow the conventions in the picocli documentation, some of the more important of which are repeated below.

Option names
~~~~~~~~~~~~

* Options should be specified on the command line using a double dash, e.g. ``--parameter``.
* Options that consist of multiple words should be separated via hyphens e.g. ``--my-multiple-word-parameter-name``.
* A ``--help`` option should be provided which details all possible options with a brief description and any short name equivalents.
* A ``--version`` option that should output the version number of the software.
* A ``--logging-level`` option should be provided which specifies the logging level to be used in any logging files. Acceptable values should be ``DEBUG``, ``TRACE``, ``INFO``, ``WARN`` and ``ERROR``.
* A ``--verbose`` option should be provided which specifies that logging output should be displayed in the console.
* A ``--install-completion`` option should be provided that creates and installs a bash completion file.

Short names
~~~~~~~~~~~

* Where possible a POSIX style short option should be provided for ease of use (see http://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap12.html#tag_12_02).

  * These should be prefixed with a single hyphen.
  * For example ``-v`` for ``--verbose``, ``-d`` for ``--dev-mode``.

* The picocli interface allows combinations of options without parameters, for example, ```-v`` and ```-d`` can be combined as ``-vd``.

Positional parameters
~~~~~~~~~~~~~~~~~~~~~

* Parameters specified without an option should ideally all be part of a list.

  * For example, in ``java -jar test.jar file1 file2 file3``, the parameters file1, file2 and file3 should be a list of files that are all acted on together (e.g. a list of CorDapps).

* Avoid using positional parameters to mean different things, which involves someone remembering in which order things need to be specified.

  * For example, avoid ``java -jar test.jar configfile1 cordapp1 cordapp2`` where parameter 1 is the config file and any subsequent parameters are the CorDapps.
  * Use ``java -jar test.jar cordapp1 cordapp2 --config-file configfile1`` instead.

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