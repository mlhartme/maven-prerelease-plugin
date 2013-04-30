package net.oneandone.maven.plugins.prerelease.change;

import com.oneandone.devel.devreg.model.User;
import com.oneandone.devel.maven.Maven;
import net.oneandone.sushi.cli.Command;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import org.apache.maven.project.MavenProject;

import java.util.Date;

public abstract class Change implements Command {
    protected final Console console;
    protected final User user;
    protected final Maven maven;
    protected final MavenProject project;
    protected final Date now;

    @Option("local")
    private boolean local = false;

    @Option("B")
    private boolean batch = false;

    public Change(Console console, User user, Maven maven, MavenProject project) {
        if (user == null) {
            throw new IllegalArgumentException();
        }
        this.console = console;
        this.user = user;
        this.maven = maven;
        this.project = project;
        this.now = new Date();
    }


    protected String releaseVersion() {
        return Strings.removeRightOpt(project.getVersion(), "-SNAPSHOT");
    }

    public void invoke() throws Exception {
        FileNode basedir;
        File file;
        String message;

        file = File.load((FileNode) console.world.getWorking());
        message = change(file);
        if (message != null) {
            file.save();
            if (!local) {
                basedir = (FileNode) console.world.getWorking();
                if (!batch) {
                    console.info.println(basedir.exec("svn", "diff", File.PATH));
                    console.info.println(basedir.exec("svn", "st"));
                    console.readline("Press return to run\n  svn commit -m " + message + "\nOr press ctrl-c to abort");
                }
                console.info.println(basedir.exec("svn", "commit", "-m", message));
            }
        }
    }

    public abstract String change(File file) throws Exception;
}
