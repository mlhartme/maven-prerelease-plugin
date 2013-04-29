/* Copyright (c) 1&1. All Rights Reserved. */

package com.oneandone.devel.maven.plugins.prerelease;

import com.oneandone.devel.devreg.model.Registry;
import com.oneandone.devel.devreg.model.UnknownUserException;
import com.oneandone.devel.maven.plugins.prerelease.frischfleisch.Generator;
import com.oneandone.devel.maven.plugins.prerelease.core.Project;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.NodeInstantiationException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Update Frischfleisch RSS feed and optionally send email(s).
 */
@Mojo(name = "frischfleisch", defaultPhase = LifecyclePhase.DEPLOY)
public class Frischfleisch extends AbstractMojo {
    @Parameter
    protected String[] emails = new String[] {};

    @Parameter(property = "prerelease.frischfleisch.skip")
    protected boolean skip = false;

    /**
     * Email Address of the current user. Usually determined automatically.
     */
    @Parameter(property = "prerelease.frischfleisch.user")
    protected String user = null;

    /**
     * URL of the webdav share where to update rss feeds. Null to disable.
     */
    @Parameter
    protected String feeds;


    /**
     * The Maven Project Object
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    public void execute() throws MojoExecutionException {
        World world;
        FileNode basedir;

        if (skip) {
            getLog().info("prerelease.frischfleisch.skip=true");
            return;
        }
        world = new World();
        try {
            basedir = world.file(project.getBasedir());
            doExecute(getLog(), basedir, Project.forMavenProject(project), user != null ? user : getUserEmail(world), feeds, emails);
        } catch (RuntimeException | MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("plugin failed: " + e.getMessage(), e);
        }
    }

    public static void doExecute(Log log, FileNode basedir, Project project, String from, String feeds, String[] emails)
            throws Exception {
        Generator generator;
        StringBuilder builder;
        Node feedsNode;

        if (feeds == null) {
            feedsNode = null;
        } else {
            try {
                feedsNode = basedir.getWorld().node(feeds);
            } catch (NodeInstantiationException | URISyntaxException e) {
                throw new MojoExecutionException("invalid feeds parameter: " + feeds, e);
            }
        }
        generator = new Generator(project.name, project.url, project.groupId, project.artifactId, project.version, feedsNode);
        generator.run(basedir, from, emails);
        if (feedsNode != null) {
            log.info("updated feeds at " + feedsNode.getURI());
        }
        if (emails.length > 0) {
            builder = new StringBuilder("email sent to");
            for (String email : emails) {
                builder.append(' ');
                builder.append(email);
            }
            log.info(builder.toString());
        }
    }

    public String getUserEmail(World world) throws IOException {
        try {
            return Registry.loadCached(world).whoAmI().getEmail();
        } catch (UnknownUserException e) {
            return "unknown@schlund.de";
        }
    }
}
