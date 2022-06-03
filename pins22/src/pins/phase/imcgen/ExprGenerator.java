package pins.phase.imcgen;

import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.function.ObjIntConsumer;

import pins.common.report.*;
import pins.data.ast.*;
import pins.data.ast.visitor.*;
import pins.data.imc.code.expr.*;
//import pins.data.imc.code.stmt.*;
import pins.data.imc.code.stmt.ImcESTMT;
import pins.data.imc.code.stmt.ImcSTMTS;
import pins.data.imc.code.stmt.ImcStmt;
import pins.data.mem.*;
import pins.data.typ.SemChar;
import pins.data.typ.SemInt;
import pins.data.typ.SemPtr;
import pins.data.typ.SemType;
import pins.phase.memory.Memory;
//import pins.data.typ.*;
//import pins.phase.memory.*;
//import pins.phase.seman.*;
import pins.phase.seman.SemAn;

public class ExprGenerator implements AstVisitor<ImcExpr, Stack<MemFrame>> {

	@Override
	public ImcExpr visit(AstWhereExpr whereExpr, Stack<MemFrame> frames) {
		whereExpr.decls.accept(new CodeGenerator(), frames);
		ImcExpr code = whereExpr.subExpr.accept(this, frames);
		ImcGen.exprImc.put(whereExpr, code);
		return code;
	}

	public ImcExpr visit(AstBinExpr binExpr, Stack<MemFrame> frames) {
		ImcExpr fstExpr = binExpr.fstSubExpr.accept(this, frames);
		ImcExpr sndExpr = binExpr.sndSubExpr.accept(this, frames);
		ImcBINOP.Oper oper = null;
		switch(binExpr.oper){
			case ADD:
				oper = ImcBINOP.Oper.ADD;
				break;
			case AND:
				oper = ImcBINOP.Oper.AND;
				break;
			case ARR:
				ImcExpr index = new ImcBINOP(ImcBINOP.Oper.MUL, new ImcCONST( SemAn.exprOfType.get(binExpr).size() ), sndExpr);	// changed 8 to actual size (to handle n-d arrays)
				ImcExpr arrayOp = new ImcMEM(new ImcBINOP(ImcBINOP.Oper.ADD, ((ImcMEM)fstExpr).addr, index));
				ImcGen.exprImc.put(binExpr, arrayOp);
				return arrayOp;
			case DIV:
				oper = ImcBINOP.Oper.DIV;
				break;
			case EQU:
				oper = ImcBINOP.Oper.EQU;
				break;
			case GEQ:
				oper = ImcBINOP.Oper.GEQ;
				break;
			case GTH:
				oper = ImcBINOP.Oper.GTH;
				break;
			case LEQ:
				oper = ImcBINOP.Oper.LEQ;
				break;
			case LTH:
				oper = ImcBINOP.Oper.LTH;
				break;
			case MOD:
				oper = ImcBINOP.Oper.MOD;
				break;
			case MUL:
				oper = ImcBINOP.Oper.MUL;
				break;
			case NEQ:
				oper = ImcBINOP.Oper.NEQ;
				break;
			case OR:
				oper = ImcBINOP.Oper.OR;
				break;
			case SUB:
				oper = ImcBINOP.Oper.SUB;
				break;
			default:
				throw new Report.InternalError();

		}
		ImcBINOP binop  = new ImcBINOP(oper, fstExpr, sndExpr);
		ImcGen.exprImc.put(binExpr, binop);
		return binop;
	}

	public ImcExpr visit(AstPreExpr preExpr, Stack<MemFrame> frames) {
		ImcExpr sub = preExpr.subExpr.accept(this, frames);
		ImcUNOP.Oper oper = null;
		switch (preExpr.oper){
			case NOT:
				oper = ImcUNOP.Oper.NOT;
				break;
			case SUB:
				oper = ImcUNOP.Oper.NEG;
				break;
			case NEW:
				Vector<Long> offs = new Vector<>();
				Vector<ImcExpr> args = new Vector<>();
				args.add( new ImcTEMP( frames.peek().FP ));	// SL
				offs.add(0l);
				args.add(sub);	// EXPR
				offs.add(preExpr.subExpr instanceof AstNameExpr ? 0l : 0l);	// FIX THIS OFFSET
				ImcExpr newOp = new ImcCALL(new MemLabel("new"), offs, args );
				ImcGen.exprImc.put(preExpr, newOp);
				return newOp;
			case DEL:
				offs = new Vector<>();
				args = new Vector<>();
				args.add(sub);
				offs.add(0l); //
				ImcExpr delOp = new ImcCALL(new MemLabel("del"), offs, args );
				ImcGen.exprImc.put(preExpr, delOp);
				return delOp;
			case PTR:
				ImcExpr returnable = sub instanceof ImcMEM ? ((ImcMEM)sub).addr : sub;
				ImcGen.exprImc.put(preExpr, returnable );
				return returnable;
		}

		if (oper == null)
			return sub;
		else{
			ImcExpr unop = new ImcUNOP(oper, sub);
			ImcGen.exprImc.put(preExpr, unop);
			return unop;
		}

	}

	public ImcExpr visit(AstPstExpr pstExpr, Stack<MemFrame> frames){
		ImcExpr pstOp = new ImcMEM(pstExpr.subExpr.accept(this, frames));	// dodan MEM, smzd da prav
		ImcGen.exprImc.put(pstExpr, pstOp);
		return pstOp;
	}

	public ImcExpr visit(AstConstExpr constExpr, Stack<MemFrame> frames) {
		String temp="";
		int ascii = 0;
		try{
			Integer.parseInt(constExpr.name);
		}catch (Exception e){
			if (constExpr.name.toCharArray().length==4)
				ascii = constExpr.name.charAt(2);
			else
				ascii = constExpr.name.charAt(1);
		}

		ImcCONST constOp = new ImcCONST( Long.parseLong( ascii==0 ? constExpr.name : ascii+"" ) );
		ImcGen.exprImc.put(constExpr, constOp);
		return constOp;
	}

	public ImcExpr visit(AstNameExpr nameExpr, Stack<MemFrame> frames) {
		MemAccess access;
		if ( Memory.varAccesses.get( SemAn.declaredAt.get(nameExpr) ) != null  )
			access =  Memory.varAccesses.get( SemAn.declaredAt.get(nameExpr) );
		else
			access = Memory.parAccesses.get( SemAn.declaredAt.get(nameExpr) );
		if (access instanceof MemAbsAccess){
			ImcNAME name = new ImcNAME( ((MemAbsAccess)access).label );
			ImcMEM mem0 = new ImcMEM( name );
			ImcGen.exprImc.put(nameExpr, mem0 );
			return mem0;
		}
		long offset = ((MemRelAccess)access).offset;
		ImcExpr tempAccess = new ImcTEMP( frames.peek().FP );
		int current = frames.peek().depth+1;
		int decl = ((MemRelAccess)access).depth;
		int depthDifference = current - decl;
		for (int i=0; i<depthDifference; i++){
			tempAccess = new ImcMEM(tempAccess);
		}
		ImcMEM binOp = new ImcMEM( new ImcBINOP( ImcBINOP.Oper.ADD, tempAccess, new ImcCONST(offset)) );
		ImcGen.exprImc.put(nameExpr, binOp );
		return binOp;
	}

	public ImcExpr visit(AstCallExpr callExpr, Stack<MemFrame> frames) {
		ImcExpr callOp;
		Vector<Long> offsets = new Vector<>();
		Vector<ImcExpr> args = new Vector<>();

		offsets.add(0l);	// 1. argument mora biti SL
		ImcExpr tempAccess = new ImcTEMP( frames.peek().FP );
		int current = frames.peek().depth + 1;
		int decl = Memory.frames.get( SemAn.declaredAt.get(callExpr) ).depth;
		int depthDifference = current - decl;
		for (int i=0; i<depthDifference; i++){
			tempAccess = new ImcMEM(tempAccess);
		}
		args.add(tempAccess);	// Če je globalna funkcija, se SL pač ne uporabi

		int counter = 1;
		for (int i=0; i<callExpr.args.asts().size(); i++){
			args.add( callExpr.args.asts().get(i).accept(this, frames) );
			offsets.add(8l*counter);
			counter++;
		}
		callOp = new ImcCALL(Memory.frames.get(SemAn.declaredAt.get(callExpr)).label , offsets, args);
		ImcGen.exprImc.put(callExpr, callOp);
		return callOp;
	}

	public ImcExpr visit(AstCastExpr castExpr, Stack<MemFrame> frames){
		if ( SemAn.describesType.get(castExpr.type) instanceof SemInt){
			ImcExpr castOp = castExpr.subExpr.accept(this, frames);
			ImcGen.exprImc.put(castExpr, castOp);
			return castOp;
		}else if (SemAn.describesType.get(castExpr.type) instanceof SemChar){
			ImcExpr castOp = castExpr.subExpr.accept(this, frames);
			ImcExpr binOp = new ImcBINOP(ImcBINOP.Oper.MOD, castOp, new ImcCONST(256) );
			ImcGen.exprImc.put(castExpr, binOp);
			return binOp;
		}else {		//(SemAn.describesType.get(castExpr.type) instanceof SemPtr){
			ImcExpr castOp = castExpr.subExpr.accept(this, frames);
			ImcGen.exprImc.put(castExpr, castOp);
			return castOp;
		}
	}


	public ImcExpr visit(AstStmtExpr stmtExpr, Stack<MemFrame> frames) {
		Vector<ImcStmt> stmts = new Vector<>();
		for (AstStmt s : stmtExpr.stmts.asts())
			stmts.add( s.accept(new StmtGenerator(), frames) );
		AstStmt lastStmt = stmtExpr.stmts.asts().get(stmtExpr.stmts.asts().size()-1);


		ImcExpr lastExpr = null;
		if (lastStmt instanceof AstExprStmt){
			lastExpr = ((AstExprStmt)lastStmt).expr.accept(this, frames);
			stmts.remove( stmts.size()-1 );
		}

		ImcExpr finalOp = new ImcSEXPR(new ImcSTMTS(stmts), lastExpr==null ? new ImcCONST(0) : lastExpr);
		ImcGen.exprImc.put(stmtExpr, finalOp);

		return finalOp;
	}



}
