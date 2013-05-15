package net.oneandone.maven.plugins.prerelease.core;

import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PropertySnapshotListener implements ExecutionListener {
    private final ExecutionListener base;
    private final Prerelease prerelease;
    private final Map<String, String> initialProperties;

    public PropertySnapshotListener(Prerelease prerelease, ExecutionListener base) {
        this.base = base;
        this.prerelease = prerelease;
        this.initialProperties = new HashMap<>();
    }

    @Override
    public void projectDiscoveryStarted(ExecutionEvent event) {
        base.projectDiscoveryStarted(event);
    }

    @Override
    public void sessionStarted(ExecutionEvent event) {
        base.sessionStarted(event);
    }

    @Override
    public void sessionEnded(ExecutionEvent event) {
        base.sessionEnded(event);
    }

    @Override
    public void projectSkipped(ExecutionEvent event) {
        base.projectSkipped(event);
    }

    @Override
    public void projectStarted(ExecutionEvent event) {
        initialProperties(event.getSession().getCurrentProject());
        base.projectStarted(event);
    }

    @Override
    public void projectSucceeded(ExecutionEvent event) {
        saveModified(event.getSession().getCurrentProject());
        base.projectSucceeded(event);
    }

    @Override
    public void projectFailed(ExecutionEvent event) {
        base.projectFailed(event);
    }

    @Override
    public void mojoSkipped(ExecutionEvent event) {
        base.mojoSkipped(event);
    }

    @Override
    public void mojoStarted(ExecutionEvent event) {
        base.mojoStarted(event);
    }

    @Override
    public void mojoSucceeded(ExecutionEvent event) {
        base.mojoSucceeded(event);
    }

    @Override
    public void mojoFailed(ExecutionEvent event) {
        base.mojoFailed(event);
    }

    @Override
    public void forkStarted(ExecutionEvent event) {
        base.forkStarted(event);
    }

    @Override
    public void forkSucceeded(ExecutionEvent event) {
        base.forkSucceeded(event);
    }

    @Override
    public void forkFailed(ExecutionEvent event) {
        base.forkFailed(event);
    }

    @Override
    public void forkedProjectStarted(ExecutionEvent event) {
        base.forkedProjectStarted(event);
    }

    @Override
    public void forkedProjectSucceeded(ExecutionEvent event) {
        base.forkedProjectSucceeded(event);
    }

    @Override
    public void forkedProjectFailed(ExecutionEvent event) {
        base.forkedProjectFailed(event);
    }

    private void initialProperties(MavenProject project) {
        for (Map.Entry<Object, Object> entry : project.getProperties().entrySet()) {
            if (entry.getValue() instanceof String) {
                initialProperties.put((String) entry.getKey(), (String) entry.getValue());
            } else {
                throw new IllegalStateException();
            }
        }
    }

    public void saveModified(MavenProject project) {
        String old;
        Map<String, String> deployProperties;

        deployProperties = prerelease.descriptor.deployProperties;
        for (Map.Entry<Object, Object> entry : project.getProperties().entrySet()) {
            if (entry.getValue() instanceof String) {
                old = initialProperties.get(entry.getKey());
                if (!entry.getValue().equals(old)) {
                    deployProperties.put((String) entry.getKey(), (String) entry.getValue());
                }
            }
        }
        try {
            prerelease.descriptor.save(prerelease.target);
        } catch (IOException e) {
            throw new RuntimeException("TODO", e);
        }
    }

}
