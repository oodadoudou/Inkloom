#!/usr/bin/env python3
"""Keep the validated current Debug release; remove superseded generated APKs.

Dry run by default. Run after packaging:
  python3 scripts/clean_generated_artifacts.py --keep dist/Inkloom-VERSION-COMMIT-debug.apk --apply
Compiler caches, source fixtures and raster regression evidence are deliberately retained.
"""
import argparse
import hashlib
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--keep', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    keep = args.keep.resolve()
    if keep.parent != root / 'dist' or not keep.name.endswith('-debug.apk'):
        parser.error('--keep must name a Debug APK directly inside this repository’s dist directory')
    metadata = json.loads(keep.with_suffix('.json').read_text())
    if metadata.get('variant') != 'debug' or hashlib.sha256(keep.read_bytes()).hexdigest() != metadata['sha256']:
        parser.error('Retained APK does not match its Debug release evidence')
    current = tuple(map(int, metadata['version'].split('.')))
    candidates = set()
    for apk in (root / 'dist').glob('Inkloom-*-debug.apk'):
        if apk == keep:
            continue
        evidence = apk.with_suffix('.json')
        if evidence.exists():
            old = json.loads(evidence.read_text())
            if tuple(map(int, old['version'].split('.'))) > current:
                continue
        candidates.update(p for p in (apk, evidence, apk.with_suffix('.sha256')) if p.is_file())
    for notes in (root / 'dist').glob('Inkloom-*-test-notes.md'):
        try:
            version = tuple(map(int, notes.name.removeprefix('Inkloom-').removesuffix('-test-notes.md').split('.')))
        except ValueError:
            continue
        if version < current:
            candidates.add(notes)
    artifacts = root / '.codex-artifacts'
    if artifacts.exists():
        candidates.update(artifacts.rglob('*.apk'))
        candidates.update(artifacts.rglob('*.aab'))
    # Profile output is no longer a deliverable. Leave active Debug/test build products alone.
    for folder in ('outputs', 'intermediates'):
        generated = root / 'app' / 'build' / folder
        if generated.exists():
            candidates.update(p for p in generated.rglob('*') if p.is_file() and
                              any(part.lower() == 'profile' for part in p.relative_to(generated).parts))
    files = sorted(p for p in candidates if p.is_file() and not p.is_symlink() and p != keep)
    entries = [{'path': str(p.relative_to(root)), 'bytes': p.stat().st_size} for p in files]
    print(json.dumps({'mode': 'delete' if args.apply else 'dry-run', 'keep': str(keep.relative_to(root)),
                      'files': len(files), 'bytes': sum(e['bytes'] for e in entries), 'entries': entries}, indent=2))
    if args.apply:
        for path in files:
            path.unlink()
        ledger = root / 'tasks' / 'ARTIFACT_CLEANUP.json'
        ledger.parent.mkdir(exist_ok=True)
        ledger.write_text(json.dumps({'kept': str(keep.relative_to(root)), 'deleted': entries}, indent=2) + '\n')


if __name__ == '__main__':
    main()
