/*******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/
package soot.jimple.infoflow.handlers;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.infoflow.InfoflowResults;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;

/**
 * Handler that is called when information flow results become available
 * @author Steven Arzt
 */
public interface ResultsAvailableHandler {

	/**
	 * Callback that is invoked when information flow results are available
	 * @param cfg The program graph
	 * @param results The results that were computed
	 */
	public void onResultsAvailable(BiDiInterproceduralCFG<Unit, SootMethod> cfg,
			InfoflowResults results);

}
