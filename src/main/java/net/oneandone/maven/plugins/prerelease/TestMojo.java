package net.oneandone.maven.plugins.prerelease;

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
import org.codehaus.plexus.component.annotations.Requirement;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Mojo(name = "test")
public class TestMojo extends Base {
    @Component
    private BuilderCommon builderCommon;

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
        project.getArtifact().setFile(new File("target/prerelease-1.5.0-SNAPSHOT.jar"));
        getLog().info("before");
        mojoExecutor.execute(session, executions, projectIndex);
        getLog().info("after");
    }
}
