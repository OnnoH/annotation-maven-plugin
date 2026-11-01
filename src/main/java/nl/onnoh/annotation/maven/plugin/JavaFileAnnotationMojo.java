package nl.onnoh.annotation.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Adds imports and type annotations to generated Java sources.
 * <p>
 * The goal walks {@code outputDirectory} recursively, and for every {@code .java} file it inserts the configured
 * imports below the package declaration and the configured annotations above the first type declaration. Imports
 * and annotations that are already present are left alone, so running the goal twice does not duplicate them.
 */
@Mojo(name = "generate-annotations", defaultPhase = LifecyclePhase.PROCESS_SOURCES, threadSafe = true)
public class JavaFileAnnotationMojo extends AbstractMojo {

    private static final String PACKAGE_STRING = "package ";

    /**
     * A top level type declaration: optional access modifier, any number of other modifiers, and the kind of the
     * type. This matches {@code public class}, {@code public final class}, {@code public abstract class},
     * {@code public sealed interface}, {@code public record}, {@code enum} and annotation types.
     */
    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "^(?:(?:public|protected|private)\\s+)?"
                    + "(?:(?:abstract|final|static|strictfp|sealed|non-sealed)\\s+)*"
                    + "(?:class|interface|enum|record|@interface)\\s+\\w.*");

    /**
     * The directory with the Java sources to annotate, searched recursively.
     */
    @Parameter(property = "outputDirectory", defaultValue = "${project.build.directory}/generated-sources")
    private File outputDirectory;

    /**
     * The imports to insert, without the {@code import} keyword and the semicolon, for example
     * {@code com.fasterxml.jackson.annotation.JsonIgnoreProperties}.
     */
    @Parameter(property = "importField")
    private List<String> imports;

    /**
     * The annotations to insert above the first type declaration, written as they should appear in the source, for
     * example {@code @JsonIgnoreProperties(ignoreUnknown = true)}.
     */
    @Parameter(property = "annotationField")
    private List<String> annotations;

    /**
     * Creates the Mojo. Maven instantiates it and injects the parameters.
     */
    public JavaFileAnnotationMojo() {
    }

    @Override
    public void execute() throws MojoExecutionException {
        final List<String> importsToAdd = imports == null ? List.of() : imports;
        final List<String> annotationsToAdd = annotations == null ? List.of() : annotations;

        if (importsToAdd.isEmpty() && annotationsToAdd.isEmpty()) {
            getLog().info("No imports and no annotations configured, nothing to do.");
            return;
        }

        if (outputDirectory == null) {
            throw new MojoExecutionException("The parameter outputDirectory is not configured.");
        }

        if (!outputDirectory.isDirectory()) {
            getLog().info(outputDirectory.getPath() + " does not exist, nothing to do.");
            return;
        }

        getLog().info("Annotating Java source files in directory: " + outputDirectory);

        int annotated = 0;
        try (Stream<Path> paths = Files.walk(outputDirectory.toPath())) {
            final List<Path> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
            for (Path javaFile : javaFiles) {
                if (annotate(javaFile, importsToAdd, annotationsToAdd)) {
                    annotated++;
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to annotate the Java source files in " + outputDirectory, e);
        }

        getLog().info("Appended " + annotated + " classes with annotations");
    }

    /**
     * Inserts the imports and annotations that are missing from one file.
     *
     * @return {@code true} when the file was changed
     */
    private boolean annotate(Path file, List<String> importsToAdd, List<String> annotationsToAdd) throws IOException {
        getLog().debug("Processing file: " + file.getFileName());

        final List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        final int typeLine = indexOfTypeDeclaration(lines);
        if (typeLine < 0) {
            getLog().debug("No type declaration found in " + file.getFileName() + ", skipping it.");
            return false;
        }

        // The annotations go in first: they sit below the package declaration, so they do not move its index.
        boolean changed = insertAnnotations(lines, typeLine, annotationsToAdd);
        changed |= insertImports(lines, importsToAdd);

        if (changed) {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } else {
            getLog().debug(file.getFileName() + " already has the imports and annotations, leaving it alone.");
        }
        return changed;
    }

    private int indexOfTypeDeclaration(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (TYPE_DECLARATION.matcher(lines.get(i)).matches()) {
                return i;
            }
        }
        return -1;
    }

    private boolean insertAnnotations(List<String> lines, int typeLine, List<String> annotationsToAdd) {
        final List<String> present = annotationBlockAbove(lines, typeLine);
        final List<String> missing = annotationsToAdd.stream()
                .filter(annotation -> !present.contains(annotation.strip()))
                .toList();
        if (missing.isEmpty()) {
            return false;
        }
        lines.addAll(typeLine, missing);
        return true;
    }

    /**
     * The annotations that already sit directly above the type declaration, stripped of indentation.
     */
    private List<String> annotationBlockAbove(List<String> lines, int typeLine) {
        final List<String> present = new ArrayList<>();
        for (int i = typeLine - 1; i >= 0 && lines.get(i).strip().startsWith("@"); i--) {
            present.add(lines.get(i).strip());
        }
        return present;
    }

    private boolean insertImports(List<String> lines, List<String> importsToAdd) {
        final int packageLine = indexOfPackageDeclaration(lines);
        if (packageLine < 0) {
            return false;
        }
        final List<String> missing = importsToAdd.stream()
                .map(anImport -> "import " + anImport + ";")
                .filter(statement -> !containsLine(lines, statement))
                .toList();
        if (missing.isEmpty()) {
            return false;
        }
        final List<String> block = new ArrayList<>();
        block.add("");
        block.addAll(missing);
        lines.addAll(packageLine + 1, block);
        return true;
    }

    private int indexOfPackageDeclaration(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(PACKAGE_STRING)) {
                return i;
            }
        }
        return -1;
    }

    private boolean containsLine(List<String> lines, String statement) {
        return lines.stream().anyMatch(line -> line.strip().equals(statement));
    }
}
