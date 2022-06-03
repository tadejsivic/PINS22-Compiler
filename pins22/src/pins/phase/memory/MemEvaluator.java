package pins.phase.memory;

import pins.data.ast.*;
import pins.data.ast.visitor.*;
import pins.data.mem.*;
import pins.data.typ.*;
import pins.phase.seman.*;

/**
 * Computing memory layout: frames and accesses.
 */
public class MemEvaluator extends AstFullVisitor<Object, MemEvaluator.FunContext> {

	/**
	 * Functional context, i.e., used when traversing function and building a new
	 * frame, parameter acceses and variable acceses.
	 */
	protected class FunContext {
		public int depth = 0;
		public long locsSize = 0;
		public long argsSize = 0;
		public long parsSize = new SemPtr(new SemVoid()).size();
	}

	long sizeOfAtomTypes = 8; // Bytes

	// GENERAL
	public Object visit(ASTs<? extends AST> trees, FunContext ctx) {
		if (trees.location==null) ctx = new FunContext();
		Object temp = null;
		for (int i=0; i<3; i++) {
			for (AST t : trees.asts())
				if (t != null)
					t.accept(this, ctx);
			if (trees.location!=null) break;
		}
		return null;
	}

	// DECLARATIONS
	public Object visit(AstFunDecl funDecl, FunContext ctx) {
		if (Memory.frames.get(funDecl)!=null) return null;

		FunContext new_ctx = new FunContext();
		new_ctx.depth = ctx.depth+1;
		if (funDecl.pars != null)
			funDecl.pars.accept(this, new_ctx);
		funDecl.expr.accept(this, new_ctx);

		MemLabel label = ctx.depth==0 ? new MemLabel(funDecl.name) : new MemLabel();
		Memory.frames.put(funDecl, new MemFrame(label, new_ctx.depth, new_ctx.locsSize, new_ctx.argsSize) );
		return null;
	}

	public Object visit(AstParDecl parDecl, FunContext ctx) {
		if (Memory.parAccesses.get(parDecl)!=null) return null;
		parDecl.type.accept(this, ctx);
		ctx.parsSize += sizeOfAtomTypes;
		Memory.parAccesses.put(parDecl, new MemRelAccess(sizeOfAtomTypes, ctx.parsSize-sizeOfAtomTypes, ctx.depth+1));
		return null;
	}

	public Object visit(AstVarDecl varDecl, FunContext ctx) {

		if (Memory.varAccesses.get(varDecl)!=null) return null;
		varDecl.type.accept(this, ctx);

		/*System.out.println(varDecl.name);
		System.out.println(varDecl.type+", "+SemAn.describesType.get(varDecl.type)+" : "+varDecl.name);*/

		long size = SemAn.describesType.get(varDecl.type).size();
		ctx.locsSize += size;
		if (ctx.depth==0)	// Globalna
			Memory.varAccesses.put(varDecl, new MemAbsAccess(size, new MemLabel(varDecl.name) ));
		else	// Lokalna
			Memory.varAccesses.put(varDecl, new MemRelAccess(size, -ctx.locsSize, ctx.depth+1));
		return null;
	}

	// EXPRESSIONS
	public Object visit(AstCallExpr callExpr, FunContext ctx) {
		if (callExpr.args != null){
			callExpr.args.accept(this, ctx);
			ctx.argsSize = Math.max(ctx.argsSize, callExpr.args.asts().size()*sizeOfAtomTypes + sizeOfAtomTypes );
		}
		return null;
	}



}
