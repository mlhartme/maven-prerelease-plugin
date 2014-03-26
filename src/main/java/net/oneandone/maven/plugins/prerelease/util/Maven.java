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
package net.oneandone.maven.plugins.prerelease.util;

import net.oneandone.maven.plugins.prerelease.core.Prerelease;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.Logger;
import org.slf4j.spi.LocationAwareLogger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Maven {
    private final World world;
    private final MavenSession parentSession;
    private final ArtifactRepository localRepository;
    private final ExecutionListener executionListener;
    private final MavenProjectHelper projectHelper;
    private final List<ArtifactRepository> remoteRepositories;
    private final ProjectBuilder builder;

    public Maven(World world, MavenSession parentSession, ArtifactRepository localRepository,
                 ExecutionListener executionListener, MavenProjectHelper projectHelper,
                 ProjectBuilder builder, List<ArtifactRepository> remoteRepositories) {
        this.world = world;
        this.parentSession = parentSession;
        this.localRepository = localRepository;
        this.executionListener = executionListener;
        this.projectHelper = projectHelper;
        this.builder = builder;
        this.remoteRepositories = remoteRepositories;
    }

    public FileNode getLocalRepositoryDir() {
        return world.file(localRepository.getBasedir());
    }

    public ExecutionListener getExecutionListener() {
        return executionListener;
    }

    public void build(FileNode basedir, Map<String, String> userProperties, String ... goals) throws Exception {
        build(basedir, userProperties, executionListener, false, goals);
    }

    /**
     * Creates an DefaultMaven instance, initializes it form parentRequest (in Maven, this is done by MavenCli - also by
     * loading settings).
     */
    public void build(FileNode basedir, Map<String, String> userProperties, ExecutionListener theExecutionListener, boolean filter, String ... goals) throws BuildException {
        DefaultPlexusContainer container;
        org.apache.maven.Maven maven;
        MavenExecutionRequest request;
        MavenExecutionResult result;
        BuildException exception;
        PatchedBuilderCommon bc;

        request = DefaultMavenExecutionRequest.copy(parentSession.getRequest());
        container = (DefaultPlexusContainer) parentSession.getContainer();
        try {
            maven = container.lookup(org.apache.maven.Maven.class);
        } catch (ComponentLookupException e) {
            throw new IllegalStateException(e);
        }
        request.setPom(basedir.join("pom.xml").toPath().toFile());
        request.setProjectPresent(true);
        request.setGoals(Arrays.asList(goals));
        request.setBaseDirectory(basedir.toPath().toFile());
        request.setUserProperties(merged(request.getUserProperties(), userProperties));
        request.setExecutionListener(theExecutionListener);

        bc = PatchedBuilderCommon.install(container, filter);
        Logger logger = getLogger(container);
        org.slf4j.Logger previous = setTarget(logger, new PrefixLogger("prerelease", "  ", LocationAwareLogger.INFO_INT, System.out));
        logger.info("[" + basedir + "] mvn " + props(request.getUserProperties()) + Separator.SPACE.join(goals));
        try {
            result = maven.execute(request);
        } finally {
            bc.uninstall();
            if (previous != null) {
                setTarget(logger, previous);
            }
        }
        exception = null;
        for (Throwable e : result.getExceptions()) {
            if (exception == null) {
                exception = new BuildException("build failed: " + e, e);
            } else {
                exception.addSuppressed(e);
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    /** @return with tailing space */
    private static String props(Properties props) {
        StringBuilder builder;

        builder = new StringBuilder();
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            builder.append("-D").append(entry.getKey());
            if (!((String) entry.getValue()).isEmpty()) {
                builder.append('=').append(entry.getValue());
            }
            builder.append(' ');
        }
        return builder.toString();
    }

    //--

    private static Logger getLogger(DefaultPlexusContainer container) {
        return container.getLoggerManager().getLoggerForComponent("notused");
    }

    private static org.slf4j.Logger setTarget(Logger plexusLogger, org.slf4j.Logger target) {
        Field field;
        org.slf4j.Logger previous;

        try {
            field = plexusLogger.getClass().getDeclaredField("logger");
            field.setAccessible(true);
            previous = (org.slf4j.Logger) field.get(plexusLogger);
            field.set(plexusLogger, target);
        } catch (Exception e) {
            plexusLogger.warn("cannot adjust logger: " + e.getMessage());
            previous = null;
        }
        return previous;
    }

    //--

    private static Properties merged(Properties left, Map<String, String> right) {
        Properties result;

        result = new Properties(left);
        result.putAll(right);
        return result;
    }

    /** Executes the deploy phase only, with the prerelease pom */
    public void deployPrerelease(Log log, Map<String, String> propertyArgs, Prerelease prerelease) throws Exception {
        PromoteExecutionListener listener;

        listener = new PromoteExecutionListener(prerelease, projectHelper, executionListener);
        try {
            build(prerelease.checkout, prerelease.descriptor.releaseProps(propertyArgs), listener, true, "deploy");
        } catch (BuildException e) {
            if (listener.isFirstSuccess()) {
                log.warn("Promote succeeded: your artifacts have been deployed, and your svn tag was created. ");
                log.warn("However, some optional deploy goals failed with this exception:");
                log.warn(e);
                log.warn("Thus, you can use your release, but someone should have a look at this exception.");
            } else {
                throw e;
            }
        }
    }

    /** executes the deploy phase only - with the snapshot pom */
    public void deploySnapshot(FileNode directory, Log log, Map<String, String> propertyArgs, Prerelease prerelease) throws Exception {
        PromoteExecutionListener listener;

        listener = new PromoteExecutionListener(prerelease, projectHelper, executionListener);
        try {
            build(directory, propertyArgs, listener, true, "deploy");
        } catch (BuildException e) {
            if (listener.isFirstSuccess()) {
                log.warn("Snapshot deployment succeeded.");
                log.warn("However, some optional deploy goals failed with this exception:");
                log.warn(e);
                log.warn("Thus, you can use your snapshots, but someone should have a look at this exception.");
            } else {
                throw e;
            }
        }
    }

    public List<FileNode> files(List<Artifact> artifacts) {
        List<FileNode> result;

        result = new ArrayList<>();
        for (Artifact a : artifacts) {
            result.add(file(a));
        }
        return result;
    }

    public FileNode file(Artifact artifact) {
        return world.file(artifact.getFile());
    }

    //-- load poms -- for testing only

    public MavenProject loadPom(FileNode file) throws ProjectBuildingException {
        ProjectBuildingRequest request;
        ProjectBuildingResult result;

        // session initializes the repository session in the build request
        request = new DefaultProjectBuildingRequest(parentSession.getProjectBuildingRequest());
        request.setRemoteRepositories(remoteRepositories);
        request.setProcessPlugins(false);
        request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        request.setSystemProperties(System.getProperties());
        //If you don't turn this into RepositoryMerging.REQUEST_DOMINANT the dependencies will be resolved against Maven Central
        //and not against the configured repositories. The default of the DefaultProjectBuildingRequest is
        // RepositoryMerging.POM_DOMINANT.
        request.setRepositoryMerging(ProjectBuildingRequest.RepositoryMerging.REQUEST_DOMINANT);
        request.setResolveDependencies(false);
        result = builder.build(file.toPath().toFile(), request);
        return result.getProject();
    }
}
