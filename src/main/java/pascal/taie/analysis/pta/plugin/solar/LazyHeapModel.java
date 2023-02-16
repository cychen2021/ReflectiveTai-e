package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.util.AbstractModel;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.JMethod;
import pascal.taie.ir.exp.CastExp;
import pascal.taie.ir.stmt.Cast;
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

    public final Type LAZY_OBJ_UNKNOWN_TYPE
            = hierarchy.getClass("java.lang.Object").getType();

    public final String LAZY_OBJ_DESC = "LazyObj";

    LazyHeapModel(Solver solver) {
        super(solver);
        registerCastHandlers();
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
    }

    private void methodInvoke(CSVar csVar, PointsToSet pointsToSet, Invoke invoke) {
        var args = getArgs(csVar, pointsToSet, invoke, BASE, 0);
        var baseObjs = args.get(0);
        var argObjs = args.get(1);
        if (argObjs.objects().noneMatch(argObj -> {
            if (argObj.getObject().getAllocation() instanceof LazyObj lazyObj) {
                return lazyObj == LazyObj.TYPE_UNKNOWN;
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
                    for (var possibleClass: possibleClasses) {
                        Obj obj = heapModel.getMockObj(LAZY_OBJ_DESC, LazyObj.TYPE_KNOWN,
                                possibleClass.getType());
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
            CSObj csObj;
            if (classMetaObj.isKnown()) {
                Obj obj = heapModel.getMockObj(LAZY_OBJ_DESC, LazyObj.TYPE_KNOWN,
                        classMetaObj.getJClass().getType());
                csObj = csManager.getCSObj(context, obj);
            } else {
                Obj obj = heapModel.getMockObj(LAZY_OBJ_DESC, LazyObj.TYPE_UNKNOWN, LAZY_OBJ_UNKNOWN_TYPE);
                csObj = csManager.getCSObj(context, obj);
            }
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
                if (lazyObj == LazyObj.TYPE_UNKNOWN) {
                    Obj obj = heapModel.getMockObj(LAZY_OBJ_DESC, LazyObj.TYPE_KNOWN, castType);
                    CSObj csObj = csManager.getCSObj(context, obj);
                    solver.addVarPointsTo(context, target, csObj);
                }
            }
        });
    }

    public void handleNewCast(Cast cast) {
        CastExp exp = cast.getRValue();
        Type castType = exp.getCastType();
        if (castType.equals(LAZY_OBJ_UNKNOWN_TYPE)) {
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
