"""Validate the security and delivery boundary for the real playback manifest."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'


class PlaybackReceiversTest(unittest.TestCase):
    def setUp(self):
        self.application = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot().find('application')

    def test_bluetooth_receiver_accepts_privileged_sender_outside_system_uid(self):
        receiver = next(r for r in self.application.findall('receiver')
                        if r.get(ANDROID + 'name') == '.core.HeadsetAutoPlayReceiver')
        self.assertEqual('true', receiver.get(ANDROID + 'exported'))

    def test_exported_receiver_only_listens_for_protected_system_connection_actions(self):
        receiver = next(r for r in self.application.findall('receiver')
                        if r.get(ANDROID + 'name') == '.core.HeadsetAutoPlayReceiver')
        actions = {a.get(ANDROID + 'name') for a in receiver.findall('intent-filter/action')}
        self.assertEqual({
            'android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED',
            'android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED',
            'android.intent.action.HEADSET_PLUG',
        }, actions)

    def test_playback_service_remains_private(self):
        service = next(s for s in self.application.findall('service')
                       if s.get(ANDROID + 'name') == '.core.PlaybackService')
        self.assertEqual('false', service.get(ANDROID + 'exported'))
        self.assertEqual('mediaPlayback', service.get(ANDROID + 'foregroundServiceType'))

    def test_battery_and_notification_policy_permissions_are_declared(self):
        # Tanpa ACCESS_NOTIFICATION_POLICY aplikasi tidak muncul di daftar akses Jangan Ganggu
        # sehingga opsi menahan notifikasi saat menonton tidak pernah bisa aktif.
        manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
        permissions = {p.get(ANDROID + 'name') for p in manifest.findall('uses-permission')}
        self.assertIn('android.permission.ACCESS_NOTIFICATION_POLICY', permissions)
        self.assertIn('android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS', permissions)

    def test_connection_permissions_cover_old_and_new_bluetooth_without_discovery(self):
        manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
        permissions = {p.get(ANDROID + 'name'): p for p in manifest.findall('uses-permission')}
        self.assertEqual('30', permissions['android.permission.BLUETOOTH'].get(ANDROID + 'maxSdkVersion'))
        self.assertIn('android.permission.BLUETOOTH_CONNECT', permissions)
        self.assertNotIn('android.permission.BLUETOOTH_SCAN', permissions)


if __name__ == '__main__':
    unittest.main()
