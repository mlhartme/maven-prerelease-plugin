package net.oneandone.maven.plugins.prerelease.change;

import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.xml.Selector;
import net.oneandone.sushi.xml.XmlException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.namespace.NamespaceContext;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

/** Represents the changes.xml file. */

public class File {
    private static final String NAMESPACE_URI = "http://maven.apache.org/changes/1.0.0";
    public static final String PATH = "src/changes/changes.xml";

    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat MONTH_FORMAT = new SimpleDateFormat("yyyy-MM");

    public static File load(FileNode basedir) throws IOException, SAXException {
        FileNode dest;

        dest = basedir.join(PATH);
        return new File(dest, dest.readXml());
    }

    private final FileNode dest;
    private final Document doc;
    private final Selector selector;

    public File(FileNode dest, Document doc) {
        this.dest = dest;
        this.doc = doc;
        this.selector = dest.getWorld().getXml().getSelector();
        this.selector.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                if ("a".equals(prefix)) {
                    return NAMESPACE_URI;
                }
                return null;
            }

            @Override
            public String getPrefix(String namespaceURI) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Iterator getPrefixes(String namespaceURI) {
                throw new UnsupportedOperationException();
            }
        });
    }

    public void addAction(String version, Date date, String type, String message, String username) throws XmlException {
        Element firstRelease;
        Element release;
        Element action;
        String str;

        firstRelease = selector.element(doc, "/a:document/a:body/a:release[1]");
        str = firstRelease.getAttribute("version");
        if (version.equals(str)) {
            release = firstRelease;
        } else {
            release = doc.createElementNS(NAMESPACE_URI, "release");
            release.setAttribute("version", version);
            firstRelease.getParentNode().insertBefore(release, firstRelease);
        }
        action = doc.createElementNS(NAMESPACE_URI, "action");
        action.setAttribute("dev", username);
        action.setAttribute("type", type);
        action.setAttribute("date", FORMAT.format(date));
        action.setTextContent("\n        " + message + "\n      ");
        release.insertBefore(action, selector.elementOpt(release, "a:action[1]"));
    }

    public String getDescription(String version) throws XmlException {
        Element release;

        release = releaseOpt(version);
        if (release == null) {
            throw new ArgumentException("no such version: " + version);
        }
        return release.getAttribute("description");
    }

    public void setDescription(String version, String message) throws XmlException {
        Element release;
        Element body;

        release = releaseOpt(version);
        if (release == null) {
            release = doc.createElementNS(NAMESPACE_URI, "release");
            release.setAttribute("version", version);
            body = selector.element(doc, "/a:document/a:body");
            body.insertBefore(release, body.getFirstChild());
        }
        if (message == null) {
            release.removeAttribute("description");
        } else {
            release.setAttribute("description", message);
        }
    }

    public boolean releaseDate(String version, Date date) throws XmlException {
        Element release;

        release = releaseOpt(version);
        if (release == null) {
            return false;
        } else {
            release.setAttribute("date", FORMAT.format(date));
            return true;
        }
    }

    public int actions(String version) throws XmlException {
        Element release;

        release = releaseOpt(version);
        return release == null ? 0 : selector.elements(release, "a:action").size();
    }

    private Element releaseOpt(String version) throws XmlException {
        return selector.elementOpt(doc, "/a:document/a:body/a:release[@version='" + version + "']");
    }

    public void save() throws IOException {
        dest.writeXml(doc);
    }
}
