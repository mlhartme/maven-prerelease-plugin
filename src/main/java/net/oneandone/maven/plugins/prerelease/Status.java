/* Copyright (c) 1&1. All Rights Reserved. */

package net.oneandone.maven.plugins.prerelease;

import net.oneandone.maven.plugins.prerelease.core.Archive;
import net.oneandone.maven.plugins.prerelease.core.WorkingCopy;
import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Lists available pre-preleases and display up-to-date information.
 */
@Mojo(name = "status")
public class Status extends ProjectBase {
    @Override
    public void doExecute(Archive archive) throws Exception {
        String revision;
        String name;

        if (archive.directory.exists()) {
            revision = Long.toString(WorkingCopy.load(basedir()).revision());
            for (FileNode prerelease : archive.directory.list()) {
                name = prerelease.getName();
                if (name.equals(revision)) {
                    name = name + " <- CURRENT";
                } else {
                    name = name + " (out-dated)";
                }
                getLog().info(name);
            }
        }
    }

    @Override
    public boolean definesTarget() {
        return false;
    }
}
