/*******************************************************************************
 * Copyright (c) 2014 Imperial College London
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Raul Castro Fernandez - initial API and implementation
 ******************************************************************************/
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import operators.Sink;
import operators.Source;
import uk.ac.imperial.lsds.seep.api.QueryBuilder;
import uk.ac.imperial.lsds.seep.api.QueryComposer;
import uk.ac.imperial.lsds.seep.api.QueryPlan;
import uk.ac.imperial.lsds.seep.operator.Connectable;
import uk.ac.imperial.lsds.seep.operator.compose.micro.IMicroOperatorCode;
import uk.ac.imperial.lsds.seep.operator.compose.micro.IMicroOperatorConnectable;
import uk.ac.imperial.lsds.seep.operator.compose.subquery.ISubQueryConnectable;
import uk.ac.imperial.lsds.seep.operator.compose.window.IWindowDefinition;
import uk.ac.imperial.lsds.seep.operator.compose.window.WindowDefinition;
import uk.ac.imperial.lsds.seep.operator.compose.window.WindowDefinition.WindowType;
import uk.ac.imperial.lsds.streamsql.conversion.IntegerConversion;
import uk.ac.imperial.lsds.streamsql.conversion.StringConversion;
import uk.ac.imperial.lsds.streamsql.expressions.ColumnReference;
import uk.ac.imperial.lsds.streamsql.expressions.Division;
import uk.ac.imperial.lsds.streamsql.expressions.IValueExpression;
import uk.ac.imperial.lsds.streamsql.expressions.ValueExpression;
import uk.ac.imperial.lsds.streamsql.op.stateless.Projection;

public class Base implements QueryComposer{
	public QueryPlan compose() {
		
		// Declare Source
		List<String> posSpeedStr = new ArrayList<String>();
		posSpeedStr.add("vehicleId");
		posSpeedStr.add("speed");
		posSpeedStr.add("xPos");
		posSpeedStr.add("dir");
		posSpeedStr.add("hwy");
		Connectable src = QueryBuilder.newStatelessSource(new Source(), -1, posSpeedStr);

		/*
		 * Query 1
		 * 
		 * Select vehicleId, speed, xPos/1760 as segNo, dir, hwy
		 * From PosSpeedStr
		 */
		List<IValueExpression> projExpressions = new ArrayList<>();
		projExpressions.add(new ColumnReference<>(new StringConversion(), "vehicleId"));
		projExpressions.add(new ColumnReference<>(new StringConversion(), "speed"));
		IValueExpression ex = new Division(new ColumnReference<>(new StringConversion(), "xPos"), new ValueExpression<>(new IntegerConversion(), 1760));
		projExpressions.add(ex);
		projExpressions.add(new ColumnReference<>(new StringConversion(), "dir"));
		projExpressions.add(new ColumnReference<>(new StringConversion(), "hwy"));
		
		IMicroOperatorCode q1ProjCode = new Projection(projExpressions);
		IMicroOperatorConnectable q1Proj = QueryBuilder.newMicroOperator(q1ProjCode, 1, posSpeedStr);
		
		Set<IMicroOperatorConnectable> q1MicroOps = new HashSet<>();
		q1MicroOps.add(q1Proj);

		Map<Integer, IWindowDefinition> windowDefs = new HashMap<>();
		windowDefs.put(11, new WindowDefinition(WindowType.ROW_BASED, 1, 1));
		ISubQueryConnectable sq1 = QueryBuilder.newSubQuery(q1MicroOps, 2, posSpeedStr, windowDefs);

		List<String> segSpeedStr = new ArrayList<String>();
		segSpeedStr.add("vehicleId");
		segSpeedStr.add("speed");
		segSpeedStr.add("segNo");
		segSpeedStr.add("dir");
		segSpeedStr.add("hwy");
		
		/*
		 * Query 2
		 * 
		 * Select segNo, dir, hwy
		 * From SegSpeedStr [Range 5 Minutes]
		 * Group By segNo, dir, hwy
		 * Having Avg(speed) < 40
		 */
		IMicroOperatorCode projCode = new Projection(new String[] {"segNo", "dir", "hwy"});
		IMicroOperatorConnectable proj = QueryBuilder.newMicroOperator(projCode, 1, segSpeedStr);
		
		
		
		// Declare sink
		Connectable snk = QueryBuilder.newStatelessSink(new Sink("./myOut.csv"), -2, segSpeedStr);

//		sq1.connectTo(sq2, 101);
//		sq1.connectTo(sq2, 102);

		Set<ISubQueryConnectable> subQueries = new HashSet<>();
		subQueries.add(sq1);
//		subQueries.add(sq2);
		
		Connectable multiOp = QueryBuilder.newMultiOperator(subQueries, 100, posSpeedStr);

		/** Connect operators **/
		src.connectTo(multiOp, true, 11);
		multiOp.connectTo(snk, true, 0);
		
		return QueryBuilder.build();
	}
}
