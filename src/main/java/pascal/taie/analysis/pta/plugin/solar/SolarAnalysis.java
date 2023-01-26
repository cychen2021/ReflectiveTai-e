package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.language.classes.JMethod;

public class SolarAnalysis implements Plugin {
    private PropagationModel propagationModel;
    private TransformationModel transformationModel;
    private CollectiveInferenceModel collectiveInferenceModel;
    private LazyHeapModel lazyHeapModel;

    @Override
    public void setSolver(Solver solver) {
        this.propagationModel = new PropagationModel(solver);
        this.transformationModel = new TransformationModel(solver);
        this.collectiveInferenceModel = new CollectiveInferenceModel(solver);
        this.lazyHeapModel = new LazyHeapModel(solver);
    }

    @Override
    public void onNewPointsToSet(CSVar csVar, PointsToSet pts) {
        if (propagationModel.isRelevantVar(csVar.getVar())) {
            propagationModel.handleNewPointsToSet(csVar, pts);
        }
        if (transformationModel.isRelevantVar(csVar.getVar())) {
            transformationModel.handleNewPointsToSet(csVar, pts);
        }
        if (collectiveInferenceModel.isRelevantVar(csVar.getVar())) {
            collectiveInferenceModel.handleNewPointsToSet(csVar, pts);
        }
        if (lazyHeapModel.isRelevantVar(csVar.getVar())) {
            lazyHeapModel.handleNewPointsToSet(csVar, pts);
        }
    }

    @Override
    public void onNewMethod(JMethod method) {
        method.getIR().invokes(false).forEach(invoke -> {
            propagationModel.handleNewInvoke(invoke);
            transformationModel.handleNewInvoke(invoke);
            collectiveInferenceModel.handleNewInvoke(invoke);
            lazyHeapModel.handleNewInvoke(invoke);
        });
    }
}
