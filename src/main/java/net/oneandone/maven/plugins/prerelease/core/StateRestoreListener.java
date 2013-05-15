package net.oneandone.maven.plugins.prerelease.core;

import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class StateRestoreListener extends BaseExecutionListener {
    private final Prerelease prerelease;
    private final MavenProjectHelper projectHelper;

    public StateRestoreListener(Prerelease prerelease, MavenProjectHelper projectHelper, ExecutionListener base) {
        super(base);
        this.prerelease = prerelease;
        this.projectHelper = projectHelper;
    }

    @Override
    public void projectStarted(ExecutionEvent event) {
        MavenProject project;

        project = event.getSession().getCurrentProject();
        try {
            prerelease.artifactFiles(project, projectHelper);
            project.getProperties().putAll(prerelease.descriptor.deployProperties);
        } catch (IOException e) {
            throw new RuntimeException("TODO", e);
        }
        super.projectStarted(event);
    }
}
