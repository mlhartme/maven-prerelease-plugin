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
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.plugins.annotations.Mojo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wipes archives and moves prereleases to the next storage. You usually have two storages, primary and secondary.
 * Useful when you use ram disks: use the ramdisk as primary storage, and a harddisk as secondary storage.
 */
@Mojo(name = "swap", requiresProject = false)
public class Swap extends Base {
    @Override
    public void doExecute() throws Exception {
        Set<String> relatives;
        List<FileNode> storages;
        List<Node> archives;
        Archive archive;
        FileNode dest;
        FileNode storage;
        int count;
        int level;

        count = 0;
        storages = storages();
        relatives = new HashSet<>();
        for (level = 0; level < storages.size(); level++) {
            storage = storages.get(level);
            getLog().info("storage " + (level + 1) + ": " + storage.getAbsolute());
            archives = storage.find("*/*");
            for (Node candidate : archives) {
                if (!candidate.isDirectory()) {
                    continue;
                }
                relatives.add(candidate.getRelative(storage));
            }
        }
        getLog().info("archives found: " + relatives.size());
        for (String relative : relatives) {
            archive = Archive.tryOpen(directories(storages, relative));
            if (archive == null) {
                getLog().info("skipped because it is locked: " + relative);
                continue;
            }
            try {
                archive.wipe(keep);
                for (FileNode src : archive.list().values()) {
                    level = findLevel(storages, src);
                    getLog().debug(src + ": level " + level);
                    if (level == storages.size() - 1) {
                        getLog().debug("already in final storage: " + src);
                    } else {
                        dest = storages.get(level + 1).join(relative, src.getName());
                        dest.getParent().mkdirsOpt();
                        src.move(dest);
                        getLog().info("swapped " + src.getAbsolute() + " -> " + dest.getAbsolute());
                        count++;
                    }
                }
            } finally {
                archive.close();
            }
        }
        getLog().info(count + " archives swapped.");
    }

    private static int findLevel(List<FileNode> storages, Node prerelease) {
        for (int level = 0; level < storages.size(); level++) {
            if (prerelease.hasAnchestor(storages.get(level))) {
                return level;
            }
        }
        throw new IllegalStateException(prerelease.toString());
    }

    private static List<FileNode> directories(List<FileNode> storages, String relative) {
        List<FileNode> result;

        result = new ArrayList<>();
        for (FileNode storage : storages) {
            result.add(storage.join(relative));
        }
        return result;
    }
}
