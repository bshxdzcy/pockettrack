#!/usr/bin/env python3
"""Pair only this app over ADB. Never enumerate or read phone user data."""
import os, pathlib, shutil, subprocess, sys, time
root = pathlib.Path(__file__).resolve().parents[1]
adb = os.environ.get('ADB') or shutil.which('adb')
if not adb:
    raise SystemExit('ADB not found. Set ADB=/path/to/adb')
rows = subprocess.check_output([adb, 'devices'], text=True).splitlines()[1:]
devices = [row.split()[0] for row in rows if len(row.split()) > 1 and row.split()[1] == 'device']
serial = os.environ.get('ANDROID_SERIAL')
if not serial:
    if len(devices) != 1:
        raise SystemExit('Connect and authorize exactly one device, or set ANDROID_SERIAL.')
    serial = devices[0]
cmd = [adb, '-s', serial]
app = root / 'build/PocketTrack.app'
apk = root / 'build/PocketTrack.apk'
if not app.exists() or not apk.exists():
    raise SystemExit('Build both apps first.')
subprocess.run(['open', str(app)], check=True)
token_file = pathlib.Path.home() / 'Library/Application Support/PocketTrack/pairing-token'
for _ in range(50):
    if token_file.exists(): break
    time.sleep(.1)
token = token_file.read_text()
if len(token) != 64 or any(c not in '0123456789abcdef' for c in token):
    raise SystemExit('Invalid local pairing token')
install = subprocess.run(cmd + ['install', '--no-incremental', '-r', str(apk)], text=True, capture_output=True)
if install.returncode:
    if 'INSTALL_FAILED_USER_RESTRICTED' in install.stdout + install.stderr:
        raise SystemExit('Phone blocked USB installation. Allow USB installation on the phone, then rerun. No security settings were changed.')
    raise SystemExit('APK installation failed. Check the phone and ADB connection.')
subprocess.run(cmd + ['reverse', 'tcp:49671', 'tcp:49671'], check=True)
subprocess.run(cmd + ['shell', 'am', 'force-stop', 'dev.pockettrack'], check=True)
# Secret is passed as an argument, not printed or written into source files.
result = subprocess.run(cmd + ['shell', 'am', 'start', '-n', 'dev.pockettrack/.MainActivity', '--es', 'token', token], text=True, capture_output=True)
if result.returncode or 'Error' in result.stdout or 'Exception' in result.stderr:
    raise SystemExit('Could not start PocketTrack on phone. Check USB installation settings.')
print('PocketTrack installed and paired over USB. Tap Start on the phone.')
