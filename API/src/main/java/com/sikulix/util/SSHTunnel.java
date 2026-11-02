/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 * SSH Tunnel for VNC connections via JSch
 */
package com.sikulix.util;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.sikuli.support.RemoteMode;

import java.io.Closeable;
import java.io.File;

/**
 * Integrated SSH tunnel for VNC connections through a bastion/remote server.
 * Pure Java (JSch): no WSL, no sshpass, no X server, the same code on every OS.
 *
 * <p>The host key policy follows {@link RemoteMode}: in {@code confidential}
 * (the default) the server must already be in {@code ~/.ssh/known_hosts},
 * an unknown key is a hard failure; in {@code yolo} unknown keys are accepted.
 * Authentication is by password, or by private key when {@link #identity(String)}
 * is set. The cipher, MAC and key-exchange lists deliberately include legacy
 * algorithms: some of the targets this tunnel exists for are old SUSE boxes.
 *
 * <pre>
 *   try (SSHTunnel tunnel = SSHTunnel.open("192.168.1.100", "user", "password")) {
 *       VNCScreen vnc = VNCScreen.start("127.0.0.1", tunnel.getLocalPort(), null, 10, 1000);
 *       // ... use VNC
 *       vnc.stop();
 *   }
 * </pre>
 */
public class SSHTunnel implements Closeable {

    private static final int DEFAULT_SSH_PORT = 22;
    private static final int DEFAULT_VNC_PORT = 5900;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10000;
    private static final int DEFAULT_SERVER_ALIVE_INTERVAL = 30;
    private static final int DEFAULT_SERVER_ALIVE_COUNT_MAX = 3;

    private static volatile String identityPath;

    private Session session;
    private int localPort;

    private SSHTunnel() {
    }

    /**
     * Use a private key (OpenSSH or PEM file) for every tunnel opened afterwards.
     * The password given to {@code open} then serves as the key passphrase, and may be null.
     *
     * @param path absolute path to the private key, or {@code null} to go back to passwords
     */
    public static void identity(String path) {
        identityPath = (path == null || path.trim().isEmpty()) ? null : path.trim();
    }

    /**
     * @return the private key path set by {@link #identity(String)}, or {@code null}
     */
    public static String getIdentity() {
        return identityPath;
    }

    /**
     * Create and start an SSH tunnel with local port forwarding.
     *
     * @param sshHost      SSH server address (e.g., "192.168.1.100")
     * @param sshPort      SSH port (typically 22)
     * @param sshUser      SSH username (e.g., "root")
     * @param sshPassword  SSH password, or the key passphrase when an identity is set (may be null)
     * @param remoteHost   remote host for forwarding, as seen from the SSH server (typically "localhost")
     * @param remotePort   remote port to forward (e.g., 5900 for VNC)
     * @param localPort    local port to bind (e.g., 5900, or 0 for auto)
     * @return the SSHTunnel instance with the effective local port
     * @throws JSchException if the SSH connection fails, or if the host key is unknown in confidential mode
     */
    public static SSHTunnel open(String sshHost, int sshPort, String sshUser,
                                  String sshPassword, String remoteHost,
                                  int remotePort, int localPort) throws JSchException {
        SSHTunnel tunnel = new SSHTunnel();
        RemoteMode mode = RemoteMode.fromEnvOrDefault();

        SikuliLogger.info("[SSH] Opening tunnel to " + sshHost + ":" + sshPort
            + " -> " + remoteHost + ":" + remotePort + " (" + mode + ")");

        JSch jsch = new JSch();
        File knownHosts = new File(System.getProperty("user.home"), ".ssh/known_hosts");
        if (knownHosts.isFile()) {
            jsch.setKnownHosts(knownHosts.getAbsolutePath());
        } else if (!mode.isYolo()) {
            throw new JSchException("[SSH] " + knownHosts + " not found: add the host key first"
                + " (ssh-keyscan -H " + sshHost + " >> " + knownHosts + "), or set OCULIX_REMOTE_MODE=yolo");
        }

        String identity = identityPath;
        if (identity != null) {
            if (sshPassword == null || sshPassword.isEmpty()) {
                jsch.addIdentity(identity);
            } else {
                jsch.addIdentity(identity, sshPassword);
            }
        }

        Session session = jsch.getSession(sshUser, sshHost, sshPort);
        if (identity == null) {
            session.setPassword(sshPassword);
        }

        session.setConfig("StrictHostKeyChecking", mode.isYolo() ? "no" : "yes");

        // Configure ciphers compatible with old servers (SUSE 12)
        session.setConfig("cipher.s2c", "aes128-ctr,aes128-cbc,3des-cbc,aes192-ctr,aes256-ctr");
        session.setConfig("cipher.c2s", "aes128-ctr,aes128-cbc,3des-cbc,aes192-ctr,aes256-ctr");
        session.setConfig("mac.s2c", "hmac-sha2-256-etm@openssh.com,hmac-sha2-512-etm@openssh.com,hmac-sha2-256,hmac-sha1");
        session.setConfig("mac.c2s", "hmac-sha2-256-etm@openssh.com,hmac-sha2-512-etm@openssh.com,hmac-sha2-256,hmac-sha1");

        // Key exchange algorithms compatible with older servers
        session.setConfig("kex", "ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,"
            + "diffie-hellman-group-exchange-sha256,diffie-hellman-group14-sha1,"
            + "diffie-hellman-group-exchange-sha1,diffie-hellman-group1-sha1");

        // Host key types
        session.setConfig("server_host_key", "ssh-rsa,ssh-dss,ecdsa-sha2-nistp256,ssh-ed25519");

        // ServerAliveInterval instead of TCPKeepAlive (more reliable through firewalls)
        session.setServerAliveInterval(DEFAULT_SERVER_ALIVE_INTERVAL * 1000);
        session.setServerAliveCountMax(DEFAULT_SERVER_ALIVE_COUNT_MAX);

        // Disable TCP keepalive (we use ServerAlive instead)
        session.setConfig("TCPKeepAlive", "no");

        try {
            session.connect(DEFAULT_CONNECT_TIMEOUT_MS);
        } catch (JSchException e) {
            String reason = String.valueOf(e.getMessage());
            if (reason.contains("HostKey has been changed")) {
                throw new JSchException("[SSH] Host key of " + sshHost + " differs from the one in " + knownHosts
                    + ": either the server was reinstalled, or someone is impersonating it. If you trust it,"
                    + " remove the old key (ssh-keygen -R " + sshHost + ") and add the new one"
                    + " (ssh-keyscan -H " + sshHost + " >> " + knownHosts + ")", e);
            }
            if (reason.contains("UnknownHostKey") || reason.contains("reject HostKey")) {
                throw new JSchException("[SSH] Host key of " + sshHost + " is not in " + knownHosts
                    + ": add it first (ssh-keyscan -H " + sshHost + " >> " + knownHosts
                    + "), or set OCULIX_REMOTE_MODE=yolo", e);
            }
            throw e;
        }

        try {
            int assignedPort = session.setPortForwardingL(localPort, remoteHost, remotePort);
            tunnel.session = session;
            tunnel.localPort = assignedPort;
        } catch (JSchException e) {
            session.disconnect();
            throw e;
        }

        SikuliLogger.info("[SSH] Tunnel established: localhost:" + tunnel.localPort
            + " -> " + remoteHost + ":" + remotePort);

        return tunnel;
    }

    /**
     * Simplified overload: tunnel to localhost:5900 on local port 5900.
     *
     * @param sshHost     SSH server address
     * @param sshUser     SSH username
     * @param sshPassword SSH password
     * @return the SSHTunnel instance
     * @throws JSchException if SSH connection fails
     */
    public static SSHTunnel open(String sshHost, String sshUser, String sshPassword)
            throws JSchException {
        return open(sshHost, DEFAULT_SSH_PORT, sshUser, sshPassword,
                    "localhost", DEFAULT_VNC_PORT, DEFAULT_VNC_PORT);
    }

    /**
     * Overload with custom SSH port.
     *
     * @param sshHost     SSH server address
     * @param sshPort     SSH port
     * @param sshUser     SSH username
     * @param sshPassword SSH password
     * @return the SSHTunnel instance
     * @throws JSchException if SSH connection fails
     */
    public static SSHTunnel open(String sshHost, int sshPort, String sshUser,
                                  String sshPassword) throws JSchException {
        return open(sshHost, sshPort, sshUser, sshPassword,
                    "localhost", DEFAULT_VNC_PORT, DEFAULT_VNC_PORT);
    }

    /**
     * Overload with auto-assigned local port.
     *
     * @param sshHost     SSH server address
     * @param sshPort     SSH port
     * @param sshUser     SSH username
     * @param sshPassword SSH password
     * @param remoteHost  remote host for forwarding
     * @param remotePort  remote port to forward
     * @return the SSHTunnel instance
     * @throws JSchException if SSH connection fails
     */
    public static SSHTunnel openAutoPort(String sshHost, int sshPort, String sshUser,
                                          String sshPassword, String remoteHost,
                                          int remotePort) throws JSchException {
        return open(sshHost, sshPort, sshUser, sshPassword, remoteHost, remotePort, 0);
    }

    /**
     * @return the local port the tunnel is listening on
     */
    public int getLocalPort() {
        return localPort;
    }

    /**
     * @return true while the SSH session behind the tunnel is connected
     */
    public boolean isConnected() {
        return session != null && session.isConnected();
    }

    /**
     * Close the tunnel and SSH session. Safe to call twice.
     */
    @Override
    public void close() {
        if (session != null && session.isConnected()) {
            try {
                session.delPortForwardingL(localPort);
            } catch (JSchException e) {
                // the session is going down anyway
            }
            session.disconnect();
            SikuliLogger.info("[SSH] Tunnel closed (port " + localPort + ")");
        }
    }
}
