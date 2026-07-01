/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package io.github.oculix.build;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Maven core extension that injects shared {@code <dependencyManagement>}
 * entries into every project of the reactor at model-load time.
 *
 * <p>Solves the case where OculiX modules don't use parent pom inheritance
 * (each child pom is standalone for independent publishing) yet still need
 * coordinated transitive version pins for security CVEs.
 *
 * <p>Without this extension, fixing a CVE in a transitive dependency would
 * require either:
 * <ul>
 *   <li>Adding {@code <parent>} reference to every child pom (loses module
 *       autonomy for independent publishing)</li>
 *   <li>Duplicating the same {@code <dependencyManagement>} block in every
 *       child pom (4+ copies to maintain in sync)</li>
 * </ul>
 *
 * <p>This extension is the "DevOps centralization" answer: a single source
 * of truth in this Java file, applied uniformly to every module's effective
 * model. Updates here propagate everywhere on the next mvn invocation.
 *
 * <p>Refresh policy: bump entries here whenever Scorecard / OWASP
 * Dependency-Check flags a new transitive CVE. The fix lands in one place.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
@Named("oculix-dependency-management-injector")
@Singleton
public class DependencyManagementInjector extends AbstractMavenLifecycleParticipant {

  private static final Logger LOG = LoggerFactory.getLogger("oculix");

  /**
   * Forced transitive version pins. Mostly to kill CVEs in old versions
   * pulled by jython-slim 2.7.4 (Netty, Bouncy Castle) which OculiX cannot
   * avoid since jython is its core scripting engine and has no newer release.
   *
   * <p>Each entry lists groupId, artifactId, version. The reason is the
   * GHSA(s) the pin addresses — useful when revisiting whether the pin
   * is still required after upstream releases.
   */
  private static final Pin[] PINS = new Pin[] {
      // Bouncy Castle 1.84 — transitive from jython-slim 2.7.4.
      // Kills: GHSA-p93r-85wp-75v3 (high, covert timing channel),
      //        GHSA-c3fc-8qff-9hwx (LDAP injection),
      //        GHSA-wg6q-6289-32hp (broken crypto algorithm),
      //        GHSA-4cx2-fc23-5wg6 (excessive allocation).
      new Pin("org.bouncycastle", "bcprov-jdk18on", "1.84"),
      new Pin("org.bouncycastle", "bcpkix-jdk18on", "1.84"),
      new Pin("org.bouncycastle", "bcutil-jdk18on", "1.84"),

      // Netty 4.1.133.Final — transitive from jython-slim 2.7.4.
      // Kills: GHSA-mj4r-2hfc-f8p6 (high, Lz4FrameDecoder DoS),
      //        GHSA-3p8m-j85q-pgmj (zip bomb DoS),
      //        GHSA-389x-839f-4rhx, GHSA-xq3w-v528-46rv (windows DoS),
      //        GHSA-6mjq-h674-j845 (SniHandler 16MB allocation).
      new Pin("io.netty", "netty-buffer",    "4.1.133.Final"),
      new Pin("io.netty", "netty-codec",     "4.1.133.Final"),
      new Pin("io.netty", "netty-common",    "4.1.133.Final"),
      new Pin("io.netty", "netty-handler",   "4.1.133.Final"),
      new Pin("io.netty", "netty-resolver",  "4.1.133.Final"),
      new Pin("io.netty", "netty-transport", "4.1.133.Final"),

      // plexus-utils 3.6.1 — transitive from maven-core in build-extensions.
      // Kills: GHSA-6fmv-xxpf-w3cw (high, directory traversal in extractFile).
      new Pin("org.codehaus.plexus", "plexus-utils", "3.6.1"),
  };

  @Override
  public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
    int totalAdded = 0;
    int projectsTouched = 0;
    for (MavenProject project : session.getProjects()) {
      int added = inject(project);
      if (added > 0) {
        totalAdded += added;
        projectsTouched++;
      }
    }
    if (totalAdded > 0) {
      LOG.info("[oculix-dm-injector] pinned {} transitive versions across {} module(s)",
          totalAdded, projectsTouched);
    }
  }

  private int inject(MavenProject project) {
    Model model = project.getModel();
    if (model == null) {
      return 0;
    }
    DependencyManagement dm = model.getDependencyManagement();
    if (dm == null) {
      dm = new DependencyManagement();
      model.setDependencyManagement(dm);
    }
    List<Dependency> deps = dm.getDependencies();
    if (deps == null) {
      deps = new ArrayList<>();
      dm.setDependencies(deps);
    }
    int added = 0;
    for (Pin pin : PINS) {
      if (!alreadyManaged(deps, pin.groupId, pin.artifactId)) {
        Dependency d = new Dependency();
        d.setGroupId(pin.groupId);
        d.setArtifactId(pin.artifactId);
        d.setVersion(pin.version);
        deps.add(d);
        added++;
      }
    }
    return added;
  }

  private static boolean alreadyManaged(List<Dependency> deps, String groupId, String artifactId) {
    for (Dependency d : deps) {
      if (groupId.equals(d.getGroupId()) && artifactId.equals(d.getArtifactId())) {
        return true;
      }
    }
    return false;
  }

  /** Immutable groupId+artifactId+version triple. */
  private static final class Pin {
    final String groupId;
    final String artifactId;
    final String version;

    Pin(String groupId, String artifactId, String version) {
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.version = version;
    }
  }
}
