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
        Set done;
        List<FileNode> storages;
        List<Node> archives;
        Archive archive;
        String relative;
        FileNode dest;
        FileNode storage;
        int count;

        count = 0;
        storages = storages();
        // don't start at "-2" because we also want to wipe ...
        for (int level = storages.size() - 1; level >= 0; level--) {
            storage = storages.get(level);
            getLog().info("checking storage: " + storage.getAbsolute());
            archives = storage.find("*/*");
            for (Node candidate : archives) {
                if (!candidate.isDirectory()) {
                    continue;
                }
                relative = candidate.getRelative(storage);
                archive = Archive.tryOpen(directories(storages, relative));
                if (archive == null) {
                    getLog().info("skipped because it is locked: " + relative);
                } else {
                    try {
                        archive.wipe(keep);
                        for (FileNode src : archive.list().values()) {
                            if (!src.join("prerelease.properties").readString().contains("prerelease=")) {
                                // This property was introduced in 1.6, together with multiple storage support
                                getLog().info("skipped -- prerelease version too old: " + relative);
                                continue;
                            }
                            if (level == storages.size() - 1) {
                                getLog().info("already in final storage: " + src);
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

            }
        }
        getLog().info(count + " archives swapped.");
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
