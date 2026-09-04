package dev.carphysicsimproved.v1.runtime;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

/** Parses shipped Lua with the Kahlua compiler bundled by the installed game. */
public final class LuaSyntaxSmokeTest {
    private LuaSyntaxSmokeTest() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 0) {
            throw new IllegalArgumentException("At least one Lua file is required");
        }
        Class<?> compiler = Class.forName("se.krka.kahlua.luaj.compiler.LuaCompiler");
        Method loadStream = findLoadStream(compiler);
        loadStream.setAccessible(true);
        for (String argument : arguments) {
            Path file = Path.of(argument).toAbsolutePath().normalize();
            try (InputStream input = Files.newInputStream(file)) {
                try {
                    loadStream.invoke(null, input, file.toString(), null);
                } catch (InvocationTargetException error) {
                    throw new AssertionError("Kahlua rejected " + file, error.getCause());
                }
            }
        }
        System.out.println("LuaSyntaxSmokeTest: installed-game Kahlua accepted "
                + arguments.length + " shipped Lua files");
    }

    private static Method findLoadStream(Class<?> compiler) throws NoSuchMethodException {
        for (Method method : compiler.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers())
                    && method.getName().equals("loadis")
                    && parameters.length == 3
                    && InputStream.class.isAssignableFrom(parameters[0])
                    && parameters[1] == String.class) {
                return method;
            }
        }
        throw new NoSuchMethodException(compiler.getName() + ".loadis(InputStream, String, environment)");
    }
}
