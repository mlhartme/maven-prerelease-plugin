package com.oneandone.devel.maven;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.repository.metadata.GroupRepositoryMetadata;
import org.apache.maven.artifact.repository.metadata.MetadataBridge;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.sonatype.aether.RepositorySystemSession;
import org.apache.maven.settings.DefaultMavenSettingsBuilder;
import org.apache.maven.settings.MavenSettingsBuilder;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.sonatype.aether.RepositoryException;
import org.sonatype.aether.RepositoryListener;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.artifact.ArtifactType;
import org.sonatype.aether.deployment.DeployRequest;
import org.sonatype.aether.deployment.DeploymentException;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.repository.Authentication;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.ProxySelector;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.repository.RepositoryPolicy;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.VersionRangeRequest;
import org.sonatype.aether.resolution.VersionRangeResolutionException;
import org.sonatype.aether.resolution.VersionRangeResult;
import org.sonatype.aether.resolution.VersionRequest;
import org.sonatype.aether.resolution.VersionResolutionException;
import org.sonatype.aether.resolution.VersionResult;
import org.sonatype.aether.transfer.TransferListener;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.repository.DefaultMirrorSelector;
import org.sonatype.aether.util.repository.DefaultProxySelector;
import org.sonatype.aether.version.Version;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class Maven {
    public static Maven withSettings(World world) throws IOException {
        return withSettings(world, null);
    }

    public static Maven withSettings(World world, String localRepositoryDir) throws IOException {
        return withSettings(world, localRepositoryDir, container());
    }

    public static Maven withSettings(World world, String localRepositoryDir, DefaultPlexusContainer container) throws IOException {
        return withSettings(world, localRepositoryDir, container, null, null);
    }

    /** @param localRepositoryDir to override settings; may be null */
    public static Maven withSettings(World world, String localRepositoryDir, DefaultPlexusContainer container,
                                     TransferListener transferListener, RepositoryListener repositoryListener) throws IOException {
        RepositorySystem system;
        MavenRepositorySystemSession session;
        LocalRepository localRepository;
        Settings settings;
        List<RemoteRepository> remoteRepositories;

        try {
            try {
                settings = loadSettings(world, container);
            } catch (XmlPullParserException e) {
                throw new IOException("cannot load settings: " + e.getMessage(), e);
            }
            if (localRepositoryDir == null) {
                // TODO: who has precedencs: repodir from settings or from MAVEN_OPTS
                localRepositoryDir = settings.getLocalRepository();
                if (localRepositoryDir == null) {
                    localRepositoryDir = localRepositoryPathFromMavenOpts();
                    if (localRepositoryDir == null) {
                        localRepositoryDir = defaultLocalRepositoryDir(world).toPath().toFile().getAbsolutePath();
                    }
                }
            }
            localRepository = new LocalRepository(localRepositoryDir);
            system = container.lookup(RepositorySystem.class);
            session = new MavenRepositorySystemSession();
            session.setOffline(settings.isOffline());
            session.setLocalRepositoryManager(system.newLocalRepositoryManager(localRepository));

            DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector();
            for (Mirror mirror : settings.getMirrors()) {
                mirrorSelector.add(mirror.getId(), mirror.getUrl(), mirror.getLayout(), false, mirror.getMirrorOf(),
                                   mirror.getMirrorOfLayouts());
            }
            session.setMirrorSelector(mirrorSelector);

            DefaultProxySelector proxySelector = new DefaultProxySelector();
            for (Proxy proxy : settings.getProxies()) {
                Authentication proxyAuth = new Authentication(proxy.getUsername(), proxy.getPassword());
                proxySelector.add(new org.sonatype.aether.repository.Proxy(proxy.getProtocol(), proxy.getHost(),
                        proxy.getPort(), proxyAuth), proxy.getNonProxyHosts());
            }
            session.setProxySelector(proxySelector);
            remoteRepositories = remoteRepositories(settings);
            return create(world, container, system, session, remoteRepositories, proxySelector, transferListener, repositoryListener);
        } catch (ComponentLookupException e) {
            throw new IllegalStateException(e);
        }
    }

    //--

    /** distingusishes release and snapshot repos via url substrings */
    public static Maven withUrls(World world, String prefix, List<String> urls) {
        List<RemoteRepository> remote;
        String id;
        RemoteRepository repo;
        boolean releases;
        boolean snapshots;

        remote = new ArrayList<>();
        for (String url : urls) {
            // TODO: ugly ugly ...
            if (url.contains("snapshots")) {
                releases = false;
                snapshots = true;
            } else {
                releases = true;
                snapshots = false;
            }
            id = prefix + (remote.size() + 1);
            repo = new RemoteRepository(id, "default", url);
            // CAUTION: the update policy also applies to metadata, I always have to update release repositories to see new releases
            repo = repo.setPolicy(true, new RepositoryPolicy(snapshots,
                    RepositoryPolicy.UPDATE_POLICY_ALWAYS, RepositoryPolicy.CHECKSUM_POLICY_WARN));
            repo = repo.setPolicy(false, new RepositoryPolicy(releases,
                    RepositoryPolicy.UPDATE_POLICY_ALWAYS, RepositoryPolicy.CHECKSUM_POLICY_WARN));
            remote.add(repo);
        }
        return create(world, container(), null, null, false, remote, null);
    }

    //--

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

    public World getWorld() {
        return world;
    }

    public RepositorySystem getRepositorySystem() {
        return repositorySystem;
    }

    public RepositorySystemSession getRepositorySession() {
        return repositorySession;
    }

    public List<ArtifactRepository> remoteRepositoriesLegacy() {
        return remoteLegacy;
    }

    public List<RemoteRepository> remoteRepositories() {
        return remote;
    }

    public FileNode getLocalRepositoryDir() {
        return world.file(repositorySession.getLocalRepository().getBasedir());
    }

    public FileNode getLocalRepositoryFile(Artifact artifact) {
        return getLocalRepositoryDir().join(repositorySession.getLocalRepositoryManager().getPathForLocalArtifact(artifact));
    }

    public List<FileNode> files(List<Artifact> artifacts) {
        List<FileNode> result;

        result = new ArrayList<FileNode>();
        for (Artifact a : artifacts) {
            result.add(file(a));
        }
        return result;
    }

    public FileNode file(Artifact artifact) {
        return world.file(artifact.getFile());
    }

    //-- resolve

    public FileNode resolve(String groupId, String artifactId, String version) throws ArtifactResolutionException {
        return resolve(groupId, artifactId, "jar", version);
    }

    public FileNode resolve(String groupId, String artifactId, String extension, String version) throws ArtifactResolutionException {
        return resolve(new DefaultArtifact(groupId, artifactId, extension, version));
    }

    public FileNode resolve(String gav) throws ArtifactResolutionException {
        return resolve(new DefaultArtifact(gav));
    }

    public FileNode resolve(Artifact artifact) throws ArtifactResolutionException {
        return resolve(artifact, remote);
    }

    public FileNode resolve(Artifact artifact, List<RemoteRepository> remoteRepositories) throws ArtifactResolutionException {
        ArtifactRequest request;
        ArtifactResult result;

        request = new ArtifactRequest(artifact, remoteRepositories, null);
        result = repositorySystem.resolveArtifact(repositorySession, request);
        if (!result.isResolved()) {
            throw new ArtifactResolutionException(new ArrayList<ArtifactResult>()); // TODO
        }
        return world.file(result.getArtifact().getFile());
    }

    //-- load poms

    public MavenProject loadPom(Artifact artifact) throws ArtifactResolutionException, ProjectBuildingException {
        return loadPom(resolve(new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "pom", artifact.getVersion())), false);
    }

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

    //-- versions

    /** @return Latest version field from metadata.xml of the repository that was last modified. Never null */
    public String latestVersion(Artifact artifact) throws VersionRangeResolutionException, VersionResolutionException {
        Artifact range;
        VersionRangeRequest request;
        VersionRangeResult result;
        List<Version> versions;
        String version;

        // CAUTION: do not use version "LATEST" because the respective field in metadata.xml is not set reliably:
        range = artifact.setVersion("[,]");
        request = new VersionRangeRequest(range, remote, null);
        result = repositorySystem.resolveVersionRange(repositorySession, request);
        versions = result.getVersions();
        if (versions.size() == 0) {
            throw new VersionRangeResolutionException(result, "no version found");
        }
        version = versions.get(versions.size() - 1).toString();
        if (version.endsWith("-SNAPSHOT")) {
            version = latestSnapshot(artifact.setVersion(version));
        }
        return version;
    }

    /** @return a timestamp version if a deploy artifact wins; a SNAPSHOT if a location artifact wins */
    private String latestSnapshot(Artifact artifact) throws VersionResolutionException {
        VersionRequest request;
        VersionResult result;

        request = new VersionRequest(artifact, remote, null);
        result = repositorySystem.resolveVersion(repositorySession, request);
        return result.getVersion();
    }

    public String nextVersion(Artifact artifact) throws RepositoryException {
        if (artifact.isSnapshot()) {
            return latestSnapshot(artifact.setVersion(artifact.getBaseVersion()));
        } else {
            return latestRelease(artifact);
        }
    }

    public String latestRelease(Artifact artifact) throws VersionRangeResolutionException {
        List<Version> versions;
        Version version;

        versions = availableVersions(artifact.setVersion("[" + artifact.getVersion() + ",]"));

        // ranges also return SNAPSHOTS. The elease/compatibility notes say they don't, but the respective bug
        // was re-opened: http://jira.codehaus.org/browse/MNG-3092
        for (int i = versions.size() - 1; i >= 0; i--) {
            version = versions.get(i);
            if (!version.toString().endsWith("SNAPSHOT")) {
                return version.toString();
            }
        }
        return artifact.getVersion();
    }

    public List<Version> availableVersions(String groupId, String artifactId) throws VersionRangeResolutionException {
        return availableVersions(groupId, artifactId, null);
    }

    public List<Version> availableVersions(String groupId, String artifactId, ArtifactType type) throws VersionRangeResolutionException {
        return availableVersions(new DefaultArtifact(groupId, artifactId, null, null, "[0,)", type));
    }

    public List<Version> availableVersions(Artifact artifact) throws VersionRangeResolutionException {
        VersionRangeRequest request;
        VersionRangeResult rangeResult;

        request = new VersionRangeRequest(artifact, remote, null);
        rangeResult = repositorySystem.resolveVersionRange(repositorySession, request);
        return rangeResult.getVersions();
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

    public static Settings loadSettings(World world, DefaultPlexusContainer container)
            throws IOException, XmlPullParserException, ComponentLookupException {
        DefaultMavenSettingsBuilder builder;
        MavenExecutionRequest request;

        builder = (DefaultMavenSettingsBuilder) container.lookup(MavenSettingsBuilder.ROLE);
        request = new DefaultMavenExecutionRequest();
        request.setGlobalSettingsFile(locateMaven(world).join("conf/settings.xml").toPath().toFile());
        request.setUserSettingsFile(((FileNode) world.getHome().join(".m2/settings.xml")).toPath().toFile());
        return builder.buildSettings(request);
    }

    private static String localRepositoryPathFromMavenOpts() {
        String value;

        value = System.getenv("MAVEN_OPTS");
        if (value != null) {
            for (String entry : Separator.SPACE.split(value)) {
                if (entry.startsWith("-Dmaven.repo.local=")) {
                    return entry.substring(entry.indexOf('=') + 1);
                }
            }
        }
        return null;
    }

    private static FileNode locateMaven(World world) throws IOException {
        String home;
        FileNode mvn;

        mvn = which(world, "mvn");
        if (mvn != null) {
            mvn = mvn.getParent().getParent();
            if (mvn.join("conf").isDirectory()) {
                return mvn;
            }
        }

        home = System.getenv("MAVEN_HOME");
        if (home != null) {
            return world.file(home);
        }
        throw new IOException("cannot locate maven");
    }

    // TODO: sushi
    private static FileNode which(World world, String cmd) throws IOException {
        String path;
        FileNode file;

        path = System.getenv("PATH");
        if (path != null) {
            for (String entry : Separator.on(':').trim().split(path)) {
                file = world.file(entry).join(cmd);
                if (file.isFile()) {
                    while (file.isLink()) {
                        file = (FileNode) file.resolveLink();
                    }
                    return file;
                }
            }
        }
        return null;
    }

    private static List<RemoteRepository> remoteRepositories(Settings settings) {
        List<RemoteRepository> result;
        List<String> actives;
        RemoteRepository remote;

        result = new ArrayList<RemoteRepository>();
        actives = settings.getActiveProfiles();
        for (Profile profile : settings.getProfiles()) {
            if (actives.contains(profile.getId())) { // TODO: proxy config?
                for (org.apache.maven.settings.Repository configured : profile.getRepositories()) {
                    remote = new RemoteRepository(configured.getId(), "default", configured.getUrl());
                    remote = setPolicy(remote, true, configured.getSnapshots());
                    remote = setPolicy(remote, false, configured.getReleases());
                    result.add(remote);
                }
            }
        }
        return result;
    }

    private static RemoteRepository setPolicy(RemoteRepository remote, boolean snapshots,
                                              org.apache.maven.settings.RepositoryPolicy policy) {
        if (policy == null) {
            return remote;
        }
        return remote.setPolicy(snapshots, new RepositoryPolicy(policy.isEnabled(), policy.getUpdatePolicy(), policy.getChecksumPolicy()));
    }

    //--

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
