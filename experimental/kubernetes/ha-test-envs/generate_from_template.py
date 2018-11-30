#!/usr/bin/env python

# Jinja2 needs to be installed, pip install Jinja2
# example run: ./generate_from_template.py -o artemis1/2.6.3 -t templates/artemis/2.6.3 -v '{"name":"artemis", "image":"vromero/activemq-artemis:2.6.3", "artemis_users":["NodeA=O=PartyA, L=London, C=GB", "NodeB=O=PartyB, L=London, C=GB"], "artemis_role_nodes":"Node=NodeA,NodeB"}'
# All jinja2 template variables must be defined!
import argparse
import ast
import sys
import os
import jinja2

def main():
    arg_parser = argparse.ArgumentParser()
    arg_parser.add_argument('--output-folder', '-o', help = 'output folder', required = True)
    arg_parser.add_argument('--template-folder', '-t', help = 'template folder', required = True)
    arg_parser.add_argument('--values-dictionary', '-v', help = 'dictionary of template values, \'{"var_name":"value"}\'', required = True)
    args = arg_parser.parse_args()

    output_folder = args.output_folder
    template_folder = args.template_folder
    values_dictionary = ast.literal_eval(args.values_dictionary)

    if os.path.exists(output_folder):
        sys.exit(output_folder + ' already exists') 

    os.makedirs(output_folder)
    env = jinja2.Environment(loader = jinja2.FileSystemLoader('.'), undefined = jinja2.StrictUndefined)

    for root, _, files in os.walk(template_folder):
        for file in files:
            gen_path = os.path.join(output_folder, os.path.relpath(root, template_folder))

            if not os.path.exists(gen_path):
                os.makedirs(gen_path)
            
            open(os.path.join(gen_path, file), 'w').write(env.get_template(os.path.join(root, file)).render(values_dictionary))

if __name__ == '__main__':
    main()