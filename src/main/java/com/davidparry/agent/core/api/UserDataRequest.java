/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents user data information for requests
 * @param extensionVersion The version of the extension
 * @param osPlatform The operating system platform - 'aix', 'darwin', 'freebsd', 'linux', 'openbsd', 'sunos', 'win32'
 * @param osVersion The operating system version (JDK version)
 * @param editorType The type of editor - typically 'cli'
 */
public record UserDataRequest(
    @JsonProperty("extension_version") String extensionVersion,
    @JsonProperty("os_platform") String osPlatform,
    @JsonProperty("os_version") String osVersion,
    @JsonProperty("editor_type") String editorType
) {

}
