#!/usr/bin/env python3
"""Bounded public-doc/ignore check; never prints matching credential contents.
Run from any directory: python tools/test_public_docs.py
Does not scan history, images, binaries or release attachments.
"""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT).decode('utf-8')


def check():
    paths = [p for p in git('ls-files', '-z').split('\0') if p]
    failures = []
    secret_patterns = [
        r'-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----',
        r'GOCSPX-[A-Za-z0-9_-]{12,}',
        r'(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})',
        r'AKIA[0-9A-Z]{16}',
        r'"type"\s*:\s*"service_account"',
    ]
    secret = re.compile('|'.join(secret_patterns))
    personal_path = re.compile(r'[A-Za-z]:[\\/]+Users[\\/]+(?!Public\b)[^\\/\s`"<>]+|/home/(?!\$USER\b|user\b|sunshine\b|username\b)[^/\s`"<>]+')
    private_filename = re.compile(r'(?:^|/)(?:client_secret[^/]*\.json|desktop-oauth[^/]*\.json|[^/]*service[-_]account[^/]*\.json|[^/]*-firebase-adminsdk-[^/]*\.json|[^/]*\.(?:jks|keystore|p12|pfx))$', re.I)
    for name in paths:
        if private_filename.search(name):
            failures.append(f'{name}: private credential/signing filename tracked')
        if name.startswith(('third-party/', 'build-local/')):
            continue
        path = ROOT / name
        if path.suffix.lower() not in ('.md', '.txt', '.json', '.yaml', '.yml', '.gradle', '.properties', '.xml'):
            continue
        try:
            text = path.read_text(encoding='utf-8-sig')
        except (UnicodeError, OSError):
            continue
        for number, line in enumerate(text.splitlines(), 1):
            if secret.search(line):
                failures.append(f'{name}:{number}: credential-like content; inspect privately')
            if path.suffix.lower() == '.md' and personal_path.search(line):
                failures.append(f'{name}:{number}: personal absolute path')
    probes = [
        'build-local/setup.exe', 'build-local/report.json', '.env', '.env.local',
        'cloud/.env.production', 'client_secret_example.json', 'cloud/desktop-oauth.json',
        'cloud/sample-service-account.json', 'sample-firebase-adminsdk-local.json',
        'android/app/google-services.json', 'android/local.properties',
        'android/key.properties', 'android/upload.jks', 'android/upload.keystore',
        'cloud/server.key', 'cloud/upload.p12', 'cloud/upload.pfx',
        'cloud/cakey.pem', 'cloud/cacert.pem', 'cloud/cakey.pem.nous-account.json',
        'config/credentials/cakey.pem', 'session_history/session_history.db', 'crash-dumps/test.dmp',
    ]
    for name in probes:
        result = subprocess.run(['git', 'check-ignore', '--no-index', '-q', name], cwd=ROOT)
        if result.returncode != 0:
            failures.append(f'{name}: expected ignore rule missing')
    for name in ['README.md', 'android/README.md', 'docs/maintainers/README.md']:
        text = (ROOT / name).read_text(encoding='utf-8')
        for link in re.findall(r'\]\(([^)]+)\)', text):
            if '://' in link or link.startswith('#'):
                continue
            target = (ROOT / name).parent / link.split('#', 1)[0]
            if not target.exists():
                failures.append(f'{name}: missing relative link {link}')
    assert not failures, '\n'.join(failures)
    print(f'PASS: {len(paths)} tracked paths checked; public docs and {len(probes)} ignore probes')


if __name__ == '__main__':
    check()
