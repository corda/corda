# {{{ Dependencies

from __future__ import print_function
import sys, os

try:
    from getpass import getpass
except:
    def getpass(message): return raw_input(message)

try:
    from keyring import get_password, set_password
except:
    def get_password(account, user): return None
    def set_password(account, user, password): pass

# Python 2.x fix; raw_input was renamed to input in Python 3
try: input = raw_input
except NameError: pass

# }}}

# {{{ prompt(message, secret) - Get input from user; if secret is true, hide the input
def prompt(message, secret=False):
    try:
        return getpass(message) if secret else input(message)
    except:
        print()
        return ''
# }}}

# {{{ confirm(message, auto_yes) - Request confirmation from user and proceed if the response is 'yes'
def confirm(message, auto_yes=False):
    if auto_yes:
        print(message.replace('?', '.'))
        return
    if not prompt(u'{} (y/N) '.format(message)).lower().strip().startswith('y'):
        sys.exit(1)
# }}}

# {{{ login(account, user, password, use_keyring) - Present user with login prompt and return the provided username and password. If use_keyring is true, use previously provided password (if any)
def login(account, user=None, password=None, use_keyring=True, reset_keyring=False):
    if not user:
        if 'JIRA_USER' not in os.environ:
            user = prompt('Username: ')
            user = u'{}@r3.com'.format(user) if '@' not in user else user
            if not user: return (None, None)
        else:
            user = os.environ['JIRA_USER']
            print('Username: {}'.format(user))
    else:
        user = u'{}@r3.com'.format(user) if '@' not in user else user
        print('Username: {}'.format(user))
    if reset_keyring:
        set_password(account, user, '')
    password = get_password(account, user) if password is None and use_keyring and not reset_keyring else password
    if not password:
        password = prompt('Password: ', secret=True)
        if not password: return (None, None)
    else:
        print('Password: ********')
    if use_keyring:
        set_password(account, user, password)
    print()
    return (user, password)
# }}}
