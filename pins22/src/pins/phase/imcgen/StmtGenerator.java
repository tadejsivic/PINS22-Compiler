package pins.phase.imcgen;

import java.lang.invoke.VolatileCallSite;
import java.util.*;

import pins.data.ast.*;
import pins.data.ast.visitor.*;
import pins.data.imc.code.expr.ImcBINOP;
import pins.data.imc.code.expr.ImcExpr;
import pins.data.imc.code.expr.ImcMEM;
import pins.data.imc.code.stmt.*;
import pins.data.mem.*;

public class StmtGenerator implements AstVisitor<ImcStmt, Stack<MemFrame>> {

	@Override
	public ImcStmt visit(AstAssignStmt assignStmt, Stack<MemFrame> frames) {
		ImcStmt code = new ImcMOVE( assignStmt.fstSubExpr.accept(new ExprGenerator(), frames),
				assignStmt.sndSubExpr.accept(new ExprGenerator(), frames));
		ImcGen.stmtImc.put(assignStmt, code);
		return code;
	}

	public ImcStmt visit(AstIfStmt ifStmt, Stack<MemFrame> frames){
		ImcExpr cond = ifStmt.condExpr.accept(new ExprGenerator(), frames);
		ImcStmt then = ifStmt.thenBodyStmt.accept(this, frames);
		ImcStmt elseStmt = null;
		if (ifStmt.elseBodyStmt!=null)
			 elseStmt = ifStmt.elseBodyStmt.accept(this, frames);

		MemLabel thenLabel = new MemLabel();
		MemLabel elseLabel;
		if (elseStmt==null)
			elseLabel = null;
		else
			elseLabel = new MemLabel();
		MemLabel endLabel = new MemLabel();
		ImcCJUMP cJump;

		if (elseStmt == null)
			cJump = new ImcCJUMP(cond, thenLabel, endLabel);
		else
			cJump = new ImcCJUMP(cond, thenLabel, elseLabel);
		ImcJUMP thenJump = new ImcJUMP(elseLabel);
		ImcJUMP endJump = new ImcJUMP(endLabel);
		Vector<ImcStmt> stmts = new Vector<>();
		stmts.add(cJump);
		stmts.add(new ImcLABEL(thenLabel));
		stmts.add(then);
		if (elseStmt!=null) {
			stmts.add(endJump);
			stmts.add(new ImcLABEL(elseLabel));
			stmts.add(elseStmt);
			stmts.add(new ImcLABEL(endLabel));
		}else{
			stmts.add(new ImcLABEL(endLabel));

		}

		ImcStmt ifOp = new ImcSTMTS(stmts);
		ImcGen.stmtImc.put(ifStmt, ifOp);
		return ifOp;
	}

	public ImcStmt visit(AstWhileStmt whileStmt, Stack<MemFrame> frames){
		ImcExpr cond = whileStmt.condExpr.accept(new ExprGenerator(), frames);
		ImcStmt body = whileStmt.bodyStmt.accept(this, frames);

		MemLabel loopLabel = new MemLabel();
		MemLabel bodyLabel = new MemLabel();
		MemLabel endLabel = new MemLabel();
		ImcStmt cJump = new ImcCJUMP(cond, bodyLabel, endLabel);
		ImcStmt jump = new ImcJUMP(loopLabel);

		Vector<ImcStmt> stmts = new Vector<>();
		stmts.add(new ImcLABEL(loopLabel));
		stmts.add(cJump);
		stmts.add(new ImcLABEL(bodyLabel));
		stmts.add(body);
		stmts.add(jump);
		stmts.add(new ImcLABEL(endLabel));
		ImcStmt whileOp = new ImcSTMTS(stmts);

		ImcGen.stmtImc.put(whileStmt, whileOp);
		return whileOp;
	}

	public ImcStmt visit(AstExprStmt exprStmt, Stack<MemFrame> frames){
		ImcExpr e = exprStmt.expr.accept(new ExprGenerator(), frames);
		ImcStmt s = new ImcESTMT(e);
		ImcGen.stmtImc.put(exprStmt, s);
		//ImcGen.exprImc.put(exprStmt.expr, e);
		return s;
	}


}
