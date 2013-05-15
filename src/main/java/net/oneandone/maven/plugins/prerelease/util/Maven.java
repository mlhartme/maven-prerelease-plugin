package net.oneandone.maven.plugins.prerelease.util;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.codehaus.plexus.PlexusContainer;
import org.sonatype.aether.RepositorySystemSession;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.Logger;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.RepositoryPolicy;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class Maven {
    public static Maven withDefaults(World world) {
        DefaultPlexusContainer container;
        RepositorySystem system;
        MavenRepositorySystemSession session;
        LocalRepository localRepository;
        ArtifactRepository repository;

        container = container(null, null, Logger.LEVEL_DISABLED);
        repository = new DefaultArtifactRepository("central", "http://repo1.maven.org/maven2", new DefaultRepositoryLayout(),
                new ArtifactRepositoryPolicy(false, RepositoryPolicy.UPDATE_POLICY_DAILY, RepositoryPolicy.CHECKSUM_POLICY_WARN),
                new ArtifactRepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_WARN)
            );
        try {
            localRepository = new LocalRepository(defaultLocalRepositoryDir(world).getAbsolute());
            system = container.lookup(RepositorySystem.class);
            session = new MavenRepositorySystemSession();
            session.setOffline(false);
            session.setLocalRepositoryManager(system.newLocalRepositoryManager(localRepository));
            session.setProxySelector(null);
            return new Maven(world, container, null, session, container.lookup(ProjectBuilder.class), Arrays.asList(repository));
        } catch (ComponentLookupException e) {
            throw new IllegalStateException(e);
        }
    }

    //--

    public static FileNode defaultLocalRepositoryDir(World world) {
        return world.file(new File(System.getProperty("user.home"))).join(".m2/repository");
    }

    public static DefaultPlexusContainer container(ClassWorld classWorld, ClassRealm realm, int loglevel) {
        DefaultContainerConfiguration config;
        DefaultPlexusContainer container;

        config = new DefaultContainerConfiguration();
        if (classWorld != null) {
            config.setClassWorld(classWorld);
        }
        if (realm != null) {
            config.setRealm(realm);
        }
        try {
            container = new DefaultPlexusContainer(config);
        } catch (PlexusContainerException e) {
            throw new IllegalStateException(e);
        }
        container.getLoggerManager().setThreshold(loglevel);
        return container;
    }

    //--

    private final World world;
    private final PlexusContainer container;
    private final ExecutionListener executionListener;
    private final RepositorySystemSession repositorySession;
    private final List<ArtifactRepository> remoteLegacy; // needed to load poms :(

    // TODO: use a project builder that works without legacy classes, esp. without ArtifactRepository ...
    // As far as I know, there's no such project builder in mvn 3.0.2.
    private final ProjectBuilder builder;

    public Maven(World world, PlexusContainer container, ExecutionListener executionListener,
                 RepositorySystemSession repositorySession, ProjectBuilder builder, List<ArtifactRepository> remoteLegacy) {
        this.world = world;
        this.container = container;
        this.executionListener = executionListener;
        this.repositorySession = repositorySession;
        this.builder = builder;
        this.remoteLegacy = remoteLegacy;
    }

    public void build(final Log log, FileNode basedir, boolean alwaysUpdate, Properties userProperties, String ... goals) throws Exception {
        org.apache.maven.Maven maven;
        MavenExecutionRequest request;
        MavenExecutionResult result;
        Exception exception;

        maven = container.lookup(org.apache.maven.Maven.class);
        request = new DefaultMavenExecutionRequest();
        request.setPom(basedir.join("pom.xml").toPath().toFile());
        request.setGoals(Arrays.asList(goals));
        request.setBaseDirectory(basedir.toPath().toFile());
        request.setUserProperties(userProperties);
        request.setExecutionListener(executionListener);
        if (alwaysUpdate) {
            request.setUpdateSnapshots(alwaysUpdate);
        }
        result = maven.execute(request);
        // TODO: log
        exception = null;
        for (Throwable e : result.getExceptions()) {
            if (exception == null) {
                exception = new Exception("build failed: " + e, e);
            } else {
                exception.addSuppressed(e);
            }
        }
        if (exception != null) {
            throw exception;
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

    //-- load poms

    public MavenProject loadPom(FileNode file) throws ProjectBuildingException {
        ProjectBuildingRequest request;
        ProjectBuildingResult result;

        request = new DefaultProjectBuildingRequest();
        request.setRepositorySession(repositorySession);
        request.setRemoteRepositories(remoteLegacy);
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
