import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import uk.ac.imperial.lsds.seep.multi.IMicroOperatorCode;
import uk.ac.imperial.lsds.seep.multi.ITupleSchema;
import uk.ac.imperial.lsds.seep.multi.MicroOperator;
import uk.ac.imperial.lsds.seep.multi.MultiOperator;
import uk.ac.imperial.lsds.seep.multi.QueryConf;
import uk.ac.imperial.lsds.seep.multi.SubQuery;
import uk.ac.imperial.lsds.seep.multi.TupleSchema;
import uk.ac.imperial.lsds.seep.multi.Utils;
import uk.ac.imperial.lsds.seep.multi.WindowDefinition;
import uk.ac.imperial.lsds.streamsql.expressions.eint.IntColumnReference;
import uk.ac.imperial.lsds.streamsql.expressions.eint.IntConstant;
import uk.ac.imperial.lsds.streamsql.op.gpu.deprecated.stateless.SelectionKernel;
import uk.ac.imperial.lsds.streamsql.op.stateless.Selection;
import uk.ac.imperial.lsds.streamsql.predicates.IntComparisonPredicate;

public class TestHybridSelection {

	public static void main(String [] args) {
		
		String filename = args[0];
		
		WindowDefinition window = 
			new WindowDefinition (TestUtils.TYPE, TestUtils.RANGE, TestUtils.SLIDE);
		
		ITupleSchema schema = new TupleSchema (TestUtils.OFFSETS, TestUtils._TUPLE_);
		/*
		IPredicate [] predicates = new IPredicate [4];
		
		predicates[0] = new IntComparisonPredicate(IntComparisonPredicate.LESS_OP, new IntColumnReference(1), 
				new IntAddition (new IntColumnReference(2), new IntConstant(1))
		);
		
		predicates[1] = new IntComparisonPredicate(IntComparisonPredicate.GREATER_OP, new IntColumnReference(1), 
				new IntSubtraction (new IntColumnReference(2), new IntConstant(1))
		);
		
		predicates[2] = new IntComparisonPredicate(IntComparisonPredicate.LESS_OP, new IntColumnReference(3), 
				new IntAddition (new IntColumnReference(4), new IntConstant(1))
		);
		
		predicates[3] = new IntComparisonPredicate(IntComparisonPredicate.GREATER_OP, new IntColumnReference(4), 
				new IntSubtraction (new IntColumnReference(4), new IntConstant(1))
		);
		
		IMicroOperatorCode selectionCPUCode = new Selection (
			new ANDPredicate (predicates)
		);
		
		IMicroOperatorCode selectionGPUCode = new SelectionKernel (
			new ANDPredicate (predicates),
			schema,
			filename
		);
		*/
		/*
		IPredicate [] predicates = new IPredicate [20];
		for (int i = 0; i < predicates.length; i++) {
			int j = i % 6 + 1;
			predicates[i] = new IntComparisonPredicate(IntComparisonPredicate.LESS_OP, new IntColumnReference(j), new IntConstant(i + 2));
		}
		*/
		IMicroOperatorCode selectionCPUCode = new Selection (
				new IntComparisonPredicate(IntComparisonPredicate.LESS_OP, new IntColumnReference(1), new IntConstant(2))
			);
			
			IMicroOperatorCode selectionGPUCode = new SelectionKernel (
				new IntComparisonPredicate(IntComparisonPredicate.LESS_OP, new IntColumnReference(1), new IntConstant(2)),
				schema,
				filename
			);
		
		MicroOperator uoperator = new MicroOperator (selectionCPUCode, selectionGPUCode, 1);
		
		/* Query */
		Set<MicroOperator> operators = new HashSet<MicroOperator>();
		operators.add(uoperator);
		
		Set<SubQuery> queries = new HashSet<SubQuery>();
		SubQuery query = new SubQuery (0, operators, schema, window, new QueryConf(200, 1024));
		queries.add(query);
			
		MultiOperator operator = new MultiOperator(queries, 0);
		operator.setup();
		
		byte [] data = new byte [Utils.BUNDLE];
		ByteBuffer b = ByteBuffer.wrap(data);
		while (b.hasRemaining())
			b.putInt(1);
		try {
			while (true) {
				operator.processData (data);
				/* Thread.sleep(1000L); */
			}
		} catch (Exception e) { 
			e.printStackTrace(); 
			System.exit(1);
		}
	}
}