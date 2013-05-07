package net.oneandone.maven.plugins.prerelease;

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.lifecycle.MavenExecutionPlan;
import org.apache.maven.lifecycle.internal.BuilderCommon;
import org.apache.maven.lifecycle.internal.LifecycleTask;
import org.apache.maven.lifecycle.internal.MojoExecutor;
import org.apache.maven.lifecycle.internal.PhaseRecorder;
import org.apache.maven.lifecycle.internal.ProjectIndex;
import org.apache.maven.lifecycle.internal.ReactorBuildStatus;
import org.apache.maven.lifecycle.internal.ReactorContext;
import org.apache.maven.lifecycle.internal.TaskSegment;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.component.annotations.Requirement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Invoke all plugins attached to the deploy phase. Uses internally by the Promote Mojo. */

@Mojo(name = "do-promote")
public class DoPromote extends Base {
    @Component
    private BuilderCommon builderCommon;

    @Component
    private MavenProjectHelper projectHelper;

    @Component
    private MojoExecutor mojoExecutor;

    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /** comma separated list of artifact ids */
    @Parameter(property = "prerelease.promote.mandatory", required = true)
    protected String mandatory;

    @Override
    public void doExecute() throws Exception {
        List<MojoExecution> mandatoryExecutions;
        List<MojoExecution> optionalExecutions;
        List<String> mandatories;

        mandatories = Separator.COMMA.split(mandatory);
        MavenExecutionPlan executionPlan =
                builderCommon.resolveBuildPlan(session, project, new TaskSegment(false, new LifecycleTask("deploy")), new HashSet<Artifact>());
        getLog().info(executionPlan.toString());
        mandatoryExecutions = new ArrayList<>();
        optionalExecutions = new ArrayList<>();
        for (MojoExecution obj : executionPlan.getMojoExecutions()) {
            if ("deploy".equals(obj.getLifecyclePhase())) {
                if (mandatories.contains(obj.getPlugin().getArtifactId())) {
                    getLog().info("mandatory: " + obj.getPlugin().getArtifactId() + ":" + obj.getGoal());
                    mandatoryExecutions.add(obj);
                } else {
                    getLog().info("optional " + obj.getPlugin().getArtifactId() + ":" + obj.getGoal());
                    optionalExecutions.add(obj);
                }
            }
        }
        ProjectIndex projectIndex = new ProjectIndex(session.getProjects());
        artifactFiles();
        mojoExecutor.execute(session, mandatoryExecutions, projectIndex);
        try {
            mojoExecutor.execute(session, mandatoryExecutions, projectIndex);
        } catch (Exception e) {
            getLog().warn("Promote succeeded: your artifacts have been deployed, and your svn tag was created. ");
            getLog().warn("However, optional promote goals failed with this exception: ");
            getLog().warn(e);
            getLog().warn("Thus, you can use your release, but someone experienced should have a look.");
        }
    }

    public void artifactFiles() throws IOException {
        FileNode artifacts;
        String name;
        String str;
        String type;
        String classifier;

        artifacts = world.file(project.getBasedir()).getParent().getParent().join("artifacts");
        for (FileNode file : artifacts.list()) {
            name = file.getName();
            if (name.endsWith(".md5") || name.endsWith(".sha1") || name.endsWith(".asc") || name.equals("_maven.repositories")) {
                // skip
            } else {
                type = file.getExtension();
                if ("pom".equals(type) && !project.getPackaging().equals("pom")) {
                    // ignored
                } else {
                    str = name.substring(0, name.length() - type.length() - 1);
                    str = Strings.removeLeft(str, project.getArtifactId() + "-");
                    str = Strings.removeLeft(str, project.getVersion());
                    if (str.isEmpty()) {
                        project.getArtifact().setFile(file.toPath().toFile());
                    } else {
                        classifier = Strings.removeLeft(str, "-");
                        projectHelper.attachArtifact(project, file.toPath().toFile(), classifier);
                    }
                }
            }
        }
    }
}
