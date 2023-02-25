package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.collection.Pair;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnnotationManager {
    private final Map<Pair<JMethod, Integer>, ReflectiveAnnotation> methodAnnotations = new HashMap<>();
    private final Map<Pair<JMethod, Integer>, ReflectiveAnnotation> fieldAnnotations = new HashMap<>();
    private final Map<Pair<JMethod, Integer>, ReflectiveAnnotation> classAnnotations = new HashMap<>();
    private final Solver solver;
    private final ReflectiveAnnotation.Parser parser;

    public AnnotationManager(Solver solver) {
        this.solver = solver;
        this.parser = new ReflectiveAnnotation.Parser(solver.getHierarchy(), solver.getTypeSystem());
    }

    public void load(String annotationFileName) {
        try (BufferedReader fileReader = new BufferedReader(new FileReader(annotationFileName))) {
            for (String line: fileReader.lines().toList()) {
                List<String> parts = Arrays.stream(line.split("@")).map(String::strip).toList();
                if (parts.size() != 2) {
                    throw new RuntimeException("Invalid annotation format: " + line);
                }
                String loc = parts.get(0);
                List<String> locParts = Arrays.stream(loc.split("/")).map(String::strip).toList();
                if (locParts.size() != 2) {
                    throw new RuntimeException("Invalid annotation location: " + loc);
                }
                JMethod method = solver.getHierarchy().getMethod(locParts.get(0));
                int idx = Integer.parseInt(locParts.get(1));
                ReflectiveAnnotation annotation = parser.parse(parts.get(1));
                switch (annotation.getKind()) {
                    case Class -> classAnnotations.put(new Pair<>(method, idx), annotation);
                    case Method -> methodAnnotations.put(new Pair<>(method, idx), annotation);
                    case Field -> fieldAnnotations.put(new Pair<>(method, idx), annotation);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean has(ReflectiveAnnotation.Kind kind, JMethod method, int idx) {
        switch (kind) {
            case Class -> {
                return classAnnotations.containsKey(new Pair<>(method, idx));
            }
            case Method -> {
                return methodAnnotations.containsKey(new Pair<>(method, idx));
            }
            case Field -> {
                return fieldAnnotations.containsKey(new Pair<>(method, idx));
            }
            default -> {
                throw new RuntimeException("Invalid annotation kind: " + kind);
            }
        }
    }

    public ReflectiveAnnotation getAnnotation(ReflectiveAnnotation.Kind kind, JMethod method, int idx) {
        switch (kind) {
            case Class -> {
                return classAnnotations.get(new Pair<>(method, idx));
            }
            case Method -> {
                return methodAnnotations.get(new Pair<>(method, idx));
            }
            case Field -> {
                return fieldAnnotations.get(new Pair<>(method, idx));
            }
            default -> {
                throw new RuntimeException("Invalid annotation kind: " + kind);
            }
        }
    }
}
