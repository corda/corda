CLI UX Guide
============

Command line options
--------------------

Command line utilities should use picocli (http://picocli.info) to provide a unified interface and follow the conventions in the picocli documentation, some of the more important of which are repeated below.

Option names
~~~~~~~~~~~~

* Options should be specified command line using a double dash, e.g. ``--parameter``.
* Options that consist of multiple words should be separated via hyphens e.g. ``--my-multiple-word-paramter-name``

Short names
~~~~~~~~~~~

* Where possible a POSIX style short option should be provided for ease of use (see http://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap12.html#tag_12_02)

  * These should be prefixed with a single hyphen
  * For example ``-v`` for ``--verbose``, ``-d`` for ``--dev-mode``

* The picocli interface allows combinations of options without parameters, for example, -v and -d can be combined as -vd

Positional parameters
~~~~~~~~~~~~~~~~~~~~~

* Parameters specified without an option should ideally all be part of a list

  * For example, use ``java -jar test.jar file1 file2 file3``, the parameters file1, file2 and file3 should be a list of files that all are all acted on together (e.g. a list of cordapps)

* Avoid using positional parameters to mean different thing, which involves someone remembering which order things need to be specified

  * For example, avoid ``java -jar test.jar configfile1 cordapp1 cordapp2`` where parameter 1 is the config file and any subsequent parameters are the cordapps
  * Use ``java -jar test.jar cordapp1 cordapp2 --config-file configfile1`` instead

Defaults
~~~~~~~~

* Flags should have sensible defaults
* Boolean flags should always default to false. Specifying the flag without a parameter should set it to true. For example --my-flag should be equal to --my-flag=true and no option should be equal to --my-flag=false
* Do a bit of work to figure out reasonable defaults. Nobody likes having to set a dozen flags before the tool will cooperate

Adding a new option
~~~~~~~~~~~~~~~~~~~

* Boolean options should start with is or has?
* Any new options must be  documented in the docsite.
* Avoid using acronyms in option names and try and make them as descriptive as possible.

Parameter stability
~~~~~~~~~~~~~

* Avoid removing parameters. If, for some reason, a parameter needs to be renamed, add a new parameter with the new name and deprecate the old parameter, or alternatively keep both versions of the parameter.


Application behavior
--------------------

* Set exit codes using exitProcess. Zero means success. Other numbers mean errors. Setting a unique error code (starting from 1) for each thing that can conceivably break makes your tool shell-scripting friendly
* Your ``--help`` text or other docs should ideally include examples. Writing examples is also a good way to find out that your program requires a dozen flags to do anything
* Don’t print logging output to the console unless the user requested it via a ``-–verbose`` flag (conventionally shortened to -v) or a ``-–log-to-console`` flag. Logs should be either suppressed or saved to a text file during normal usage, except for errors, which are always OK to print
