# Pygments lexer

We were getting a lot of warnings in the docs build, and people were unable to see the real warnings due to this.  So we're on a mission
to sort all of these out.

A lot of the errors were because the kotlin lexer in Pygments (the syntax highlighter that sphinx uses) didn't cope with a lot of the 
kotlin syntax that we use.  

We have fixes for the kotlin lexer that we are trying to get checked into Pygments, but while this is taking place we need to maintain a 
slightly hacked corda/docs-build docker image in which to build the docs.

## Some notes on building and testing

The sphinx/pygments brigade have delightfully decided that mercurial is a good idea.  So broadly speaking, to build/test a fix:

 * checkout pygments from [here](https://bitbucket.org/birkenfeld/pygments-main/overview)
 * copy the two python files in (might be worth diffing - they're based on 2.3.1 - nb the kotlin test is entirely new)   
 * build pygments whl file
 
   ```
   cd /path/to/pygments/
   python setup.py install
   pip install wheel
   wheel convert dist/Pygments-2.3.1.dev20190  # obviously use your version
   cp Pygments-2.3.1.dev20190401-py27-none-any.whl /path/to/corda/docs/source/docs_builder/lexer-fix
   ```
 * build the latest docker build (see docs readme)

   ```
   cd docs
   docker build -t corda/docs-builder:latest -f docs_builder/lexer-fix/Dockerfile .
   ```
  
 * push the new image up to docker hub (nb you can also test by going to /opt/docs and running `./make-docsite.sh`)
 
