package net.oneandone.maven.plugins.prerelease.core;

import net.oneandone.maven.plugins.prerelease.Plugin;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class ScheduleTest {
    @Test
    public void empty() {
        Properties properties;
        Schedule orig;

        properties = new Properties();
        orig = new Schedule();
        orig.save(properties, "");
        assertEquals(orig, Schedule.load(properties, ""));
    }

    @Test
    public void normal() {
        Properties properties;
        Schedule orig;

        properties = new Properties();
        orig = new Schedule();
        orig.add(new Plugin("group", "artifact", "goal", Plugin.Phase.BEFORE_PROMOTE, "skip"));
        orig.add(new Plugin("group2", "artifact2", "goal2", Plugin.Phase.AFTER_PROMOTE, "skip2"));
        orig.save(properties, "");
        assertEquals(orig, Schedule.load(properties, ""));
    }
}
