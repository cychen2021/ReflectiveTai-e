/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.pta.plugin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.config.Configs;
import pascal.taie.ir.exp.Var;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.MutableInt;
import pascal.taie.util.collection.Maps;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Function;

/**
 * Profiler to help identify analysis hot spots in the analyzed program
 * and assist performance tuning for pointer analysis.
 */
public class Profiler implements Plugin {

    private static final Logger logger = LogManager.getLogger(Profiler.class);

    /**
     * Reports the results for top N elements.
     */
    private static final int TOP_N = 100;

    private CSManager csManager;

    private final Map<CSVar, MutableInt> csVarVisited = Maps.newMap();

    private final Map<Var, MutableInt> varVisited = Maps.newMap();

    @Override
    public void setSolver(Solver solver) {
        csManager = solver.getCSManager();
    }

    @Override
    public void onNewPointsToSet(CSVar csVar, PointsToSet pts) {
        csVarVisited.computeIfAbsent(csVar,
                unused -> new MutableInt(0)).add(1);
        varVisited.computeIfAbsent(csVar.getVar(),
                unused -> new MutableInt(0)).add(1);
    }

    @Override
    public void onFinish() {
        String outPath = new File(Configs.getOutputDir(), "pta-profile.txt").toString();
        try (PrintStream out = new PrintStream(new FileOutputStream(outPath))) {
            logger.info("Dumping pointer analysis profile to {} ...", outPath);
            // report variables
            reportTop(out, "frequently-visited variables",
                    varVisited, v -> v.getMethod() + "/" + v.getName());
            reportTop(out, "frequently-visited CS variables",
                    csVarVisited, CSVar::toString);
            // count and report methods
            Map<JMethod, MutableInt> methodVarVisited = Maps.newMap();
            varVisited.forEach((v, times) ->
                    methodVarVisited.computeIfAbsent(v.getMethod(),
                                    unused -> new MutableInt(0))
                            .add(times.get()));
            reportTop(out, "method containers (of frequently-visited variables)",
                    methodVarVisited, JMethod::toString);
            Map<CSMethod, MutableInt> csMethodVarVisited = Maps.newMap();
            csVarVisited.forEach((v, times) -> {
                CSMethod method = csManager.getCSMethod(
                        v.getContext(), v.getVar().getMethod());
                csMethodVarVisited.computeIfAbsent(method,
                                unused -> new MutableInt(0))
                        .add(times.get());
            });
            reportTop(out, "CS method containers (of frequently-visited CS variables)",
                    csMethodVarVisited, CSMethod::toString);
            // count and report classes
            Map<JClass, MutableInt> classVarVisited = Maps.newMap();
            methodVarVisited.forEach((m, times) ->
                    classVarVisited.computeIfAbsent(m.getDeclaringClass(),
                                    unused -> new MutableInt(0))
                            .add(times.get()));
            reportTop(out, "class containers (of frequently-visited variables)",
                    classVarVisited, JClass::toString);
        } catch (FileNotFoundException e) {
            logger.warn("Failed to write pointer analysis profile to {}, caused by {}",
                    outPath, e);
        }
    }

    private static <E> void reportTop(
            PrintStream out, String desc,
            Map<E, MutableInt> visited, Function<E, String> toString) {
        out.printf("Top %d %s:%n", TOP_N, desc);
        // obtain top N elements
        PriorityQueue<E> topQueue = new PriorityQueue<>(TOP_N,
                Comparator.comparingInt(e -> visited.get(e).get()));
        visited.keySet().forEach(e -> {
            topQueue.add(e);
            if (topQueue.size() > TOP_N) {
                topQueue.poll();
            }
        });
        // the topQueue is a minimum heap, thus, to report elements
        // from larger to small, we need to reverse the stream
        topQueue.stream()
                .sorted(topQueue.comparator().reversed())
                .forEach(e -> out.printf("%s\t%s%n",
                        toString.apply(e), visited.get(e).get()));
        out.println();
    }
}
