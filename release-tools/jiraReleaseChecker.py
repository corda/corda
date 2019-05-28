#!/usr/local/bin/python

#-------------------------------------------------------------------------------
#
#  Usage
# =======
#
#   ./jiraReleaseChecker.py <oldTag> <jiraTag> <jiraUser> [-m mode]
#   ./jiraReleaseChecker.py release-V3.1 "Corda 3.3" some.user@r3.com [-m not-in-jira]
#
# <oldTag> is the point prior to the current branches head in history from
# which to inspect commits. Normally this will be the tag of the previous
# release. e.g.
#       
#  master ----------------------------------------------
#               \
#  release/4     -----------+--------------+------------
#                          /              /
#               release/4.0    release/4.1
#
# The current release in the above example will be 4.2 and those commits
# extend from 4.1 having been backported from master. Thus <oldTag> is
# release/4.1
#
# <jiraTag> should refer to the version string used within
# the R3 Corda Jira to track the release. For example, for 3.3 this would be
# "Corda 3.3"
#
# <jiraUser> should be a registered user able to authenticate with the
# R3 Jira system. Authentication and password management is handled through
# the native OS keyring implementation.
#
# The script should be run on the relevant release branch within the git
# repository.
#
#  Modes
# -------
#
# The tool can operate in 3 modes
#
#   * rst           - The default when omitted. Will take the combined lists
#                     of issues fixed from both Jira and commit summaries and
#                     format that list in such a way it can be included within
#                     the release notes for the next release. Will include hyper
#                     links to the R3 Jira for each ticket.
#   * not-in-jira   - Print a list of tickets that are included in commit 
#                     summaries but are not tagged in Jira as fixed in the release
#   * not-in-commit - Print a list of tickets that are tagged in Jira but that
#                     are not mentioned in any commit summary,
#
#  Pre Requisites
# ================
#
# pip
# pyjira        
# gitpython
# keyring (optional)
#
#  Installation
# --------------
# Should be a simple matter of ``pip install <package>``
#
#  Atlassian Passwords
# =====================
#
# It's important to note that the Jira REST API no longer allows the use of
# user passwords for authentication. Rather, the password that must be supplied
# should be a generated API token. These can be created for your account at the
# following link
#
# https://id.atlassian.com/manage/api-tokens
#
#  Issues
# ========
#
# Doesn't really handle many errors all that well, also gives no mechanism 
# to enter a correct password into the keyring if a wrong one is added which
# isn't great but for now this should do
##
#-------------------------------------------------------------------------------

import re
import sys
import getpass
import argparse

try :
    import keyring
except ImportError :
    disableKeyring = True
else :
    disableKeyring = False

from jira import JIRA
from git import Repo

#-------------------------------------------------------------------------------

R3_JIRA_ADDR = "https://r3-cev.atlassian.net"
JIRA_MAX_RESULTS = 50

#-------------------------------------------------------------------------------

#
# For a given user (provide via the command line) authenticate with Jira and
# return an interface object instance
#
def jiraLogin(args_) :
    if not args_.resetPassword :
        password = keyring.get_password ('jira', args_.jiraUser) if not disableKeyring else None
    else :
        password = None

    if not password:
        password = getpass.getpass("Please enter your JIRA authkey, " +
                "it will be stored in your OS Keyring: ")

        if not disableKeyring :
            keyring.set_password ('jira', args_.jiraUser, password)

    return JIRA(R3_JIRA_ADDR, basic_auth=(args_.jiraUser, password))

#-------------------------------------------------------------------------------

#
# Cope with Jira REST API paginating query results
#
def jiraQuery (jira, query) :
    offset = 0
    results = JIRA_MAX_RESULTS
    rtn = []
    while (results == JIRA_MAX_RESULTS) :
        issues = jira.search_issues(query, maxResults=JIRA_MAX_RESULTS, startAt=offset)
        results = len(issues)
        if results > 0 :
            offset += JIRA_MAX_RESULTS
            rtn += issues

    return rtn

#-------------------------------------------------------------------------------

#
# Take a Jira issue and format it in such a way we can include it as a line
# item in the release notes formatted with a hyperlink to the issue in Jira
#
def issueToRST(issue) :
    return "* %s [`%s <%s/browse/%s>`_]" % (
            issue.fields.summary,
            issue.key,
            R3_JIRA_ADDR,
            issue.key)

#-------------------------------------------------------------------------------

#
# Get a list of jiras from Jira where those jiras are marked as fixed
# in some specific version (set on the command line).
#
# Optionally, an already authenticated Jira connection instance can be
# provided to avoid re-authenticating. The authenticated object
# is returned for reuse.
#
def getJirasFromJira(args_, jira_ = None) :
    jira = jiraLogin(args_) if jira_ == None else jira_

    return jiraQuery(jira, \
            'project in (Corda, Ent) And fixVersion in ("%s") and status in (Done)' % (args_.jiraTag)) \
            , jira

#-------------------------------------------------------------------------------

def getJiraIdsFromJira(args_, jira_ = None) :
    jira = jiraLogin(args_) if jira_ == None else jira_

    jirasFromJira, _ = jiraQuery(jira, \
            'project in (Corda, Ent) And fixVersion in ("%s") and status in (Done)' % (args_.jiraTag)) \
            , jira

    return [ j.key for j in jirasFromJira ], jira

#-------------------------------------------------------------------------------

def getJiraIdsFromCommits(args_) :
    jiraMatch = re.compile("(CORDA-\d+|ENT-\d+)")
    repo = Repo(".", search_parent_directories = True)

    jirasFromCommits = []
    for commit in list (repo.iter_commits ("%s.." % (args_.oldTag))) :
        jirasFromCommits += jiraMatch.findall(commit.summary)

    return jirasFromCommits

#-------------------------------------------------------------------------------

#
# Take the set of all tickets completed in a release (the union of those
# tagged in Jira and those marked in commit summaries) and format them
# for inclusion in the release notes (rst  format).
#
def rst (args_) :
    jiraIdsFromCommits = getJiraIdsFromCommits(args_)
    jirasFromJira, jiraObj = getJirasFromJira(args_)

    jiraIdsFromJira = [ jira.key for jira in jirasFromJira ]

    #
    # Grab the set of JIRA's that aren't tagged as fixed in the release but are
    # mentioned in a commit and pull down the JIRA information for those so as
    # to get access to their summary
    #
    extraJiras = set(jiraIdsFromCommits).difference(jiraIdsFromJira)

    if extraJiras != set() :
        jirasFromJira += jiraQuery(jiraObj, "key in (%s)" % (", ".join(extraJiras)))

    for jira in jirasFromJira :
        print issueToRST(jira)

#-------------------------------------------------------------------------------

def notInJira(args_) :
    jiraIdsFromCommits = getJiraIdsFromCommits(args_)
    jiraIdsFromJira, _ = getJiraIdsFromJira(args_)

    print 'Issues mentioned in commits but not set as "fixed in" in Jira'

    for jiraId in set(jiraIdsFromJira).difference(jiraIdsFromCommits) :
        print jiraId

#-------------------------------------------------------------------------------

def notInCommit(args_) :
    jiraIdsFromCommits = getJiraIdsFromCommits(args_)
    jiraIdsFromJira, _ = getJiraIdsFromJira(args_)

    print 'Issues tagged in Jira as fixed but not mentioned in any commit summary'

    for jiraId in set(jiraIdsFromCommits).difference(jiraIdsFromJira) :
        print jiraId

#-------------------------------------------------------------------------------

if __name__ == "__main__" :
    parser = argparse.ArgumentParser()
    parser.add_argument("-m", "--mode", help="display a square of a given number",
            choices = [ "rst", "not-in-jira", "not-in-commit"])
    parser.add_argument("oldTag", help="The previous release tag")
    parser.add_argument("jiraTag", help="The current Jira release")
    parser.add_argument("jiraUser", help="Who to authenticate with Jira as")
    parser.add_argument("--resetPassword", help="Set flag to allow resetting of password in the keyring",
            action='store_true')

    args = parser.parse_args()

    if not args.mode : args.mode = "rst"

    if args.mode == "rst" : rst(args)
    elif args.mode == "not-in-jira" : notInJira(args)
    elif args.mode == "not-in-commit" : notInCommit(args)

#-------------------------------------------------------------------------------
 

