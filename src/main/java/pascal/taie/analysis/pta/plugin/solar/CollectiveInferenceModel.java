package pascal.taie.analysis.pta.plugin.solar;

import java.util.*;

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
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.ClassType;
import pascal.taie.language.type.PrimitiveType;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.VoidType;

import javax.annotation.Nullable;

import static pascal.taie.analysis.pta.plugin.solar.Util.*;
import static pascal.taie.analysis.pta.plugin.solar.MethodMetaObj.SignatureRecord;

class CollectiveInferenceModel extends AbstractModel {

    CollectiveInferenceModel(Solver solver) {
        super(solver);
    }

    @Override
    protected void registerVarAndHandler() {
        // Method.invoke(Object, Object[])
        JMethod methodInvoke = hierarchy.getJREMethod(
                "<java.lang.reflect.Method: java.lang.Object invoke(java.lang.Object,java.lang.Object[])>");
        registerRelevantVarIndexes(methodInvoke, BASE, 0, 1);
        registerAPIHandler(methodInvoke, this::methodInvoke);
    }

    private void methodInvoke(CSVar csVar, PointsToSet pts, Invoke invoke) {
        List<PointsToSet> args = getArgs(csVar, pts, invoke, BASE, 0, 1);

        // For x = (A) m.invoke(y, args)
        Var x = invoke.getLValue();
        InvokeInstanceExp iExp = (InvokeInstanceExp) invoke.getInvokeExp();
        Var m = iExp.getBase();
        Type A;
        if (x != null) {
            A = x.getType();
        } else {
            A = null;
        }

        PointsToSet mtdObjs = args.get(0); // m
        PointsToSet recvObjs = args.get(1); // y
        PointsToSet argObjs = args.get(2); // args
        Context context = csVar.getContext();

        boolean recvObjContainsUnknown = recvObjs.objects().anyMatch(csObj -> {
            if (csObj.getObject().getType() instanceof ClassType classType) {
                return typeUnknown(classType);
            }
            return false;
        });

        mtdObjs.forEach(mtdObj -> {
            if (!(mtdObj.getObject().getAllocation() instanceof MethodMetaObj methodMetaObj)) {
                return;
            }
            if (!methodMetaObj.baseClassKnown() && !recvObjContainsUnknown) {
                solver.addVarPointsTo(context, m, invTp(methodMetaObj.getSignature(), recvObjs));
            }
            if (!methodMetaObj.signatureKnown()) {
                solver.addVarPointsTo(context, m, invSig(methodMetaObj.getBaseClass(), argObjs, A));
            }
            if (!methodMetaObj.baseClassKnown() && recvObjContainsUnknown
                    && methodMetaObj.getMethodName() != null
                    && methodMetaObj.getParameterTypes() != null) {
                solver.addVarPointsTo(context, m, invS2T(methodMetaObj.getSignature(), argObjs, A));
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
    private PointsToSet invTp(SignatureRecord signature, PointsToSet recvObjs) {
        PointsToSet result = solver.makePointsToSet();

        for (CSObj recvObj : recvObjs) {
            Type recvType = recvObj.getObject().getType();
            if (!(recvType instanceof ClassType classType)) {
                continue;
            }
            if (!typeUnknown(classType)) {
                MethodMetaObj methodMetaObj = MethodMetaObj.of(classType.getJClass(), signature);
                Obj newMethodObj = heapModel.getMockObj(MethodMetaObj.DESC, methodMetaObj,
                        MethodMetaObj.TYPE);

                result.addObject(solver.getCSManager().getCSObj(defaultHctx, newMethodObj));
            }
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
                result.addObject(solver.getCSManager().getCSObj(defaultHctx, heapModel.getMockObj(
                        MethodMetaObj.DESC,
                        MethodMetaObj.of(baseClass, SignatureRecord.of(null, null, possibleReturnType)),
                        MethodMetaObj.TYPE
                )));
            } else {
                for (var possibleArgTypes: allPossibleArgTypes) {
                    result.addObject(solver.getCSManager().getCSObj(defaultHctx, heapModel.getMockObj(
                            MethodMetaObj.DESC,
                            MethodMetaObj.of(baseClass, SignatureRecord.of(null, possibleArgTypes, possibleReturnType)),
                            MethodMetaObj.TYPE
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
    private PointsToSet invS2T(SignatureRecord signature, PointsToSet argObjs,
                               Type resultType) {
        PointsToSet result = solver.makePointsToSet();
        if (signature.methodName() == null || signature.paramTypes() == null) {
            throw new RuntimeException("Unreachable");
        }
        Type returnType = signature.returnType();
        if (returnType != null && !returnType.equals(resultType)
                && !typeSystem.isSubtype(returnType, resultType)
                && !typeSystem.isSubtype(resultType, returnType)) {
            return result;
        }

        var ptp = findArgTypes(argObjs);
        if (ptp != null && !ptp.contains(signature.paramTypes())) {
            return result;
        }

        var possibleClasses = findClassByMethodSignature(signature.returnType(),
                signature.methodName(), signature.paramTypes());

        for (var possibleClass: possibleClasses) {
            result.addObject(solver.getCSManager().getCSObj(defaultHctx, heapModel.getMockObj(
                    MethodMetaObj.DESC,
                    MethodMetaObj.of(possibleClass, signature),
                    MethodMetaObj.TYPE
            )));
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

    private List<JClass> findClassByMethodSignature(@Nullable Type returnType, @NotNull String name,
                                                    @NotNull List<Type> paramTypes) {
        return hierarchy.allClasses().filter(klass -> {
            for (var method : klass.getDeclaredMethods()) {
                if (!method.isPublic()) {
                    continue;
                }
                if (!method.getName().equals(name)) {
                    continue;
                }
                if (returnType != null && !method.getReturnType().equals(returnType)) {
                    continue;
                }
                if (method.getParamTypes().size() != paramTypes.size()) {
                    continue;
                }
                boolean match = true;
                for (int i = 0; i < paramTypes.size(); i++) {
                    if (!method.getParamTypes().get(i).equals(paramTypes.get(i))) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return true;
                }
            }
            return false;
        }).toList();
    }
}
