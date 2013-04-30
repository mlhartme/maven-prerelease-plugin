package net.oneandone.maven.plugins.prerelease.change;

import com.oneandone.devel.devreg.model.User;
import com.oneandone.devel.maven.Maven;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.cli.Value;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import net.oneandone.sushi.xml.Dom;
import net.oneandone.sushi.xml.Selector;
import net.oneandone.sushi.xml.XmlException;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Site;
import org.apache.maven.project.MavenProject;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.namespace.NamespaceContext;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Iterator;

public class Dependency extends Change {
    @Value(name = "artifact", position = 1)
    private String artifact;

    @Value(name = "version", position = 2)
    private String version;

    private final Selector selector;

    public Dependency(Console console, User user, Maven maven, MavenProject project) {
        super(console, user, maven, project);
        selector = new Selector();
        selector.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                if (!prefix.equals("M")) {
                    throw new IllegalStateException();
                }
                return "http://maven.apache.org/POM/4.0.0";
            }

            @Override
            public String getPrefix(String namespaceURI) {
                throw new IllegalStateException();
            }

            @Override
            public Iterator getPrefixes(String namespaceURI) {
                throw new IllegalStateException();
            }
        });
    }

    @Override
    public String change(File file) throws Exception {
        org.apache.maven.model.Dependency dep;
        FileNode pom;
        Document document;
        String old;
        String message;
        String changelog;
        MavenProject depPom;

        dep = lookupDependency(artifact);
        if (dep == null) {
            throw new ArgumentException("dependency not found");
        }
        console.verbose.println("artifact: " + dep);
        pom = console.world.file(project.getFile());
        document = pom.readXml();
        old = setVersion(document, dep.getGroupId(), dep.getArtifactId());
        if (version.equals(old)) {
            console.info.println("no changes");
            return null;
        }
        pom.writeXml(document, false).appendString("\n");
        depPom = maven.loadPom(new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(), dep.getType(), version));
        message = "Update " + depPom.getName() + " " + old + " to " + version;
        changelog = getSiteChangelog(depPom);
        if (changelog != null) {
            message = message + "\n(see " + changelog + " for details)";
        }
        file.addAction(releaseVersion(), now, "update", message, user.getLogin());
        return message;
    }

    public org.apache.maven.model.Dependency lookupDependency(String artifactId) {
        for (org.apache.maven.model.Dependency dependency: project.getDependencies()) {
            if (dependency.getArtifactId().equals(artifactId)) {
                return dependency;
            }
        }
        return null;
    }

    //--

    /** @return previous version */
    private String setVersion(Document document, String groupId, String artifactId) throws XmlException {
        Element v;
        String old;
        boolean found;

        found = false;
        old = null;
        for (Element dependency: selector.elements(document, "/M:project/M:dependencies/M:dependency")) {
            if (groupId.equals(Dom.getString(selector.element(dependency, "M:groupId")))) {
                if (artifactId.equals(Dom.getString(selector.element(dependency, "M:artifactId")))) {
                    if (found) {
                        throw new ArgumentException("duplicate dependency");
                    }
                    v = selector.element(dependency, "version");
                    old = Dom.getString(v);
                    v.setTextContent(version);
                    found = true;
                }
            }
        }
        if (!found) {
            throw new IllegalStateException();
        }
        return old;
    }

    //--

    private String getSiteChangelog(MavenProject pom) throws IOException, URISyntaxException {
        String url;
        DistributionManagement dm;
        Site site;

        dm = pom.getDistributionManagement();
        if (dm == null) {
            return null;
        }
        site = dm.getSite();
        if (site == null) {
            return null;
        }
        url = site.getUrl();
        if (url == null) {
            return null;
        }
        url = Strings.removeLeftOpt(url, "dav:");
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        url = url + "changes-report.html";
        try {
            console.world.node(url).readBytes();
        } catch (IOException e) {
            console.error.println("No changelog found: " + url);
            return null;
        }
        url = url + "#a" + pom.getVersion();
        return url;
    }
}
