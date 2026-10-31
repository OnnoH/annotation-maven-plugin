package nl.onnoh.annotation.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Mojo(name = "generate-annotations", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class JavaFileAnnotationMojo extends AbstractMojo {

    final String PACKAGE_STRING = "package";
    final String CLASS_STRING = "public class";
    final String ENUM_STRING = "public enum";

    @Parameter(property = "outputDirectory")
    private File outputDirectory;

    @Parameter(property = "importField")
    private List<String> imports;


    @Parameter(property = "annotationField")
    private List<String> annotations;

    public void execute() {
        final File directory = outputDirectory;

        if (!directory.exists()) {
            getLog().info(directory.getPath() + " does not exists.");
            return;
        }

        getLog().info("Annotating java class files in directory: " + outputDirectory);

        AtomicInteger nrOfAppendedClasses = new AtomicInteger();
        Arrays.stream(Objects.requireNonNull(directory.listFiles())).forEach(file -> {
            System.out.println("Processing file: " + file.getName());
            try {
                List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).startsWith(PACKAGE_STRING)) {
                        System.out.println("Package: " + lines.get(i));
                        int finalI = i + 1;
                        lines.add(finalI, "");
                        imports.forEach(im -> lines.add(finalI + 1, "import " + im + ";"));
                    } else if (lines.get(i).startsWith(CLASS_STRING) || lines.get(i).startsWith(ENUM_STRING)) {
                        int finalI = i;
                        System.out.println("Class: " + lines.get(i));
                        annotations.forEach(a -> lines.add(finalI, a));
                        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
                        nrOfAppendedClasses.getAndIncrement();
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        getLog().info("Appended " + nrOfAppendedClasses + " classes with annotations");
    }
}