package net.oneandone.maven.plugins.prerelease.maven;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.repository.metadata.GroupRepositoryMetadata;
import org.apache.maven.artifact.repository.metadata.MetadataBridge;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.sonatype.aether.RepositorySystemSession;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.Logger;
import org.sonatype.aether.RepositoryListener;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.deployment.DeployRequest;
import org.sonatype.aether.deployment.DeploymentException;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.repository.Authentication;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.ProxySelector;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.repository.RepositoryPolicy;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.transfer.TransferListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class Maven {
    public static Maven withDefaults(World world) {
        return withDefaults(world, false);
    }

    public static Maven withDefaults(World world, boolean offline) {
        return create(world, container(), null, null, offline, Repositories.STANDARD_READ, null);
    }

    //--

    public static Maven create(World world, DefaultPlexusContainer container,
                               TransferListener transferListener, RepositoryListener repositoryListener,
                               boolean offline, List<RemoteRepository> remoteRepositories, ProxySelector proxySelector) {
        RepositorySystem system;
        MavenRepositorySystemSession session;
        LocalRepository localRepository;

        try {
            localRepository = new LocalRepository(defaultLocalRepositoryDir(world).getAbsolute());
            system = container.lookup(RepositorySystem.class);
            session = new MavenRepositorySystemSession();
            session.setOffline(offline);
            session.setLocalRepositoryManager(system.newLocalRepositoryManager(localRepository));
            session.setProxySelector(proxySelector);
            return create(world, container, system, session, remoteRepositories, proxySelector, transferListener, repositoryListener);
        } catch (ComponentLookupException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Maven create(World world, DefaultPlexusContainer container, RepositorySystem system,
                               MavenRepositorySystemSession session, List<RemoteRepository> remoteRepositories, ProxySelector proxySelector,
                               TransferListener transferListener, RepositoryListener repositoryListener) throws ComponentLookupException {
        RemoteRepository repository;

        if (transferListener != null) {
            session.setTransferListener(transferListener);
        }
        if (repositoryListener != null) {
            session.setRepositoryListener(repositoryListener);
        }
        if (proxySelector != null) {
            for (int i = 0, max = remoteRepositories.size(); i < max; i++) {
                repository = remoteRepositories.get(i);
                repository = repository.setProxy(proxySelector.getProxy(repository));
                remoteRepositories.set(i, repository);
            }
        }
        return new Maven(world, system, session, container.lookup(ProjectBuilder.class), remoteRepositories, convert(remoteRepositories));
    }

    //--

    public static FileNode defaultLocalRepositoryDir(World world) {
        return world.file(new File(System.getProperty("user.home"))).join(".m2/repository");
    }

    public static DefaultPlexusContainer container() {
        return container(null, null, Logger.LEVEL_DISABLED);
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
    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession repositorySession;
    private final List<RemoteRepository> remote;
    private final List<ArtifactRepository> remoteLegacy; // needed to load poms :(

    // TODO: use a project builder that works without legacy classes, esp. without ArtifactRepository ...
    // As far as I know, there's no such project builder as of mvn 3.0.2.
    private final ProjectBuilder builder;

    public Maven(World world, RepositorySystem repositorySystem, RepositorySystemSession repositorySession, ProjectBuilder builder,
                 List<RemoteRepository> remote, List<ArtifactRepository> remoteLegacy) {
        this.world = world;
        this.repositorySystem = repositorySystem;
        this.repositorySession = repositorySession;
        this.builder = builder;
        this.remote = remote;
        this.remoteLegacy = remoteLegacy;
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
        try {
            return loadPom(file, false);
        } catch (ArtifactResolutionException e) {
            throw new IllegalStateException(e);
        }
    }

    public MavenProject loadPom(FileNode file, boolean resolve) throws ArtifactResolutionException, ProjectBuildingException {
        return loadPom(file, resolve, null, null, null);
    }

    public MavenProject loadPom(FileNode file, boolean resolve, Properties userProperties, List<String> profiles,
                                List<Dependency> dependencies) throws ArtifactResolutionException, ProjectBuildingException {
        ProjectBuildingRequest request;
        ProjectBuildingResult result;

        request = new DefaultProjectBuildingRequest();
        request.setRepositorySession(repositorySession);
        request.setRemoteRepositories(remoteLegacy);
        request.setProcessPlugins(false);
        request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        request.setSystemProperties(System.getProperties());
        if (userProperties != null) {
            request.setUserProperties(userProperties);
        }
        //If you don't turn this into RepositoryMerging.REQUEST_DOMINANT the dependencies will be resolved against Maven Central
        //and not against the configured repositories. The default of the DefaultProjectBuildingRequest is
        // RepositoryMerging.POM_DOMINANT.
        request.setRepositoryMerging(ProjectBuildingRequest.RepositoryMerging.REQUEST_DOMINANT);
        request.setResolveDependencies(resolve);
        if (profiles != null) {
            request.setActiveProfileIds(profiles);
        }
        result = builder.build(file.toPath().toFile(), request);
        if (dependencies != null) {
            if (!resolve) {
                throw new IllegalArgumentException();
            }
            dependencies.addAll(result.getDependencyResolutionResult().getDependencies());
        }
        return result.getProject();
    }

    //-- deploy

    /** convenience method */
    public void deploy(RemoteRepository target, Artifact ... artifacts) throws DeploymentException {
        deploy(target, null, Arrays.asList(artifacts));
    }

    /** convenience method */
    public void deploy(RemoteRepository target, String pluginName, Artifact ... artifacts) throws DeploymentException {
        deploy(target, pluginName, Arrays.asList(artifacts));
    }

    /**
     * You'll usually pass one jar artifact and the corresponding pom artifact.
     * @param pluginName null if you deploy normal artifacts; none-null for Maven Plugins, that you wish to add a plugin mapping for;
     *                   specifies the plugin name in this case.  */
    public void deploy(RemoteRepository target, String pluginName, List<Artifact> artifacts) throws DeploymentException {
        DeployRequest request;
        GroupRepositoryMetadata gm;
        String prefix;

        request = new DeployRequest();
        for (Artifact artifact : artifacts) {
            if (artifact.getFile() == null) {
                throw new IllegalArgumentException(artifact.toString() + " without file");
            }
            request.addArtifact(artifact);
            if (pluginName != null) {
                gm = new GroupRepositoryMetadata(artifact.getGroupId());
                prefix = getGoalPrefixFromArtifactId(artifact.getArtifactId());
                gm.addPluginMapping(prefix, artifact.getArtifactId(), pluginName);
                request.addMetadata(new MetadataBridge(gm));
            }
        }
        request.setRepository(target);
        repositorySystem.deploy(repositorySession, request);
    }

    /** from PluginDescriptor */
    public static String getGoalPrefixFromArtifactId(String artifactId) {
        if ("maven-plugin-plugin".equals(artifactId)) {
            return "plugin";
        } else {
            return artifactId.replaceAll("-?maven-?", "").replaceAll("-?plugin-?", "");
        }
    }

    //-- utils

    private static List<ArtifactRepository> convert(List<RemoteRepository> remoteRepositories) {
        List<ArtifactRepository> result;

        result = new ArrayList<ArtifactRepository>(remoteRepositories.size());
        for (RemoteRepository repository : remoteRepositories) {
            result.add(convert(repository));
        }
        return result;
    }

    private static ArtifactRepository convert(RemoteRepository repository) {
        RepositoryPolicy sp;
        RepositoryPolicy rp;
        ArtifactRepository result;

        sp = repository.getPolicy(true);
        rp = repository.getPolicy(false);
        result = new DefaultArtifactRepository(repository.getId(), repository.getUrl(), new DefaultRepositoryLayout(),
                new ArtifactRepositoryPolicy(sp.isEnabled(), sp.getUpdatePolicy(), sp.getChecksumPolicy()),
                new ArtifactRepositoryPolicy(rp.isEnabled(), rp.getUpdatePolicy(), rp.getChecksumPolicy())
            );
        result.setProxy(convert(repository.getProxy()));
        return result;
    }

    private static org.apache.maven.repository.Proxy convert(org.sonatype.aether.repository.Proxy proxy) {
        org.apache.maven.repository.Proxy result;
        Authentication auth;

        if (proxy == null) {
            return null;
        }
        result = new org.apache.maven.repository.Proxy();
        auth = proxy.getAuthentication();
        if (auth != null) {
            if (auth.getPrivateKeyFile() != null) {
                throw new UnsupportedOperationException(auth.getPrivateKeyFile());
            }
            if (auth.getPassphrase() != null) {
                throw new UnsupportedOperationException(auth.getPassphrase());
            }
            result.setUserName(auth.getUsername());
            result.setPassword(auth.getPassword());
        }
        result.setHost(proxy.getHost());
        result.setPort(proxy.getPort());
        result.setProtocol(proxy.getType());
        return result;
    }
}
