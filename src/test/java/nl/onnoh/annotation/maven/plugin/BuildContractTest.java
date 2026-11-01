package nl.onnoh.annotation.maven.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The acceptance criteria of ONN-118 that are about the build, the POM and the workflows rather than about the
 * behaviour of the goal. They are checked by reading the files in the repository, so they fail in the {@code test}
 * phase like any other test.
 */
class BuildContractTest {

    private static final Path BASE_DIR = Path.of(System.getProperty("basedir", "."));

    @Test
    @Tag("AC-1")
    @DisplayName("AC-1: maven-core is in provided scope, so it is not bundled")
    void declaresMavenCoreAsProvided() throws Exception {
        assertThat(scopeOf("maven-core")).isEqualTo("provided");
        assertThat(scopeOf("maven-plugin-api")).isEqualTo("provided");
        assertThat(scopeOf("maven-plugin-annotations")).isEqualTo("provided");
    }

    @Test
    @Tag("AC-2")
    @DisplayName("AC-2: the sources log through getLog(), not System.out or printStackTrace")
    void logsThroughTheMojoLog() throws IOException {
        try (Stream<Path> sources = Files.walk(BASE_DIR.resolve("src/main/java"))) {
            List<String> offenders = new ArrayList<>();
            for (Path source : sources.filter(Files::isRegularFile).toList()) {
                String text = Files.readString(source, StandardCharsets.UTF_8);
                if (text.contains("System.out") || text.contains("System.err") || text.contains("printStackTrace()")) {
                    offenders.add(source.toString());
                }
            }
            assertThat(offenders).isEmpty();
        }
    }

    /**
     * The declared side of AC-4: the default that ends up in the plugin descriptor. That the default is also
     * applied is checked by running the goal in a real Maven build, in {@code src/it/ac4-default-output-directory}.
     */
    @Test
    @Tag("AC-4")
    @DisplayName("AC-4: the plugin descriptor declares the generated-sources default")
    void defaultsTheOutputDirectory() throws Exception {
        Path descriptor = BASE_DIR.resolve("target/classes/META-INF/maven/plugin.xml");
        assertThat(descriptor).as("the generated plugin descriptor").exists();

        assertThat(Files.readString(descriptor, StandardCharsets.UTF_8)).contains(
                "<outputDirectory implementation=\"java.io.File\""
                        + " default-value=\"${project.build.directory}/generated-sources\">");
    }

    @Test
    @Tag("AC-9")
    @DisplayName("AC-9: ci.yml runs mvn -B verify on pull requests and pushes to main")
    void continuousIntegrationBuildsEveryChange() throws IOException {
        String ci = read(".github/workflows/ci.yml");

        assertThat(ci).contains("pull_request:");
        assertThat(ci).contains("branches: [ main ]");
        assertThat(ci).contains("run: mvn -B verify");
    }

    @Test
    @Tag("AC-10")
    @DisplayName("AC-10: release.yml deploys from the tag of a published release")
    void releaseWorkflowPublishesFromTheTag() throws IOException {
        String release = read(".github/workflows/release.yml");

        assertThat(release).contains("release:");
        assertThat(release).contains("types: [ published ]");
        assertThat(release).contains("^v[0-9]+\\.[0-9]+\\.[0-9]+");
        assertThat(release).contains("versions:set -DnewVersion=\"${TAG#v}\"");
        assertThat(release).contains("run: mvn -B -Prelease deploy");
        assertThat(release).contains("server-id: central");
        assertThat(release).contains("gpg-private-key: ${{ secrets.GPG_PRIVATE_KEY }}");
        assertThat(release).contains("CENTRAL_USERNAME: ${{ secrets.CENTRAL_USERNAME }}");
        assertThat(release).contains("CENTRAL_TOKEN: ${{ secrets.CENTRAL_TOKEN }}");
        assertThat(release).contains("MAVEN_GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}");
    }

    /**
     * The declared side of AC-11. That the jars are really built and complete is checked by the CI step
     * "AC-11: the release build produced the plugin, sources and javadoc jars", which runs after the release build.
     */
    @Test
    @Tag("AC-11")
    @DisplayName("AC-11: the release profile and the CI gate for the Central artifacts are in place")
    void releaseProfileBuildsTheCentralArtifacts() throws Exception {
        List<String> releasePlugins = releaseProfilePluginArtifactIds();

        assertThat(releasePlugins).contains(
                "maven-source-plugin",
                "maven-javadoc-plugin",
                "maven-gpg-plugin",
                "central-publishing-maven-plugin");

        String ci = read(".github/workflows/ci.yml");
        assertThat(ci).contains("run: mvn -B -Prelease -Dgpg.skip verify");
        assertThat(ci).contains("AC-11: the release build produced the plugin, sources and javadoc jars");
        assertThat(ci).contains("-sources.jar");
        assertThat(ci).contains("-javadoc.jar");
    }

    private String read(String path) throws IOException {
        return Files.readString(BASE_DIR.resolve(path), StandardCharsets.UTF_8);
    }

    private String scopeOf(String artifactId) throws Exception {
        for (Element dependency : elements(pom().getElementsByTagName("dependency"))) {
            if (artifactId.equals(childText(dependency, "artifactId"))) {
                return childText(dependency, "scope");
            }
        }
        throw new AssertionError("the POM does not declare " + artifactId);
    }

    private List<String> releaseProfilePluginArtifactIds() throws Exception {
        for (Element profile : elements(pom().getElementsByTagName("profile"))) {
            if (!"release".equals(childText(profile, "id"))) {
                continue;
            }
            List<String> artifactIds = new ArrayList<>();
            for (Element plugin : elements(profile.getElementsByTagName("plugin"))) {
                artifactIds.add(childText(plugin, "artifactId"));
            }
            return artifactIds;
        }
        throw new AssertionError("the POM has no release profile");
    }

    private Document pom() throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(BASE_DIR.resolve("pom.xml").toFile());
    }

    private List<Element> elements(NodeList nodes) {
        List<Element> elements = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            elements.add((Element) nodes.item(i));
        }
        return elements;
    }

    private String childText(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && name.equals(child.getNodeName())) {
                return child.getTextContent().strip();
            }
        }
        return null;
    }
}
