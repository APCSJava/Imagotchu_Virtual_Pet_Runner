import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** Describes a compiled pet class and provides read-only access to its fields. */
final class PetClass implements AutoCloseable {
    final Class<?> type;
    final Method tick;
    final List<Constructor<?>> constructors = new ArrayList<>();
    final List<Method> actions = new ArrayList<>();
    private final List<Field> fields = new ArrayList<>();
    private final URLClassLoader loader;

    static PetClass load(Path file) throws IOException, ReflectiveOperationException {
        if (!Files.isRegularFile(file) || !file.getFileName().toString().endsWith(".class")) {
            throw new IllegalArgumentException("Select a compiled .class file.");
        }
        String fileName = file.getFileName().toString();
        String className = fileName.substring(0, fileName.length() - ".class".length());
        URL folder = file.toAbsolutePath().getParent().toUri().toURL();
        // Use a fresh loader without the app classpath so the selected file wins.
        URLClassLoader loader = new URLClassLoader(new URL[] {folder}, null);
        try {
            return new PetClass(Class.forName(className, false, loader), loader);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            try {
                loader.close();
            } catch (IOException closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
    }

    private PetClass(Class<?> type, URLClassLoader loader) throws NoSuchMethodException {
        this.type = type;
        this.loader = loader;
        if (!Modifier.isPublic(type.getModifiers()) || Modifier.isAbstract(type.getModifiers())
                || type.getEnclosingClass() != null || type.isEnum()) {
            throw new IllegalArgumentException("Choose a public, concrete, top-level pet class.");
        }
        tick = type.getDeclaredMethod("tick");
        if (!Modifier.isPublic(tick.getModifiers()) || Modifier.isStatic(tick.getModifiers())
                || tick.getReturnType() != void.class) {
            throw new IllegalArgumentException("The pet must declare public void tick().");
        }

        constructors.addAll(Arrays.asList(type.getConstructors()));
        constructors.sort(Comparator.comparingInt((Constructor<?> c) -> c.getParameterCount())
                .thenComparing(PetClass::signature));
        if (constructors.stream().noneMatch(PetClass::supportsArguments)) {
            throw new IllegalArgumentException(
                    "Provide a public constructor with no arguments or with int, double, "
                    + "boolean, or String arguments.");
        }

        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                field.setAccessible(true);
                fields.add(field);
            }
        }
        fields.sort(Comparator.comparing(Field::getName));

        for (Method method : type.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)
                    && !method.isSynthetic() && !method.equals(tick) && !isPropertyGetter(method)) {
                actions.add(method);
            }
        }
        actions.sort(Comparator.comparing(PetClass::signature));
    }

    private boolean isPropertyGetter(Method method) {
        if (method.getParameterCount() != 0 || method.getReturnType() == void.class) {
            return false;
        }
        for (Field field : fields) {
            String name = field.getName();
            String property = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            if (method.getName().equals("get" + property)) {
                return true;
            }
            if (method.getName().equals("is" + property)
                    && (method.getReturnType() == boolean.class
                    || method.getReturnType() == Boolean.class)) {
                return true;
            }
        }
        return false;
    }

    Map<String, String> snapshot(Object pet) throws IllegalAccessException {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (Field field : fields) {
            snapshot.put(field.getName(), displayValue(field.get(pet)));
        }
        return snapshot;
    }

    static String displayValue(Object value) {
        if (!(value instanceof String)) {
            return String.valueOf(value);
        }
        String escaped = ((String) value).replace("\\", "\\\\")
                .replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    static String signature(Executable executable) {
        StringJoiner arguments = new StringJoiner(", ");
        for (Parameter parameter : executable.getParameters()) {
            String label = parameter.getType().getSimpleName();
            if (parameter.isNamePresent()) {
                label += " " + parameter.getName();
            }
            arguments.add(label);
        }
        String name = executable instanceof Constructor<?>
                ? executable.getDeclaringClass().getSimpleName() : executable.getName();
        return name + "(" + arguments + ")";
    }

    static boolean supportsArguments(Executable executable) {
        for (Class<?> type : executable.getParameterTypes()) {
            if (type != int.class && type != double.class && type != boolean.class
                    && type != String.class) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() throws IOException {
        loader.close();
    }
}
