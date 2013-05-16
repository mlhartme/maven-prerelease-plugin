package net.oneandone.maven.plugins.prerelease.util;

import net.oneandone.maven.plugins.prerelease.core.BaseExecutionListener;
import net.oneandone.maven.plugins.prerelease.core.Prerelease;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PrepareExecutionListener extends BaseExecutionListener {
    private final Prerelease prerelease;
    private final Map<String, String> initialProperties;

    public PrepareExecutionListener(Prerelease prerelease, ExecutionListener base) {
        super(base);
        this.prerelease = prerelease;
        this.initialProperties = new HashMap<>();
    }

    @Override
    public void projectStarted(ExecutionEvent event) {
        initialProperties(event.getSession().getCurrentProject());
        super.projectStarted(event);
    }

    @Override
    public void projectSucceeded(ExecutionEvent event) {
        super.projectSucceeded(event);
        saveModified(event.getSession().getCurrentProject());
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
