package tools;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.nio.file.Path;
import java.util.jar.JarFile;

/** Small build-time helper for inspecting Java 25 class signatures without decompiling code. */
public final class ClassSummary {
    private ClassSummary() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: ClassSummary <jar> <binary-class-name>... | ClassSummary <class> -");
        }

        if (args[1].equals("-")) {
            printModel(args[0], ClassFile.of().parse(Path.of(args[0])));
            return;
        }

        try (JarFile jar = new JarFile(Path.of(args[0]).toFile())) {
            for (int i = 1; i < args.length; i++) {
                String className = args[i];
                String entryName = className.replace('.', '/') + ".class";
                var entry = jar.getJarEntry(entryName);
                if (entry == null) {
                    System.out.println("MISSING " + className);
                    continue;
                }

                byte[] bytes;
                try (var in = jar.getInputStream(entry)) {
                    bytes = in.readAllBytes();
                }

                printModel(className, ClassFile.of().parse(bytes));
            }
        }
    }

    private static void printModel(String className, ClassModel model) {
        System.out.printf("CLASS %s major=%d flags=%s super=%s%n",
                className,
                model.majorVersion(),
                model.flags().flags(),
                model.superclass().map(Object::toString).orElse("<none>"));

        for (FieldModel field : model.fields()) {
            System.out.printf("  FIELD %s %s %s%n",
                    field.flags().flags(), field.fieldName().stringValue(), field.fieldType().stringValue());
        }
        for (MethodModel method : model.methods()) {
            System.out.printf("  METHOD %s %s%s code=%s%n",
                    method.flags().flags(),
                    method.methodName().stringValue(),
                    method.methodType().stringValue(),
                    method.code().isPresent());
        }
    }
}
