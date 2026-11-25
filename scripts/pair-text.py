#!/usr/bin/env python3
"""Provision only PocketTrack's shared key over a physically connected, authorized ADB link."""
import hashlib,os,pathlib,secrets,shutil,subprocess
root=pathlib.Path(__file__).resolve().parents[1]
adb=os.environ.get('ADB') or shutil.which('adb')
if not adb:raise SystemExit('Set ADB=/path/to/adb')
rows=subprocess.check_output([adb,'devices','-l'],text=True).splitlines()[1:]
serial=os.environ.get('ANDROID_SERIAL')
physical=[r.split()[0] for r in rows if len(r.split())>1 and r.split()[1]=='device' and any(x.startswith('usb:') for x in r.split())]
if serial is None:
    if len(physical)!=1:raise SystemExit('Connect exactly one authorized phone by USB.')
    serial=physical[0]
if serial not in physical:raise SystemExit('Pairing requires physical USB.')
path=pathlib.Path.home()/'Library/Application Support/PocketTrack/pairing-token'
path.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
if not path.exists():
    fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as f:f.write(secrets.token_hex(32))
token=path.read_text()
if len(token)!=64 or any(c not in '0123456789abcdef' for c in token):raise SystemExit('Invalid existing pairing key')
cmd=[adb,'-s',serial]
subprocess.run(cmd+['shell','am','force-stop','dev.pockettrack'],check=True)
result=subprocess.run(cmd+['shell','am','start','-n','dev.pockettrack/.MainActivity','--es','token',token],capture_output=True,text=True)
if result.returncode or 'Error' in result.stdout or 'Exception' in result.stderr:raise SystemExit('Could not open PocketTrack. Install the APK first.')
print('Pairing fingerprint: '+hashlib.sha256(token.encode()).hexdigest()[:12])
print('On the phone, confirm only if this fingerprint matches. If already paired, no confirmation appears.')
print('You can unplug USB after pairing; text uses Bluetooth.')
