/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.Set;

/**
 * Configures SSH credentials for Git operations using a private key provided via environment variable.
 *
 * <p>On application startup, this component:
 * <ul>
 *   <li>Creates the ~/.ssh directory with proper permissions</li>
 *   <li>Writes the private key to a file with strict permissions</li>
 *   <li>Creates an SSH config that points to the private key</li>
 *   <li>Attempts to add the key to the SSH agent if available</li>
 * </ul>
 */
@Component
public class GitSshKeySetup {
    private static final Logger logger = LoggerFactory.getLogger(GitSshKeySetup.class);
    private static final String GIT_SSH_PRIVATE_KEY = "GIT_SSH_PRIVATE_KEY";
    private static final String GIT_ADO_SSH_PRIVATE_KEY = "GIT_ADO_SSH_PRIVATE_KEY";
    private final String sshDir;
    private final String privateKeyPath;
    private final String privateAdoKeyPath;

    /**
     * Creates a new GitSshKeySetup using the current user's home directory.
     */
    public GitSshKeySetup() {
        String userHome = System.getProperty("user.home");
        this.sshDir = userHome + "/.ssh";
        this.privateKeyPath = sshDir + "/aws_ecdsa";
        this.privateAdoKeyPath = sshDir+ "/id_ado_rsa";
    }

    /**
     * Initializes the SSH configuration using the GIT_SSH_PRIVATE_KEY environment variable.
     * If the variable is not present, logs an error and returns.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void setupSshKeys() {
        try {
            String privateKeyEnv = System.getenv(GIT_SSH_PRIVATE_KEY);
            String adoPrivateKeyEnv = System.getenv(GIT_ADO_SSH_PRIVATE_KEY);

            boolean hasGitKey = privateKeyEnv != null && !privateKeyEnv.isEmpty();
            boolean hasAdoKey = adoPrivateKeyEnv != null && !adoPrivateKeyEnv.isEmpty();

            if (!hasGitKey) {
                logger.info("No SSH private key found in environment variable {}, skipping Git SSH key setup", GIT_SSH_PRIVATE_KEY);
            }
            if (!hasAdoKey) {
                logger.info("No SSH private key found in environment variable {}, skipping ADO SSH key setup", GIT_ADO_SSH_PRIVATE_KEY);
            }

            // If no keys are provided, skip SSH setup entirely
            if (!hasGitKey && !hasAdoKey) {
                logger.info("No SSH keys configured, skipping SSH setup");
                return;
            }

            // Create .ssh directory if needed
            createSshDirectory();

            // Decode and write private keys only if they exist
            if (hasGitKey) {
                String privateKey = decodeKey(privateKeyEnv);
                writePrivateKey(privateKey, privateKeyPath);
            }

            if (hasAdoKey) {
                String adoPrivateKey = decodeKey(adoPrivateKeyEnv);
                writePrivateKey(adoPrivateKey, privateAdoKeyPath);
            }

            // Create SSH config
            createSshConfig(hasGitKey, hasAdoKey);
            
            // Try to add key to SSH agent if available
            addKeyToAgent(hasGitKey, hasAdoKey);

            logger.info("SSH keys successfully configured for Git operations");

        } catch (Exception e) {
            throw new RuntimeException("Failed to setup SSH keys", e);
        }
    }

    /**
     * Encodes the current SSH private key file to Base64.
     *
     * @return Base64-encoded private key contents
     * @throws RuntimeException if the private key file is missing or cannot be read
     */
    protected String encodeKey() {
        try {
            Path keyPath = Paths.get(privateKeyPath);
            if (!Files.exists(keyPath)) {
                logger.error("SSH private key file not found at {}", privateKeyPath);
                throw new RuntimeException("SSH private key file not found at " + privateKeyPath);
            }
            String contents = Files.readString(keyPath, StandardCharsets.UTF_8);
            return Base64.getEncoder().encodeToString(contents.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read SSH private key from " + privateKeyPath, e);
        }
    }

    /**
     * Decodes the given key. If it is Base64-encoded, decodes it to plain text; otherwise returns as-is.
     *
     * @param encodedKey Base64-encoded key string or plain text
     * @return decoded key in plain text
     */
    protected String decodeKey(String encodedKey) {
        try {
            // Try to decode as base64 first
            return new String(Base64.getDecoder().decode(encodedKey));
        } catch (IllegalArgumentException e) {
            // If not base64, assume it's already plain text
            return encodedKey;
        }
    }

    private void createSshDirectory() throws IOException {
        Path sshPath = Paths.get(sshDir);

        if (!Files.exists(sshPath)) {
            Files.createDirectories(sshPath);

            // Set directory permissions to 700 (rwx------)
            if (isUnix()) {
                Set<PosixFilePermission> perms = Set.of(PosixFilePermission.OWNER_READ,
                                                        PosixFilePermission.OWNER_WRITE,
                                                        PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(sshPath, perms);
                logger.info("Unix style OS set up ssh directory with {}", perms);
            }
        }
    }

    private void writePrivateKey(String privateKey, String path) throws IOException {
        Path keyPath = Paths.get(path);

        // Write the private key
        Files.writeString(keyPath, privateKey);

        // Set strict permissions: 600 (rw-------)
        if (isUnix()) {
            Set<PosixFilePermission> perms = Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(keyPath, perms);
            logger.info("Setup unix style OS set up ssh directory with {} and keypath {}", perms, keyPath);
        }

        logger.debug("Private key written to: {}", path);
    }

    private void createSshConfig(boolean hasGitKey, boolean hasAdoKey) throws IOException {
        String sshConfigPath = sshDir + "/config";
        Path configPath = Paths.get(sshConfigPath);

        StringBuilder sshConfig = new StringBuilder();
        
        if (hasGitKey) {
            sshConfig.append("""
                Host github.com
                    HostName github.com
                    User git
                    IdentityFile %s
                    IdentitiesOnly yes
                    StrictHostKeyChecking accept-new
                
                """.formatted(privateKeyPath));
        }
        
        if (hasAdoKey) {
            sshConfig.append("""
                Host ssh.dev.azure.com
                    HostName ssh.dev.azure.com
                    User git
                    IdentityFile %s
                    IdentitiesOnly yes
                    StrictHostKeyChecking accept-new
                
                """.formatted(privateAdoKeyPath));
        }

        Files.writeString(configPath, sshConfig.toString());

        // Set permissions: 600
        if (isUnix()) {
            Set<PosixFilePermission> perms = Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(configPath, perms);
        }

        logger.info("SSH config written to: {} ", sshConfigPath);
    }

    private void addKeyToAgent(boolean hasGitKey, boolean hasAdoKey) {
        try {
            // Check if SSH agent is running
            String sshAuthSock = System.getenv("SSH_AUTH_SOCK");
            if (sshAuthSock != null && !sshAuthSock.isEmpty()) {
                logger.info("SSH_AUTH_SOCK detected: {}, attempting to add key to agent", sshAuthSock);
                if (hasGitKey) {
                    addKeyPathToAgent(privateKeyPath);
                }
                if (hasAdoKey) {
                    addKeyPathToAgent(privateAdoKeyPath);
                }
            } else {
                logger.info("SSH agent not detected (SSH_AUTH_SOCK not set), skipping agent configuration");
            }
        } catch (Exception e) {
            logger.warn("Error adding key to SSH agent: {}", e.getMessage());
        }
    }

    private void addKeyPathToAgent(String path) {
        try {
            ProcessBuilder pb = new ProcessBuilder("ssh-add", path);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                logger.info("Successfully added SSH key to agent");
            } else {
                logger.warn("Failed to add SSH key to agent, exit code: {}", exitCode);
            }
        } catch (Exception e) {
            logger.warn("Error adding keypath {} to SSH agent", path, e);
        }
    }

    
    private boolean isUnix() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("nix") || os.contains("nux") || os.contains("mac");
    }
}