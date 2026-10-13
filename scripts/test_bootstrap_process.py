"""Host integration checks for the packaged bootstrap's Unix process contract.

These prove zip execution and Linux process-group behavior. They do not execute
Android Os.kill or establish Android SELinux behavior; device smoke tests remain
required. The small host identity guard expresses the application's contract.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import unittest
import uuid
import zipfile

ROOT = Path(__file__).resolve().parent.parent


def stat_fields(pid):
    data = Path(f"/proc/{pid}/stat").read_text()
    return data[data.rfind(")") + 2:].split()


def live(pid):
    try:
        return stat_fields(pid)[0] not in ("Z", "X")
    except FileNotFoundError:
        return False


def kill_owned_group(record, expected_token):
    """Host reference guard; never signal arbitrary, stale or mismatched groups."""
    try:
        pid = record["pid"]
        if pid <= 1 or pid == os.getpid() or record["token"] != expected_token:
            return False
        if record["uid"] != os.getuid() or record["pgid"] != pid or record["sid"] != pid:
            return False
        fields = stat_fields(pid)
        if int(fields[2]) != pid or int(fields[3]) != pid or fields[19] != record["startTimeTicks"]:
            return False
        os.kill(-pid, signal.SIGKILL)
        return True
    except (FileNotFoundError, ProcessLookupError, KeyError, TypeError, ValueError):
        return False


@unittest.skipUnless(sys.platform.startswith("linux"), "Requires Linux process groups and /proc")
class BootstrapProcessTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.directory = Path(self.temporary.name)
        self.entry = self.directory / "yt-dlp"
        packaged = ROOT / "app/src/main/assets/engine-bootstrap.py"
        self.assertEqual(packaged.read_bytes(), (ROOT / "scripts/engine_bootstrap.py").read_bytes())
        shutil.copyfile(packaged, self.entry)
        self.token = uuid.uuid4().hex
        self.control = self.directory / (self.token + ".json")
        self.process = None

    def tearDown(self):
        if self.process is not None:
            if self.process.poll() is None:
                try:
                    if os.getpgid(self.process.pid) == self.process.pid:
                        os.killpg(self.process.pid, signal.SIGKILL)
                    else:
                        self.process.kill()
                except ProcessLookupError:
                    pass
            self.process.communicate(timeout=5)
        self.temporary.cleanup()

    def start(self, *arguments):
        self.process = subprocess.Popen(
            [sys.executable, str(self.entry), "--tubeflow-process-file", str(self.control),
             "--tubeflow-process-token", self.token, *arguments],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        return self.process

    def await_file(self, path):
        deadline = time.monotonic() + 8
        while time.monotonic() < deadline:
            if path.exists():
                return json.loads(path.read_text())
            if self.process.poll() is not None:
                self.fail(f"Bootstrap exited early: {self.process.communicate()}")
            time.sleep(0.02)
        self.fail(f"Bootstrap did not create {path.name}")

    def fixture(self):
        # A child and grandchild deliberately ignore TERM. Group KILL must stop
        # both, including descendants whose parent is also being terminated.
        grandchild = "import signal,time;signal.signal(signal.SIGTERM,signal.SIG_IGN);time.sleep(30)"
        child = (
            "import subprocess,sys,time,signal,json,pathlib;"
            "signal.signal(signal.SIGTERM,signal.SIG_IGN);"
            f"p=subprocess.Popen([sys.executable,'-c',{grandchild!r}]);"
            f"pathlib.Path({str(self.directory / 'grandchild.json')!r}).write_text(json.dumps(p.pid));"
            "time.sleep(30)")
        body = (
            "import subprocess,sys,time,json,pathlib\n"
            f"p=subprocess.Popen([sys.executable,'-c',{child!r}])\n"
            f"pathlib.Path({str(self.directory / 'child.json')!r}).write_text(json.dumps(p.pid))\n"
            "time.sleep(30)\n")
        with zipfile.ZipFile(self.directory / "yt-dlp-original", "w") as archive:
            archive.writestr("__main__.py", body)
        self.start()
        record = self.await_file(self.control)
        child_pid = self.await_file(self.directory / "child.json")
        grandchild_pid = self.await_file(self.directory / "grandchild.json")
        return record, child_pid, grandchild_pid

    def test_official_zip_runs_and_normal_exit_cleans_control_file(self):
        shutil.copyfile(ROOT / "app/src/main/assets/yt-dlp", self.directory / "yt-dlp-original")
        manifest = json.loads((ROOT / "app/src/main/assets/engine.json").read_text())
        expected = manifest["version"]
        self.assertEqual(hashlib.sha256((self.directory / "yt-dlp-original").read_bytes()).hexdigest(), manifest["sha256"])
        process = self.start("--version")
        output, errors = process.communicate(timeout=15)
        self.assertEqual(process.returncode, 0, errors)
        self.assertEqual(output.strip(), expected)
        self.assertFalse(self.control.exists())

    def test_actual_entrypoint_accepts_download_extension_flag_combinations(self):
        shutil.copyfile(ROOT / "app/src/main/assets/yt-dlp", self.directory / "yt-dlp-original")
        expected = json.loads((ROOT / "app/src/main/assets/engine.json").read_text())["version"]
        for flags in (("--tubeflow-validate-resume",),
                      ("--tubeflow-validate-resume", "--tubeflow-optional-audio"),
                      ("--tubeflow-validate-resume", "--tubeflow-optional-audio", "--tubeflow-compress-cover", "--tubeflow-mix-snapshot")):
            with self.subTest(flags=flags):
                process = self.start(*flags, "--version")
                output, errors = process.communicate(timeout=20)
                self.assertEqual(0, process.returncode, errors)
                self.assertEqual(expected, output.strip())
                self.assertFalse(self.control.exists())

    def test_group_kill_stops_stubborn_child_and_grandchild(self):
        record, child, grandchild = self.fixture()
        self.assertEqual(record["pid"], self.process.pid)
        self.assertEqual(os.getpgid(child), self.process.pid)
        self.assertEqual(os.getpgid(grandchild), self.process.pid)
        self.assertTrue(kill_owned_group(record, self.token))
        self.process.communicate(timeout=5)
        self.assertEqual(self.process.returncode, -signal.SIGKILL)
        deadline = time.monotonic() + 3
        while any(live(pid) for pid in (child, grandchild)) and time.monotonic() < deadline:
            time.sleep(0.02)
        self.assertFalse(live(child))
        self.assertFalse(live(grandchild))

    def test_wrong_token_stale_pid_start_time_and_uid_fail_closed(self):
        record, child, grandchild = self.fixture()
        self.assertFalse(kill_owned_group(record, uuid.uuid4().hex))
        self.assertFalse(kill_owned_group(dict(record, startTimeTicks="0"), self.token))
        self.assertFalse(kill_owned_group(dict(record, uid=os.getuid() + 1), self.token))
        self.assertFalse(kill_owned_group(dict(record, pid=os.getpid()), self.token))
        self.assertTrue(live(self.process.pid))
        self.assertTrue(live(child))
        self.assertTrue(live(grandchild))
        self.assertTrue(kill_owned_group(record, self.token))


if __name__ == "__main__":
    unittest.main()
