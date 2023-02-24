package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.stmt.Cast;
import pascal.taie.language.classes.JMethod;

public class SolarAnalysis implements Plugin {
    private PropagationModel propagationModel;
    private TransformationModel transformationModel;
    private CollectiveInferenceModel collectiveInferenceModel;
    private LazyHeapModel lazyHeapModel;

    @Override
    public void setSolver(Solver solver) {
        LazyObj.Builder lazyObjBuilder = new LazyObj.Builder(solver.getTypeSystem());
        MethodMetaObj.Builder methodMetaObjBuilder = new MethodMetaObj.Builder(solver.getTypeSystem());
        FieldMetaObj.Builder fieldMetaObjBuilder = new FieldMetaObj.Builder(solver.getTypeSystem());
        this.propagationModel = new PropagationModel(solver, new ClassMetaObj.Builder(solver.getTypeSystem()),
                methodMetaObjBuilder, fieldMetaObjBuilder);
        this.transformationModel = new TransformationModel(solver);
        this.collectiveInferenceModel = new CollectiveInferenceModel(solver, lazyObjBuilder,
                methodMetaObjBuilder, fieldMetaObjBuilder);
        this.lazyHeapModel = new LazyHeapModel(solver, lazyObjBuilder);
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
