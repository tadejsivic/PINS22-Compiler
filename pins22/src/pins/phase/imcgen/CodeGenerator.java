package pins.phase.imcgen;

import java.util.*;

import pins.data.ast.*;
import pins.data.ast.visitor.*;
import pins.data.imc.code.expr.ImcExpr;
import pins.data.imc.code.expr.ImcMEM;
import pins.data.imc.code.expr.ImcSEXPR;
import pins.data.imc.code.expr.ImcTEMP;
import pins.data.imc.code.stmt.ImcMOVE;
import pins.data.imc.code.stmt.ImcStmt;
import pins.data.mem.*;
import pins.phase.memory.*;
import pins.phase.seman.SemAn;

public class CodeGenerator extends AstFullVisitor<Object, Stack<MemFrame>> {

	// General
	public Object visit(ASTs<? extends AST> trees, Stack<MemFrame> frames) {
		for (AST t : trees.asts()){
			frames = new Stack<MemFrame>();
			if (t != null)
				t.accept(this, frames);
		}
		return null;
	}

	public Object visit(AstFunDecl funDecl,  Stack<MemFrame> frames) {
		frames.push( Memory.frames.get(funDecl) );

		ImcExpr ex = (ImcExpr)funDecl.expr.accept(new ExprGenerator(), frames);
		ImcStmt save = new ImcMOVE( new ImcTEMP( frames.peek().RV ) , ex );
		ImcGen.exprImc.put(funDecl.expr, new ImcSEXPR(save, new ImcTEMP(frames.peek().RV)) );

		frames.pop();
		return null;
	}

}
