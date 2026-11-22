#!/usr/bin/env python3
"""Copy a verified Debug APK to Google Drive Desktop, then remove superseded deliveries.

The default destination is My Drive/Inkloom/Debug in the single mounted Google Drive account.
Only the newest APK goes to Drive; its evidence (.json, .sha256, notes) stays in dist/.
Cloud transfer is performed by Google Drive Desktop; a local hash check is not a cloud download test.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import tempfile


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def atomic_copy(source, target):
    descriptor, temporary = tempfile.mkstemp(prefix='.inkloom-upload-', dir=target.parent)
    os.close(descriptor)
    temporary = Path(temporary)
    try:
        shutil.copyfile(source, temporary)
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--destination', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    apk = args.apk.resolve()
    if apk.parent != root / 'dist' or not re.fullmatch(r'Inkloom-\d+\.\d+\.\d+-[0-9a-f]+-debug\.apk', apk.name):
        parser.error('--apk must be a versioned Debug delivery in dist/')
    metadata = json.loads(apk.with_suffix('.json').read_text())
    if metadata['variant'] != 'debug' or sha256(apk) != metadata['sha256']:
        parser.error('APK failed its delivery hash/variant check')
    target = args.destination
    if target is None:
        roots = [p / 'My Drive' for p in (Path.home() / 'Library/CloudStorage').glob('GoogleDrive-*') if (p / 'My Drive').is_dir()]
        if len(roots) != 1:
            parser.error('Use --destination to select an existing Google Drive account')
        target = roots[0] / 'Inkloom/Debug'
    target = target.expanduser().resolve()
    if target == apk.parent:
        parser.error('Destination must differ from local dist/')
    target.mkdir(parents=True, exist_ok=True)
    notes = apk.parent / f"Inkloom-{metadata['version']}-test-notes.md"
    for evidence in (apk.with_suffix('.sha256'), notes):
        if not evidence.is_file():
            parser.error(f'Missing delivery evidence: {evidence.name}')
    atomic_copy(apk, target / apk.name)
    # Retire an older package only once the new copy is verified.
    if sha256(target / apk.name) != metadata['sha256']:
        raise RuntimeError(f'Drive copy hash mismatch for {apk.name}; old deliveries retained')
    keep = {apk.name}
    current = tuple(map(int, metadata['version'].split('.')))
    deleted = []
    for path in target.iterdir():
        match = re.fullmatch(r'Inkloom-(\d+\.\d+\.\d+)-(?:[0-9a-f]+-debug\.(?:apk|json|sha256)|test-notes\.md)', path.name)
        if path.name not in keep and match and path.is_file() and not path.is_symlink():
            if tuple(map(int, match[1].split('.'))) <= current:
                path.unlink()
                deleted.append(path.name)
    result = {'destination': str(target), 'apk': apk.name, 'version': metadata['version'],
              'sha256': metadata['sha256'], 'localDriveCopyVerified': True,
              'cloudTransfer': 'Managed by Google Drive Desktop; not a cloud download verification',
              'removedSupersededDeliveries': deleted}
    (root / 'tasks').mkdir(exist_ok=True)
    (root / 'tasks/APK_DRIVE_SYNC.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
