package pascal.taie.analysis.pta.plugin.solar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pascal.taie.analysis.pta.core.cs.context.Context;
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

import static pascal.taie.analysis.pta.plugin.solar.Util.*;

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

        mtdObjs.forEach(mtdObj -> {
            if (!(mtdObj.getObject().getAllocation() instanceof MethodMetaObj methodMetaObj)) {
                return;
            }
            boolean mtdSigKnown = methodMetaObj.methodNameKnown() && methodMetaObj.returnTypeKnown()
                    && methodMetaObj.parameterTypesKnown();
            if (!methodMetaObj.baseClassKnown()) {
                solver.addVarPointsTo(context, m, invTp(recvObjs));
                if (mtdSigKnown) { // TODO pt(y) is not checked
                    // validate the method signature inside this sub-function
                    solver.addVarPointsTo(context, m, invS2T(mtdObjs, argObjs, A));
                }
            }

            if (!mtdSigKnown) {
                solver.addVarPointsTo(context, m, invSig(methodMetaObj.getBaseClass(), argObjs, A));
            }
        });
    }

    /**
     * I-InvTp: use the type(s) of pt(y) to infer the class type of a method
     *
     * @param recvObjs points-to set of the receiver object `y`
     * @return new objects pointed to by `m`
     */
    private PointsToSet invTp(PointsToSet recvObjs) {
        PointsToSet result = solver.makePointsToSet();

        for (CSObj recvObj : recvObjs) {
            if (!(recvObj.getObject().getAllocation() instanceof ClassMetaObj classMetaObj)) {
                continue;
            }
            if (classMetaObj.isKnown()) {
                MethodMetaObj methodMetaObj = MethodMetaObj.unknown(classMetaObj.getJClass(), null,
                        null, null, null);
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
     * @param baseClass base class of the method
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

        // TODO: Refine the search based on known arg types.
        for (var possibleReturnTypes: allPossibleReturnTypes) {
            result.addObject(solver.getCSManager().getCSObj(defaultHctx, heapModel.getMockObj(
                    MethodMetaObj.DESC,
                    MethodMetaObj.unknown(baseClass, null, null, possibleReturnTypes, null),
                    MethodMetaObj.TYPE
            )));
        }

        return result;
    }

    /**
     * I-InvS2T: use a method signature to infer the class type of a method
     *
     * @param mtdObjs    points-to set of the method object `m` in `x = (A)
     *                   m.invoke(y, args)`
     * @param argObjs    `args`
     * @param resultType A
     * @return new objects pointed to by `m`
     */
    private PointsToSet invS2T(PointsToSet mtdObjs, PointsToSet argObjs, Type resultType) {
        PointsToSet result = solver.makePointsToSet();
        // TODO
        return result;
    }

    private List<JClass> findClassByMethodSignature(MethodMetaObj metaObj) {
        List<JClass> result = List.of();
        // TODO
        return result;
    }
}
