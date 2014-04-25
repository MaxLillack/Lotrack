/* Soot - a J*va Optimization Framework
 * Copyright (C) 2003 Navindra Umanee <navindra@cs.mcgill.ca>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

package soot.toolkits.graph;

import java.util.*;

/**
 * Constructs a dominator tree structure from the given
 * DominatorsFinder.  The nodes in DominatorTree are of type
 * DominatorNode.
 *
 * <p>
 *
 * Note: DominatorTree does not currently implement DirectedGraph
 * since it provides 4 methods of navigating the nodes where the
 * meaning of getPredsOf and getSuccsOf diverge from the usual meaning
 * in a DirectedGraph implementation.
 *
 * <p>
 *
 * If you need a DirectedGraph implementation, see DominatorTreeAdapter.
 *
 * @author Navindra Umanee
 **/
public class DominatorTree {

    protected DominatorsFinder dominators;
    protected DirectedGraph graph;
    protected ArrayList<DominatorNode> heads;
    protected ArrayList<DominatorNode> tails;
    
    /**
     * "gode" is a node in the original graph, "dode" is a node in the
     * dominator tree.
     **/
    protected HashMap<Object, DominatorNode> godeToDode;

    public DominatorTree(DominatorsFinder dominators) {
        // if(Options.v().verbose())
        // G.v().out.println("[" + graph.getBody().getMethod().getName() +
        // "]     Constructing DominatorTree...");

        this.dominators = dominators;
        this.graph = dominators.getGraph();

        heads = new ArrayList<DominatorNode>();
        tails = new ArrayList<DominatorNode>();
        godeToDode = new HashMap();

        buildTree();
    }

    /**
     * Returns the original graph to which the Dominator tree
     * pertains.
     **/
    public DirectedGraph getGraph() {
        return dominators.getGraph();
    }

    /**
     * Returns the root of the dominator tree.
     **/
    public List<DominatorNode> getHeads() {
        return new ArrayList<DominatorNode>(heads);
    }
    
    /**
     * Gets the first head of the dominator tree. This function is implemented 
     * for single-headed trees and mainly for backwards compatibility.
     * @return The first head of the dominator tree
     */
    public DominatorNode getHead() {
        return heads.isEmpty() ? null : heads.get(0);
    }

    /**
     * Returns a list of the tails of the dominator tree.
     **/
    public List<DominatorNode> getTails() {
        return new ArrayList<DominatorNode>(tails);
    }

    /**
     * Returns the parent of node in the tree, null if the node is at
     * the root.
     **/
    public DominatorNode getParentOf(DominatorNode node) {
        return node.getParent();
    }

    /**
     * Returns the children of node in the tree.
     **/
    public List<DominatorNode> getChildrenOf(DominatorNode node) {
        return new ArrayList<DominatorNode>(node.getChildren());
    }

    /**
     * Finds all the predecessors of node in the original
     * DirectedGraph and returns a list of the corresponding
     * DominatorNodes.
     **/
    public List<DominatorNode> getPredsOf(DominatorNode node) {
        List preds = graph.getPredsOf(node.getGode());

        List<DominatorNode> predNodes = new ArrayList<DominatorNode>();
        for(Iterator<DominatorNode> predsIt = preds.iterator(); predsIt.hasNext();){
            Object pred = predsIt.next();
            predNodes.add(getDode(pred));
        }

        return predNodes;
    }

    /**
     * Finds all the successors of node in the original DirectedGraph
     * and returns a list of the corresponding DominatorNodes.
     **/
    public List<DominatorNode> getSuccsOf(DominatorNode node) {
        List succs = graph.getSuccsOf(node.getGode());
        List<DominatorNode> succNodes = new ArrayList<DominatorNode>();
        for(Iterator<DominatorNode> succsIt = succs.iterator(); succsIt.hasNext();){
            Object succ = succsIt.next();
            succNodes.add(getDode(succ));
        }
        return succNodes;
    }

    /**
     * Returns true if idom immediately dominates node.
     **/
    public boolean isImmediateDominatorOf(DominatorNode idom, DominatorNode node) {
        // node.getParent() could be null
        return (node.getParent() == idom);
    }

    /**
     * Returns true if dom dominates node.
     **/
    public boolean isDominatorOf(DominatorNode dom, DominatorNode node) {
        return dominators.isDominatedBy(node.getGode(), dom.getGode());
    }

    /**
     * Returns the DominatorNode for a given node in the original
     * DirectedGraph.
     **/
    public DominatorNode getDode(Object gode) {
        DominatorNode dode = (DominatorNode)godeToDode.get(gode);

        if (dode == null) {
            throw new RuntimeException("Assertion failed: Dominator tree does not have a corresponding dode for gode (" + gode + ")");
        }

        return dode;
    }

    /**
     * Returns an iterator over the nodes in the tree.  No ordering is
     * implied.
     **/
    public Iterator<DominatorNode> iterator() {
        return godeToDode.values().iterator();
    }

    /**
     * Returns the number of nodes in the tree.
     **/
    public int size() {
        return godeToDode.size();
    }

    /**
     * Add all the necessary links between nodes to form a meaningful
     * tree structure.
     **/
    protected void buildTree() {
        // hook up children with parents and vice-versa
        for(Iterator godesIt = graph.iterator(); godesIt.hasNext();) {
            Object gode = godesIt.next();

            DominatorNode dode = fetchDode(gode);
            DominatorNode parent = fetchParent(gode);

            if (parent == null) {
                heads.add(dode);
            } else {
                parent.addChild(dode);
                dode.setParent(parent);
            }
        }

        // identify the tail nodes
        for(Iterator<DominatorNode> dodesIt = this.iterator(); dodesIt.hasNext(); ) {
            DominatorNode dode = dodesIt.next();
            if(dode.isTail()) {
                tails.add(dode);
            }
        }
    }

    /**
     * Convenience method, ensures we don't create more than one
     * DominatorNode for a given block.
     **/
    protected DominatorNode fetchDode(Object gode) {
        DominatorNode dode;

        if (godeToDode.containsKey(gode)) {
            dode = (DominatorNode) godeToDode.get(gode);
        } else {
            dode = new DominatorNode(gode);
            godeToDode.put(gode, dode);
        }

        return dode;
    }

    protected DominatorNode fetchParent(Object gode) {
        Object immediateDominator = dominators.getImmediateDominator(gode);

        if (immediateDominator == null) {
            return null;
        }

        return fetchDode(immediateDominator);
    }
}
