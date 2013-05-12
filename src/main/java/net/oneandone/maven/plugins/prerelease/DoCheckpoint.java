package net.oneandone.maven.plugins.prerelease;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** Invoke all plugins attached to the deploy phase. Uses internally by the Promote Mojo. */

@Mojo(name = "do-snapshot")
public class DoCheckpoint extends Base {
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    @Override
    public void doExecute() throws Exception {
        project.getProperties();
    }
}
