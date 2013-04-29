package com.oneandone.devel.maven.plugins.prerelease.core;

import com.oneandone.devel.maven.plugins.prerelease.Plugin;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import org.apache.maven.plugin.logging.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/*
 * Extra configuration for plugins that need to run only on promoting builds.
 *
 * We need configuration on a per-plugin basis, because we need to invoke individual plugins before/after promoting.
 * Profiles are not an option because you cannot disable execution of all other plugins.
 */
public class Schedule {
    public static Schedule load(Properties properties, String prefix) {
        Schedule schedule;
        String groupId;

        schedule = new Schedule();
        for (int no = 1; true; no++) {
            groupId = properties.getProperty(prefix + no + ".groupId");
            if (groupId == null) {
                return schedule;
            }
            schedule.plugins.add(new Plugin(
                    groupId,
                    get(properties, prefix + no + ".artifactId"),
                    get(properties, prefix + no + ".goal"),
                    Plugin.Phase.valueOf(get(properties, prefix + no + ".phase")),
                    get(properties, prefix + no + ".skip")));
        }
    }

    private static String get(Properties properties, String key) {
        String result;

        result = properties.getProperty(key);
        if (result == null) {
            throw new IllegalStateException("missing property: " + key);
        }
        return result;
    }

    //--

    private final List<Plugin> plugins;

    public Schedule() {
        this(new ArrayList<Plugin>());
    }

    public Schedule(List<Plugin> plugins) {
        this.plugins = plugins;
    }

    public void add(Plugin plugin) {
        plugins.add(plugin);
    }

    public List<String> nonePromotingProperties() {
        List<String> result;

        result = new ArrayList<>();
        for (Plugin plugin : plugins) {
            result.add("-D" + plugin.skip);
        }
        return result;
    }

    public List<String> promotingProperties() {
        List<String> result;

        result = new ArrayList<>();
        for (Plugin plugin : plugins) {
            if (plugin.phase == Plugin.Phase.AFTER_PROMOTE) {
                result.add("-D" + plugin.skip);
            }
        }
        return result;
    }

    public void beforePromote(Log log, FileNode workingCopy, Properties userProperties) throws Failure {
        mvn(log, workingCopy, Plugin.Phase.BEFORE_PROMOTE, userProperties);
    }

    public void afterPromote(Log log, FileNode workingCopy, Properties userProperties) throws Failure {
        mvn(log, workingCopy, Plugin.Phase.AFTER_PROMOTE, userProperties);
    }

    public void mvn(Log log, FileNode workingCopy, Plugin.Phase phase, Properties userProperties) throws Failure {
        List<String> goals;
        Launcher mvn;

        goals = new ArrayList<>();
        for (Plugin plugin : plugins) {
            if (plugin.phase == phase) {
                goals.add(plugin.groupId + ":" + plugin.artifactId + ":" + plugin.goal);
            }
        }
        if (!goals.isEmpty()) {
            mvn = com.oneandone.devel.maven.plugins.prerelease.util.Maven.mvn(workingCopy, userProperties);
            mvn.args(goals);
            log.info("executing " + phase  + " goals: " + mvn.toString());
            log.info(mvn.exec());
        }
    }

    public void save(Properties properties, String prefix) {
        int no;

        no = 1;
        for (Plugin plugin : plugins) {
            properties.setProperty(prefix + no + ".artifactId", plugin.artifactId);
            properties.setProperty(prefix + no + ".groupId", plugin.groupId);
            properties.setProperty(prefix + no + ".goal", plugin.goal);
            properties.setProperty(prefix + no + ".phase", plugin.phase.toString());
            properties.setProperty(prefix + no + ".skip", plugin.skip);
            no++;
        }
    }

    public boolean equals(Object obj) {
        Schedule schedule;

        if (obj instanceof Schedule) {
            schedule = (Schedule) obj;
            return plugins.equals(schedule.plugins);
        }
        return false;
    }

    public int hashCode() {
        return plugins.hashCode();
    }
}
