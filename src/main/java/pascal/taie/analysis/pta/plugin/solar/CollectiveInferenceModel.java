package pascal.taie.analysis.pta.plugin.solar;

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
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.ArrayType;
import pascal.taie.language.type.ClassType;
import pascal.taie.language.type.PrimitiveType;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.TypeSystem;

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
        InvokeInstanceExp iExp = (InvokeInstanceExp) invoke.getInvokeExp(); // FIXME
        Var m = iExp.getBase();
        Type A;
        if (x != null) {
            A = x.getType();
        } else {
            A = iExp.getType();
        }

        PointsToSet mtdObjs = args.get(0); // m
        PointsToSet recvObjs = args.get(1); // y
        PointsToSet argObjs = args.get(2); // args
        Context context = csVar.getContext();

        mtdObjs.forEach(mtdObj -> {
            if (mtdObj.getObject().getAllocation() instanceof MethodMetaObj methodMetaObj) {

                boolean mtdSigKnown = methodMetaObj.methodNameKnown() && methodMetaObj.returnTypeKnown()
                        && methodMetaObj.parameterTypesKnown();

                if (!methodMetaObj.baseClassKnown()) {
                    solver.addVarPointsTo(context, m, invTp(recvObjs));

                    if (mtdSigKnown) { // TODO pt(y) is not checked
                        // validate the method signature inside this sub-function
                        solver.addVarPointsTo(context, m, invS2T(methodMetaObj));
                    }
                }

                if (!mtdSigKnown) {
                    solver.addVarPointsTo(context, m, invSig(argObjs, A));
                }
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
            if (recvObj.getObject().getAllocation() instanceof ClassMetaObj classMetaObj) {
                if (classMetaObj.isKnown()) {
                    MethodMetaObj methodMetaObj = MethodMetaObj.unknown(classMetaObj.getJClass(), null,
                            null, null);
                    Obj newMethodObj = heapModel.getMockObj(MethodMetaObj.DESC, methodMetaObj,
                            MethodMetaObj.TYPE);

                    result.addObject(solver.getCSManager().getCSObj(defaultHctx, newMethodObj));
                }
            }
        }

        return result;
    }

    /**
     * I-InvSig: use the information available at a call site (excluding y) to infer
     * the descriptor of a method signature
     *
     * @param argObjs    points-to set of args in `x = (A) m.invoke(y, args)`
     * @param resultType A
     * @return new objects pointed to by `m`
     */
    private PointsToSet invSig(PointsToSet argObjs, Type resultType) {
        PointsToSet result = solver.makePointsToSet();

        for (List<Type> paramTypes : parameterTypesEnumeration(argObjs)) {
            for (Type returnType : findReturnTypes(resultType)) {
                MethodMetaObj methodMetaObj = MethodMetaObj.unknown(null, null, paramTypes, returnType);
                Obj newMethodObj = heapModel.getMockObj(MethodMetaObj.DESC, methodMetaObj, MethodMetaObj.TYPE);
                result.addObject(solver.getCSManager().getCSObj(defaultHctx, newMethodObj));
            }
        }

        return result;
    }

    /**
     * I-InvS2T: use a method signature to infer the class type of a method
     *
     * @param methodMetaObj signature and parent class of a method
     * @return new objects pointed to by `m`
     */
    private PointsToSet invS2T(MethodMetaObj methodMetaObj) {
        PointsToSet result = solver.makePointsToSet();

        for (JClass jClass : findClassByMethodSignature(methodMetaObj)) {
            MethodMetaObj m = MethodMetaObj.unknown(jClass, methodMetaObj.getMethodName(),
                    methodMetaObj.getParameterTypes(), methodMetaObj.getReturnType());
            Obj newMethodObj = heapModel.getMockObj(MethodMetaObj.DESC, m, MethodMetaObj.TYPE);
            result.addObject(solver.getCSManager().getCSObj(defaultHctx, newMethodObj));
        }
        return result;
    }

    private boolean parameterTypesValidation(MethodMetaObj methodMetaObj, PointsToSet argObjs) {
        boolean result = false;
        List<Type> parameterTypes = methodMetaObj.getParameterTypes();
        // TODO
        return result;
    }

    private List<List<Type>> parameterTypesEnumeration(PointsToSet argObjs) {
        List<List<Type>> result = List.of();
        // TODO
        return result;
    }

    private List<JClass> findClassByMethodSignature(MethodMetaObj metaObj) {
        List<JClass> result = List.of();
        for (MethodRef method : metaObj.search()) {
            result.add(method.getDeclaringClass());
        }
        return result;
    }

    private List<Type> findReturnTypes(Type A) {
        List<Type> result = List.of();
        ClassHierarchy classHierarchy = solver.getHierarchy();
        TypeSystem typeSystem = solver.getTypeSystem();

        if (A instanceof ClassType classType) {
            JClass jClass = classType.getJClass();

            // add A's subclasses (including A itself)
            for (JClass subclass : classHierarchy.getAllSubclassesOf(jClass)) {
                result.add(typeSystem.getClassType(subclass.getName()));
            }
            // add parent classes of A (except Object)
            classHierarchy.allClasses().filter(c -> classHierarchy.isSubclass(jClass, c))
                    .filter(c -> !(c.getSimpleName() == "Object"))
                    .forEach(c -> result.add(typeSystem.getClassType(c.getName())));

        } else if (A instanceof PrimitiveType primitiveType) {
            result.add(typeSystem.getBoxedType(primitiveType));
        }
        return result;
    }
}
