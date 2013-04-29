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

import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// TODO: sushi 2.8.3

public class OnShutdown extends Thread {
    private static OnShutdown singleton;

    /** a static singleton, because I don't want a shutdown hook for every world instance */
    public static synchronized OnShutdown get() {
        if (singleton == null) {
            singleton = new OnShutdown();
            Runtime.getRuntime().addShutdownHook(singleton);
        }
        return singleton;
    }

    /** null if the exit task has already been started */
    private List<FileNode> delete;

    // to generate directory names
    private int dirNo;

    public OnShutdown() {
        this.delete = new ArrayList<>();
        this.dirNo = 0;
    }

    //--

    @Override
    public synchronized void run() {
        List<FileNode> tmp;

        tmp = delete;
        delete = null;
        for (FileNode node : tmp) {
            tryDelete(node);
        }
    }

    //--

    /**
     * @param node  file or directory
     */
    public synchronized void deleteAtExit(FileNode node) {
        if (delete == null) {
            // already exiting
            tryDelete(node);
        } else {
            delete.add(node);
        }
    }

    /**
     * @param node  file or directory
     */
    public synchronized void dontDeleteAtExit(FileNode node) {
        if (delete == null) {
            throw new IllegalStateException();
        }
        if (!delete.remove(node)) {
            throw new IllegalArgumentException("not registered: " + node.getAbsolute());
        }
    }

    private boolean tryDelete(FileNode node) {
        try {
            if (node.exists()) {
                node.deleteTree();
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
