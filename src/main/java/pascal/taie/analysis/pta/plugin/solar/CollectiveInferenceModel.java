package pascal.taie.analysis.pta.plugin.solar;

import java.util.*;
import java.util.function.BiFunction;

import com.sun.istack.NotNull;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.ArrayIndex;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.util.AbstractModel;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.exp.InvokeInstanceExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Cast;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.ClassType;
import pascal.taie.language.type.PrimitiveType;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.VoidType;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.collection.Pair;

import javax.annotation.Nullable;

import static pascal.taie.analysis.pta.plugin.solar.Util.*;

class CollectiveInferenceModel extends AbstractModel {
    private final LazyObj.Builder lazyObjBuilder;
    private final MethodMetaObj.Builder methodMetaObjBuilder;
    private final FieldMetaObj.Builder fieldMetaObjBuilder;

    CollectiveInferenceModel(Solver solver, LazyObj.Builder lazyObjBuilder, MethodMetaObj.Builder methodMetaObjBuilder, FieldMetaObj.Builder fieldMetaObjBuilder) {
        super(solver);
        this.lazyObjBuilder = lazyObjBuilder;
        this.methodMetaObjBuilder = methodMetaObjBuilder;
        this.fieldMetaObjBuilder = fieldMetaObjBuilder;
    }

    private final MultiMap<Var, Type> castToTypes = Maps.newMultiMap();

    public void handleNewCast(Cast cast) {
        Type castType = cast.getRValue().getCastType();
        if (Util.isConcerned(castType) && !castType.equals(lazyObjBuilder.getUnknownType())) {
            Var source = cast.getRValue().getValue();
            castToTypes.put(source, castType);
        }
    }

    @Override
    protected void registerVarAndHandler() {
        // Method.invoke(Object, Object[])
        JMethod methodInvoke = hierarchy.getJREMethod(
                "<java.lang.reflect.Method: java.lang.Object invoke(java.lang.Object,java.lang.Object[])>");
        registerRelevantVarIndexes(methodInvoke, BASE, 0, 1);
        registerAPIHandler(methodInvoke, this::methodInvoke);

        JMethod fieldGet = hierarchy.getJREMethod(
                "<java.lang.reflect.Field: java.lang.Object get(java.lang.Object)>");
        registerRelevantVarIndexes(fieldGet, BASE, 0);
        registerAPIHandler(fieldGet, this::fieldGet);

        JMethod fieldSet = hierarchy.getJREMethod(
                "<java.lang.reflect.Field: void set(java.lang.Object,java.lang.Object)>");
        registerRelevantVarIndexes(fieldSet, BASE, 0, 1);
        registerAPIHandler(fieldSet, this::fieldSet);
    }

    private void fieldSet(CSVar csVar, PointsToSet pts, Invoke invoke) {
        List<PointsToSet> args = getArgs(csVar, pts, invoke, BASE, 0, 1);

        PointsToSet fldObjs = args.get(0); // m
        PointsToSet recvObjs = args.get(1); // y
        PointsToSet valueObjs = args.get(2); // x
        InvokeInstanceExp iExp = (InvokeInstanceExp) invoke.getInvokeExp();
        Var f = iExp.getBase();
        Context context = csVar.getContext();

        boolean recvObjContainsUnknown = recvObjs.objects()
                .anyMatch(Util::isLazyObjUnknownType);

        fldObjs.forEach(fldObj -> {
            if (!(fldObj.getObject().getAllocation() instanceof FieldMetaObj fieldMetaObj)) {
                return;
            }
            if (!fieldMetaObj.baseClassIsKnown()) {
                solver.addVarPointsTo(context, f, setTp(fieldMetaObj.getSignature(), recvObjs));
            }
            if (!fieldMetaObj.signatureIsKnown()) {
                solver.addVarPointsTo(context, f, setSig(fieldMetaObj.getBaseClass(), valueObjs));
            }
            if (!fieldMetaObj.baseClassIsKnown() && recvObjContainsUnknown
                    && fieldMetaObj.getFieldName() != null) {
                solver.addVarPointsTo(context, f, setS2T(fieldMetaObj.getSignature(), valueObjs));
            }
        });
    }

    private void fieldGet(CSVar csVar, PointsToSet pts, Invoke invoke) {
        List<PointsToSet> args = getArgs(csVar, pts, invoke, BASE, 0);

        Var x = invoke.getLValue();
        InvokeInstanceExp iExp = (InvokeInstanceExp) invoke.getInvokeExp();
        Var f = iExp.getBase();
        Set<Type> possibleA = castToTypes.get(x);

        PointsToSet fldObjs = args.get(0); // m
        PointsToSet recvObjs = args.get(1); // y
        Context context = csVar.getContext();

        boolean recvObjContainsUnknown = recvObjs.objects()
                .anyMatch(Util::isLazyObjUnknownType);

        fldObjs.forEach(fldObj -> {
            if (!(fldObj.getObject().getAllocation() instanceof FieldMetaObj fieldMetaObj)) {
                return;
            }
            if (!fieldMetaObj.baseClassIsKnown()) {
                solver.addVarPointsTo(context, f, getTp(fieldMetaObj.getSignature(), recvObjs));
            }
            for (Type A: possibleA) {
                if (!fieldMetaObj.signatureIsKnown()) {
                    solver.addVarPointsTo(context, f, getSig(fieldMetaObj.getBaseClass(), A));
                }
                if (!fieldMetaObj.baseClassIsKnown() && recvObjContainsUnknown
                        && fieldMetaObj.getFieldName() != null) {
                    solver.addVarPointsTo(context, f, getS2T(fieldMetaObj.getSignature(), A));
                }
            }
        });
    }

    private PointsToSet setS2T(FieldMetaObj.SignatureRecord signature, PointsToSet valueObjs) {
        PointsToSet result = solver.makePointsToSet();
        if (signature.fieldName() == null) {
            throw new RuntimeException("Unreachable");
        }
        for (CSObj valueObj: valueObjs) {
            if (isLazyObjUnknownType(valueObj)) {
                continue;
            }
            Type valueType = valueObj.getObject().getType();

            List<JClass> possibleClasses =
                    findClassesByFieldSignature(valueType, signature.fieldName(), (fieldType, valueType1) ->
                            fieldType.equals(valueType1) || typeSystem.isSubtype(fieldType, valueType1)
                    );
            for (JClass possibleClass: possibleClasses) {
                FieldMetaObj metaObj = fieldMetaObjBuilder.build(possibleClass, signature);
                result.addObject(solver.getCSManager().getCSObj(defaultHctx, heapModel.getMockObj(
                        metaObj.getDesc(),
                        metaObj,
                        metaObj.getType()
                )));
            }
        }
        return result;
    }

    private PointsToSet setSig(JClass baseClass, PointsToSet valueObjs) {
        PointsToSet result = solver.makePointsToSet();

        List<Type> allPossibleReturnTypes = new ArrayList<>();

        for (CSObj valueObj: valueObjs) {
            if (isLazyObjUnknownType(valueObj)) {
                continue;
            }

            Type valueType = valueObj.getObject().getType();

            if (valueType == null) {
                allPossibleReturnTypes.addAll(hierarchy.allClasses().map(JClass::getType).toList());
                allPossibleReturnTypes.addAll(Arrays.stream(PrimitiveType.values()).toList());
            } else if (!(valueType instanceof ClassType classType)) {
                allPossibleReturnTypes.add(valueType);
            } else {
                allPossibleReturnTypes.addAll(superClassesOf(classType.getJClass()).stream().map(JClass::getType).toList());
                allPossibleReturnTypes.add(classType);
            }

            for (var possibleReturnType: allPossibleReturnTypes) {
                FieldMetaObj fieldMetaObj = fieldMetaObjBuilder.build(baseClass,
                        FieldMetaObj.SignatureRecord.of(null, possibleReturnType));
                result.addObject(solver.getCSManager().getCSObj(defaultHctx, heapModel.getMockObj(
                        fieldMetaObj.getDesc(),
                        fieldMetaObj,
                        fieldMetaObj.getType()
                )));
            }
        }

        return result;
    }

    private PointsToSet setTp(FieldMetaObj.SignatureRecord signature, PointsToSet recvObjs) {
        PointsToSet result = solver.makePointsToSet();
        for (CSObj recvObj: recvObjs) {
            if (isLazyObjUnknownType(recvObj)) {
                continue;
            }
            Type recvType = recvObj.getObject().getType();
            if (!(recvType instanceof ClassType classType)) {
                continue;
            }
            FieldMetaObj fieldMetaObj = fieldMetaObjBuilder.build(classType.getJClass(), signature);
            Obj newMethodObj = heapModel.getMockObj(fieldMetaObj.getDesc(), fieldMetaObj, fieldMetaObj.getType());

            result.addObject(solver.getCSManager().getCSObj(defaultHctx, newMethodObj));
        }
        return result;
    }

    private PointsToSet getS2T(FieldMetaObj.SignatureRecord signature, Type resultType) {
        PointsToSet result = solver.makePointsToSet();
        if (signature.fieldName() == null) {
            throw new RuntimeException("Unreachable");
        }

        List<JClass> possibleClasses =
                findClassesByFieldSignature(resultType, signature.fieldName(), (fieldType, valueType) ->
                        fieldType.equals(valueType) || typeSystem.isSubtype(fieldType, valueType) || typeSystem.isSubtype(valueType, fieldType)
                );
        for (JClass possibleClass: possibleClasses) {
            FieldMetaObj fieldMetaObj = fieldMetaObjBuilder.build(possibleClass, FieldMetaObj.SignatureRecord.of(signature.fieldName(), signature.fieldType()));
            result.addObject(solver.getCSManager().getCSObj(defaultHctx, heapModel.getMockObj(
                    fieldMetaObj.getDesc(),
                    fieldMetaObj,
                    fieldMetaObj.getType()
            )));
        }
        return result;
    }

    private PointsToSet getSig(JClass baseClass, Type resultType) {
        PointsToSet result = solver.makePointsToSet();

        List<Type> allPossibleReturnTypes = new ArrayList<>();

        if (resultType == null) {
            allPossibleReturnTypes.addAll(hierarchy.allClasses().map(JClass::getType).toList());
            allPossibleReturnTypes.addAll(Arrays.stream(PrimitiveType.values()).toList());
        } else if (!(resultType instanceof ClassType classType)) {
            allPossibleReturnTypes.add(resultType);
        } else {
            allPossibleReturnTypes.addAll(superClassesOf(classType.getJClass()).stream().map(JClass::getType).toList());
            allPossibleReturnTypes.addAll(hierarchy.getAllSubclassesOf(classType.getJClass()).stream().map(JClass::getType).toList());
        }

        for (var possibleReturnType: allPossibleReturnTypes) {
            FieldMetaObj fieldMetaObj = fieldMetaObjBuilder.build(baseClass,
                    FieldMetaObj.SignatureRecord.of(null, possibleReturnType));
            result.addObject(solver.getCSManager().getCSObj(defaultHctx, heapModel.getMockObj(
                    fieldMetaObj.getDesc(),
                    fieldMetaObj,
                    fieldMetaObj.getType()
            )));
        }

        return result;
    }

    private PointsToSet getTp(FieldMetaObj.SignatureRecord signature, PointsToSet recvObjs) {
        PointsToSet result = solver.makePointsToSet();
        for (CSObj recvObj: recvObjs) {
            if (isLazyObjUnknownType(recvObj)) {
                continue;
            }
            Type recvType = recvObj.getObject().getType();
            if (!(recvType instanceof ClassType classType)) {
                continue;
            }
            FieldMetaObj fieldMetaObj = fieldMetaObjBuilder.build(classType.getJClass(), signature);
            Obj newMethodObj = heapModel.getMockObj(fieldMetaObj.getDesc(), fieldMetaObj, fieldMetaObj.getType());

            result.addObject(solver.getCSManager().getCSObj(defaultHctx, newMethodObj));
        }
        return result;
    }

    private void methodInvoke(CSVar csVar, PointsToSet pts, Invoke invoke) {
        List<PointsToSet> args = getArgs(csVar, pts, invoke, BASE, 0, 1);

        // For x = (A) m.invoke(y, args)
        Var x = invoke.getLValue();
        InvokeInstanceExp iExp = (InvokeInstanceExp) invoke.getInvokeExp();
        Var m = iExp.getBase();
        Set<Type> possibleA;
        if (x == null) {
            possibleA = new HashSet<>(List.of(PrimitiveType.values()));
            possibleA.add(VoidType.VOID);
            possibleA.add(lazyObjBuilder.getUnknownType());
        } else {
             var tmp = castToTypes.get(x);
             if (tmp.isEmpty()) {
                 possibleA = new HashSet<>(List.of(PrimitiveType.values()));
                 possibleA.add(VoidType.VOID);
                 possibleA.add(lazyObjBuilder.getUnknownType());
             } else {
                 possibleA = tmp;
             }
        }

        PointsToSet mtdObjs = args.get(0); // m
        PointsToSet recvObjs = args.get(1); // y
        PointsToSet argObjs = args.get(2); // args
        Context context = csVar.getContext();

        boolean recvObjContainsUnknown = recvObjs.objects()
                .anyMatch(Util::isLazyObjUnknownType);

        mtdObjs.forEach(mtdObj -> {
            if (!(mtdObj.getObject().getAllocation() instanceof MethodMetaObj methodMetaObj)) {
                return;
            }
            if (!methodMetaObj.baseClassIsKnown()) {
                solver.addVarPointsTo(context, m, invTp(methodMetaObj.getSignature(), recvObjs));
            }
            for (Type A: possibleA) {
                if (!methodMetaObj.signatureIsKnown()) {
                    solver.addVarPointsTo(context, m, invSig(methodMetaObj.getBaseClass(), argObjs, A));
                }
                if (!methodMetaObj.baseClassIsKnown() && recvObjContainsUnknown
                        && methodMetaObj.getMethodName() != null) {
                    solver.addVarPointsTo(context, m, invS2T(methodMetaObj.getSignature(), argObjs, A));
                }
            }
        });
    }

    /**
     * I-InvTp: use the type(s) of pt(y) to infer the class type of a method
     *
     * @param signature  the original method meta-object's signature
     * @param recvObjs points-to set of the receiver object `y`
     * @return new objects pointed to by `m`
     */
    private PointsToSet invTp(MethodMetaObj.SignatureRecord signature, PointsToSet recvObjs) {
        PointsToSet result = solver.makePointsToSet();

        for (CSObj recvObj : recvObjs) {
            if (isLazyObjUnknownType(recvObj)) {
                continue;
            }
            Type recvType = recvObj.getObject().getType();
            if (!(recvType instanceof ClassType classType)) {
                continue;
            }
            MethodMetaObj methodMetaObj = methodMetaObjBuilder.build(classType.getJClass(), signature);
            Obj newMethodObj = heapModel.getMockObj(methodMetaObj.getDesc(), methodMetaObj,
                    methodMetaObj.getType());

            result.addObject(solver.getCSManager().getCSObj(defaultHctx, newMethodObj));
        }

        return result;
    }

    /**
     * I-InvSig: use the information available at a call site (excluding y) to infer
     * the descriptor of a method signature
     *
     * @param baseClass  the original method meta-object's base class
     * @param argObjs    points-to set of args in `x = (A) m.invoke(y, args)`
     * @param resultType A
     * @return new objects pointed to by `m`
     */
    private PointsToSet invSig(JClass baseClass, PointsToSet argObjs, Type resultType) {
        PointsToSet result = solver.makePointsToSet();

        List<Type> allPossibleReturnTypes = new ArrayList<>();

        if (resultType == null) {
            allPossibleReturnTypes.addAll(hierarchy.allClasses().map(JClass::getType).toList());
            allPossibleReturnTypes.add(VoidType.VOID);
            allPossibleReturnTypes.addAll(Arrays.stream(PrimitiveType.values()).toList());
        } else if (!(resultType instanceof ClassType classType)) {
            allPossibleReturnTypes.add(resultType);
        } else {
            allPossibleReturnTypes.addAll(superClassesOf(classType.getJClass()).stream().map(JClass::getType).toList());
            allPossibleReturnTypes.addAll(hierarchy.getAllSubclassesOf(classType.getJClass()).stream().map(JClass::getType).toList());
        }

        var allPossibleArgTypes = findArgTypes(argObjs);
        for (var possibleReturnType: allPossibleReturnTypes) {
            if (allPossibleArgTypes == null) {
                MethodMetaObj metaObj = methodMetaObjBuilder.build(baseClass, MethodMetaObj.SignatureRecord.of(null, null, possibleReturnType));
                result.addObject(solver.getCSManager().getCSObj(defaultHctx, heapModel.getMockObj(
                        metaObj.getDesc(),
                        metaObj,
                        metaObj.getType()
                )));
            } else {
                for (var possibleArgTypes: allPossibleArgTypes) {
                    MethodMetaObj metaObj = methodMetaObjBuilder.build(baseClass, MethodMetaObj.SignatureRecord.of(null, possibleArgTypes, possibleReturnType));
                    result.addObject(solver.getCSManager().getCSObj(defaultHctx, heapModel.getMockObj(
                            metaObj.getDesc(),
                            metaObj,
                            metaObj.getType()
                    )));
                }
            }
        }

        return result;
    }

    /**
     * I-InvS2T: use a method signature to infer the class type of a method
     *
     * @param signature  the original method meta-object's signature
     * @param argObjs    `args`
     * @param resultType A
     * @return new objects pointed to by `m`
     */
    private PointsToSet invS2T(MethodMetaObj.SignatureRecord signature, PointsToSet argObjs,
                               Type resultType) {
        PointsToSet result = solver.makePointsToSet();
        if (signature.methodName() == null) {
            throw new RuntimeException("Unreachable");
        }
        Type returnType = signature.returnType();
        if (returnType != null && !returnType.equals(resultType)
                && !typeSystem.isSubtype(returnType, resultType)
                && !typeSystem.isSubtype(resultType, returnType)) {
            return result;
        }

        assert signature.paramTypes() == null;
        List<MethodMetaObj.SignatureRecord> possibleSigs = new ArrayList<>();
        var ptp = findArgTypes(argObjs);
        if (ptp != null) {
            for (var paramTypes: ptp) {
                possibleSigs.add(MethodMetaObj.SignatureRecord.of(signature.methodName(), paramTypes, returnType));
            }
        }

        List<Pair<MethodMetaObj.SignatureRecord, List<JClass>>> possibleClasses = new ArrayList<>();
        for (var sig: possibleSigs) {
            possibleClasses.add(
                    new Pair<>(sig, findClassesByMethodSignature(resultType, sig.methodName(),
                            sig.paramTypes()))
            );
        }

        for (var possibleSigAndClasses: possibleClasses) {
            for (var possibleClass: possibleSigAndClasses.second()) {
                MethodMetaObj metaObj = methodMetaObjBuilder.build(possibleClass, possibleSigAndClasses.first());
                result.addObject(solver.getCSManager().getCSObj(defaultHctx, heapModel.getMockObj(
                        metaObj.getDesc(),
                        metaObj,
                        metaObj.getType()
                )));
            }
        }
        return result;
    }

    private Set<List<Type>> findArgTypes(PointsToSet argObjects) {
        Set<List<Type>> result = new HashSet<>();
        for (CSObj argArray: argObjects) {
            int arrLen = constArraySize(argArray.getObject());
            if (arrLen == -1) {
                return null;
            }

            ArrayIndex idx = csManager.getArrayIndex(argArray);
            Set<Type> elementTypes = new HashSet<>();
            idx.getObjects().forEach(obj -> {
                Type t = obj.getObject().getType();
                elementTypes.add(t);
            });
            var somePossibilities = cartesian(elementTypes, arrLen);
            result.addAll(somePossibilities);
        }
        return result;
    }

    private static<T> List<List<T>> cartesian(Set<T> singleChoice, int repeatTimes) {
        List<List<T>> result = new ArrayList<>();
        if (repeatTimes == 0) {
            result.add(new ArrayList<>());
            return result;
        }
        for (T choice: singleChoice) {
            for (List<T> subResult: cartesian(singleChoice, repeatTimes - 1)) {
                subResult.add(choice);
                result.add(subResult);
            }
        }
        return result;
    }

    private List<JClass> findClassesByFieldSignature(@Nullable Type valueType, @NotNull String name,
                                                     BiFunction<Type, Type, Boolean> typeCheck) {
        return hierarchy.allClasses().filter(klass -> {
            for (var field : klass.getDeclaredFields()) {
                if (!field.isPublic()) {
                    continue;
                }
                if (!field.getName().equals(name)) {
                    continue;
                }
                Type fieldType = field.getType();
                if (valueType != null && !typeCheck.apply(fieldType, valueType)) {
                    continue;
                }
                return true;
            }
            return false;
        }).toList();
    }

    private List<JClass> findClassesByMethodSignature(@Nullable Type resultType, @NotNull String name,
                                                      @NotNull List<Type> paramTypes) {
        return hierarchy.allClasses().filter(klass -> {
            for (var method : klass.getDeclaredMethods()) {
                if (!method.isPublic()) {
                    continue;
                }
                if (!method.getName().equals(name)) {
                    continue;
                }
                Type returnType = method.getReturnType();
                if (resultType != null && !returnType.equals(resultType)
                        &&!typeSystem.isSubtype(returnType, resultType) && !typeSystem.isSubtype(resultType, returnType)) {
                    continue;
                }
                if (paramTypes != null) {
                    if (method.getParamTypes().size() != paramTypes.size()) {
                        continue;
                    }
                    if (paramTypesFit(typeSystem, method.getParamTypes(), paramTypes)) {
                        return true;
                    }
                } else {
                    return true;
                }
            }
            return false;
        }).toList();
    }
}
