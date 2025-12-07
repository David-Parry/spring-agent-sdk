/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class DirectoryPrinter {
    private static final Logger logger = LoggerFactory.getLogger(DirectoryPrinter.class);

    public static String buildDirectoryTree(Path root) {
        StringBuilder sb = new StringBuilder();
        try {
            buildTree(sb, root, "", true);
        } catch (IOException | UncheckedIOException er) {
            logger.error("Failure building directory tree for path {}", root, er);
        }
        return sb.toString();
    }

    private static void buildTree(StringBuilder sb, Path path, String prefix, boolean isLast) throws IOException {
        sb.append(prefix).append(isLast ? "└── " : "├── ").append(path.getFileName()).append(System.lineSeparator());

        if (Files.isDirectory(path)) {
            List<Path> children = Files
                    .list(path)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .collect(Collectors.toList());
            for (int i = 0; i < children.size(); i++) {
                buildTree(sb, children.get(i), prefix + (isLast ? "    " : "│   "), i == children.size() - 1);
            }
        }
    }


}
