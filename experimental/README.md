# Experimental module

The purpose of this module is to hold contracts/cordapps that aren't yet ready for code review, but which still want
to be refactored and kept compiling as the underlying platform changes. Code placed into experimental *must* eventually
either be moved into the main modules and go through code review, or deleted.

Code placed here can be committed to directly onto master at any time as long as it doesn't break the build
(no compile failures or unit test failures). Any commits here that break the build will simply be rolled back.

