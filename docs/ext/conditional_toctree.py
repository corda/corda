import re
from docutils.parsers.rst import directives
from sphinx.directives.other import TocTree

def setup(app):
    app.add_directive('conditional-toctree', ConditionalTocTree)
    ConditionalTocTree.defined_tags = app.tags.tags.keys()
    return {'version': '1.0.0'}

def tag(argument):
    return directives.choice(argument, ('htmlmode', 'pdfmode'))

class ConditionalTocTree(TocTree):

    defined_tags = []
    has_content = True
    required_arguments = 0
    optional_arguments = 0
    final_argument_whitespace = False
    option_spec = {
        'maxdepth': int,
        'name': directives.unchanged,
        'caption': directives.unchanged_required,
        'glob': directives.flag,
        'hidden': directives.flag,
        'includehidden': directives.flag,
        'titlesonly': directives.flag,
        'reversed': directives.flag,
        'if_tag': tag,
    }

    def run(self):
        if_tag = self.options.get('if_tag')
        if if_tag in self.defined_tags:
            return TocTree.run(self)
        else:
            return []
