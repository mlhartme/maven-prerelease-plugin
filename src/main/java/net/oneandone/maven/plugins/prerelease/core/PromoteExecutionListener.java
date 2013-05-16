package net.oneandone.maven.plugins.prerelease.core;

import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.IOException;

public class PromoteExecutionListener extends BaseExecutionListener {
    private final Prerelease prerelease;
    private final MavenProjectHelper projectHelper;
    private int mojosStarted;
    private boolean firstSuccess;

    public PromoteExecutionListener(Prerelease prerelease, MavenProjectHelper projectHelper, ExecutionListener base) {
        super(base);
        this.prerelease = prerelease;
        this.projectHelper = projectHelper;
        this.mojosStarted = 0;
        this.firstSuccess = false;
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

    @Override
    public void mojoStarted(ExecutionEvent event) {
        mojosStarted++;
        super.mojoStarted(event);
    }

    @Override
    public void mojoSucceeded(ExecutionEvent event) {
        if (mojosStarted == 1) {
            System.out.println("first success: " + event);
            firstSuccess = true;
        }
        super.mojoSucceeded(event);
    }

    @Override
    public void mojoFailed(ExecutionEvent event) {
        super.mojoFailed(event);
    }

    public boolean isFirstSuccess() {
        return firstSuccess;
    }
}
