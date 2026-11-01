"""Sign a CI-configured APK using the recovered original private Drive backup."""
import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tempfile

CERT = '26e7b63fcdd306fe3f52a0172a3180160c0aee359a6c00f933ded41e8c0b8318'

def main():
    if len(sys.argv) != 3:
        raise SystemExit('Usage: sign-original-apk.py UNSIGNED_APK SIGNED_APK')
    source, target = map(Path, sys.argv[1:])
    private = Path(os.getenv('TG_STREAM_ORIGINAL_KEY_DIR', '/workspace/private-signing-original'))
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
    verify = [str(tools/'apksigner'), 'verify', '--min-sdk-version', '25', '--print-certs']
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as temporary:
        aligned = Path(temporary)/'aligned.apk'
        subprocess.run([str(tools/'zipalign'), '-f', '-p', '4', str(source), str(aligned)], check=True)
        subprocess.run([str(tools/'apksigner'), 'sign', '--ks', str(private/'debug.keystore'),
                        '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'file:'+str(private/'store-password'),
                        '--key-pass', 'file:'+str(private/'key-password'), '--out', str(target), str(aligned)], check=True)
    result = subprocess.check_output(verify+[str(target)], text=True)
    if 'certificate SHA-256 digest: '+CERT not in result:
        target.unlink(missing_ok=True)
        raise SystemExit('Original certificate mismatch; output removed')
    print('Original V5.6 signing certificate verified')
    print('APK SHA256:', hashlib.sha256(target.read_bytes()).hexdigest())

if __name__ == '__main__':
    main()
