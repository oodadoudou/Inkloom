"""Exercise delivery verification/retirement in temporary directories, never a Drive account."""
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('sync_debug_delivery', Path(__file__).resolve().parents[1] / 'sync_debug_delivery.py')
delivery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(delivery)


class VerifiedDeliveryTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / 'dist').mkdir()
        (self.root / 'tasks').mkdir()
        self.destination = self.root / 'drive'
        self.destination.mkdir()
        self.apk = self.root / 'dist/Inkloom-0.223.0-abcdef12-debug.apk'
        self.apk.write_bytes(b'test delivery payload')
        self.apk.with_suffix('.json').write_text(json.dumps({'variant': 'debug', 'version': '0.223.0',
            'sha256': hashlib.sha256(self.apk.read_bytes()).hexdigest()}))
        self.apk.with_suffix('.sha256').write_text('test checksum evidence')
        self.notes = self.root / 'dist/Inkloom-0.223.0-test-notes.md'
        self.notes.write_text('test notes')
        self.old = self.destination / 'Inkloom-0.222.0-1234abcd-debug.apk'
        self.old.write_bytes(b'previous verified delivery')
        self.unrelated = self.destination / 'personal-file.txt'
        self.unrelated.write_text('keep')

    def run_sync(self):
        with patch.object(delivery, '__file__', str(self.root / 'scripts/sync_debug_delivery.py')), \
             patch.object(sys, 'argv', ['sync', '--apk', str(self.apk), '--destination', str(self.destination)]), \
             patch('builtins.print'):
            delivery.main()

    def test_complete_delivery_verified_before_old_package_retired(self):
        self.run_sync()
        for source in (self.apk, self.apk.with_suffix('.json'), self.apk.with_suffix('.sha256'), self.notes):
            self.assertEqual(source.read_bytes(), (self.destination / source.name).read_bytes())
        self.assertFalse(self.old.exists())
        self.assertTrue(self.unrelated.exists())
        self.assertTrue(json.loads((self.root / 'tasks/APK_DRIVE_SYNC.json').read_text())['fourFilesHashVerified'])

    def test_corrupt_evidence_copy_keeps_previous_package(self):
        real_copy = delivery.atomic_copy
        def corrupt_notes(source, target):
            real_copy(source, target)
            if source.resolve() == self.notes.resolve():
                target.write_text('incomplete upload')
        with patch.object(delivery, 'atomic_copy', corrupt_notes):
            with self.assertRaisesRegex(RuntimeError, 'old deliveries retained'):
                self.run_sync()
        self.assertEqual(b'previous verified delivery', self.old.read_bytes())
        self.assertTrue(self.unrelated.exists())
        self.assertFalse((self.root / 'tasks/APK_DRIVE_SYNC.json').exists())


if __name__ == '__main__':
    unittest.main()
