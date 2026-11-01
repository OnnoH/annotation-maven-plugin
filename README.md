# annotation-maven-plugin

A Maven plugin that adds imports and type annotations to generated Java sources, for instance to annotate the
classes `xjc` generates without changing the generator. Running it twice does not duplicate anything.

## Usage

```xml
<plugin>
    <groupId>nl.onnoh.annotation.maven.plugin</groupId>
    <artifactId>annotation-plugin</artifactId>
    <version>0.1.0</version>
    <executions>
        <execution>
            <goals>
                <goal>generate-annotations</goal>
            </goals>
            <configuration>
                <outputDirectory>${project.build.directory}/generated-sources/xjc</outputDirectory>
                <imports>
                    <import>com.fasterxml.jackson.annotation.JsonIgnoreProperties</import>
                </imports>
                <annotations>
                    <annotation>@JsonIgnoreProperties(ignoreUnknown = true)</annotation>
                </annotations>
            </configuration>
        </execution>
    </executions>
</plugin>
```

The goal `generate-annotations` runs in the `process-sources` phase by default.

| Parameter | Default | Description |
|---|---|---|
| `outputDirectory` | `${project.build.directory}/generated-sources` | The directory with the Java sources to annotate, searched recursively. A directory that does not exist is skipped. |
| `imports` | none | Imports to insert below the package declaration, without `import` and `;`. |
| `annotations` | none | Annotations to insert above the first type declaration, written as they appear in the source. |

## Build

```shell
mvn -B verify
```

## Release

The board publishes a GitHub release with the tag `v<version>`; the `Release` workflow then publishes that version
to Maven Central.

## License

MIT, see [LICENSE](LICENSE).
