/* Copyright (c) 1&1. All Rights Reserved. */

package net.oneandone.maven.plugins.prerelease;

import com.oneandone.devel.maven.Maven;
import net.oneandone.sushi.fs.World;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.ProjectBuilder;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.RemoteRepository;

import java.util.List;

public abstract class Base extends AbstractMojo {
    /**
     * Where to store prereleases.
     */
    @Parameter(property = "prerelease.archive", defaultValue = "${settings.localRepository}/../prereleases", required = true)
    protected String archiveRoot;

    /**
     * Where to store prereleases.
     */
    @Parameter(property = "prerelease.lockTimeout", defaultValue = "3600", required = true)
    protected int lockTimeout;

    /**
     * Where to send problem mails. Leave empty to switch off.
     */
    @Parameter
    protected String problemEmail;

    @Component
    private ProjectBuilder projectBuilder;

    /**
     * The entry point to Aether, i.e. the component doing all the work.
     */
    @Component
    private RepositorySystem repositorySystem;

    /**
     * The current repository/network configuration of Maven.
     */
    @Parameter(property = "repositorySystemSession", readonly = true)
    private RepositorySystemSession repositorySession;

    /**
     * The project's remote repositories to use for the resolution of project dependencies.
     */
    @Parameter(property = "project.remoteProjectRepositories", readonly = true)
    private List<RemoteRepository> remoteRepositories;

    @Parameter(property = "project.remoteArtifactRepositories", readonly = true)
    private List<ArtifactRepository> remoteRepositoriesLegacy;

    @Component
    protected MavenSession session;

    protected final World world;

    protected boolean alwaysUpdate;

    public Base() {
        this.world = new World();
    }

    @Override
    public void execute() throws MojoExecutionException {
        getLog().debug("user-properties: " + session.getUserProperties());
        alwaysUpdate = "always".equals(repositorySession.getUpdatePolicy());
        try {
            doExecute();
        } catch (RuntimeException | MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("plugin failed: " + e.getMessage(), e);
        }
    }

    public abstract void doExecute() throws Exception;

    protected Maven maven() {
        return new Maven(world, repositorySystem, repositorySession, projectBuilder, remoteRepositories, remoteRepositoriesLegacy);
    }
}
