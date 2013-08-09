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
package net.oneandone.maven.plugins.prerelease.core;

import net.oneandone.sushi.fs.file.FileNode;

import java.util.List;

public class Storages {
    public static Storages create(List<String> paths) {
        return null;
    }

    private final FileNode[] directories;

    public Storages(FileNode[] directories) {
        if (directories.length == 0) {
            throw new IllegalArgumentException();
        }
        this.directories = directories;
    }

    public FileNode primary() {
        return directories[0];
    }
}
