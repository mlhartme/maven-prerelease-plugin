package net.oneandone.maven.plugins.prerelease.change;

import com.oneandone.devel.devreg.model.User;
import com.oneandone.devel.maven.Maven;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.xml.XmlException;
import org.apache.maven.project.MavenProject;

public class Unfreeze extends Change {
    public Unfreeze(Console console, User user, Maven maven, MavenProject project) {
        super(console, user, maven, project);
    }

    @Override
    public String change(File file) throws XmlException {
        String description;

        description = file.getDescription(releaseVersion());
        if (description == null || !description.startsWith("FREEZE")) {
            throw new ArgumentException("not frozen");
        }
        file.setDescription(releaseVersion(), null);
        return "freeze finished";
    }
}
