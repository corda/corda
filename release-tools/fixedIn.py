#!/usr/bin/python

#-------------------------------------------------------------------------------
#
# The purpose of this script is to operate on a release branch for a release
# and automagically infer the list of fixed issues contained within that
# release for inclusion within the release notes
#
#  Usage
# -------
#
# ./fixedIn.py <previous release tag> <Jira release version> <Jira user>
#
# The previous release tag refers to the previous release on the branch or, if
# a new release branch from master, the branch point. For example, for the 3.3
# release of Corda this would be release-V3.1 (because 3.2 shipped from a
# separate branch)
#
# The Jira release version should refer to the version string used within
# the R3 Corda Jira to track the release. For example, for 3.3 this would be
# "Corda 3.3"
#
# The Jira user should be a registered user able to authenticate with the
# R3 Jira system. Authentication and password management is handled through
# the native OS keyring implementation.
#
# The script should be run on the relevant release branch within the git
# repository.
#
#  For Example
# -------------
# ./fixedIn.py release-V3.1 "Corda 3.3" some.user@r3.com
#
#  Pre Requisites
# ----------------
#
# pip
# pyjira        
# gitpython
#
#  Issues
# --------
# Doesn't really handle many errors all that well, also gives no mechanism 
# to enter a correct password into the keyring if a wrong one is added which
# isn't great but for now this should do
#
#-------------------------------------------------------------------------------

import re
import sys
import keyring
import getpass

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
def jiraLogin(user) :
    password = keyring.get_password ('jira', user)

    if not password:
        password = getpass.getpass("Please enter your JIRA password, " +
                "it will be stored in your OS Keyring: ")
        keyring.set_password ('jira', user, password)

    return JIRA(R3_JIRA_ADDR, auth=(user, password))

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

def helpAndExitWithError(error) :
    print "ERROR: " + error + "\n"
    print ("Usage: ./fixedIn.py <previous release tag> <current target version> <Jira user>")
    sys.exit(1)

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

def main (args_) :
    jiraMatch = re.compile("(CORDA-\d+|ENT-\d+)")
    repo = Repo(".", search_parent_directories = True)

    jirasFromCommits = []
    for commit in list (repo.iter_commits ("%s.." % (args_[0]))) :
        jirasFromCommits += jiraMatch.findall(commit.summary)

    jira = jiraLogin(args_[2])

    issues = jiraQuery(jira, 'project in (Corda, Ent) And fixVersion in ("%s") and status in (Done)' % (args_[1])) 

    jirasFromJira = [ commit.key for commit in issues ]

    #
    # Grab the set of JIRA's that aren't tagged as fixed in the release but are mentioned
    # in a commit and pull down the JIRA information for those so as to get access
    # to their summary
    #
    extraJiras = set(jirasFromCommits).difference(jirasFromJira)
    issues += jiraQuery(jira, "key in (%s)" % (", ".join(extraJiras)))

    for issue in issues :
        print issueToRST(issue)

#-------------------------------------------------------------------------------

if __name__ == "__main__" :
    if len(sys.argv) != 4 : 
        helpAndExitWithError ("Incorrect number of arguments")
    main(sys.argv[1:])

#-------------------------------------------------------------------------------

