package dev.roadcraft.pz.agent;

import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipFile;

/** Developer-only inventory of external binary references to a class family. */
public final class ClassReferenceDump {
    private ClassReferenceDump() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected game jar and internal owner prefix");
        }
        String prefix = arguments[1];
        Set<String> references = new TreeSet<>();
        try (ZipFile archive = new ZipFile(Path.of(arguments[0]).toFile())) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class") || entry.getName().startsWith(prefix)) {
                    continue;
                }
                byte[] bytes;
                try (var input = archive.getInputStream(entry)) {
                    bytes = input.readAllBytes();
                }
                var model = ClassFile.of().parse(bytes);
                for (var method : model.methods()) {
                    method.code().ifPresent(code -> code.elementStream().forEach(element -> {
                        if (element instanceof InvokeInstruction invoke
                                && invoke.owner().asInternalName().startsWith(prefix)) {
                            references.add("METHOD " + invoke.owner().asInternalName() + "."
                                    + invoke.name().stringValue() + invoke.type().stringValue()
                                    + " <- " + model.thisClass().asInternalName() + "."
                                    + method.methodName().stringValue() + method.methodType().stringValue());
                        } else if (element instanceof FieldInstruction field
                                && field.owner().asInternalName().startsWith(prefix)) {
                            references.add("FIELD " + field.opcode() + " "
                                    + field.owner().asInternalName() + "."
                                    + field.name().stringValue() + ":" + field.type().stringValue()
                                    + " <- " + model.thisClass().asInternalName() + "."
                                    + method.methodName().stringValue() + method.methodType().stringValue());
                        }
                    }));
                }
            }
        }
        references.forEach(System.out::println);
    }
}
