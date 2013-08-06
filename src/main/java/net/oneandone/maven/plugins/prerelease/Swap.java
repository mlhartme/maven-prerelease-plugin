/**
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.maven.plugins.prerelease;

import net.oneandone.maven.plugins.prerelease.core.Archive;
import net.oneandone.maven.plugins.prerelease.core.Target;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.List;

/**
 * Moves directories from the primary archive to a secondary archive. Also wipes all archives.
 */
@Mojo(name = "swap", requiresProject = false)
public class Swap extends Base {
    /**
     * Location of the secondary archive root.
     */
    @Parameter(property = "prerelease.swap", required = true)
    private String swap;

    @Override
    public void doExecute() throws Exception {
        FileNode root;
        List<Node> directories;
        Archive archive;
        FileNode swapRoot;
        FileNode dest;

        root = world.file(archiveRoot);
        if (!root.exists()) {
            return;
        }
        swapRoot = world.file(swap);
        directories = root.find("*/*");
        for (Node dir : directories) {
            if (!dir.isDirectory()) {
                continue;
            }
            archive = Archive.tryOpen((FileNode) dir);
            if (archive == null) {
                getLog().info("skipped: " + dir);
            } else {
                try {
                    archive.wipe(keep, null);
                    for (Node src : dir.list()) {
                        if (src.isLink()) {
                            continue;
                        }
                        if (src.getName().equals(Target.REMOVE)) {
                            continue;
                        }
                        dest = swapRoot.join(dir.getRelative(root), src.getName());
                        dest.mkdirs();
                        src.copyDirectory(dest);
                        src.deleteTree();
                        getLog().info(archive + " swapped out to " + dest.getAbsolute());
                    }
                } finally {
                    archive.close();
                }
            }
        }
        getLog().info(directories.size() + " archives processed.");
    }
}
