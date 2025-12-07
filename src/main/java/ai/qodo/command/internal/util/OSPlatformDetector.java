/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.util;

import java.util.Locale;

/**
 * Utility class to detect the operating system platform from JVM environment properties.
 */
public class OSPlatformDetector {
    
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    private static final String OS_ARCH = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
    
    public enum OSType {
        WINDOWS("windows"),
        MACOS("darwin"),
        LINUX("linux"),
        UNIX("unix"),
        SOLARIS("solaris"),
        UNKNOWN("unknown");
        
        private final String platformName;
        
        OSType(String platformName) {
            this.platformName = platformName;
        }
        
        public String getPlatformName() {
            return platformName;
        }
    }
    
    /**
     * JDK information containing version and vendor details.
     */
    public static class JDKInfo {
        private final String version;
        private final String vendor;
        private final String versionIdentifier;
        
        public JDKInfo(String version, String vendor, String versionIdentifier) {
            this.version = version;
            this.vendor = vendor;
            this.versionIdentifier = versionIdentifier;
        }
        
        public String getVersion() { return version; }
        public String getVendor() { return vendor; }
        public String getVersionIdentifier() { return versionIdentifier; }
        
        @Override
        public String toString() {
            return String.format("JDKInfo{version='%s', vendor='%s', versionIdentifier='%s'}", 
                version, vendor, versionIdentifier);
        }
    }
    
    /**
     * Detects the operating system type from JVM system properties.
     * 
     * @return OSType enum representing the detected operating system
     */
    public static OSType detectOSType() {
        if (OS_NAME.contains("win")) {
            return OSType.WINDOWS;
        } else if (OS_NAME.contains("mac") || OS_NAME.contains("darwin")) {
            return OSType.MACOS;
        } else if (OS_NAME.contains("nix") || OS_NAME.contains("nux") || OS_NAME.contains("aix")) {
            return OSType.LINUX;
        } else if (OS_NAME.contains("sunos") || OS_NAME.contains("solaris")) {
            return OSType.SOLARIS;
        } else if (OS_NAME.contains("unix")) {
            return OSType.UNIX;
        } else {
            return OSType.UNKNOWN;
        }
    }
    
    /**
     * Detects JDK information from JVM system properties.
     * 
     * @return JDKInfo object containing version, vendor, and combined identifier
     */
    public static JDKInfo detectJDKInfo() {
        String javaVersion = System.getProperty("java.version");
        String javaVendor = System.getProperty("java.vendor");
        
        // Create a combined version identifier string
        String versionIdentifier = String.format("%s (%s)", javaVersion, javaVendor);
        
        return new JDKInfo(javaVersion, javaVendor, versionIdentifier);
    }
    
    /**
     * Gets the platform name suitable for API responses (e.g., "darwin", "windows", "linux").
     * 
     * @return String representing the platform name
     */
    public static String getPlatformName() {
        return detectOSType().getPlatformName();
    }
    
    /**
     * Gets the JDK version and identifier combined as a string.
     * 
     * @return String representing the JDK version and vendor identifier
     */
    public static String getJDKVersionIdentifier() {
        return detectJDKInfo().getVersionIdentifier();
    }
    
    /**
     * Gets just the JDK version.
     * 
     * @return String representing the JDK version
     */
    public static String getJDKVersion() {
        return detectJDKInfo().getVersion();
    }
    
    /**
     * Gets just the JDK vendor.
     * 
     * @return String representing the JDK vendor
     */
    public static String getJDKVendor() {
        return detectJDKInfo().getVendor();
    }
    
    /**
     * Gets detailed OS information including name, version, and JDK details.
     * 
     * @return OSInfo object containing comprehensive OS and JDK details
     */
    public static OSInfo getOSInfo() {
        return new OSInfo(
            System.getProperty("os.name"),
            System.getProperty("os.version"),
            System.getProperty("os.arch"),
            detectOSType(),
            detectJDKInfo()
        );
    }
    
    /**
     * Checks if the current OS is Windows.
     * 
     * @return true if running on Windows, false otherwise
     */
    public static boolean isWindows() {
        return detectOSType() == OSType.WINDOWS;
    }
    
    /**
     * Checks if the current OS is macOS.
     * 
     * @return true if running on macOS, false otherwise
     */
    public static boolean isMacOS() {
        return detectOSType() == OSType.MACOS;
    }
    
    /**
     * Checks if the current OS is Linux.
     * 
     * @return true if running on Linux, false otherwise
     */
    public static boolean isLinux() {
        return detectOSType() == OSType.LINUX;
    }
    
    /**
     * Checks if the current OS is Unix-like (Linux, macOS, Unix, Solaris).
     * 
     * @return true if running on a Unix-like system, false otherwise
     */
    public static boolean isUnixLike() {
        OSType osType = detectOSType();
        return osType == OSType.LINUX || osType == OSType.MACOS || 
               osType == OSType.UNIX || osType == OSType.SOLARIS;
    }
    
    /**
     * Data class containing comprehensive OS and JDK information.
     */
    public static class OSInfo {
        private final String osName;
        private final String osVersion;
        private final String osArch;
        private final OSType osType;
        private final JDKInfo jdkInfo;
        
        public OSInfo(String osName, String osVersion, String osArch, OSType osType, JDKInfo jdkInfo) {
            this.osName = osName;
            this.osVersion = osVersion;
            this.osArch = osArch;
            this.osType = osType;
            this.jdkInfo = jdkInfo;
        }
        
        public String getOsName() { return osName; }
        public String getOsVersion() { return osVersion; }
        public String getOsArch() { return osArch; }
        public OSType getOsType() { return osType; }
        public JDKInfo getJdkInfo() { return jdkInfo; }
        public String getPlatformName() { return osType.getPlatformName(); }
        public String getJDKVersionIdentifier() { return jdkInfo.getVersionIdentifier(); }
        public String getJDKVersion() { return jdkInfo.getVersion(); }
        public String getJDKVendor() { return jdkInfo.getVendor(); }
        
        @Override
        public String toString() {
            return String.format("OSInfo{osName='%s', osVersion='%s', osArch='%s', platformName='%s', jdkVersionIdentifier='%s'}", 
                osName, osVersion, osArch, getPlatformName(), getJDKVersionIdentifier());
        }
    }
}