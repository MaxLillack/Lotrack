/*******************************************************************************
 * Copyright (c) 2015 Eric Bodden.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Eric Bodden - initial API and implementation
 ******************************************************************************/
package heros;

import heros.solver.PathEdge;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.graphdb.schema.ConstraintDefinition;
import org.neo4j.tooling.GlobalGraphOperations;

public class Neo4JConnector<N,D> {
	private GraphDatabaseService neo4j;
	
	private static enum RelTypes implements RelationshipType
	{
	    KNOWS
	}
	private static enum LotrackLabels implements Label
	{
	    Statement
	}
	
	public class MyDTO
	{
		private N target;
		private PathEdge<N, D> edge;
		private String type;
		
		public MyDTO(N target, PathEdge<N, D> edge, String type) {
			this.target = target;
			this.edge = edge;
			this.type = type;
		}

		public N getTarget() {
			return target;
		}

		public PathEdge<N, D> getEdge() {
			return edge;
		}

		public String getType() {
			return type;
		}
	}
	
	private Queue<MyDTO> toBeInserted = new LinkedList<>();
	
	
	public Neo4JConnector()
	{
		GraphDatabaseBuilder builder = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder( new File("C:\\Users\\Max\\Documents\\Neo4j\\default.graphdb") );
		builder.setConfig(GraphDatabaseSettings.keep_logical_logs, "false");
		neo4j = builder.newGraphDatabase();
		Runtime.getRuntime().addShutdownHook( new Thread()
	    {
	        @Override
	        public void run()
	        {
	        	neo4j.shutdown();
	        }
	    } );
	

		try(Transaction tx = neo4j.beginTx())
		{
			// Delete old Nodes
			GlobalGraphOperations globalOps = GlobalGraphOperations.at(neo4j);
			for(Node node : globalOps.getAllNodes())
			{
				for(Relationship relationship : node.getRelationships())
				{
					relationship.delete();
				}
				node.delete();
			}
			tx.success();
		}
		
		try(Transaction tx = neo4j.beginTx())
		{
			// Drop old constraints
			for(ConstraintDefinition constraintDefinition : neo4j.schema().getConstraints(LotrackLabels.Statement))
			{
				constraintDefinition.drop();
			}
			
			// Unique constraint
			neo4j.schema()
	             .constraintFor( LotrackLabels.Statement )
	             .assertPropertyIsUnique( "hashcode" )
	             .create();
			
			tx.success();
		}

	}
	
	public void writeQueue()
	{
		String queryString = "MERGE (s:Statement {hashcode: {hashcode}, target: {target}}) RETURN s";
		
		try(Transaction tx = neo4j.beginTx())
		{
			while(!toBeInserted.isEmpty())
			{
				MyDTO dto = toBeInserted.poll();
				
				Map<String, Object> parameters = new HashMap<>();
				parameters.put( "hashcode", dto.getEdge().getTarget().hashCode());
				parameters.put( "target", dto.getEdge().getTarget().toString());
				ResourceIterator<Node> resultIterator = neo4j.execute( queryString, parameters ).columnAs( "s" );
				Node firstNode = resultIterator.next();
				
				Map<String, Object> parameters2 = new HashMap<>();
				parameters2.put( "hashcode", dto.getTarget().hashCode());
				parameters2.put( "target", dto.getTarget().toString());
				ResourceIterator<Node> resultIterator2 = neo4j.execute( queryString, parameters2 ).columnAs( "s" );
				Node secondNode = resultIterator2.next();
				
				Relationship relationship = firstNode.createRelationshipTo( secondNode, RelTypes.KNOWS );
				relationship.setProperty( "type", dto.getType());
				relationship.setProperty( "source fact", dto.getEdge().factAtSource().toString());
				relationship.setProperty( "target fact", dto.getEdge().factAtTarget().toString());
			}
			tx.success();
		}
	}
	
	public void logEdge(N target, PathEdge<N, D> edge, String type)
	{
		toBeInserted.add(new MyDTO(target, edge, type));
		
		if(toBeInserted.size() > 100)
		{
			writeQueue();
		}
	}

}
