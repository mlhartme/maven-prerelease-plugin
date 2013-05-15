package net.oneandone.maven.plugins.prerelease.util;

import net.oneandone.maven.plugins.prerelease.core.Prerelease;
import net.oneandone.maven.plugins.prerelease.core.StateRestoreListener;
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
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.DefaultLifecycles;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.lifecycle.LifecycleNotFoundException;
import org.apache.maven.lifecycle.LifecyclePhaseNotFoundException;
import org.apache.maven.lifecycle.MavenExecutionPlan;
import org.apache.maven.lifecycle.internal.BuilderCommon;
import org.apache.maven.lifecycle.internal.ExecutionPlanItem;
import org.apache.maven.lifecycle.internal.LifecycleModuleBuilder;
import org.apache.maven.lifecycle.internal.ReactorContext;
import org.apache.maven.lifecycle.internal.TaskSegment;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.MojoNotFoundException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginNotFoundException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.prefix.NoPluginFoundForPrefixException;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.Logger;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.RepositoryPolicy;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

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
            return new Maven(world, null, null, session, container.lookup(ProjectBuilder.class), Arrays.asList(repository));
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
    private final MavenSession parentSession;
    private final ExecutionListener executionListener;
    private final RepositorySystemSession repositorySession;
    private final List<ArtifactRepository> remoteLegacy; // needed to load poms :(

    // TODO: use a project builder that works without legacy classes, esp. without ArtifactRepository ...
    // As far as I know, there's no such project builder in mvn 3.0.2.
    private final ProjectBuilder builder;

    public Maven(World world, MavenSession parentSession, ExecutionListener executionListener,
                 RepositorySystemSession repositorySession, ProjectBuilder builder, List<ArtifactRepository> remoteLegacy) {
        this.world = world;
        this.parentSession = parentSession;
        this.executionListener = executionListener;
        this.repositorySession = repositorySession;
        this.builder = builder;
        this.remoteLegacy = remoteLegacy;
    }

    public ExecutionListener getExecutionListener() {
        return executionListener;
    }

    public void build(FileNode basedir, String ... goals) throws Exception {
        build(basedir, executionListener, false, goals);
    }

    /**
     * Creates an DefaultMaven instance, initializes it form parentRequest (in Maven, this is done by MavenCli - also by
     * loading settings).
     */
    public void build(FileNode basedir, ExecutionListener theExecutionListener, final boolean filter, String ... goals) throws Exception {
        MavenExecutionRequest parentRequest;
        org.apache.maven.Maven maven;
        MavenExecutionRequest request;
        MavenExecutionResult result;
        Exception exception;

        parentRequest = parentSession.getRequest();
        maven = parentSession.getContainer().lookup(org.apache.maven.Maven.class);
        request = new DefaultMavenExecutionRequest();
        request.setLoggingLevel(parentRequest.getLoggingLevel());
        request.setUserSettingsFile(parentRequest.getUserSettingsFile());
        request.setGlobalSettingsFile(parentRequest.getGlobalSettingsFile());
        request.setUserToolchainsFile(parentRequest.getUserToolchainsFile());
        request.setShowErrors(parentRequest.isShowErrors());

        request.setOffline(parentRequest.isOffline());
        request.setInteractiveMode(parentRequest.isInteractiveMode());
        request.setPluginGroups(parentRequest.getPluginGroups());
        request.setLocalRepository(parentRequest.getLocalRepository());

        for (Server server : parentRequest.getServers()) {
            server = server.clone();
            request.addServer( server );
        }
        for (Proxy proxy : parentRequest.getProxies()) {
            if (proxy.isActive()) {
                request.addProxy(proxy.clone());
            }
        }

        for (Mirror mirror : parentRequest.getMirrors()) {
            request.addMirror(mirror.clone());
        }

        request.setActiveProfiles(parentRequest.getActiveProfiles());
        for ( org.apache.maven.model.Profile profile : parentRequest.getProfiles() ) {
            request.addProfile(profile.clone());
        }

        request.setPom(basedir.join("pom.xml").toPath().toFile());
        request.setGoals(Arrays.asList(goals));
        request.setBaseDirectory(basedir.toPath().toFile());
        request.setSystemProperties(parentRequest.getSystemProperties());
        request.setUserProperties(parentRequest.getUserProperties());
        request.setExecutionListener(theExecutionListener);
        request.setUpdateSnapshots(parentRequest.isUpdateSnapshots());

        LifecycleModuleBuilder b = parentSession.getContainer().lookup(LifecycleModuleBuilder.class);
        Field f = b.getClass().getDeclaredField("builderCommon");
        f.setAccessible(true);
        final BuilderCommon bc = (BuilderCommon) f.get(b);
        f.set(b, new BuilderCommon() {
            public MavenExecutionPlan resolveBuildPlan(MavenSession session, MavenProject project, TaskSegment taskSegment,
                                                       Set<org.apache.maven.artifact.Artifact> projectArtifacts)
                    throws PluginNotFoundException, PluginResolutionException, LifecyclePhaseNotFoundException,
                    PluginDescriptorParsingException, MojoNotFoundException, InvalidPluginDescriptorException,
                    NoPluginFoundForPrefixException, LifecycleNotFoundException, PluginVersionResolutionException,
                    LifecycleExecutionException {
                final MavenExecutionPlan result;
                result = bc.resolveBuildPlan(session, project, taskSegment, projectArtifacts);
                return filter? filtered(result) : result;
            }

            public void handleBuildError(final ReactorContext buildContext, final MavenSession rootSession,
                                         final MavenProject mavenProject, Exception e, final long buildStartTime) {
                bc.handleBuildError(buildContext, rootSession, mavenProject, e, buildStartTime);
            }
        });

        try {
            result = maven.execute(request);
        } finally {
            f.set(b, bc);
        }

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

    public void deployOnly(Prerelease prerelease, MavenProjectHelper projectHelper) throws Exception {
        build(prerelease.checkout, new StateRestoreListener(prerelease, projectHelper, executionListener), true, "deploy");

        /* TODO
        mandatories = Separator.COMMA.split(mandatory);
        MavenExecutionPlan executionPlan =
                builderCommon.resolveBuildPlan(session, project, new TaskSegment(false, new LifecycleTask("deploy")), new HashSet<org.apache.maven.artifact.Artifact>());
        mandatoryExecutions = new ArrayList<>();
        optionalExecutions = new ArrayList<>();
        for (MojoExecution obj : executionPlan.getMojoExecutions()) {
            if ("deploy".equals(obj.getLifecyclePhase())) {
                if (mandatories.contains(obj.getPlugin().getArtifactId())) {
                    log.info("mandatory: " + obj.getPlugin().getArtifactId() + ":" + obj.getGoal());
                    mandatoryExecutions.add(obj);
                } else {
                    log.info("optional " + obj.getPlugin().getArtifactId() + ":" + obj.getGoal());
                    optionalExecutions.add(obj);
                }
            } else {
                log.info("unknown: " + obj.getPlugin().getArtifactId() + ":" + obj.getGoal());
            }
        }*/

        /* TODO
        try {
            mojoExecutor.execute(session, optionalExecutions, index);
        } catch (Exception e) {
            log.warn("Promote succeeded: your artifacts have been deployed, and the svn tag was created. ");
            log.warn("However, optional promote goals failed with this exception: ");
            log.warn(e);
            log.warn("Thus, you can use your release, but someone experienced should have a look.");
        }*/
    }

    private MavenExecutionPlan filtered(MavenExecutionPlan base) {
        List<ExecutionPlanItem> lst;

        lst = new ArrayList<>();
        for (ExecutionPlanItem item : base) {
            if ("deploy".equals(item.getLifecyclePhase())) {
                lst.add(item);
                System.out.println("addint " + item);
            }
        }
        try {
            return new MavenExecutionPlan(lst, parentSession.getContainer().lookup(DefaultLifecycles.class));
        } catch (ComponentLookupException e) {
            throw new IllegalStateException(e);
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
