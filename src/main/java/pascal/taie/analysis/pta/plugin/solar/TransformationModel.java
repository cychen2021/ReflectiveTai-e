package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.analysis.graph.callgraph.CallKind;
import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.*;
import pascal.taie.analysis.pta.core.solver.PointerFlowEdge;
import pascal.taie.analysis.pta.plugin.util.AbstractModel;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.exp.InstanceFieldAccess;
import pascal.taie.ir.exp.InvokeVirtual;
import pascal.taie.ir.exp.StaticFieldAccess;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.proginfo.FieldRef;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.LoadField;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.ir.stmt.StoreField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.NullType;
import pascal.taie.language.type.Type;
import pascal.taie.util.collection.Pair;

import java.util.*;

class TransformationModel extends AbstractModel {
    private int freshVarCounter = 0;
    private final List<Pair<Set<CSVar>, ReflectionCallEdge>> pendingInvokes = new ArrayList<>();

    private abstract static class PointerPassingEdge {
        private final CSVar from;
        private final CSVar to;
        private final Type type;

        public PointerPassingEdge(CSVar from, CSVar to, Type type) {
            this.from = from;
            this.to = to;
            this.type = type;
        }

        public abstract PointerFlowEdge.Kind kind();

        public CSVar from() {
            return from;
        }

        public CSVar to() {
            return to;
        }

        public Type type() {
            return type;
        }
    }

    private static class ArgPassingEdge extends PointerPassingEdge {
        public ArgPassingEdge(CSVar from, CSVar to, Type type) {
            super(from, to, type);
        }

        @Override
        public PointerFlowEdge.Kind kind() {
            return PointerFlowEdge.Kind.PARAMETER_PASSING;
        }
    }

    private static class ReturnEdge extends PointerPassingEdge {
        public ReturnEdge(CSVar from, CSVar to, Type type) {
            super(from, to, type);
        }

        @Override
        public PointerFlowEdge.Kind kind() {
            return PointerFlowEdge.Kind.RETURN;
        }
    }

    private class ReflectionCallEdge {
        private final CSCallSite callSite;
        private final CSMethod callee;
        private final CSVar thisArg;
        private final List<CSVar> args;

        private final CSVar resultReceiver;
        private final CSCallSite realCallSite;

        public ReflectionCallEdge(CSCallSite mockCallSite, CSMethod callee, CSVar thisArg,
                                  List<CSVar> args, CSVar resultReceiver, CSCallSite realCallSite) {
            this.callSite = mockCallSite;
            this.callee = callee;
            this.thisArg = thisArg;
            this.args = args;
            this.resultReceiver = resultReceiver;
            this.realCallSite = realCallSite;
        }

        public Edge<CSCallSite, CSMethod> callEdge() {
            return new Edge<>(CallKind.VIRTUAL, callSite, callee);
        }

        public CSCallSite getRealCallSite() {
            return realCallSite;
        }

        public JMethod getCallee() {
            return callee.getMethod();
        }

        public List<PointerPassingEdge> pfgEdges() {
            Context calleeContext = callee.getContext();
            JMethod calleeMethod = callee.getMethod();
            Var thisVar = calleeMethod.getIR().getThis();
            List<Var> argVars = calleeMethod.getIR().getParams();

            List<PointerPassingEdge> result = new ArrayList<>();
            if (thisVar != null) {
                result.add(
                        new ArgPassingEdge(
                                thisArg,
                                csManager.getCSVar(calleeContext, thisVar),
                                thisVar.getType()
                        )
                );
            }

            for (int i = 0; i < args.size(); i++) {
                result.add(
                        new ArgPassingEdge(
                                args.get(i),
                                csManager.getCSVar(calleeContext, argVars.get(i)),
                                argVars.get(i).getType()
                        )
                );
            }

            if (resultReceiver == null) {
                return result;
            }

            List<Var> returnVars = calleeMethod.getIR().getReturnVars();
            for (Var returnVar: returnVars) {
                result.add(
                        new ReturnEdge(
                                csManager.getCSVar(calleeContext, returnVar),
                                resultReceiver,
                                calleeMethod.getReturnType()
                        )
                );
            }

            return result;
        }
    }

    private final SolarAnalysis solarAnalysis;

    public TransformationModel(SolarAnalysis solarAnalysis) {
        super(solarAnalysis.getSolver());
        this.solarAnalysis = solarAnalysis;
    }

    @Override
    protected void registerVarAndHandler() {
        JMethod methodInvoke = hierarchy.getJREMethod("<java.lang.reflect.Method: java.lang.Object invoke(java.lang.Object,java.lang.Object[])>");
        registerRelevantVarIndexes(methodInvoke, BASE, 0, 1);
        registerAPIHandler(methodInvoke, this::methodInvoke);

        JMethod fieldGet = hierarchy.getJREMethod("<java.lang.reflect.Field: java.lang.Object get(java.lang.Object)>");
        registerRelevantVarIndexes(fieldGet, BASE);
        registerAPIHandler(fieldGet, this::fieldGet);

        JMethod fieldSet = hierarchy.getJREMethod("<java.lang.reflect.Field: void set(java.lang.Object,java.lang.Object)>");
        registerRelevantVarIndexes(fieldSet, BASE);
        registerAPIHandler(fieldSet, this::fieldSet);
    }

    private void fieldSet(CSVar csVar, PointsToSet pts, Invoke invoke) {
        Context context = csVar.getContext();
        List<PointsToSet> args = getArgs(csVar, pts, invoke, BASE);
        PointsToSet fieldMetaObjs = args.get(0);
        var baseVar = invoke.getInvokeExp().getArg(0);
        var valueVar = invoke.getInvokeExp().getArg(1);
        var container = csManager.getCSMethod(context, invoke.getContainer());

        CSCallSite csCallSite = csManager.getCSCallSite(context, invoke);

        fieldMetaObjs.forEach(csFieldObj -> {
            if (!(csFieldObj.getObject().getAllocation() instanceof FieldMetaObj fieldMetaObj)) {
                return;
            }
            if (!fieldMetaObj.baseClassIsKnown()) {
                return;
            }
            Set<FieldRef> fieldRefs = fieldMetaObj.search();

            boolean isStatic =
                    baseVar.isConst() && baseVar.getConstValue().getType() instanceof NullType;

            List<Stmt> mockStmts = new ArrayList<>();
            for (var fieldRef: fieldRefs) {
                StoreField mockStore;
                var field = fieldRef.resolve();
                if (isStatic) {
                    if (!field.isStatic()) {
                        continue;
                    }
                    mockStore = new StoreField(new StaticFieldAccess(fieldRef), valueVar);
                } else {
                    if (field.isStatic()) {
                        continue;
                    }
                    mockStore = new StoreField(new InstanceFieldAccess(fieldRef, baseVar), valueVar);
                }
                mockStmts.add(mockStore);
                solarAnalysis.getQualityInterpreter().addReflectiveObject(csCallSite, field);
            }
            solver.addStmts(container, mockStmts);
        });
    }

    private void fieldGet(CSVar csVar, PointsToSet pts, Invoke invoke) {
        Context context = csVar.getContext();
        List<PointsToSet> args = getArgs(csVar, pts, invoke, BASE);
        PointsToSet fieldMetaObjs = args.get(0);
        var baseVar = invoke.getInvokeExp().getArg(0);
        var resultVar = invoke.getResult();
        var container = csManager.getCSMethod(context, invoke.getContainer());
        CSCallSite csCallSite = csManager.getCSCallSite(context, invoke);

        fieldMetaObjs.forEach(csFieldObj -> {
            if (!(csFieldObj.getObject().getAllocation() instanceof FieldMetaObj fieldMetaObj)) {
                return;
            }
            if (!fieldMetaObj.baseClassIsKnown()) {
                return;
            }
            Set<FieldRef> fieldRefs = fieldMetaObj.search();

            boolean isStatic =
                    baseVar.isConst() && baseVar.getConstValue().getType() instanceof NullType;

            List<Stmt> mockStmts = new ArrayList<>();
            for (var fieldRef: fieldRefs) {
                LoadField mockLoad;
                var field = fieldRef.resolve();
                if (isStatic) {
                    if (!field.isStatic()) {
                        continue;
                    }
                    mockLoad = new LoadField(resultVar, new StaticFieldAccess(fieldRef));
                } else {
                    if (field.isStatic()) {
                        continue;
                    }
                    mockLoad = new LoadField(resultVar, new InstanceFieldAccess(fieldRef, baseVar));
                }
                mockStmts.add(mockLoad);
                solarAnalysis.getQualityInterpreter().addReflectiveObject(csCallSite, field);
            }
            solver.addStmts(container, mockStmts);
        });
    }

    public void watchFreshArgs(CSVar csVar, PointsToSet pts) {
        Stack<Integer> toRemove = new Stack<>();
        outer:for (int i = 0; i < pendingInvokes.size(); i++) {
            var pair = pendingInvokes.get(i);
            var relevantVars = pair.first();
            if (relevantVars.isEmpty() || relevantVars.contains(csVar)) {
                for (var arg: relevantVars) {
                    var argPts = arg == csVar ? pts : solver.getPointsToSetOf(arg);
                    if (argPts == null || argPts.isEmpty()) {
                        continue outer;
                    }
                }
                var edge = pair.second();
                if (edge.getCallee().getDeclaringClass().toString().equals("D")) {
                    System.out.println("HIT");
                }
                solver.addCallEdge(edge.callEdge());

                var pfgEdges = edge.pfgEdges();
                for (var pfgEdge: pfgEdges) {
                    solver.addPFGEdge(pfgEdge.from(), pfgEdge.to(), pfgEdge.kind(), pfgEdge.type());
                }
                if (toRemove.isEmpty() || toRemove.peek() != i) {
                    toRemove.push(i);
                }
                solarAnalysis.getQualityInterpreter().addReflectiveObject(edge.getRealCallSite(), edge.getCallee());
            }
        }
        while (!toRemove.isEmpty()) {
            pendingInvokes.remove(toRemove.pop().intValue());
        }
    }

    private void methodInvoke(CSVar csVar, PointsToSet pts, Invoke invoke) {
        Context context = csVar.getContext();
        List<PointsToSet> args = getArgs(csVar, pts, invoke, BASE, 0, 1);
        PointsToSet mtdMetaObjs = args.get(0);
        PointsToSet receiveObjs = args.get(1);
        PointsToSet argArrayObjs = args.get(2);
        var receiveVar = invoke.getInvokeExp().getArg(0);
        Var resultVar = invoke.getResult();

        CSCallSite realCallSite = csManager.getCSCallSite(context, invoke);

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
                    for (CSObj receiveObj: receiveObjs) {
                        Type receiveType = receiveObj.getObject().getType();
                        if (!Util.isConcerned(receiveType)) {
                            continue;
                        }
                        JMethod callee = hierarchy.dispatch(receiveType, methodRef);
                        if (callee != null) {
                            methods.add(callee);
                        }
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

                boolean satisfied = false;
                for (var arrObj: argArrayObjs) {
                    int arrLen = Util.constArraySize(arrObj.getObject());
                    if (arrLen != -1 && arrLen != declaredParamTypes.size()) {
                        continue;
                    }
                    satisfied = true;
                    ArrayIndex index = csManager.getArrayIndex(arrObj);
                    for (var csFreshArg: csFreshArgs) {
                        solver.addPFGEdge(index, csFreshArg, PointerFlowEdge.Kind.ARRAY_LOAD, csFreshArg.getType());
                    }
                }

                if (!satisfied) {
                    continue;
                }

                var mockInvoke = new Invoke(callee,
                        new InvokeVirtual(callee.getRef(), receiveVar, freshArgs));
                var mockCallSite = csManager.getCSCallSite(context, mockInvoke);
                var csCallee = csManager.getCSMethod(context, callee);
                pendingInvokes.add(new Pair<>(new HashSet<>(csFreshArgs),
                        new ReflectionCallEdge(
                                mockCallSite,
                                csCallee,
                                csManager.getCSVar(context, receiveVar),
                                csFreshArgs,
                                resultVar != null ? csManager.getCSVar(context, resultVar) : null,
                                realCallSite
                        )
                ));
            }
        });
    }
}

