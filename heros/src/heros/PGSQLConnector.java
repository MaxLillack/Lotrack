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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.Queue;

import heros.solver.PathEdge;

public class PGSQLConnector<N,D,I extends InterproceduralCFG<N, M>, M> {
	
	private Queue<MyDTO> toBeInserted = new LinkedList<>();
	
	public PGSQLConnector() {
		// clear table
		try(Connection connection = DriverManager.getConnection("jdbc:postgresql:max?user=postgres&password=geheim"))
		{
			try(Statement statement = connection.createStatement())
			{
				statement.execute("TRUNCATE TABLE \"public\".\"Edges\" RESTART IDENTITY");
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public class MyDTO
	{
		private N target;
		private PathEdge<N, D> edge;
		private String type;
		private String fromMethod;
		private String toMethod;
		
		public MyDTO(N target, PathEdge<N, D> edge, String type,
				String fromMethod, String toMethod) {
			this.target = target;
			this.edge = edge;
			this.type = type;
			this.fromMethod = fromMethod;
			this.toMethod = toMethod;
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
		public String getFromMethod() {
			return fromMethod;
		}
		public String getToMethod() {
			return toMethod;
		}
		
	}

	public void logEdge(N target, PathEdge<N, D> edge, String type, I icfg)
	{
		
		M fromMethod = icfg.getMethodOf(edge.getTarget());
		M toMethod = icfg.getMethodOf(target);
		
		toBeInserted.add(new MyDTO(target, edge, type, fromMethod.toString(), toMethod.toString()));
		
		if(toBeInserted.size() > 1000)
		{
			writeQueue();
		}
	}
	
	public void writeQueue()
	{
		String insertSQL = "INSERT INTO \"public\".\"Edges\" (\"from\", \"to\", \"fromFact\", \"toFact\", \"fromMethod\", \"toMethod\", \"fromHash\", \"toHash\") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
		try(Connection connection = DriverManager.getConnection("jdbc:postgresql:max?user=postgres&password=geheim"))
		{
			PreparedStatement preparedStatement = connection.prepareStatement(insertSQL);
			while(!toBeInserted.isEmpty())
			{
				MyDTO dto = toBeInserted.poll();
				preparedStatement.setString(1, dto.getEdge().getTarget().toString());
				preparedStatement.setString(2, dto.getTarget().toString());
				preparedStatement.setString(3, dto.getEdge().factAtSource().toString());
				preparedStatement.setString(4, dto.getEdge().factAtTarget().toString());
				
				// fromMethod
				preparedStatement.setString(5, dto.getFromMethod());
				// toMethod
				preparedStatement.setString(6, dto.getToMethod());
				
				// fromHash
				preparedStatement.setInt(7, dto.getEdge().getTarget().hashCode());
				// toHash
				preparedStatement.setInt(8, dto.getTarget().hashCode());

//				preparedStatement.executeUpdate();
				preparedStatement.addBatch();
			}
			
			preparedStatement.executeBatch();
			
//			System.out.println("connected to pg");
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
