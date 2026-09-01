package tools;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.nio.file.Path;
import java.util.jar.JarFile;

/** Dumps class-file API code elements for selected methods; intended for compatibility audits. */
public final class MethodCode {
    private MethodCode() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: MethodCode <jar-or-class> <binary-class-or-dash> <method> [descriptor]");
        }

        ClassModel model;
        Path source = Path.of(args[0]);
        if (args[1].equals("-")) {
            model = ClassFile.of().parse(source);
        } else {
            try (JarFile jar = new JarFile(source.toFile())) {
                var entry = jar.getJarEntry(args[1].replace('.', '/') + ".class");
                if (entry == null) {
                    throw new IllegalArgumentException("Class not found: " + args[1]);
                }
                try (var in = jar.getInputStream(entry)) {
                    model = ClassFile.of().parse(in.readAllBytes());
                }
            }
        }

        String wantedName = args[2];
        String wantedType = args.length >= 4 ? args[3] : null;
        for (var method : model.methods()) {
            String name = method.methodName().stringValue();
            String type = method.methodType().stringValue();
            if (!name.equals(wantedName) || (wantedType != null && !type.equals(wantedType))) {
                continue;
            }
            System.out.printf("METHOD %s%s %s%n", name, type, method.flags().flags());
            method.code().orElseThrow().forEach(element ->
                    System.out.printf("  %-32s %s%n", element.getClass().getSimpleName(), element));
        }
    }
}
