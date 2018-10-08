from __future__ import print_function
from argparse import Action, ArgumentParser
import sys, traceback

# {{{ Representation of a command-line program
class Program:

    # Create a new command-line program representation, provided an optional name and description
    def __init__(self, name=None, description=None):
        self.parser = ArgumentParser(name, description=description)
        self.subparsers = self.parser.add_subparsers(title='commands')
        self.arguments = []

    # Enter program definition block
    def __enter__(self):
        return self

    # Add argument to the top-level command-line interface and all registered sub-commands
    def add(self, name, *args, **kwargs):
        self.parser.add_argument(name, *args, **kwargs)
        self.arguments.append(([name] + list(args), kwargs))

    # Add sub-command to the set of command-line options
    def command(self, name, description, handler):
        return Command(self, self.subparsers, name, description, handler)

    # Parse arguments from the command line, and run the associated command handler
    def __exit__(self, type, value, tb):
        args = self.parser.parse_args()
        try:
            if 'func' in args:
                args.func(args)
            else:
                self.parser.print_help()
        except KeyboardInterrupt:
            print()
        except Exception as error:
            if args.verbose:
                t, exception, tb = sys.exc_info()
                self.parser.error('{}\n\n{}'.format(error.message, '\n'.join(traceback.format_tb(tb))))
            else:
                try:
                    self.parser.error(error.message)
                except AttributeError:
                    self.parser.error(str(error))
# }}}

# {{{ Representation of a sub-command of a command-line program
class Command:

    # Create a sub-command, provided a name, description and command handler
    def __init__(self, program, subparsers, name, description, handler):
        self.program = program
        self.subparsers = subparsers
        self.name = name
        self.description = description
        self.handler = handler

    # Enter sub-command definition block
    def __enter__(self):
        self.parser = self.subparsers.add_parser(self.name, description=self.description)
        return self

    # Add argument to the CLI command
    def add(self, name, *args, **kwargs):
        self.parser.add_argument(name, *args, **kwargs)

    # Exit sub-command definition block and register default handler
    def __exit__(self, type, value, traceback):
        for (args, kwargs) in self.program.arguments:
            self.parser.add_argument(*args, **kwargs)
        self.parser.set_defaults(func=self.handler)

# }}}
