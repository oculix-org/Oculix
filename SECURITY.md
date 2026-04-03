# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 3.0.x   | ✅ Active development, security patches applied |
| 2.0.5   | ⚠️ Legacy SikuliX — no patches, migrate to 3.x |
| < 2.0   | ❌ End of life |

## Reporting a Vulnerability

If you discover a security vulnerability in OculiX, please report it by **opening a GitHub Issue** with the label `security`:

**[Open a security issue](https://github.com/oculix-org/Oculix/issues/new?labels=security&template=security_report.md)**

### What to include

- OculiX version affected
- Operating system and Java version
- Description of the vulnerability
- Steps to reproduce (if possible)
- Potential impact assessment

### What to expect

- **Acknowledgment** within 48 hours
- **Triage and severity assessment** within 1 week
- **Patch timeline** communicated once the issue is confirmed:
  - Critical (remote code execution, data exfiltration): patch within 7 days
  - High (privilege escalation, denial of service): patch within 30 days
  - Medium/Low: addressed in the next scheduled release

### Scope

OculiX interacts with screens, keyboards, mice, VNC connections, SSH tunnels, and ADB devices. The following areas are particularly sensitive:

- **VNC connections** — authentication, encryption, credential handling
- **SSH tunnels** — key management, tunnel security
- **ADB bridge** — device access control
- **OCR engines** — input sanitization (PaddleOCR, EasyOCR, Tesseract)
- **Script execution** — Jython/JRuby sandboxing in `-r` mode
- **Native library loading** — OpenCV, Tesseract DLL/dylib extraction and execution

### Out of scope

- Vulnerabilities in upstream dependencies (OpenCV, Tesseract, Jython) — please report those to their respective projects
- Issues requiring physical access to the machine running OculiX
- Social engineering attacks

### Responsible Disclosure

We believe in transparency. Security issues are tracked as GitHub Issues so the community benefits from the fixes and understands the risks. If you believe the vulnerability is too sensitive for a public issue, contact the maintainer directly via [GitHub profile](https://github.com/julienmerconsulting).

## Security Best Practices for OculiX Users

- Run OculiX with the **minimum required privileges**
- Use **encrypted VNC connections** when testing remote systems
- Do not store credentials in plain text in `.sikuli` scripts
- Keep your Java runtime and OculiX version up to date
- Review scripts from untrusted sources before execution
