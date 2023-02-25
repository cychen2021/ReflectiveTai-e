package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.plugin.util.AbstractModel;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.JMethod;
import pascal.taie.ir.exp.CastExp;
import pascal.taie.ir.stmt.Cast;
import pascal.taie.language.type.ClassType;
import pascal.taie.util.TriConsumer;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.language.type.Type;
import pascal.taie.util.collection.Sets;

import java.util.Set;

class LazyHeapModel extends AbstractModel {
    protected final MultiMap<Var, Cast> castRelevantVars = Maps.newMultiMap(Maps.newHybridMap());

    protected final Set<TriConsumer<CSVar, PointsToSet, Cast>> castHandlers
            = Sets.newHybridSet();

    private final SolarAnalysis solarAnalysis;
    LazyHeapModel(SolarAnalysis solarAnalysis) {
        super(solarAnalysis.getSolver());
        this.solarAnalysis = solarAnalysis;
    }

    @Override
    protected void registerVarAndHandler() {
        JMethod classNewInstance = hierarchy.getJREMethod("<java.lang.Class: java.lang.Object newInstance()>");
        registerRelevantVarIndexes(classNewInstance, BASE);
        registerAPIHandler(classNewInstance, this::classNewInstance);

        JMethod methodInvoke = hierarchy.getJREMethod(
                "<java.lang.reflect.Method: java.lang.Object invoke(java.lang.Object,java.lang.Object[])>");
        registerRelevantVarIndexes(methodInvoke, BASE, 0);
        registerAPIHandler(methodInvoke, this::methodInvoke);

        JMethod fieldGet = hierarchy.getJREMethod(
                "<java.lang.reflect.Field: java.lang.Object get(java.lang.Object)>");
        registerRelevantVarIndexes(fieldGet, BASE, 0);
        registerAPIHandler(fieldGet, this::fieldAccess);

        JMethod fieldSet = hierarchy.getJREMethod(
                "<java.lang.reflect.Field: void set(java.lang.Object,java.lang.Object)>");
        registerRelevantVarIndexes(fieldSet, BASE, 0);
        registerAPIHandler(fieldSet, this::fieldAccess);
    }

    private void fieldAccess(CSVar csVar, PointsToSet pointsToSet, Invoke invoke) {
        var args = getArgs(csVar, pointsToSet, invoke, BASE, 0);
        var baseObjs = args.get(0);
        var argObjs = args.get(1);
        if (argObjs.objects().noneMatch(argObj -> {
            if (argObj.getObject().getAllocation() instanceof LazyObj lazyObj) {
                return !lazyObj.isKnown();
            }
            return false;
        })) {
            return;
        }
        Var firstArg = invoke.getInvokeExp().getArg(0);
        Context context = csVar.getContext();
        baseObjs.forEach(baseObj -> {
            if (baseObj.getObject().getAllocation() instanceof FieldMetaObj metaObj) {
                if (metaObj.baseClassIsKnown()) {
                    var baseClass = metaObj.getBaseClass();
                    var possibleClasses = hierarchy.getAllSubclassesOf(baseClass);
                    possibleClasses.addAll(Util.superClassesOf(baseClass));
                    possibleClasses.remove(((ClassType)solarAnalysis.getLazyObjBuilder().getUnknownType()).getJClass());
                    for (var possibleClass: possibleClasses) {
                        LazyObj lazyObj = solarAnalysis.getLazyObjBuilder().known(possibleClass.getType());
                        Obj obj = heapModel.getMockObj(lazyObj.getDesc(), lazyObj,
                                lazyObj.getType());
                        CSObj csObj = csManager.getCSObj(context, obj);
                        solver.addVarPointsTo(context, firstArg, csObj);
                    }
                }
            }
        });
    }

    private void methodInvoke(CSVar csVar, PointsToSet pointsToSet, Invoke invoke) {
        var args = getArgs(csVar, pointsToSet, invoke, BASE, 0);
        var baseObjs = args.get(0);
        var argObjs = args.get(1);
        if (argObjs.objects().noneMatch(argObj -> {
            if (argObj.getObject().getAllocation() instanceof LazyObj lazyObj) {
                return !lazyObj.isKnown();
            }
            return false;
        })) {
            return;
        }
        Var firstArg = invoke.getInvokeExp().getArg(0);
        Context context = csVar.getContext();
        baseObjs.forEach(baseObj -> {
            if (baseObj.getObject().getAllocation() instanceof MethodMetaObj metaObj) {
                if (metaObj.baseClassIsKnown()) {
                    var baseClass = metaObj.getBaseClass();
                    var possibleClasses = hierarchy.getAllSubclassesOf(baseClass);
                    possibleClasses.addAll(Util.superClassesOf(baseClass));
                    possibleClasses.remove(((ClassType) solarAnalysis.getLazyObjBuilder().getUnknownType()).getJClass());
                    for (var possibleClass: possibleClasses) {
                        LazyObj lazyObj = solarAnalysis.getLazyObjBuilder().known(possibleClass.getType());
                        Obj obj = heapModel.getMockObj(lazyObj.getDesc(), lazyObj,
                                lazyObj.getType());
                        CSObj csObj = csManager.getCSObj(context, obj);
                        solver.addVarPointsTo(context, firstArg, csObj);
                    }
                }
            }
        });
    }

    protected void registerCastHandlers() {
        castHandlers.add(this::castHandler);
    }

    private void classNewInstance(CSVar csVar, PointsToSet pts, Invoke invoke) {
        var args = getArgs(csVar, pts, invoke, BASE);
        var baseObjs = args.get(0);
        Var result = invoke.getResult();
        Context context = csVar.getContext();
        baseObjs.forEach(baseObj -> {
            if (!(baseObj.getObject().getAllocation() instanceof ClassMetaObj classMetaObj)) {
                return;
            }
            LazyObj lazyObj;
            if (classMetaObj.isKnown()) {
                lazyObj = solarAnalysis.getLazyObjBuilder().known(classMetaObj.getJClass().getType());
            } else {
                lazyObj = solarAnalysis.getLazyObjBuilder().unknown();
            }
            Obj obj = heapModel.getMockObj(lazyObj.getDesc(), lazyObj,
                    lazyObj.getType());
            CSObj csObj = csManager.getCSObj(context, obj);
            solver.addVarPointsTo(context, result, csObj);
        });
    }

    private void castHandler(CSVar csVar, PointsToSet pts, Cast cast) {
        CastExp exp = cast.getRValue();
        assert csVar.getVar().equals(exp.getValue());
        Var target = cast.getLValue();
        Type castType = exp.getCastType();
        Context context = csVar.getContext();

        pts.forEach(srcObj -> {
            if (srcObj.getObject().getAllocation() instanceof LazyObj lazyObj) {
                if (!lazyObj.isKnown()) {
                    LazyObj newLazyObj = solarAnalysis.getLazyObjBuilder().known(castType);
                    Obj obj = heapModel.getMockObj(newLazyObj.getDesc(), newLazyObj, newLazyObj.getType());
                    CSObj csObj = csManager.getCSObj(context, obj);
                    solver.addVarPointsTo(context, target, csObj);
                }
            }
        });
    }

    public void handleNewCast(Cast cast) {
        CastExp exp = cast.getRValue();
        Type castType = exp.getCastType();
        if (castType.equals(solarAnalysis.getLazyObjBuilder().getUnknownType())) {
            return;
        }
        Var source = exp.getValue();
        castRelevantVars.put(source, cast);
    }

    @Override
    public void handleNewPointsToSet(CSVar csVar, PointsToSet pts) {
        super.handleNewPointsToSet(csVar, pts);
        castRelevantVars.get(csVar.getVar()).forEach(cast -> {
            castHandlers.forEach(handler -> handler.accept(csVar, pts, cast));
        });
    }

    @Override
    public boolean isRelevantVar(Var var) {
        return super.isRelevantVar(var) || castRelevantVars.containsKey(var);
    }
}
