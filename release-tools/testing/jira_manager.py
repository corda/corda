# {{{ JIRA dependency
from jira import JIRA
from jira.exceptions import JIRAError
# }}}

# {{{ Python 2 and 3 interoperability
try:
    unicode('')
except NameError:
    unicode = str
# }}}

# {{{ Class for interacting with a hosted JIRA system
class Jira:

    # {{{ Constants
    BLOCKS = 'Blocks'
    DUPLICATE = 'Duplicate'
    RELATES = 'Relates'
    # }}}

    # {{{ init(address) - Initialise JIRA class, pointing it to the JIRA endpoint
    def __init__(self, address='https://r3-cev.atlassian.net'):
        self.address = address
        self.jira = None
        self.mock_key = 1
        self.custom_fields_by_name, self.custom_fields_by_key = {}, {}
    # }}}

    # {{{ login(user, password) - Log in as a specific JIRA user
    def login(self, user, password):
        try:
            self.jira = JIRA(self.address, auth=(user, password))
            for x in self.jira.fields():
                if x['custom']:
                    self.custom_fields_by_name[x['name']] = x['key']
                    self.custom_fields_by_key[x['key']] = x['name']
            return self
        except Exception as error:
            message = error.message
            if isinstance(error, JIRAError):
                message = error.text if error.text and len(error.text) > 0 and not error.text.startswith('<!') else message
            raise Exception('failed to log in to JIRA{}{}'.format(': ' if message else '', message))
    # }}}

    # {{{ search(query) - Search for issues and manually traverse pages if multiple pages are returned
    def search(self, query, *args):
        max_count = 50
        index, offset, count = 0, 0, max_count
        query = query.format(*args) if len(args) > 0 else query
        while count == max_count:
            try:
                issues = self.jira.search_issues(query, maxResults=max_count, startAt=offset)
                count = len(issues)
                offset += count
                for issue in issues:
                    index += 1
                    yield Issue(self, index=index, issue=issue)
            except JIRAError as error:
                raise Exception('failed to run query "{}": {}'.format(query, error.text))
    # }}}

    # {{{ find(key) - Look up issue by key
    def find(self, key):
        try:
            issue = self.jira.issue(key)
            return Issue(self, issue=issue)
        except JIRAError as error:
            raise Exception('failed to look up issue "{}": {}'.format(key, error.text))
    # }}}

    # {{{ create(fields, dry_run) - Create a new issue
    def create(self, fields, dry_run=False):
        if dry_run:
            return Issue(self, fields=fields)
        try:
            issue = self.jira.create_issue(fields)
            return Issue(self, issue=issue)
        except JIRAError as error:
            raise Exception('failed to create issue: {}'.format(error.text))
    # }}}

    # {{{ link(issue_key, other_issue_key, relationship, dry_run) - Link one issue to another
    def link(self, issue_key, other_issue_key, relationship=RELATES, dry_run=False):
        if dry_run:
            return
        try:
            self.jira.create_issue_link(
                type=relationship,
                inwardIssue=issue_key,
                outwardIssue=other_issue_key,
                comment={
                    'body': 'Linked {} to {}'.format(issue_key, other_issue_key),
                }
            )
        except JIRAError as error:
            raise Exception('failed to link {} and {}: {}'.format(issue_key, other_issue_key, error.text))
    # }}}

# }}}

# {{{ Representation of a JIRA issue
class Issue:

    mock_index = 1

    # {{{ init(jira, index, issue, key, fields) - Instantiate an abstract representation of an issue
    def __init__(self, jira, index=0, issue=None, key=None, fields=None):
        self._jira = jira
        self._index = index
        self._issue = issue
        self._fields = fields
        self._key = key if key else u'DRY-{:03d}'.format(Issue.mock_index)
        if not key and not issue:
            Issue.mock_index += 1
    # }}}

    # {{{ getattr(key) - Get attribute from issue
    def __getattr__(self, key):
        if key == 'index':
            return self._index
        if self._issue:
            value = self._issue.__getattr__(key)
            return WrappedDictionary(value) if key == 'fields' else value
        elif self._fields:
            if key == 'key':
                return self._key
            elif key == 'fields':
                return WrappedDictionary(self._fields)
        return None
    # }}}

    # {{{ getitem(key) - Get item from issue
    def __getitem__(self, key):
        return self.__getattr__(key)
    # }}}

    # {{{ str() - Get a string representation of the issue
    def __str__(self):
        summary = self.fields.summary.strip()
        labels = self.fields.labels
        if len(labels) > 0:
            return u'[{}] {} ({})'.format(self.key, summary, ', '.join(labels))
        else:
            return u'[{}] {}'.format(self.key, summary)
    # }}}

    # {{{ clone(..., dry_run) - Create a clone of the issue, resetting any provided fields)
    def clone(self, **kwargs):
        dry_run = kwargs['dry_run'] if 'dry_run' in kwargs else False
        fields = self.fields.to_dict()
        whitelisted_fields = [
            'project', 'summary', 'description', 'issuetype', 'labels', 'parent', 'priority',
            self._jira.custom_fields_by_name['Target Version/s'],
        ]
        if 'parent' not in kwargs:
            whitelisted_fields += [
                self._jira.custom_fields_by_name['Epic Link'],
                self._jira.custom_fields_by_name['Preconditions'],
                self._jira.custom_fields_by_name['Test Steps'],
                self._jira.custom_fields_by_name['Acceptance Criteria'],
                self._jira.custom_fields_by_name['Primary Test Environment'],
            ]
        for key in kwargs:
            value = kwargs[key]
            if key == 'parent' and type(value) in (str, unicode):
                fields[key] = { 'key' : value }
            elif key == 'version':
                fields[self._jira.custom_fields_by_name['Target Version/s']] = [{ 'name' : value }]
            else:
                fields[key] = value
        for key in [key for key in fields if key not in whitelisted_fields]:
            del fields[key]
        new_issue = self._jira.create(fields, dry_run=dry_run)
        return new_issue
    # }}}

# }}}

# {{{ Dictionary with attribute getters
class WrappedDictionary:
    def __init__(self, dictionary):
        self.dictionary = dictionary

    def __getattr__(self, key):
        return self.__getitem__(key)

    def __getitem__(self, key):
        return self.dictionary[key]

    def to_dict(self):
        return dict(self.dictionary)

# }}}
