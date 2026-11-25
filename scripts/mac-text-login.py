#!/usr/bin/env python3
"""Enable/disable login launch of this project's text receiver; never changes system-wide jobs."""
import argparse,os,pathlib,plistlib,subprocess
parser=argparse.ArgumentParser();parser.add_argument('--disable',action='store_true');args=parser.parse_args()
root=pathlib.Path(__file__).resolve().parents[1]
app=(root/'build/PocketTrackText.app').resolve()
label='dev.pockettrack.text.login'
path=pathlib.Path.home()/'Library/LaunchAgents'/f'{label}.plist'
domain=f'gui/{os.getuid()}'
if args.disable:
    subprocess.run(['launchctl','bootout',f'{domain}/{label}'],capture_output=True)
    if path.exists():path.unlink()
    print('PocketTrack login launch disabled. The running app can be quit from its PT menu.')
else:
    if not (app/'Contents/MacOS/PocketTrackText').is_file():raise SystemExit('Build the Mac text app first.')
    if path.exists():
        old=plistlib.loads(path.read_bytes())
        if old.get('Label')!=label:raise SystemExit('Unexpected existing launch agent; left unchanged.')
    path.parent.mkdir(parents=True,exist_ok=True)
    payload={'Label':label,'ProgramArguments':['/usr/bin/open','-g','-a',str(app),'--args','--background'],'RunAtLoad':True,'AssociatedBundleIdentifiers':['dev.pockettrack.text'],'ProcessType':'Interactive'}
    temporary=path.with_suffix('.tmp');temporary.write_bytes(plistlib.dumps(payload));os.chmod(temporary,0o600);temporary.replace(path)
    subprocess.run(['launchctl','bootout',f'{domain}/{label}'],capture_output=True)
    subprocess.run(['launchctl','bootstrap',domain,str(path)],check=True)
    print('PocketTrack will open quietly at login and receive automatically. PT menu can pause or quit it.')
