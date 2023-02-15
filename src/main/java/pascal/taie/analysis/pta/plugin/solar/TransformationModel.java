package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.analysis.graph.callgraph.CallKind;
import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.util.AbstractModel;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.exp.InvokeVirtual;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.ArrayType;
import pascal.taie.language.type.ClassType;
import pascal.taie.language.type.NullType;
import pascal.taie.language.type.Type;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static pascal.taie.analysis.pta.plugin.solar.Util.constArraySize;
import static pascal.taie.analysis.pta.plugin.solar.Util.isLazyObjUnknownType;

class TransformationModel extends AbstractModel {
    private int freshVarCounter = 0;

    protected TransformationModel(Solver solver) {
        super(solver);
    }

    @Override
    protected void registerVarAndHandler() {
        JMethod methodInvoke = hierarchy.getJREMethod("<java.lang.reflect.Method: java.lang.Object invoke(java.lang.Object,java.lang.Object[])>");
        registerRelevantVarIndexes(methodInvoke, BASE, 0, 1);
        registerAPIHandler(methodInvoke, this::methodInvoke);
    }

    private void methodInvoke(CSVar csVar, PointsToSet pts, Invoke invoke) {
        Context context = csVar.getContext();
        List<PointsToSet> args = getArgs(csVar, pts, invoke, BASE, 0, 1);
        PointsToSet mtdMetaObjs = args.get(0);
        PointsToSet receiveObjs = args.get(1);
        PointsToSet argArrayObjs = args.get(2);
        var receiveVar = invoke.getInvokeExp().getArg(0);

        mtdMetaObjs.forEach(mtd -> {
            if (!(mtd.getObject().getAllocation() instanceof MethodMetaObj mtdMetaObj)) {
                return;
            }
            if (!mtdMetaObj.baseClassIsKnown()) {
                return;
            }
            Set<MethodRef> methodRefs = mtdMetaObj.search();
            Set<JMethod> methods = new HashSet<>();

            if (receiveVar.isConst() && receiveVar.getConstValue().getType() instanceof NullType) {
                for (MethodRef methodRef: methodRefs) {
                    if (!methodRef.isStatic()) {
                        continue;
                    }
                    JMethod callee = hierarchy.dispatch(mtdMetaObj.getBaseClass(), methodRef);
                    if (callee != null) {
                        methods.add(callee);
                    }
                }
            } else {
                for (var receiveObj: receiveObjs) {
                    if (!isLazyObjUnknownType(receiveObj)) {
                        Type receiveType = receiveObj.getObject().getType();
                        if (isConcerned(receiveType)) {
                            for (MethodRef methodRef : methodRefs) {
                                JMethod callee = hierarchy.dispatch(receiveType, methodRef);
                                if (callee != null) {
                                    methods.add(callee);
                                }
                            }
                        }
                    } else {
                        for (MethodRef methodRef : methodRefs) {
                            JMethod callee = methodRef.resolve();
                            if (callee != null) {
                                methods.add(callee);
                            }
                        }
                    }
                }
            }

            for (var callee: methods) {
                List<Var> params = new ArrayList<>();
                var declaredParamTypes = callee.getParamTypes();

                for (Type paramType : declaredParamTypes) {
                    params.add(
                            new Var(invoke.getContainer(),
                                    "%solar-param-" + freshVarCounter++,
                                    paramType, -1)
                    );
                }

                List<CSObj> possibleArrObjs = new ArrayList<>();
                boolean possible = false;
                for (var arr: argArrayObjs) {
                    var constArrSize = constArraySize(arr.getObject());
                    if (constArrSize != -1 && constArrSize != declaredParamTypes.size()) {
                        continue;
                    }
                    var index = csManager.getArrayIndex(arr);
                    possibleArrObjs.addAll(solver.getPointsToSetOf(index).getObjects());
                    possible = true;
                }

                var possible2 = declaredParamTypes.isEmpty();
                List<CSObj> possibleParamObjs = new ArrayList<>();
                for (var obj: possibleArrObjs) {
                    var objType = obj.getObject().getType();
                    boolean argPossible = true;
                    for (Type declaredParamType: declaredParamTypes) {
                        if (!objType.equals(declaredParamType)
                                && !typeSystem.isSubtype(declaredParamType, objType)) {
                            argPossible = false;
                            break;
                        }
                    }
                    if (argPossible) {
                        possible2 = true;
                        possibleParamObjs.add(obj);
                    }
                }

                possible = possible && possible2;

                if (!possible) {
                    continue;
                }

                for (var obj: possibleParamObjs) {
                    solver.addVarPointsTo(context, params.get(0), obj);
                }

                var mockInvoke = new Invoke(callee,
                        new InvokeVirtual(callee.getRef(), receiveVar, params));
                var mockCallSite = csManager.getCSCallSite(context, mockInvoke);
                var csCallee = csManager.getCSMethod(context, callee);
                solver.addCallEdge(new Edge<>(CallKind.VIRTUAL, mockCallSite, csCallee));
            }
        });
    }

    /**
     * TODO: merge with DefaultSolver.isConcerned(Exp)
     */
    private static boolean isConcerned(Type type) {
        return type instanceof ClassType || type instanceof ArrayType;
    }
}

