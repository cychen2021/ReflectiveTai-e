/*
 * Tai-e: A Program Analysis Framework for Java
 *
 * Copyright (C) 2020 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2020 Yue Li <yueli@nju.edu.cn>
 * All rights reserved.
 *
 * This software is designed for the "Static Program Analysis" course at
 * Nanjing University, and it supports a subset of Java features.
 * Tai-e is only for educational and academic purposes, and any form of
 * commercial use is disallowed.
 */

package pascal.taie.pta.core.heap;

import pascal.taie.ir.exp.NewExp;
import pascal.taie.java.TypeManager;

public class AllocationSiteBasedModel extends AbstractHeapModel {

    public AllocationSiteBasedModel(TypeManager typeManager) {
        super(typeManager);
    }

    @Override
    protected Obj doGetObj(NewExp newExp) {
        return getNewObj(newExp);
    }
}
