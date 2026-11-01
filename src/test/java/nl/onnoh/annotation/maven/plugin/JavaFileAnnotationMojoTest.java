package nl.onnoh.annotation.maven.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JavaFileAnnotationMojoTest {

    private static final List<String> IMPORTS = List.of(
            "com.fasterxml.jackson.annotation.JsonIgnoreProperties",
            "com.fasterxml.jackson.annotation.JsonInclude");
    private static final List<String> ANNOTATIONS = List.of(
            "@JsonIgnoreProperties(ignoreUnknown = true)",
            "@JsonInclude(JsonInclude.Include.NON_NULL)");

    @TempDir
    Path sources;

    @Tag("AC-5")
    @DisplayName("AC-5: a public class gets the configured imports and annotations")
    @Test
    void annotatesAPublicClass() throws Exception {
        Path file = write("Order.java", """
                package nl.onnoh.generated;

                public class Order {
                }
                """);

        run(mojo(sources.toFile(), IMPORTS, ANNOTATIONS));

        assertThat(lines(file)).containsExactly(
                "package nl.onnoh.generated;",
                "",
                "import com.fasterxml.jackson.annotation.JsonIgnoreProperties;",
                "import com.fasterxml.jackson.annotation.JsonInclude;",
                "",
                "@JsonIgnoreProperties(ignoreUnknown = true)",
                "@JsonInclude(JsonInclude.Include.NON_NULL)",
                "public class Order {",
                "}");
    }

    @Tag("AC-5")
    @DisplayName("AC-5: every kind of top level type gets the imports and annotations")
    @ParameterizedTest
    @ValueSource(strings = {
            "public class Order {\n}",
            "public final class Order {\n}",
            "public abstract class Order {\n}",
            "public sealed interface Order permits Order.One {\n}",
            "public interface Order {\n}",
            "public record Order(String id) {\n}",
            "public enum Order {\n    ONE\n}",
            "enum Order {\n    ONE\n}",
            "public @interface Order {\n}"
    })
    void annotatesEveryKindOfTopLevelType(String declaration) throws Exception {
        Path file = write("Order.java", "package nl.onnoh.generated;\n\n" + declaration + "\n");

        run(mojo(sources.toFile(), IMPORTS, ANNOTATIONS));

        assertThat(lines(file)).contains(
                "import com.fasterxml.jackson.annotation.JsonIgnoreProperties;",
                "@JsonIgnoreProperties(ignoreUnknown = true)",
                "@JsonInclude(JsonInclude.Include.NON_NULL)");
        assertThat(lines(file)).containsSubsequence(
                "@JsonInclude(JsonInclude.Include.NON_NULL)",
                declaration.lines().findFirst().orElseThrow());
    }

    @Tag("AC-7")
    @DisplayName("AC-7: the imports keep the configured order")
    @Test
    void insertsTheImportsInTheConfiguredOrder() throws Exception {
        Path file = write("Order.java", "package nl.onnoh.generated;\n\npublic class Order {\n}\n");

        run(mojo(sources.toFile(), IMPORTS, ANNOTATIONS));

        assertThat(lines(file)).containsSubsequence(
                "import com.fasterxml.jackson.annotation.JsonIgnoreProperties;",
                "import com.fasterxml.jackson.annotation.JsonInclude;");
    }

    @Tag("AC-5")
    @DisplayName("AC-5: a file without a type declaration is left alone")
    @Test
    void leavesAFileWithoutATypeDeclarationAlone() throws Exception {
        Path file = write("package-info.java", """
                /**
                 * Generated sources.
                 */
                package nl.onnoh.generated;
                """);
        String before = Files.readString(file, StandardCharsets.UTF_8);

        run(mojo(sources.toFile(), IMPORTS, ANNOTATIONS));

        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(before);
    }

    @Tag("AC-8")
    @DisplayName("AC-8: a second run adds no duplicates")
    @Test
    void isIdempotentOnASecondRun() throws Exception {
        Path file = write("Order.java", "package nl.onnoh.generated;\n\npublic class Order {\n}\n");

        run(mojo(sources.toFile(), IMPORTS, ANNOTATIONS));
        String afterFirstRun = Files.readString(file, StandardCharsets.UTF_8);
        run(mojo(sources.toFile(), IMPORTS, ANNOTATIONS));

        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(afterFirstRun);
    }

    @Tag("AC-6")
    @DisplayName("AC-6: files in subpackages are annotated too")
    @Test
    void annotatesFilesInSubPackages() throws Exception {
        Path file = write("sub/nested/Order.java", "package nl.onnoh.generated.sub.nested;\n\npublic class Order {\n}\n");

        run(mojo(sources.toFile(), IMPORTS, ANNOTATIONS));

        assertThat(lines(file)).contains(
                "import com.fasterxml.jackson.annotation.JsonIgnoreProperties;",
                "@JsonIgnoreProperties(ignoreUnknown = true)");
    }

    @Tag("AC-3")
    @DisplayName("AC-3: unset imports and annotations do not throw")
    @Test
    void doesNothingWhenImportsAndAnnotationsAreNotConfigured() throws Exception {
        Path file = write("Order.java", "package nl.onnoh.generated;\n\npublic class Order {\n}\n");
        String before = Files.readString(file, StandardCharsets.UTF_8);

        run(mojo(sources.toFile(), null, null));

        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(before);
    }

    @Tag("AC-3")
    @DisplayName("AC-3: unset imports alone do not throw")
    @Test
    void addsOnlyTheAnnotationsWhenNoImportsAreConfigured() throws Exception {
        Path file = write("Order.java", "package nl.onnoh.generated;\n\npublic class Order {\n}\n");

        run(mojo(sources.toFile(), null, ANNOTATIONS));

        assertThat(lines(file)).containsExactly(
                "package nl.onnoh.generated;",
                "",
                "@JsonIgnoreProperties(ignoreUnknown = true)",
                "@JsonInclude(JsonInclude.Include.NON_NULL)",
                "public class Order {",
                "}");
    }

    @Tag("AC-2")
    @DisplayName("AC-2: a missing directory is reported, not fatal")
    @Test
    void doesNotFailOnAMissingDirectory() throws Exception {
        JavaFileAnnotationMojo mojo = mojo(sources.resolve("absent").toFile(), IMPORTS, ANNOTATIONS);

        run(mojo);
    }

    @Tag("AC-4")
    @DisplayName("AC-4: no outputDirectory at all fails the build")
    @Test
    void failsWhenTheOutputDirectoryIsNotConfigured() throws Exception {
        JavaFileAnnotationMojo mojo = mojo(null, IMPORTS, ANNOTATIONS);

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("outputDirectory");
    }

    @Tag("AC-2")
    @DisplayName("AC-2: an IO failure fails the build with MojoExecutionException")
    @Test
    void failsWhenAFileCannotBeRead() throws Exception {
        Path file = write("Order.java", "package nl.onnoh.generated;\n\npublic class Order {\n}\n");
        assertThat(file.toFile().setReadable(false)).isTrue();
        assumeTrue(!Files.isReadable(file), "the test user can read the file regardless of its permissions");
        JavaFileAnnotationMojo mojo = mojo(sources.toFile(), IMPORTS, ANNOTATIONS);

        try {
            assertThatThrownBy(mojo::execute)
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessageContaining("Failed to annotate")
                    .hasCauseInstanceOf(IOException.class);
        } finally {
            assertThat(file.toFile().setReadable(true)).isTrue();
        }
    }

    private void run(JavaFileAnnotationMojo mojo) throws MojoExecutionException {
        mojo.execute();
    }

    private JavaFileAnnotationMojo mojo(File outputDirectory, List<String> imports, List<String> annotations)
            throws Exception {
        JavaFileAnnotationMojo mojo = new JavaFileAnnotationMojo();
        set(mojo, "outputDirectory", outputDirectory);
        set(mojo, "imports", imports);
        set(mojo, "annotations", annotations);
        return mojo;
    }

    private void set(JavaFileAnnotationMojo mojo, String name, Object value) throws Exception {
        Field field = JavaFileAnnotationMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(mojo, value);
    }

    private Path write(String name, String content) throws IOException {
        Path file = sources.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private List<String> lines(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }
}
