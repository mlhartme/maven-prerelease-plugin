/* Copyright (c) 1&1. All Rights Reserved. */

package com.oneandone.devel.maven.plugins.prerelease.frischfleisch;

import java.util.ArrayList;
import java.util.List;

/** Represents the rss and emails content */
public class Content {
    private final List<String> lines;

    public Content() {
        this.lines = new ArrayList<String>();
    }

    public void header(String name, String value) {
        if (value == null) {
            throw new IllegalArgumentException("missing value for header " + name);
        }
        if (value.indexOf('\n') != -1) {
            throw new IllegalArgumentException("invalid newline in value " + value);
        }
        lines.add(name + ": " + value);
    }

    public void body(String line) {
        if (line.indexOf('\n') != -1) {
            throw new IllegalArgumentException("invalid newline in body line " + line);
        }
        lines.add(line);
    }

    @Override
    public String toString() {
        StringBuilder builder;

        builder = new StringBuilder();
        for (String line : lines) {
            builder.append(line).append('\n');
        }
        return builder.toString();
    }
}
