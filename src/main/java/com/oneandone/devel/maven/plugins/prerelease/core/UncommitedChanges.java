package com.oneandone.devel.maven.plugins.prerelease.core;

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.util.List;

public class UncommitedChanges extends Exception {
    public final List<FileNode> modifications;

    public UncommitedChanges(List<FileNode> modifications) {
        super("uncommited changes:\n" + Strings.indent(Separator.RAW_LINE.join(modifications), "  "));
        this.modifications = modifications;
    }
}
