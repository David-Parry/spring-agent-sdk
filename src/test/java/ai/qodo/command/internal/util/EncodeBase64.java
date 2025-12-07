/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EncodeBase64 {

    public static void main(String[] args) {
        String s = "";
        String t = Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
        System.out.println(t);

        System.out.println(new String(Base64.getDecoder().decode(t)));
    }

}
