package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.analysis.graph.callgraph.CallKind;
import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.ArrayIndex;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.solver.PointerFlowEdge;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.util.AbstractModel;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.exp.InvokeVirtual;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.NullType;
import pascal.taie.language.type.Type;
import pascal.taie.util.collection.Pair;

import java.util.*;

class TransformationModel extends AbstractModel {
    private int freshVarCounter = 0;

    protected TransformationModel(Solver solver) {
        super(solver);
    }

    @Override
    protected void registerVarAndHandler() {
        JMethod methodInvoke = hierarchy.getJREMethod("<java.lang.reflect.Method: java.lang.Object invoke(java.lang.Object,java.lang.Object[])>");
        registerRelevantVarIndexes(methodInvoke, BASE, 1);
        registerAPIHandler(methodInvoke, this::methodInvoke);
    }

    public void watchFreshArgs(CSVar csVar, PointsToSet pts) {
        List<Integer> toRemove = new ArrayList<>();
        outer: for (int i = 0; i < pendingInvokes.size(); i++) {
            var pair = pendingInvokes.get(i);
            var relevantVars = pair.first();
            if (relevantVars.contains(csVar)) {
                for (var arg: relevantVars) {
                    var argPts = solver.getPointsToSetOf(arg);
                    if (argPts == null || argPts.isEmpty()) {
                        continue outer;
                    }
                }
                var edge = pair.second();
                solver.addCallEdge(edge);
                toRemove.add(i);
            }
        }
        for (var idx: toRemove) {
            pendingInvokes.remove(idx.intValue());
        }
    }

    private List<Pair<Set<CSVar>, Edge<CSCallSite, CSMethod>>> pendingInvokes = new ArrayList<>();

    private void methodInvoke(CSVar csVar, PointsToSet pts, Invoke invoke) {
        Context context = csVar.getContext();
        List<PointsToSet> args = getArgs(csVar, pts, invoke, BASE, 1);
        PointsToSet mtdMetaObjs = args.get(0);
        PointsToSet argArrayObjs = args.get(1);
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
                for (MethodRef methodRef : methodRefs) {
                    if (!mtdMetaObj.baseClassIsKnown()) {
                        continue;
                    }
                    JMethod callee = hierarchy.dispatch(mtdMetaObj.getBaseClass(), methodRef);
                    if (callee != null) {
                        methods.add(callee);
                    }
                }
            }

            for (var callee: methods) {
                List<Var> freshArgs = new ArrayList<>();
                var declaredParamTypes = callee.getParamTypes();

                for (Type paramType : declaredParamTypes) {
                    freshArgs.add(
                            new Var(invoke.getContainer(),
                                    "%solar-transformation-fresh-arg-" + freshVarCounter++,
                                    paramType, -1)
                    );
                }

                var csFreshArgs = freshArgs.stream().map(arg -> csManager.getCSVar(context, arg)).toList();

                for (var arrObj: argArrayObjs) {
                    ArrayIndex index = csManager.getArrayIndex(arrObj);
                    for (var csFreshArg: csFreshArgs) {
                        solver.addPFGEdge(index, csFreshArg, PointerFlowEdge.Kind.ARRAY_LOAD, csFreshArg.getType());
                    }
                }

                var mockInvoke = new Invoke(callee,
                        new InvokeVirtual(callee.getRef(), receiveVar, freshArgs));
                var mockCallSite = csManager.getCSCallSite(context, mockInvoke);
                var csCallee = csManager.getCSMethod(context, callee);
                pendingInvokes.add(new Pair<>(new HashSet<>(csFreshArgs), new Edge<>(CallKind.VIRTUAL, mockCallSite, csCallee)));
            }
        });
    }
}

