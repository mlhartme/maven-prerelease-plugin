package net.oneandone.maven.plugins.prerelease.util;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionRequestPopulator;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.lifecycle.internal.LifecycleStarter;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.project.ProjectSorter;
import org.apache.maven.repository.DelegatingLocalArtifactRepository;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.codehaus.plexus.util.StringUtils;
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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Maven {
    /** @param userProperties properties specified by the user with -D command line args. */
    public static Launcher launcher(FileNode basedir, Properties userProperties) {
        Launcher mvn;

        mvn = new Launcher(basedir, "mvn", "-B", /* same as mvn release plugin: */ "-DperformRelease");
        for (Map.Entry<Object, Object> entry : userProperties.entrySet()) {
            mvn.arg("-D" + entry.getKey() + "=" + entry.getValue());
        }
        return mvn;
    }

    //--

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
            return new Maven(world, new MavenSession(container, session, new DefaultMavenExecutionRequest(), new DefaultMavenExecutionResult()),
                    session, container.lookup(ProjectBuilder.class), Arrays.asList(repository), new DefaultMavenExecutionRequestPopulator(), null);
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
    private final RepositorySystemSession repositorySession;
    private final List<ArtifactRepository> remoteLegacy; // needed to load poms :(
    private final MavenExecutionRequestPopulator populator;

    // TODO: use a project builder that works without legacy classes, esp. without ArtifactRepository ...
    // As far as I know, there's no such project builder in mvn 3.0.2.
    private final ProjectBuilder builder;
    private LifecycleStarter lifecycleStarter;

    public Maven(World world, MavenSession parentSession, RepositorySystemSession repositorySession, ProjectBuilder builder,
                 List<ArtifactRepository> remoteLegacy, MavenExecutionRequestPopulator populator, LifecycleStarter lifecycleStarter) {
        this.world = world;
        this.parentSession = parentSession;
        this.repositorySession = repositorySession;
        this.builder = builder;
        this.remoteLegacy = remoteLegacy;
        this.populator = populator;
        this.lifecycleStarter = lifecycleStarter;
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
        request.setProcessPlugins(true);
        request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        request.setSystemProperties(System.getProperties());
        //If you don't turn this into RepositoryMerging.REQUEST_DOMINANT the dependencies will be resolved against Maven Central
        //and not against the configured repositories. The default of the DefaultProjectBuildingRequest is
        // RepositoryMerging.POM_DOMINANT.
        request.setRepositoryMerging(ProjectBuildingRequest.RepositoryMerging.REQUEST_DOMINANT);
        request.setResolveDependencies(true);
        result = builder.build(file.toPath().toFile(), request);
        return result.getProject();
    }

    //--

    /** collected from Maven's startup code */
    public MavenSession subsession(FileNode basedir, String ... goals) throws Exception {
        MavenExecutionRequest request;
        MavenExecutionResult result;
        MavenSession session;
        ProjectBuildingRequest buildingRequest;
        List<ProjectBuildingResult> buildingResults;

        request = new DefaultMavenExecutionRequest();
        request.setGoals(Arrays.asList(goals));
        populator.populateDefaults(request);
        DelegatingLocalArtifactRepository delegatingLocalArtifactRepository = new DelegatingLocalArtifactRepository( request.getLocalRepository() );
        request.setLocalRepository( delegatingLocalArtifactRepository );
        // TODO: maven has this:
        // request.getProjectBuildingRequest().setRepositorySession(parentSession.getRepositorySession());
        buildingRequest = request.getProjectBuildingRequest();
        buildingResults = builder.build(Arrays.asList(basedir.join("pom.xml").toPath().toFile()), request.isRecursive(), buildingRequest);
        result = new DefaultMavenExecutionResult();
        session = new MavenSession(parentSession.getContainer(), parentSession.getRepositorySession(), request, result);
        if (buildingResults.size() != 1) {
            throw new IllegalStateException();
        }
        session.setProjects(Collections.singletonList(buildingResults.get(0).getProject()));
        // TODO: why
        session.getCurrentProject().setPluginArtifactRepositories(parentSession.getCurrentProject().getRemoteArtifactRepositories());
        // TODO: legacySupport.setSession

        result.setTopologicallySortedProjects(session.getProjects());
        result.setProject(session.getTopLevelProject());

        ProjectSorter projectSorter = new ProjectSorter( session.getProjects() );

        ProjectDependencyGraph projectDependencyGraph = createDependencyGraph( projectSorter, request );
        session.setProjects( projectDependencyGraph.getSortedProjects() );
        session.setProjectDependencyGraph( projectDependencyGraph );

        return session;
    }

    private ProjectDependencyGraph createDependencyGraph( ProjectSorter sorter, MavenExecutionRequest request )
            throws MavenExecutionException
    {
        ProjectDependencyGraph graph = new DefaultProjectDependencyGraph( sorter );

        List<MavenProject> activeProjects = sorter.getSortedProjects();

        activeProjects = trimSelectedProjects( activeProjects, graph, request );
        activeProjects = trimResumedProjects( activeProjects, request );

        if (activeProjects.size() != sorter.getSortedProjects().size()) {
            throw new IllegalStateException();
        }

        return graph;
    }

    private List<MavenProject> trimSelectedProjects( List<MavenProject> projects, ProjectDependencyGraph graph,
                                                     MavenExecutionRequest request )
            throws MavenExecutionException
    {
        List<MavenProject> result = projects;

        if ( !request.getSelectedProjects().isEmpty() )
        {
            File reactorDirectory = null;
            if ( request.getBaseDirectory() != null )
            {
                reactorDirectory = new File( request.getBaseDirectory() );
            }

            Collection<MavenProject> selectedProjects = new LinkedHashSet<MavenProject>( projects.size() );

            for ( String selector : request.getSelectedProjects() )
            {
                MavenProject selectedProject = null;

                for ( MavenProject project : projects )
                {
                    if ( isMatchingProject( project, selector, reactorDirectory ) )
                    {
                        selectedProject = project;
                        break;
                    }
                }

                if ( selectedProject != null )
                {
                    selectedProjects.add( selectedProject );
                }
                else
                {
                    throw new MavenExecutionException( "Could not find the selected project in the reactor: "
                            + selector, request.getPom() );
                }
            }

            boolean makeUpstream = false;
            boolean makeDownstream = false;

            if ( MavenExecutionRequest.REACTOR_MAKE_UPSTREAM.equals( request.getMakeBehavior() ) )
            {
                makeUpstream = true;
            }
            else if ( MavenExecutionRequest.REACTOR_MAKE_DOWNSTREAM.equals( request.getMakeBehavior() ) )
            {
                makeDownstream = true;
            }
            else if ( MavenExecutionRequest.REACTOR_MAKE_BOTH.equals( request.getMakeBehavior() ) )
            {
                makeUpstream = true;
                makeDownstream = true;
            }
            else if ( StringUtils.isNotEmpty(request.getMakeBehavior()) )
            {
                throw new MavenExecutionException( "Invalid reactor make behavior: " + request.getMakeBehavior(),
                        request.getPom() );
            }

            if ( makeUpstream || makeDownstream )
            {
                for ( MavenProject selectedProject : new ArrayList<MavenProject>( selectedProjects ) )
                {
                    if ( makeUpstream )
                    {
                        selectedProjects.addAll( graph.getUpstreamProjects( selectedProject, true ) );
                    }
                    if ( makeDownstream )
                    {
                        selectedProjects.addAll( graph.getDownstreamProjects( selectedProject, true ) );
                    }
                }
            }

            result = new ArrayList<MavenProject>( selectedProjects.size() );

            for ( MavenProject project : projects )
            {
                if ( selectedProjects.contains( project ) )
                {
                    result.add( project );
                }
            }
        }

        return result;
    }

    private List<MavenProject> trimResumedProjects( List<MavenProject> projects, MavenExecutionRequest request )
            throws MavenExecutionException
    {
        List<MavenProject> result = projects;

        if ( StringUtils.isNotEmpty( request.getResumeFrom() ) )
        {
            File reactorDirectory = null;
            if ( request.getBaseDirectory() != null )
            {
                reactorDirectory = new File( request.getBaseDirectory() );
            }

            String selector = request.getResumeFrom();

            result = new ArrayList<MavenProject>( projects.size() );

            boolean resumed = false;

            for ( MavenProject project : projects )
            {
                if ( !resumed && isMatchingProject( project, selector, reactorDirectory ) )
                {
                    resumed = true;
                }

                if ( resumed )
                {
                    result.add( project );
                }
            }

            if ( !resumed )
            {
                throw new MavenExecutionException( "Could not find project to resume reactor build from: " + selector
                        + " vs " + projects, request.getPom() );
            }
        }

        return result;
    }

    private boolean isMatchingProject( MavenProject project, String selector, File reactorDirectory )
    {
        // [groupId]:artifactId
        if ( selector.indexOf( ':' ) >= 0 )
        {
            String id = ':' + project.getArtifactId();

            if ( id.equals( selector ) )
            {
                return true;
            }

            id = project.getGroupId() + id;

            if ( id.equals( selector ) )
            {
                return true;
            }
        }

        // relative path, e.g. "sub", "../sub" or "."
        else if ( reactorDirectory != null )
        {
            File selectedProject = new File( new File( reactorDirectory, selector ).toURI().normalize() );

            if ( selectedProject.isFile() )
            {
                return selectedProject.equals( project.getFile() );
            }
            else if ( selectedProject.isDirectory() )
            {
                return selectedProject.equals( project.getBasedir() );
            }
        }

        return false;
    }


    public void execute(Log log, MavenSession session) throws Exception {
        lifecycleStarter.execute(session);

        if (session.getResult().hasExceptions()) {
            for (Throwable t : session.getResult().getExceptions()) {
                log.debug(t);
                log.error(t.getMessage());
            }
            throw new Exception("build failed");
        }
    }
}
