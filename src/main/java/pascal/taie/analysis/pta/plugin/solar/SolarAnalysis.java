package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.language.classes.JMethod;

public class SolarAnalysis implements Plugin {
    private Solver solver;

    private PropagationModel propagationModel;

    @Override
    public void setSolver(Solver solver) {
        this.solver = solver;
        this.propagationModel = new PropagationModel(solver);
    }

    @Override
    public void onNewPointsToSet(CSVar csVar, PointsToSet pts) {
        if (propagationModel.isRelevantVar(csVar.getVar())) {
            propagationModel.handleNewPointsToSet(csVar, pts);
        }
    }

    @Override
    public void onNewCallEdge(Edge<CSCallSite, CSMethod> edge) {
        Plugin.super.onNewCallEdge(edge);
    }

    @Override
    public void onNewMethod(JMethod method) {
        method.getIR().invokes(false).forEach(invoke -> {
            propagationModel.handleNewInvoke(invoke);
        });
    }
}
