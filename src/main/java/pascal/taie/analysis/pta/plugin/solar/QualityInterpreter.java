package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.exp.NullLiteral;
import pascal.taie.ir.exp.Var;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;

import static pascal.taie.analysis.pta.plugin.solar.Util.*;
import static pascal.taie.language.classes.ClassNames.OBJECT;

import java.util.*;

public class QualityInterpreter {
    // TODO: Use different thresholds for different reflective methods?
    private int precisionThreshold = -1;
    private final Solver solver;
    private final MultiMap<CSCallSite, JField> fieldReflectiveObjects = Maps.newMultiMap();
    private final MultiMap<CSCallSite, JMethod> methodReflectiveObjects = Maps.newMultiMap();
    private final MultiMap<CSCallSite, InferenceItem> inferenceItems = Maps.newMultiMap();

    public static abstract class InferenceItem {
        protected final CSVar reflectiveVar;
        protected final CSVar baseVar;

        public InferenceItem(CSVar reflectiveVar, CSVar baseVar) {
            this.reflectiveVar = reflectiveVar;
            this.baseVar = baseVar;
        }

        public CSVar getReflectiveVar() {
            return reflectiveVar;
        }

        public CSVar getBaseVar() {
            return baseVar;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InferenceItem that = (InferenceItem) o;
            return Objects.equals(reflectiveVar, that.reflectiveVar) && Objects.equals(baseVar, that.baseVar);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reflectiveVar, baseVar);
        }
    }

    public static class FieldGetInferenceItem extends InferenceItem {
        private final Type castType;

        public FieldGetInferenceItem(CSVar reflectiveVar, CSVar baseVar, Type castType) {
            super(reflectiveVar, baseVar);
            this.castType = castType;
        }

        public Type getCastType() {
            return castType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            FieldGetInferenceItem that = (FieldGetInferenceItem) o;
            return Objects.equals(castType, that.castType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), castType);
        }
    }

    public static class FieldSetInferenceItem extends InferenceItem {
        private final CSVar valueVar;

        public FieldSetInferenceItem(CSVar reflectiveVar, CSVar baseVar, CSVar valueVar) {
            super(reflectiveVar, baseVar);
            this.valueVar = valueVar;
        }

        public CSVar getValueVar() {
            return valueVar;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            FieldSetInferenceItem that = (FieldSetInferenceItem) o;
            return Objects.equals(valueVar, that.valueVar);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), valueVar);
        }
    }

    public static class MethodInvokeInferenceItem extends InferenceItem {
        private final CSVar argVar;

        public MethodInvokeInferenceItem(CSVar reflectiveVar, CSVar baseVar, CSVar argVar) {
            super(reflectiveVar, baseVar);
            this.argVar = argVar;
        }

        public CSVar getArgVar() {
            return argVar;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            MethodInvokeInferenceItem that = (MethodInvokeInferenceItem) o;
            return Objects.equals(argVar, that.argVar);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), argVar);
        }
    }

    private final Type objectType;

    public QualityInterpreter(Solver solver) {
        this.solver = solver;
        this.objectType = solver.getTypeSystem().getType(OBJECT);
        if (solver.getOptions().has("solar-precision-threshold")) {
            Object threshold = solver.getOptions().get("solar-precision-threshold");
            if (threshold == null) {
                return;
            }
            if ((int) threshold > 0) {
                this.precisionThreshold = (int) threshold;
            }
        }
    }

    public void addReflectiveObject(CSCallSite callSite, Object object) {
        if (object instanceof JField field) {
            fieldReflectiveObjects.put(callSite, field);
        } else if (object instanceof JMethod method) {
            methodReflectiveObjects.put(callSite, method);
        } else {
            throw new IllegalArgumentException("Unsupported reflective object: " + object);
        }
    }

    public void addInferenceItem(CSCallSite callSite, InferenceItem inferenceItem) {
        this.inferenceItems.put(callSite, inferenceItem);
    }

    public Collection<CSCallSite> checkPrecision() {
        Set<CSCallSite> result = new HashSet<>();
        if (precisionThreshold <= 0) {
            return result;
        }
        for (var key: fieldReflectiveObjects.keySet()) {
            var values = fieldReflectiveObjects.get(key);
            if (values.size() > precisionThreshold) {
                result.add(key);
            }
        }
        for (var key: methodReflectiveObjects.keySet()) {
            var values = methodReflectiveObjects.get(key);
            if (values.size() > precisionThreshold) {
                result.add(key);
            }
        }
        return result;
    }

    public Collection<CSCallSite> checkSoundness() {
        Set<CSCallSite> result = new HashSet<>();
        outer: for (CSCallSite key: inferenceItems.keySet()) {
            var values = inferenceItems.get(key);
            for (InferenceItem item: values) {
                if (item instanceof MethodInvokeInferenceItem mItem) {
                    if (!isNull(mItem.getBaseVar().getVar())) {
                        PointsToSet yPts = solver.getPointsToSetOf(mItem.getBaseVar());
                        if (!allKnown(yPts)) {
                            result.add(key);
                            continue outer;
                        }
                    }
                    PointsToSet mPts = solver.getPointsToSetOf(mItem.getReflectiveVar());
                    for (var mObj: mPts) {
                        if (mObj.getObject().getAllocation() instanceof MethodMetaObj metaObj) {
                            if (!metaObj.baseClassIsKnown() && metaObj.signatureIsKnown()
                                    && metaObj.getMethodName() == null) {
                                result.add(key);
                                continue outer;
                            }
                        }
                    }
                    var ptp = possibleArgTypes(solver.getCSManager(), solver.getPointsToSetOf(mItem.getArgVar()));
                    if (ptp ==null || ptp.isEmpty()) {
                        result.add(key);
                        continue outer;
                    }
                } else if (item instanceof FieldGetInferenceItem fGetItem) {
                    if (!isNull(fGetItem.getBaseVar().getVar())) {
                        PointsToSet yPts = solver.getPointsToSetOf(fGetItem.getBaseVar());
                        if (!allKnown(yPts)) {
                            result.add(key);
                            continue outer;
                        }
                    }
                    PointsToSet fPts = solver.getPointsToSetOf(fGetItem.getReflectiveVar());
                    for (var fObj: fPts) {
                        if (fObj.getObject().getAllocation() instanceof FieldMetaObj metaObj) {
                            if (!metaObj.baseClassIsKnown() && metaObj.signatureIsKnown()
                                    && metaObj.getFieldName() == null) {
                                result.add(key);
                                continue outer;
                            }
                        }
                    }
                    if (fGetItem.getCastType().equals(objectType)) {
                        result.add(key);
                        continue outer;
                    }
                } else if (item instanceof FieldSetInferenceItem fSetItem) {
                    if (!isNull(fSetItem.getBaseVar().getVar())) {
                        PointsToSet yPts = solver.getPointsToSetOf(fSetItem.getBaseVar());
                        if (!allKnown(yPts)) {
                            result.add(key);
                            continue outer;
                        }
                    }
                    PointsToSet fPts = solver.getPointsToSetOf(fSetItem.getReflectiveVar());
                    for (var fObj: fPts) {
                        if (fObj.getObject().getAllocation() instanceof FieldMetaObj metaObj) {
                            if (!metaObj.baseClassIsKnown() && metaObj.signatureIsKnown()
                                    && metaObj.getFieldName() == null) {
                                result.add(key);
                                continue outer;
                            }
                        }
                    }
                    PointsToSet xPts = solver.getPointsToSetOf(fSetItem.getValueVar());
                    if (!allKnown(xPts)) {
                        result.add(key);
                        continue outer;
                    }
                } else {
                    throw new IllegalArgumentException("Unsupported inference item: " + item);
                }
            }
        }
        return result;
    }

    private static boolean allKnown(PointsToSet pts) {
        for (var obj: pts) {
            if (Util.isLazyObjUnknownType(obj)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNull(Var var) {
        return var.isConst() && var.getConstValue().equals(NullLiteral.get());
    }
}
