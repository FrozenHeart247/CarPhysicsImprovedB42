package dev.roadcraft.pz.agent;

import java.lang.classfile.ClassFile;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipFile;

/** Developer-only classfile instruction dump for selected clean-room ABI methods. */
public final class ClassMethodDump {
    private ClassMethodDump() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 3) {
            throw new IllegalArgumentException("Expected game jar, internal class name and method names");
        }
        Set<String> requested = java.util.Arrays.stream(arguments)
                .skip(2)
                .collect(java.util.stream.Collectors.toSet());
        try (ZipFile archive = new ZipFile(Path.of(arguments[0]).toFile())) {
            var entry = archive.getEntry(arguments[1] + ".class");
            byte[] bytes;
            try (var input = archive.getInputStream(entry)) {
                bytes = input.readAllBytes();
            }
            var model = ClassFile.of().parse(bytes);
            for (var method : model.methods()) {
                if (!requested.contains(method.methodName().stringValue())) {
                    continue;
                }
                System.out.println("METHOD " + method.methodName().stringValue()
                        + method.methodType().stringValue());
                method.code().orElseThrow().elementStream().forEach(element -> System.out.println("  " + element));
            }
        }
    }
}
