package tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.FieldVisitor;
import net.bytebuddy.jar.asm.Handle;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;

/**
 * Small read-only utility used to compare Project Zomboid class shapes without
 * loading them. This matters when the inspected class is newer than the JDK
 * running the tool.
 */
public final class ClassInspector {
    private static final Map<Integer, String> OPCODE_NAMES = opcodeNames();

    private ClassInspector() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("usage: ClassInspector <class-file|jar!entry> [method-name]");
        }

        byte[] bytes = readClass(args[0]);
        String selectedMethod = args.length == 2 ? args[1] : null;
        ClassReader reader = new ClassReader(bytes);
        reader.accept(new InspectionVisitor(selectedMethod), ClassReader.SKIP_FRAMES);
    }

    private static byte[] readClass(String spec) throws IOException {
        int separator = spec.indexOf('!');
        if (separator < 0) {
            return Files.readAllBytes(Path.of(spec));
        }

        Path jarPath = Path.of(spec.substring(0, separator));
        String entryName = spec.substring(separator + 1).replace('\\', '/');
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entry = jar.getJarEntry(entryName);
            if (entry == null) {
                throw new IOException("JAR entry not found: " + entryName);
            }
            try (var input = jar.getInputStream(entry)) {
                return input.readAllBytes();
            }
        }
    }

    private static Map<Integer, String> opcodeNames() {
        Map<Integer, List<String>> candidates = new HashMap<>();
        for (var field : Opcodes.class.getFields()) {
            try {
                if (field.getType() == int.class) {
                    int value = field.getInt(null);
                    String name = field.getName();
                    if (value >= 0 && value <= 255 && isInstructionName(name)) {
                        candidates.computeIfAbsent(value, ignored -> new ArrayList<>()).add(name);
                    }
                }
            } catch (IllegalAccessException ignored) {
                // Public constants should always be accessible.
            }
        }

        Map<Integer, String> result = new HashMap<>();
        for (var entry : candidates.entrySet()) {
            entry.getValue().sort(Comparator.comparingInt(String::length).thenComparing(String::compareTo));
            result.put(entry.getKey(), entry.getValue().get(0));
        }
        return result;
    }

    private static boolean isInstructionName(String name) {
        return !(name.startsWith("ACC_")
                || name.startsWith("ASM")
                || name.startsWith("V1_")
                || name.startsWith("H_")
                || name.startsWith("F_")
                || name.startsWith("T_")
                || name.startsWith("SOURCE_")
                || name.startsWith("COMPUTE_"));
    }

    private static String opcode(int value) {
        return OPCODE_NAMES.getOrDefault(value, "OP_" + value);
    }

    private static final class InspectionVisitor extends ClassVisitor {
        private final String selectedMethod;

        private InspectionVisitor(String selectedMethod) {
            super(Opcodes.ASM9);
            this.selectedMethod = selectedMethod;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            System.out.printf("CLASS version=%d access=%d name=%s super=%s interfaces=%s%n",
                    version, access, name, superName, String.join(",", interfaces));
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            System.out.printf("FIELD access=%d name=%s desc=%s value=%s%n", access, name, descriptor, value);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                String[] exceptions) {
            System.out.printf("METHOD access=%d name=%s desc=%s%n", access, name, descriptor);
            if (selectedMethod == null || !selectedMethod.equals(name)) {
                return null;
            }
            return new InstructionVisitor(name, descriptor);
        }
    }

    private static final class InstructionVisitor extends MethodVisitor {
        private final Map<Label, String> labels = new LinkedHashMap<>();
        private int nextLabel;

        private InstructionVisitor(String method, String descriptor) {
            super(Opcodes.ASM9);
            System.out.printf("CODE_BEGIN %s%s%n", method, descriptor);
        }

        private String label(Label label) {
            return labels.computeIfAbsent(label, ignored -> "L" + nextLabel++);
        }

        @Override
        public void visitLabel(Label label) {
            System.out.println(label(label) + ":");
        }

        @Override
        public void visitInsn(int opcode) {
            System.out.printf("  %s%n", opcode(opcode));
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            System.out.printf("  %s %d%n", opcode(opcode), operand);
        }

        @Override
        public void visitVarInsn(int opcode, int variable) {
            System.out.printf("  %s v%d%n", opcode(opcode), variable);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            System.out.printf("  %s %s%n", opcode(opcode), type);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            System.out.printf("  %s %s.%s %s%n", opcode(opcode), owner, name, descriptor);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            System.out.printf("  %s %s.%s%s%n", opcode(opcode), owner, name, descriptor);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle,
                Object... bootstrapMethodArguments) {
            System.out.printf("  INVOKEDYNAMIC %s%s bootstrap=%s%n", name, descriptor, bootstrapMethodHandle);
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            System.out.printf("  %s %s%n", opcode(opcode), label(label));
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof Type type) {
                System.out.printf("  LDC type:%s%n", type.getDescriptor());
            } else {
                System.out.printf("  LDC %s:%s%n", value == null ? "null" : value.getClass().getSimpleName(), value);
            }
        }

        @Override
        public void visitIincInsn(int variable, int increment) {
            System.out.printf("  IINC v%d %d%n", variable, increment);
        }

        @Override
        public void visitTableSwitchInsn(int minimum, int maximum, Label defaultLabel, Label... switchLabels) {
            System.out.printf("  TABLESWITCH %d..%d default=%s%n", minimum, maximum, label(defaultLabel));
        }

        @Override
        public void visitLookupSwitchInsn(Label defaultLabel, int[] keys, Label[] switchLabels) {
            System.out.printf("  LOOKUPSWITCH count=%d default=%s%n", keys.length, label(defaultLabel));
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int dimensions) {
            System.out.printf("  MULTIANEWARRAY %s %d%n", descriptor, dimensions);
        }

        @Override
        public void visitEnd() {
            System.out.println("CODE_END");
        }
    }
}
