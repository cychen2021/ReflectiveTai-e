package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.TypeSystem;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReflectiveAnnotation {
    public enum Kind {
        Method, Field, Class
    }
    private final Kind kind;

    private final Collection<Object> entries;
    private ReflectiveAnnotation(Kind kind, Collection<Object> entries) {
        this.kind = kind;
        this.entries = entries;
    }

    public Kind getKind() {
        return kind;
    }

    public Collection<JClass> getClasses() {
        if (kind != Kind.Class) {
            throw new IllegalStateException("Cannot get classes from a non-class annotation");
        }

        Set<JClass> result = new HashSet<>();

        for (Object entry: entries) {
            JClass classEntry = (JClass) entry;
            result.add(classEntry);
        }
        return result;
    }

    public Collection<JMethod> getMethods() {
        if (kind != Kind.Method) {
            throw new IllegalStateException("Cannot get methods from a non-method annotation");
        }

        Set<JMethod> result = new HashSet<>();

        for (Object entry: entries) {
            JMethod methodEntry = (JMethod) entry;
            result.add(methodEntry);
        }
        return result;
    }

    public Collection<JField> getFields() {
        if (kind != Kind.Field) {
            throw new IllegalStateException("Cannot get fields from a non-field annotation");
        }

        Set<JField> result = new HashSet<>();
        for (Object entry: entries) {
            JField fieldEntry = (JField) entry;
            result.add(fieldEntry);
        }
        return result;
    }

    public static class Parser {
        private final ClassHierarchy classHierarchy;
        private final TypeSystem typeSystem;

        public Parser(ClassHierarchy classHierarchy, TypeSystem typeSystem) {
            this.classHierarchy = classHierarchy;
            this.typeSystem = typeSystem;
        }

        public ReflectiveAnnotation parse(String line) {
            List<String> parts = Arrays.stream(line.strip().split("]")).map(String::strip).toList();
            if (parts.size() != 2) {
                throw new IllegalArgumentException("Invalid reflective annotation: " + line);
            }
            String kind = parts.get(0);
            Kind k;
            switch (kind) {
                case "[class" -> k = Kind.Class;
                case "[method" -> k = Kind.Method;
                case "[field" -> k = Kind.Field;
                default -> throw new IllegalArgumentException("Invalid reflective annotation: " + line);
            }
            List<String> entries = Arrays.stream(parts.get(1).split(";")).map(String::strip).toList();
            List<Object> parsedEntries = new ArrayList<>();
            for (String entry: entries) {
                if (entry.isEmpty()) {
                    throw new IllegalArgumentException("Invalid reflective annotation: " + line);
                }
                switch (k) {
                    case Class -> {
                        JClass jClass = classHierarchy.getClass(entry);
                        parsedEntries.add(jClass);
                    }
                    case Field -> {
                        JField jField = classHierarchy.getField(entry);
                        parsedEntries.add(jField);
                    }
                    case Method -> {
                        JMethod jMethod = classHierarchy.getMethod(entry);
                        parsedEntries.add(jMethod);
                    }
                }
            }
            return new ReflectiveAnnotation(k, parsedEntries);
        }
    }
}
