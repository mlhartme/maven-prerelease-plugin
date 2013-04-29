/* Copyright (c) 1&1. All Rights Reserved. */

package com.oneandone.devel.maven.plugins.prerelease.frischfleisch;

import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.rss.Channel;
import net.oneandone.sushi.rss.Feed;
import net.oneandone.sushi.rss.Item;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import org.apache.maven.plugins.changes.model.Action;
import org.apache.maven.plugins.changes.model.ChangesDocument;
import org.apache.maven.plugins.changes.model.Release;
import org.apache.maven.plugins.changes.model.io.xpp3.ChangesXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import javax.mail.MessagingException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Generator {
    private final Node feeds;
    private final String name;
    private final String url;
    private final String groupId;
    private final String artifactId;
    private final String version;

    public Generator(String name, String url, String groupId, String artifactId, String version, Node feeds) throws IOException {
        this.name = name;
        this.url = url;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.feeds = feeds;
    }

    //-- main methods

    public void run(FileNode basedir, String user, String[] emails)
            throws IOException, MessagingException, FrischfleischException, XmlPullParserException {
        run(basedir, new String[] { feedName(), "Digest.xml" }, user, emails);
    }

    public void run(FileNode basedir, String[] feedNames, String user, String[] emails)
            throws FrischfleischException, IOException, MessagingException, XmlPullParserException {
        Content content;
        String str;
        Date date;
        Node feed;

        if (isEmpty(url)) {
            throw new FrischfleischException("no url in pom.xml");
        }
        content = new Content();
        date = new Date();
        addHeader(content, date, user);
        addChanges(basedir, content);
        str = content.toString();
        // feeds before emails - it's more robust
        if (feeds != null) {
            feed = feeds.join("content", contentName());
            feed.writeString(str);
            feed(feedNames, new Item(name + " " + version, feed.getURI().toString(), null, user, "" + date.getTime(), date));
        }
        if (emails.length > 0) {
            new Mailer().send(user, emails, subject(), str);
        }
    }

    //-- feed handling

    private void feed(String[] feedNames, Item item) throws FrischfleischException {
        Node node;
        Feed feed;
        Channel channel;

        for (String feedName : feedNames) {
            try {
                node = this.feeds.join(feedName);
                if (!node.exists()) {
                    feed = new Feed();
                    feed.channels().add(createChannel());
                } else {
                    feed = Feed.read(node);
                    if (feed.channels().size() != 1) {
                        throw new FrischfleischException("1 channel expected, got " + feed.channels().size());
                    }
                }
                channel = feed.channels().get(0);
                channel.add(item, 25);
                feed.write(node);
            } catch (RuntimeException | FrischfleischException e) {
                throw e;
            } catch (Exception e) {
                throw new FrischfleischException(feedName + ": cannot update feed", e);
            }
        }
    }

    private Channel createChannel() {
        return new Channel(groupId + "." + artifactId, url, "Tracks newly deployed artifacts");
    }

    //-- content creation

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

    private void addHeader(Content content, Date date, String deployer) {
        content.header("GroupId", groupId);
        content.header("ArtifactId", artifactId);
        content.header("Version", version);
        content.header("Date", DATE_FORMAT.format(date));
        content.header("Deployer", deployer);
        content.header("Machine", getMachine());
        content.header("Home", url);
        content.header("Prerelease Version", getVersion());
    }

    private static final Separator SEP = Separator.on('\n').trim();

    private void addChanges(FileNode basedir, Content content) throws IOException, XmlPullParserException {
        File changes;
        ChangesXpp3Reader read;
        ChangesDocument doc;
        Release release;
        String prefix;

        changes = basedir.join("src/changes/changes.xml").toPath().toFile();
        if (!changes.exists()) {
            return;
        }
        read = new ChangesXpp3Reader();
        doc = read.read(new FileInputStream(changes));
        release = lookupRelease(doc, Strings.removeRightOpt(version, "-SNAPSHOT"));
        if (release == null) {
            return;
        }
        content.body("Changes: " + opt(release.getDescription()));
        for (Action action : release.getActions()) {
            prefix = Strings.padLeft(opt(action.getDev()), 10) + "  " + opt(action.getType());
            for (String line : SEP.split(opt(action.getAction()))) {
                content.body(Strings.padRight(prefix, 20) + line);
                prefix = "";
            }
        }
    }

    private static String opt(String str) {
        return str == null ? "" : str;
    }

    private static Release lookupRelease(ChangesDocument doc, String version) {
        for (Release release : doc.getBody().getReleases()) {
            if (version.equals(release.getVersion())) {
                return release;
            }
        }
        return null;
    }

    private static final String NONE = "(none)";

    private String getVersion() {
        Package pkg;

        pkg = getClass().getPackage();
        return pkg.getSpecificationVersion();
    }

    private String getMachine() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    //-- helper code

    private static boolean isEmpty(String str) {
        return str == null || str.trim().length() == 0;
    }

    private String feedName() {
        return groupId + "." + artifactId + ".xml";
    }

    private String contentName() {
        return groupId + "." + artifactId + ".txt";
    }

    private String subject() {
        return "[frischfleisch] " + name + " " + version;
    }
}
