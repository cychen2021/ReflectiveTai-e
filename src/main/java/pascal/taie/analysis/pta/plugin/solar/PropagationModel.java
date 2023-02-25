package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.ArrayIndex;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.plugin.util.AbstractModel;
import pascal.taie.analysis.pta.plugin.util.CSObjs;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.collection.Pair;

import java.util.*;

class PropagationModel extends AbstractModel {
    private final SolarAnalysis solarAnalysis;
    private final Map<Invoke, Pair<JMethod, Integer>> classIntrospectIndices = new HashMap<>();
    private final Map<Invoke, Pair<JMethod, Integer>> fieldIntrospectIndices = new HashMap<>();
    private final Map<Invoke, Pair<JMethod, Integer>> methodIntrospectIndices = new HashMap<>();

    private final Set<JMethod> classIntrospectMethods = new HashSet<>();
    private final Set<JMethod> fieldIntrospectMethods = new HashSet<>();
    private final Set<JMethod> methodIntrospectMethods = new HashSet<>();

    PropagationModel(SolarAnalysis solarAnalysis) {
        super(solarAnalysis.getSolver());
        this.solarAnalysis = solarAnalysis;

        JMethod classForName = hierarchy.getJREMethod("<java.lang.Class: java.lang.Class forName(java.lang.String)>");
        classIntrospectMethods.add(classForName);
        JMethod classForName2 = hierarchy.getJREMethod("<java.lang.Class: java.lang.Class forName(java.lang.String,boolean,java.lang.ClassLoader)>");
        classIntrospectMethods.add(classForName2);

        JMethod classGetMethod = hierarchy.getJREMethod("<java.lang.Class: java.lang.reflect.Method getMethod(java.lang.String,java.lang.Class[])>");
        methodIntrospectMethods.add(classGetMethod);

        JMethod classGetMethods = hierarchy.getJREMethod("<java.lang.Class: java.lang.reflect.Method[] getMethods()>");
        methodIntrospectMethods.add(classGetMethods);

        JMethod classGetField = hierarchy.getJREMethod("<java.lang.Class: java.lang.reflect.Field getField(java.lang.String)>");
        fieldIntrospectMethods.add(classGetField);
    }

    public void onNewMethod(JMethod method) {
        int classCounter = 0;
        int fieldCounter = 0;
        int methodCounter = 0;
        for (Invoke invoke: method.getIR().invokes(false).toList()) {
            JMethod callee = invoke.getInvokeExp().getMethodRef().resolveNullable();
            if (callee != null) {
                if (classIntrospectMethods.contains(callee)) {
                    classIntrospectIndices.put(invoke, new Pair<>(method, classCounter));
                    classCounter++;
                } else if (fieldIntrospectMethods.contains(callee)) {
                    fieldIntrospectIndices.put(invoke, new Pair<>(method, fieldCounter));
                    fieldCounter++;
                } else if (methodIntrospectMethods.contains(callee)) {
                    methodIntrospectIndices.put(invoke, new Pair<>(method, methodCounter));
                    methodCounter++;
                }
            }
        };
    }

    @Override
    protected void registerVarAndHandler() {
        JMethod classForName = hierarchy.getJREMethod("<java.lang.Class: java.lang.Class forName(java.lang.String)>");
        registerRelevantVarIndexes(classForName, 0);
        registerAPIHandler(classForName, this::classForName);

        JMethod classForName2 = hierarchy.getJREMethod("<java.lang.Class: java.lang.Class forName(java.lang.String,boolean,java.lang.ClassLoader)>");
        // TODO: take class loader into account
        registerRelevantVarIndexes(classForName2, 0);
        registerAPIHandler(classForName2, this::classForName);

        JMethod classGetMethod = hierarchy.getJREMethod("<java.lang.Class: java.lang.reflect.Method getMethod(java.lang.String,java.lang.Class[])>");
        registerRelevantVarIndexes(classGetMethod, BASE, 0);
        registerAPIHandler(classGetMethod, this::classGetMethod);

        JMethod classGetMethods = hierarchy.getJREMethod("<java.lang.Class: java.lang.reflect.Method[] getMethods()>");
        registerRelevantVarIndexes(classGetMethods, BASE);
        registerAPIHandler(classGetMethods, this::classGetMethods);

        JMethod classGetField = hierarchy.getJREMethod("<java.lang.Class: java.lang.reflect.Field getField(java.lang.String)>");
        registerRelevantVarIndexes(classGetField, BASE, 0);
        registerAPIHandler(classGetField, this::classGetField);
    }

    private void classGetField(CSVar csVar, PointsToSet pts, Invoke invoke) {
        Context context = csVar.getContext();
        Var result = invoke.getResult();

        var idx = fieldIntrospectIndices.get(invoke);
        if (solarAnalysis.getAnnotationManager().has(ReflectiveAnnotation.Kind.Field, idx.first(), idx.second())) {
            ReflectiveAnnotation annotation =
                    solarAnalysis.getAnnotationManager().getAnnotation(ReflectiveAnnotation.Kind.Field, idx.first(), idx.second());
            Collection<JField> fields = annotation.getFields();
            for (JField jField: fields) {
                FieldMetaObj metaObj = solarAnalysis.getFieldMetaObjBuilder().build(jField);
                Obj clsObj = heapModel.getMockObj(metaObj.getDesc(), metaObj, metaObj.getType());
                CSObj csObj = csManager.getCSObj(defaultHctx, clsObj);
                solver.addVarPointsTo(context, result, csObj);
            }
            return;
        }

        List<PointsToSet> args = getArgs(csVar, pts, invoke, BASE, 0);
        PointsToSet baseClsObjs = args.get(0);
        PointsToSet fNameObjs = args.get(1);
        fNameObjs.forEach(fNameObj -> {
            baseClsObjs.forEach(baseClsObj -> {
                String fName = CSObjs.toString(fNameObj);
                JClass baseCls = CSObjs.toClass(baseClsObj);
                if (baseCls == null) {
                    ClassMetaObj clsMetaObj = (ClassMetaObj) baseClsObj.getObject().getAllocation();
                    baseCls = clsMetaObj.getJClass();
                }

                FieldMetaObj metaObj = solarAnalysis.getFieldMetaObjBuilder().build(baseCls, fName == null ? null : FieldMetaObj.SignatureRecord.of(fName, null));
                Obj fldObj = heapModel.getMockObj(metaObj.getDesc(), metaObj, metaObj.getType());
                CSObj csObj = csManager.getCSObj(defaultHctx, fldObj);
                solver.addVarPointsTo(context, result, csObj);
            });
        });
    }

    private void classForName(CSVar csVar, PointsToSet pts, Invoke invoke) {
        Context context = csVar.getContext();
        Var result = invoke.getResult();
        var idx = classIntrospectIndices.get(invoke);
        if (solarAnalysis.getAnnotationManager().has(ReflectiveAnnotation.Kind.Class, idx.first(), idx.second())) {
            ReflectiveAnnotation annotation =
                    solarAnalysis.getAnnotationManager().getAnnotation(ReflectiveAnnotation.Kind.Class, idx.first(), idx.second());
            Collection<JClass> classes = annotation.getClasses();
            for (JClass jClass: classes) {
                ClassMetaObj metaObj = solarAnalysis.getClassMetaObjBuilder().build(jClass);
                Obj clsObj = heapModel.getMockObj(metaObj.getDesc(), metaObj, metaObj.getType());
                CSObj csObj = csManager.getCSObj(defaultHctx, clsObj);
                solver.addVarPointsTo(context, result, csObj);
            }
            return;
        }

        pts.forEach(obj -> {
            String className = CSObjs.toString(obj);
            JClass klass;
            if (className == null) {
                klass = null;
            } else {
                klass = hierarchy.getClass(className);
            }
            solver.initializeClass(klass);
            if (result != null) {
                ClassMetaObj metaObj = solarAnalysis.getClassMetaObjBuilder().build(klass);
                Obj clsObj = heapModel.getMockObj(metaObj.getDesc(), metaObj, metaObj.getType());
                CSObj csObj = csManager.getCSObj(defaultHctx, clsObj);
                solver.addVarPointsTo(context, result, csObj);
            }
        });
    }

    private void classGetMethod(CSVar csVar, PointsToSet pts, Invoke invoke) {
        Context context = csVar.getContext();
        Var result = invoke.getResult();

        var idx = methodIntrospectIndices.get(invoke);
        if (solarAnalysis.getAnnotationManager().has(ReflectiveAnnotation.Kind.Method, idx.first(), idx.second())) {
            ReflectiveAnnotation annotation =
                    solarAnalysis.getAnnotationManager().getAnnotation(ReflectiveAnnotation.Kind.Method, idx.first(), idx.second());
            Collection<JMethod> methods = annotation.getMethods();
            for (JMethod jMethod: methods) {
                MethodMetaObj metaObj = solarAnalysis.getMethodMetaObjBuilder().build(jMethod);
                Obj mtdObj = heapModel.getMockObj(metaObj.getDesc(), metaObj, metaObj.getType());
                CSObj csObj = csManager.getCSObj(defaultHctx, mtdObj);
                solver.addVarPointsTo(context, result, csObj);
            }
            return;
        }

        List<PointsToSet> args = getArgs(csVar, pts, invoke, BASE, 0);
        PointsToSet baseClsObjs = args.get(0);
        PointsToSet mNameObjs = args.get(1);
        mNameObjs.forEach(mNameObj -> {
            baseClsObjs.forEach(baseClsObj -> {
                String mName = CSObjs.toString(mNameObj);
                JClass baseCls = CSObjs.toClass(baseClsObj);
                if (baseCls == null) {
                    ClassMetaObj clsMetaObj = (ClassMetaObj) baseClsObj.getObject().getAllocation();
                    baseCls = clsMetaObj.getJClass();
                }

                MethodMetaObj metaObj = solarAnalysis.getMethodMetaObjBuilder().build(baseCls, mName == null ? null : MethodMetaObj.SignatureRecord.of(mName, null, null));

                Obj mtdObj = heapModel.getMockObj(metaObj.getDesc(), metaObj, metaObj.getType());
                CSObj csObj = csManager.getCSObj(defaultHctx, mtdObj);
                solver.addVarPointsTo(context, result, csObj);
            });
        });
    }

    private void classGetMethods(CSVar csVar, PointsToSet pts, Invoke invoke) {
        Context context = csVar.getContext();
        Var result = invoke.getResult();

        var idx = methodIntrospectIndices.get(invoke);
        if (solarAnalysis.getAnnotationManager().has(ReflectiveAnnotation.Kind.Method, idx.first(), idx.second())) {
            ReflectiveAnnotation annotation =
                    solarAnalysis.getAnnotationManager().getAnnotation(ReflectiveAnnotation.Kind.Method, idx.first(), idx.second());
            Collection<JMethod> methods = annotation.getMethods();
            for (JMethod jMethod: methods) {
                MethodMetaObj metaObj = solarAnalysis.getMethodMetaObjBuilder().build(jMethod);
                Obj mtdObj = heapModel.getMockObj(metaObj.getDesc(), metaObj,
                        typeSystem.getArrayType(metaObj.getType(), 1));
                CSObj csObj = csManager.getCSObj(defaultHctx, mtdObj);
                ArrayIndex arrIdx = csManager.getArrayIndex(csObj);
                solver.addVarPointsTo(context, result, csObj);
                solver.addPointsTo(arrIdx, csObj);
            }
            return;
        }

        List<PointsToSet> args = getArgs(csVar, pts, invoke, BASE);
        PointsToSet baseClsObjs = args.get(0);
        baseClsObjs.forEach(baseClsObj -> {
            JClass baseCls = CSObjs.toClass(baseClsObj);
            if (baseCls == null) {
                ClassMetaObj clsMetaObj = (ClassMetaObj) baseClsObj.getObject().getAllocation();
                baseCls = clsMetaObj.getJClass();
            }

            MethodMetaObj metaObj = solarAnalysis.getMethodMetaObjBuilder().build(baseCls, null);

            Obj mtdObj = heapModel.getMockObj(metaObj.getDesc(), metaObj,
                    typeSystem.getArrayType(metaObj.getType(), 1));
            CSObj csObj = csManager.getCSObj(defaultHctx, mtdObj);
            ArrayIndex arrIdx = csManager.getArrayIndex(csObj);
            solver.addVarPointsTo(context, result, csObj);
            solver.addPointsTo(arrIdx, csObj);
        });
    }
}
