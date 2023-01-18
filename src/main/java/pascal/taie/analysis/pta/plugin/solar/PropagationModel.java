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

        JMethod classGetMethod = hierarchy.getJREMethod("<java.lang.Class: java.lang.reflect.Method getMethod(java.lang.String, java.lang.Class[])>");
        registerRelevantVarIndexes(classGetMethod, BASE, 0);
        registerAPIHandler(classGetMethod, this::classGetMethod);

        JMethod classGetField = hierarchy.getJREMethod("<java.lang.Class: java.lang.reflect.Field getField(java.lang.String)>");
        registerRelevantVarIndexes(classGetField, BASE, 0);
        registerAPIHandler(classGetField, this::classGetField);
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
            Obj clsObj;
            if (klass == null) {
                clsObj = heapModel.getConstantObj(ClassMetaLiteral.unknown());
            } else {
                clsObj = heapModel.getConstantObj(ClassMetaLiteral.from(klass.getType()));
            }
            CSObj csObj = csManager.getCSObj(defaultHctx, clsObj);
            Var result = invoke.getResult();
            solver.addVarPointsTo(context, result, csObj);
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
                ExtendedJClass baseCls = ExtendedJClass.from(baseClsObj);
                assert baseCls != null;

                ExtendedType returnType = ExtendedType.unknown();
                Optional<List<ExtendedType>> parameterTypes = Optional.empty();
                Optional<String> mtdName;
                if (mName == null) {
                    mtdName = Optional.empty();
                } else {
                    mtdName = Optional.of(mName);
                }

                Obj mtdObj = heapModel.getConstantObj(
                        MethodMetaLiteral.from(baseCls, mtdName, parameterTypes, returnType,
                                        false) // TODO: Consider static methods
                );
                CSObj csObj = csManager.getCSObj(defaultHctx, mtdObj);
                Var result = invoke.getResult();
                solver.addVarPointsTo(context, result, csObj);
            });
        });
    }

    private void classGetField(CSVar csVar, PointsToSet pts, Invoke invoke) {
        Context context = csVar.getContext();
        List<PointsToSet> args = getArgs(csVar, pts, invoke, BASE, 0);
        PointsToSet baseClsObjs = args.get(0);
        PointsToSet mNameObjs = args.get(1);
        mNameObjs.forEach(fNameObj -> {
            baseClsObjs.forEach(baseClsObj -> {
                String mName = CSObjs.toString(fNameObj);
                ExtendedJClass baseCls = ExtendedJClass.from(baseClsObj);
                assert baseCls != null;

                ExtendedType fldType = ExtendedType.unknown();
                Optional<String> fldName;
                if (mName == null) {
                    fldName = Optional.empty();
                } else {
                    fldName = Optional.of(mName);
                }

                // TODO: Consider static methods
                Obj mtdObj = heapModel.getConstantObj(
                        FieldMetaLiteral.from(baseCls, fldName, fldType, false));
                CSObj csObj = csManager.getCSObj(defaultHctx, mtdObj);
                Var result = invoke.getResult();
                solver.addVarPointsTo(context, result, csObj);
            });
        });
    }
}
