#!/usr/bin/env python3
"""Run receiver tests on an isolated port, without UI or OS input injection."""
import os, pathlib, socket, subprocess, sys, time
root=pathlib.Path(__file__).resolve().parents[1]
exe=root/'build/PocketTrack.app/Contents/MacOS/PocketTrack'
subprocess.run([str(exe),'--self-test'],check=True)
env=dict(os.environ,POCKETTRACK_TEST_PORT='49672')
p=subprocess.Popen([str(exe),'--dry-run'],env=env)
try:
    for _ in range(50):
        if p.poll() is not None:raise RuntimeError('Test receiver exited')
        try:
            with socket.create_connection(('127.0.0.1',49672),timeout=.1):pass
            break
        except OSError:time.sleep(.1)
    else:raise RuntimeError('Test receiver failed to start')
    time.sleep(.15)
    subprocess.run([sys.executable,str(root/'tests/protocol_test.py')],env=env,check=True)
finally:
    p.terminate()
    try:p.wait(timeout=3)
    except subprocess.TimeoutExpired:p.kill();p.wait()
