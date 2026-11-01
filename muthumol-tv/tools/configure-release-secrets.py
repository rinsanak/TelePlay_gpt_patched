"""Upload protected local signing material through the GitHub Secrets API, never Git."""
import base64
import json
import os
from pathlib import Path
import subprocess
import sys

REPO = 'rinsanak/TelePlay_gpt_patched'

def gh(*args, value=None):
    return subprocess.run(['gh', *args, '--repo', REPO], input=value,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)

if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: configure-release-secrets.py PROTECTED_SIGNING_DIRECTORY')
    directory = Path(sys.argv[1])
    # This read-only check must pass before attempting mutations.
    gh('secret', 'list', '--json', 'name')
    values = {
        'MUTHUMOL_KEYSTORE_BASE64': base64.b64encode((directory/'muthumol-release.jks').read_bytes()),
        'MUTHUMOL_STORE_PASSWORD': (directory/'store-password').read_bytes(),
        'MUTHUMOL_KEY_PASSWORD': (directory/'key-password').read_bytes(),
    }
    for name, value in values.items():
        gh('secret', 'set', name, value=value)
        print('Saved Actions secret:', name)
    gh('variable', 'set', 'MUTHUMOL_KEY_ALIAS', '--body', 'muthumol-release')
    for name in ['TG_API_ID', 'TG_API_HASH']:
        if os.environ.get(name):
            gh('secret', 'set', name, value=os.environ[name].encode())
            print('Saved Actions secret:', name)
    names = {x['name'] for x in json.loads(gh('secret', 'list', '--json', 'name').stdout)}
    missing = {'TG_API_ID', 'TG_API_HASH'} - names
    if missing:
        raise SystemExit('Signing saved; login release still needs Actions secrets: ' + ', '.join(sorted(missing)))
    print('Required Actions secret names are present. Run the release workflow and verify the feed.')
