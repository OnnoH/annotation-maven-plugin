// AC-4: the goal ran with outputDirectory omitted, so it must have annotated the file under the default directory.
File annotated = new File(basedir, 'target/generated-sources/nl/onnoh/it/Order.java')
assert annotated.isFile() : 'AC-4: nothing was written to target/generated-sources'

assert annotated.readLines() == [
        'package nl.onnoh.it;',
        '',
        'import javax.annotation.processing.Generated;',
        '',
        '@Generated("annotation-maven-plugin")',
        'public class Order {',
        '}'] : 'AC-4: unexpected result:\n' + annotated.text
