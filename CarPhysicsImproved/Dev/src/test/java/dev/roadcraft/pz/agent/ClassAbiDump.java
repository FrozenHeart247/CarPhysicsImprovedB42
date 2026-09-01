package dev.roadcraft.pz.agent;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

/** Developer-only ABI inventory for clean-room loose-class implementations. */
public final class ClassAbiDump {
    private ClassAbiDump() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 2) {
            throw new IllegalArgumentException("Expected game jar and one or more internal class names");
        }
        try (ZipFile archive = new ZipFile(Path.of(arguments[0]).toFile())) {
            for (String internalName : List.of(arguments).subList(1, arguments.length)) {
                var entry = archive.getEntry(internalName + ".class");
                if (entry == null) {
                    throw new IllegalArgumentException("Missing " + internalName);
                }
                byte[] bytes;
                try (var input = archive.getInputStream(entry)) {
                    bytes = input.readAllBytes();
                }
                ClassModel model = ClassFile.of().parse(bytes);
                System.out.println("CLASS " + internalName + " " + model.flags().flags());
                model.superclass().ifPresent(value -> System.out.println("  SUPER " + value.asInternalName()));
                for (var field : model.fields()) {
                    System.out.println("  FIELD " + field.flags().flags() + " "
                            + field.fieldName().stringValue() + " " + field.fieldType().stringValue());
                }
                for (var method : model.methods()) {
                    System.out.println("  METHOD " + method.flags().flags() + " "
                            + method.methodName().stringValue() + method.methodType().stringValue());
                }
            }
        }
    }
}
