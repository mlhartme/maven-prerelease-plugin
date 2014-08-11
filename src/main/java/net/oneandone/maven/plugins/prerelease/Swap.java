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
import net.oneandone.maven.plugins.prerelease.core.Project;
import net.oneandone.maven.plugins.prerelease.core.Storages;
import net.oneandone.maven.plugins.prerelease.core.Target;
import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.IOException;
import java.util.Set;

/**
 * Wipes archives and moves prereleases to the next storage. You usually have two storages, primary and secondary.
 * Useful when you use ram disks: use the ramdisk as primary storage, and a harddisk as secondary storage.
 */
@Mojo(name = "swap", requiresProject = false)
public class Swap extends Base {
    @Override
    public void doExecute() throws Exception {
        Set<Project> projects;
        Storages storages;
        Archive archive;
        FileNode dest;
        int count;
        int level;
        FileNode src;

        count = 0;
        storages = storages();
        projects = storages.list(getLog());
        getLog().info("archives found: " + projects.size());
        for (Project project : projects) {
            archive = new Archive(project, storages.directories(project));
            try {
                archive.open(-1, null);
            } catch (IOException e) {
                getLog().info("skipped because it is locked: " + project);
                continue;
            }
            try {
                archive.wipe(keep);
                for (Target target : archive.list()) {
                    src = target.join();
                    level = storages.findLevel(src);
                    getLog().debug(src + ": level " + level);
                    level++;
                    if (level >= storages.levels()) {
                        getLog().debug("already in final storage: " + src);
                    } else {
                        dest = storages.directory(project, level);
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
}
