# Contributing

Please take a moment to review this document in order to make the contribution
process easy and effective for everyone involved!

## Using the issue tracker

The issue tracker is for:
* [Bug Reports](#bug-reports)
* [Feature Requests](#feature-requests)
* [Submitting Pull Requests](#pull-requests)

Please **do not** use the issue tracker for personal support requests.

## Bug Reports

A bug is a _demonstrable problem_ that is caused by the code in the repository.

Guidelines for bug reports:

1. **Use the GitHub issue search** &mdash; check if the issue has already been
   reported.

2. **Check if the issue has been fixed** &mdash; try to reproduce it using the
   `master` branch in the repository.

3. **Isolate and report the problem** &mdash; ideally create a reduced test
   case or a small [jsfiddle](http://jsfiddle.net) showing the issue.

Please try to be as detailed as possible in your report. Include information about
your operating system, browser, jQuery version, and masked input plugin version.
Please provide steps to reproduce the issue as well as the outcome you were expecting.

## Feature Requests

Feature requests are welcome. It's up to *you* to make a strong case of the merits of
this feature. Please provide as much detail and context as possible.

Features that have a very narrow use case are unlikely to be accepted unless we
can come up with a way to come to a more general solution. Please don't let
that stop you from sharing your ideas, just keep that in mind.

## Pull Requests

Good pull requests are very helpful. They should remain focused
in scope and avoid containing unrelated commits.

**IMPORTANT**: By submitting a patch, you agree that your work will be
licensed under the license used by the project.

If you have any large pull request in mind (e.g. implementing features,
refactoring code, etc), **please ask first** otherwise you risk spending
a lot of time working on something that the project's developers might
not want to merge into the project.

Please adhere to the coding conventions in the project (indentation,
accurate comments, etc.) and don't forget to add your own tests and
documentation. When working with git, we recommend the following process
in order to craft an excellent pull request:

1. [Fork](http://help.github.com/fork-a-repo/) the project, clone your fork,
   and configure the remotes:

   ```bash
   # Clone your fork of the repo into the current directory
   git clone https://github.com/<your-username>/jquery.maskedinput
   # Navigate to the newly cloned directory
   cd jquery.maskedinput
   # Assign the original repo to a remote called "upstream"
   git remote add upstream https://github.com/digitalBush/jquery.maskedinput
   ```

2. If you cloned a while ago, get the latest changes from upstream:

   ```bash
   git checkout master
   git pull upstream master
   ```

3. Create a new topic branch (off of `master`) to contain your feature, change,
   or fix.

   **IMPORTANT**: Making changes in `master` is discouraged. You should always
   keep your local `master` in sync with upstream `master` and make your
   changes in topic branches.

   ```bash
   git checkout -b <topic-branch-name>
   ```

4. Commit your changes in logical chunks. Keep your commit messages organized,
   with a short description in the first line and more detailed information on
   the following lines.

   Please use git's
   [interactive rebase](https://help.github.com/articles/interactive-rebase)
   feature to tidy up your commits before making them public. Ideally when you
   are finished you'll have a single commit.

5. Make sure all the tests are still passing.

   ```bash
   npm test
   ```

6. Push your topic branch up to your fork:

   ```bash
   git push origin <topic-branch-name>
   ```

7. [Open a Pull Request](https://help.github.com/articles/using-pull-requests/)
    with a clear title and description.

8. If you haven't updated your pull request for a while, you should consider
   rebasing on master and resolving any conflicts.

   **IMPORTANT**: _Never ever_ merge upstream `master` into your branches. You
   should always `git rebase` on `master` to bring your changes up to date when
   necessary.

   ```bash
   git checkout master
   git pull upstream master
   git checkout <your-topic-branch>
   git rebase master
   ```
