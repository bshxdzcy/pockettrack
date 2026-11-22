#!/usr/bin/env python3
"""Run against the receiver launched with --dry-run; never injects OS input."""
import socket, pathlib, time
secret = (pathlib.Path.home() / 'Library/Application Support/PocketTrack/pairing-token').read_text()
def connect():
    return socket.create_connection(('127.0.0.1',49671),timeout=4)
def read_line(s):
    data=b''
    while not data.endswith(b'\n'):
        part=s.recv(1)
        if not part:break
        data+=part
    return data
with connect() as s:
    s.sendall(b'AUTH wrong\n')
    assert s.recv(1)==b''
time.sleep(.15)
with connect() as s:
    # A TCP message may arrive split across packets.
    s.sendall(('AUTH '+secret[:10]).encode());time.sleep(.03)
    s.sendall((secret[10:]+'\nPING\n').encode())
    assert read_line(s)==b'OK\n'
    assert read_line(s)==b'PONG\n'
    s.sendall(b'MOVE nan 2\n')
    assert s.recv(1)==b''
time.sleep(.15)
with connect() as s:
    s.sendall(('AUTH '+secret+'\n').encode());assert read_line(s)==b'OK\n'
    s.sendall(b'OPTION 1\nDRAG 1\nRESET\nPING\n');assert read_line(s)==b'PONG\n'
    time.sleep(3)
    assert s.recv(1)==b''
time.sleep(.15)
with connect() as s:
    s.sendall(b'x'*9000)
    try:assert s.recv(1)==b''
    except ConnectionResetError:pass
print('PASS: authentication, fragmented packets, malformed input, heartbeat timeout, reconnect, oversized input')
