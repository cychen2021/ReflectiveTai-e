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

class LazyHeapModel extends AbstractModel {
    protected LazyHeapModel(Solver solver) {
        super(solver);
    }

    @Override
    protected void registerVarAndHandler() {
        JMethod classNewInstance = hierarchy.getJREMethod("<java.lang.Class: java.lang.Object newInstance()>");
        registerRelevantVarIndexes(classNewInstance, BASE);
        registerAPIHandler(classNewInstance, this::classNewInstance);
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
                Obj obj = heapModel.getMockObj("LazyObj", LazyObj.TYPE_KNOWN,
                        classMetaObj.getJClass().getType());
                csObj = csManager.getCSObj(context, obj);
            } else {
                Obj obj = heapModel.getMockObj("LazyObj", LazyObj.TYPE_UNKNOWN, hierarchy.getClass("java.lang.Object").getType());
                csObj = csManager.getCSObj(context, obj);
            }
            solver.addVarPointsTo(context, result, csObj);
        });
    }
}
