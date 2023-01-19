package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.util.AbstractModel;
import pascal.taie.analysis.pta.plugin.util.CSObjs;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;

import java.util.List;
import java.util.Optional;

public class PropagationModel extends AbstractModel {
    PropagationModel(Solver solver) {
        super(solver);
    }

    @Override
    protected void registerVarAndHandler() {
        JMethod classForName = hierarchy.getJREMethod("<java.lang.Class: java.lang.Class forName(java.lang.String)>");
        registerRelevantVarIndexes(classForName, 0);
        registerAPIHandler(classForName, this::classForName);

        JMethod classForName2 = hierarchy.getJREMethod("<java.lang.Class: java.lang.Class forName(java.lang.String,boolean,java.lang.ClassLoader)>");
        // TODO: take class loader into account
        registerRelevantVarIndexes(classForName2, 0);
        registerAPIHandler(classForName2, this::classForName);

        JMethod classGetMethod = hierarchy.getJREMethod("<java.lang.Class: java.lang.reflect.Method getMethod(java.lang.String,java.lang.Class[])>");
        registerRelevantVarIndexes(classGetMethod, BASE, 0);
        registerAPIHandler(classGetMethod, this::classGetMethod);

        JMethod classGetMethods = hierarchy.getJREMethod("<java.lang.Class: java.lang.reflect.Method[] getMethods()>");
        registerRelevantVarIndexes(classGetMethods, BASE);
        registerAPIHandler(classGetMethods, this::classGetMethods);
    }

    private void classForName(CSVar csVar, PointsToSet pts, Invoke invoke) {
        Context context = csVar.getContext();
        pts.forEach(obj -> {
            String className = CSObjs.toString(obj);
            JClass klass;
            if (className == null) {
                klass = null;
            } else {
                klass = hierarchy.getClass(className);
            }
            solver.initializeClass(klass);
            Var result = invoke.getResult();
            if (result != null) {
                ClassMetaObj metaObj;
                if (klass == null) {
                    metaObj = ClassMetaObj.unknown();
                } else {
                    metaObj = ClassMetaObj.known(klass);
                }

                Obj clsObj = heapModel.getMockObj("ClassMetaObj", metaObj, metaObj.getType());
                CSObj csObj = csManager.getCSObj(defaultHctx, clsObj);
                solver.addVarPointsTo(context, result, csObj);
            }
        });
    }

    private void classGetMethod(CSVar csVar, PointsToSet pts, Invoke invoke) {
        Context context = csVar.getContext();
        List<PointsToSet> args = getArgs(csVar, pts, invoke, BASE, 0);
        PointsToSet baseClsObjs = args.get(0);
        PointsToSet mNameObjs = args.get(1);
        mNameObjs.forEach(mNameObj -> {
            baseClsObjs.forEach(baseClsObj -> {
                String mName = CSObjs.toString(mNameObj);
                JClass baseCls = CSObjs.toClass(baseClsObj);

                 // TODO: Consider static methods
                MethodMetaObj metaObj = MethodMetaObj.unknown(baseCls, mName, null, null);

                Obj mtdObj = heapModel.getMockObj("MethodMetaObj", metaObj, metaObj.getType());
                CSObj csObj = csManager.getCSObj(defaultHctx, mtdObj);
                Var result = invoke.getResult();
                solver.addVarPointsTo(context, result, csObj);
            });
        });
    }

    private void classGetMethods(CSVar csVar, PointsToSet pts, Invoke invoke) {
        Context context = csVar.getContext();
        List<PointsToSet> args = getArgs(csVar, pts, invoke, BASE, 0);
        PointsToSet baseClsObjs = args.get(0);
        baseClsObjs.forEach(baseClsObj -> {
            JClass baseCls = CSObjs.toClass(baseClsObj);

            // TODO: Consider static methods
            MethodMetaObj metaObj = MethodMetaObj.unknown(baseCls, null, null, null);


            Obj mtdObj = heapModel.getMockObj("MethodMetaObj", metaObj,
                    typeSystem.getArrayType(metaObj.getType(), 1));
            CSObj csObj = csManager.getCSObj(defaultHctx, mtdObj);
            Var result = invoke.getResult();
            solver.addVarPointsTo(context, result, csObj);
        });
    }
}
