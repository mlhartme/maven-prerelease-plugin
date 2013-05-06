package net.oneandone.maven.plugins.prerelease;

import net.oneandone.sushi.fs.file.FileNode;
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

    @Override
    public void doExecute() throws Exception {
        List<MojoExecution> executions;

        MavenExecutionPlan executionPlan =
                builderCommon.resolveBuildPlan(session, project, new TaskSegment(false, new LifecycleTask("deploy")), new HashSet<Artifact>());
        getLog().info(executionPlan.toString());
        executions = new ArrayList<>();
        for (MojoExecution obj : executionPlan.getMojoExecutions()) {
            if ("deploy".equals(obj.getLifecyclePhase())) {
                getLog().info(obj.getLifecyclePhase() + " - " + obj.getPlugin().getArtifactId() + ":" + obj.getGoal());
                executions.add(obj);
            }
        }
        ProjectIndex projectIndex = new ProjectIndex( session.getProjects() );
        artifactsFile();
        mojoExecutor.execute(session, executions, projectIndex);
    }

    public void artifactsFile() throws IOException {
        FileNode artifacts;
        String str;
        String type;
        String classifier;

        artifacts = world.file(project.getBasedir()).getParent().join("artifacts");
        for (FileNode file : artifacts.list()) {
            if (file.getName().endsWith(".md5") || file.getName().endsWith(".sha1") || file.getName().endsWith(".asc")) {
                // skip
            } else {
                type = file.getExtension();
                if (type.equals(".pom") && !project.getPackaging().equals("pom")) {
                    // ignored
                } else {
                    str = file.getName();
                    str = Strings.removeRight(str, type);
                    str = Strings.removeLeft(str, project.getArtifactId() + "-");
                    str = Strings.removeRight(str, project.getVersion());
                    if (str.isEmpty()) {
                        project.getArtifact().setFile(file.toPath().toFile());
                    } else {
                        classifier = Strings.removeRight(str, "-");
                        projectHelper.attachArtifact(project, file.toPath().toFile(), classifier);
                    }
                }
            }
        }
    }
}
