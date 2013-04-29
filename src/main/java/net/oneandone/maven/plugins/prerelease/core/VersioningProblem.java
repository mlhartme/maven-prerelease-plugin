package net.oneandone.maven.plugins.prerelease.core;

import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.util.List;

public class VersioningProblem extends Exception {
    public final List<String> problems;

    public VersioningProblem(List<String> problems) {
        super("version problems:\n"
                + Strings.indent(Separator.RAW_LINE.join(problems), "  "));
        this.problems = problems;
    }
}
