# SSH Tunnel

![New](https://img.shields.io/badge/type-new%20feature-brightgreen?style=for-the-badge)
![JSch](https://img.shields.io/badge/JSch-embedded-blue?style=for-the-badge)

> Embedded SSH tunneling for remote ADB access. Zero external dependencies — JSch is vendored in the JAR.

---

## Why

Android devices on remote networks require ADB port forwarding through SSH. Instead of requiring users to set up SSH tunnels externally, OculiX embeds the full JSch library and provides a simple API.

## Architecture

```
Local Machine                    Remote Server              Android Device
┌─────────┐    SSH Tunnel      ┌─────────────┐   ADB      ┌─────────────┐
│ OculiX   │ ═══════════════>  │ SSH Server  │ ────────>  │ Device      │
│ :15037   │  port forward     │ :22         │  :5555     │ :5555       │
└─────────┘                    └─────────────┘            └─────────────┘
```

## Key Class

**`com.sikulix.util.SSHTunnel`** (251 lines)

| Method | Description |
|--------|-------------|
| `SSHTunnel(host, port, user, keyPath)` | Constructor with SSH credentials |
| `connect()` | Establish SSH connection |
| `disconnect()` | Close tunnel |
| `forwardPort(localPort, remoteHost, remotePort)` | Set up port forwarding |
| `isConnected()` | Check tunnel status |

## Features

- Key-based and password authentication
- Auto-reconnect logic
- Compatible with legacy servers (tested on SUSE 12)
- Configurable cipher suites (`aes128-ctr`, `hmac-sha2-256`)

## Vendored JSch

The full `com.jcraft.jsch.*` package (100+ files) is embedded:
- SSH2 protocol (key exchange, auth, channels)
- Key types: RSA, DSA, ECDSA
- Port forwarding, SFTP, SCP
- JCE/BouncyCastle integration
