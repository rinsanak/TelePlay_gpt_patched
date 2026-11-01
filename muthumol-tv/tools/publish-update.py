"""Verify a built update before staging it; publish only on the existing feed branch."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

CERT = '26e7b63fcdd306fe3f52a0172a3180160c0aee359a6c00f933ded41e8c0b8318'
PACKAGE = 'com.muthumol.player.dummy'
BASE = 'https://raw.githubusercontent.com/rinsanak/TelePlay_gpt_patched/tg-stream-updates/'


def stage(destination):
    project = Path('muthumol-tv')
    build = project / 'app/build'
    apk = build / 'outputs/apk/release/app-release.apk'
    if not apk.is_file():
        raise ValueError('Signed release APK is missing')
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
    cert = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--min-sdk-version', '25', '--print-certs', str(apk)], text=True)
    fingerprints = re.findall(r'certificate SHA-256 digest: ([a-f0-9]+)', cert)
    if fingerprints != [CERT]:
        raise ValueError('APK must use the original installed-app signing certificate')
    badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not package or package[1] != PACKAGE:
        raise ValueError('Unexpected APK package')
    code, version = int(package[2]), package[3]
    if not re.fullmatch(r'[A-Za-z0-9._-]{1,40}', version):
        raise ValueError('Invalid release version')
    metadata = json.loads((build / 'outputs/apk/release/output-metadata.json').read_text())
    if metadata['applicationId'] != PACKAGE or metadata['elements'][0]['versionCode'] != code:
        raise ValueError('APK and build metadata disagree')
    tests = list((build / 'test-results/testDebugUnitTest').glob('TEST-*.xml'))
    roots = [ET.parse(p).getroot() for p in tests]
    if not roots or sum(int(r.get('tests', 0)) - int(r.get('skipped', 0)) for r in roots) <= 0:
        raise ValueError('No passing tests executed')
    if any(int(r.get('failures', 0)) or int(r.get('errors', 0)) for r in roots):
        raise ValueError('Tests failed')
    lint = ET.parse(build / 'reports/lint-results-release.xml').getroot()
    if any(i.get('severity') in ('Error', 'Fatal') for i in lint.findall('issue')):
        raise ValueError('Release lint failed')
    destination.mkdir(parents=True, exist_ok=True)
    name = f'Muthumol_TV_{version}.apk'
    shutil.copy2(apk, destination / name)
    manifest = dict(versionCode=code, versionName=version, url=BASE + 'apks/' + name,
                    sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                    notes='Cinema Home UI, direct collections, improved remote navigation and typo-tolerant search. Existing subtitles retained. No Cast.')
    (destination / 'latest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(f'Verified signed {version} ({code}); staged APK and manifest')


def publish(staged):
    manifest = json.loads((staged / 'latest.json').read_text())
    previous = json.loads(Path('latest.json').read_text())
    if manifest['versionCode'] <= previous['versionCode']:
        raise ValueError('Version code must increase; refusing a stale update or downgrade')
    name = manifest['url'].removeprefix(BASE + 'apks/')
    if '/' in name or not re.fullmatch(r'Muthumol_TV_[A-Za-z0-9._-]+\.apk', name):
        raise ValueError('Invalid pinned updater URL')
    apk = staged / name
    if hashlib.sha256(apk.read_bytes()).hexdigest() != manifest['sha256']:
        raise ValueError('Staged APK checksum changed')
    # Validate every input before modifying the feed checkout.
    with __import__('zipfile').ZipFile(staged / 'source.zip') as archive:
        if archive.testzip() is not None:
            raise ValueError('Source archive is corrupt')
    Path('apks').mkdir(exist_ok=True)
    Path('sources').mkdir(exist_ok=True)
    shutil.copy2(apk, Path('apks') / name)
    shutil.copy2(staged / 'source.zip', Path('sources') / f"Muthumol_TV_{manifest['versionName']}_Source.zip")
    shutil.copy2(staged / 'latest.json', 'latest.json')
    print('Feed checkout staged; commit APK, source and manifest in one push')


if __name__ == '__main__':
    if len(sys.argv) != 3 or sys.argv[1] not in ('stage', 'publish'):
        raise SystemExit('Usage: publish-update.py stage|publish STAGING_DIRECTORY')
    globals()[sys.argv[1]](Path(sys.argv[2]).resolve())
