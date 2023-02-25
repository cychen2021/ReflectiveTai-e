package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.stmt.Cast;
import pascal.taie.language.classes.JMethod;

import java.io.FileWriter;

public class SolarAnalysis implements Plugin {
    private PropagationModel propagationModel;
    private TransformationModel transformationModel;
    private CollectiveInferenceModel collectiveInferenceModel;
    private LazyHeapModel lazyHeapModel;
    private QualityInterpreter qualityInterpreter;
    private String qualityLog = null;

    private ClassMetaObj.Builder classMetaObjBuilder;
    private MethodMetaObj.Builder methodMetaObjBuilder;
    private FieldMetaObj.Builder fieldMetaObjBuilder;
    private LazyObj.Builder lazyObjBuilder;

    public QualityInterpreter getQualityInterpreter() {
        return qualityInterpreter;
    }

    public ClassMetaObj.Builder getClassMetaObjBuilder() {
        return classMetaObjBuilder;
    }

    public MethodMetaObj.Builder getMethodMetaObjBuilder() {
        return methodMetaObjBuilder;
    }

    public FieldMetaObj.Builder getFieldMetaObjBuilder() {
        return fieldMetaObjBuilder;
    }

    public LazyObj.Builder getLazyObjBuilder() {
        return lazyObjBuilder;
    }

    private Solver solver;

    public Solver getSolver() {
        return solver;
    }

    @Override
    public void setSolver(Solver solver) {
        this.solver = solver;
        this.classMetaObjBuilder = new ClassMetaObj.Builder(solver.getTypeSystem());
        this.lazyObjBuilder = new LazyObj.Builder(solver.getTypeSystem());
        this.methodMetaObjBuilder = new MethodMetaObj.Builder(solver.getTypeSystem());
        this.fieldMetaObjBuilder = new FieldMetaObj.Builder(solver.getTypeSystem());
        this.qualityInterpreter = new QualityInterpreter(solver);

        this.propagationModel = new PropagationModel(this);
        this.transformationModel = new TransformationModel(this);
        this.collectiveInferenceModel = new CollectiveInferenceModel(this);
        this.lazyHeapModel = new LazyHeapModel(this);
        if (solver.getOptions().has("solar-quality-log")) {
            this.qualityLog = solver.getOptions().getString("solar-quality-log");
        }
    }

    @Override
    public void onFinish() {
        Plugin.super.onFinish();
        if (qualityLog == null) {
            return;
        }
        var imprecise = qualityInterpreter.checkPrecision();
        var unsound = qualityInterpreter.checkSoundness();

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("Imprecise List:\n");
        for (CSCallSite callSite: imprecise) {
            stringBuilder.append(callSite.toString()).append("\n");
        }

        stringBuilder.append("\n");

        stringBuilder.append("Unsound List:\n");
        for (CSCallSite callSite: unsound) {
            stringBuilder.append(callSite.toString()).append("\n");
        }

        try (FileWriter qualityFile = new FileWriter(qualityLog)) {
            qualityFile.write(stringBuilder.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
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
