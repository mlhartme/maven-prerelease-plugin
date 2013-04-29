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
