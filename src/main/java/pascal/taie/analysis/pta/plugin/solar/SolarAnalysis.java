package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.stmt.Cast;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;

public class SolarAnalysis implements Plugin {
    private PropagationModel propagationModel;
    private TransformationModel transformationModel;
    private CollectiveInferenceModel collectiveInferenceModel;
    private LazyHeapModel lazyHeapModel;

    private Type LAZY_OBJ_UNKNOWN_TYPE;

    @Override
    public void setSolver(Solver solver) {
        this.LAZY_OBJ_UNKNOWN_TYPE = solver.getHierarchy().getClass("java.lang.Object").getType();
        this.propagationModel = new PropagationModel(solver);
        this.transformationModel = new TransformationModel(solver);
        this.collectiveInferenceModel = new CollectiveInferenceModel(solver, LAZY_OBJ_UNKNOWN_TYPE);
        this.lazyHeapModel = new LazyHeapModel(solver, LAZY_OBJ_UNKNOWN_TYPE);
    }

    @Override
    public void onNewPointsToSet(CSVar csVar, PointsToSet pts) {
        if (propagationModel.isRelevantVar(csVar.getVar())) {
            propagationModel.handleNewPointsToSet(csVar, pts);
        }
        if (collectiveInferenceModel.isRelevantVar(csVar.getVar())) {
            collectiveInferenceModel.handleNewPointsToSet(csVar, pts);
        }
        if (lazyHeapModel.isRelevantVar(csVar.getVar())) {
            lazyHeapModel.handleNewPointsToSet(csVar, pts);
        }
        if (transformationModel.isRelevantVar(csVar.getVar())) {
            transformationModel.handleNewPointsToSet(csVar, pts);
        }
        transformationModel.watchFreshArgs(csVar, pts);
    }

    @Override
    public void onNewMethod(JMethod method) {
        method.getIR().invokes(false).forEach(invoke -> {
            propagationModel.handleNewInvoke(invoke);
            collectiveInferenceModel.handleNewInvoke(invoke);
            transformationModel.handleNewInvoke(invoke);
            lazyHeapModel.handleNewInvoke(invoke);
        });
        method.getIR().stmts().forEach(stmt -> {
            if (stmt instanceof Cast cast){
                lazyHeapModel.handleNewCast(cast);
                collectiveInferenceModel.handleNewCast(cast);
            }
        });
    }
}
