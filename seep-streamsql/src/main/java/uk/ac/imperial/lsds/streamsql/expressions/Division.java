package uk.ac.imperial.lsds.streamsql.expressions;

import uk.ac.imperial.lsds.seep.operator.compose.multi.MultiOpTuple;
import uk.ac.imperial.lsds.streamsql.types.PrimitiveType;
import uk.ac.imperial.lsds.streamsql.visitors.ValueExpressionVisitor;

public class Division<T extends PrimitiveType> implements IValueExpression<T> {

	private IValueExpression<T>[] expressions = null;

	@SuppressWarnings("unchecked")
	public Division(IValueExpression<T> expression1, IValueExpression<T> expression2) {
		this.expressions = new IValueExpression[] {expression1, expression2};
	}

	
	public Division(IValueExpression<T>[] expressions) {
		this.expressions = expressions;
	}

	@Override
	public void accept(ValueExpressionVisitor vev) {
		vev.visit(this);
	}

	@Override
	public T eval(MultiOpTuple tuple) {
		T result = this.expressions[0].eval(tuple);
		for (int i = 1; i < expressions.length; i++) {
			result = (T)result.div(expressions[i].eval(tuple));
		}
		return result;
	}

	@Override
	public IValueExpression<T>[] getInnerExpressions() {
		return expressions;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < expressions.length; i++) {
			sb.append("(").append(expressions[i]).append(")");
			if (i != expressions.length - 1)
				sb.append(" / ");
		}
		return sb.toString();
	}

}