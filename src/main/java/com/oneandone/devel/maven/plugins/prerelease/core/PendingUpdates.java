package com.oneandone.devel.maven.plugins.prerelease.core;

import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.util.List;

public class PendingUpdates extends Exception {
    public final long revision;
    public final List<String> updates;

    public PendingUpdates(long revision, List<String> updates) {
        super("pending updates for revision " + revision + ":\n"
                + Strings.indent(Separator.RAW_LINE.join(updates), "  ") + "\nRun svn update.");
        this.revision = revision;
        this.updates = updates;
    }
}
