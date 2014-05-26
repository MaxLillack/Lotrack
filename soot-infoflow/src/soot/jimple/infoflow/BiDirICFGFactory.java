/**
 * (c) Copyright 2013, Tata Consultancy Services & Ecole Polytechnique de Montreal
 * All rights reserved
 */
package soot.jimple.infoflow;


import soot.jimple.infoflow.IInfoflow.CallgraphAlgorithm;
import soot.jimple.infoflow.solver.IInfoflowCFG;

public interface BiDirICFGFactory {

    public IInfoflowCFG buildBiDirICFG(CallgraphAlgorithm callgraphAlgorithm);

}
