# USB protocol v1

Transport: Android connects to 127.0.0.1:49671 through `adb reverse tcp:49671 tcp:49671`. macOS binds only 127.0.0.1. One client, UTF-8 newline-delimited frames, 160-byte maximum line, 8192-byte maximum receive buffer. Authentication must complete within the same two-second liveness deadline.

First line: `AUTH <64 lowercase hex characters>`; server replies `OK` on success, otherwise closes. Secret is randomly generated on the Mac and provisioned to only the app by the local connection script. This is local USB authentication, not TLS.

| Command | Meaning |
| --- | --- |
| `PING` | Heartbeat; server replies `PONG` |
| `MOVE x y` | Relative pointer delta in Mac points |
| `SCROLL x y` | Horizontal and vertical scroll delta |
| `CLICK L` / `CLICK R` | Click left / right |
| `DRAG 1` / `DRAG 0` | Hold / release left mouse button |
| `OPTION 1` / `OPTION 0` | Hold / release right Option modifier (keycode 61) |
| `RESET` | Release this app's held input |

Deltas must be finite and within ±500. Unknown commands, invalid argument counts or values, oversized frames, EOF, and two seconds without a complete valid frame terminate the session and release input. Android sends a heartbeat approximately every 500 ms; connection loss returns to setup. Android does not reconnect automatically. No command can run shell commands, type arbitrary text, read files or query the user's input history.

Test mode uses `OK DRY` instead of `OK` and may bind the isolated `POCKETTRACK_TEST_PORT`. Production always binds 127.0.0.1:49671. Both ends disable Nagle. Heartbeat reading runs separately from Android input writing. Adjacent motion frames may be summed, but clicks, key changes, and motion-type changes remain ordered.
