package pins.phase.synan;

import pins.common.report.Location;
import pins.common.report.Report;
import pins.data.symbol.*;
import pins.data.ast.*;
import pins.phase.lexan.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Stack;
import java.util.Vector;

public class SynAn implements AutoCloseable {

	private LexAn lex;
	private Symbol s;
	private Symbol prev = null;
	private Symbol temp = null;
	private Stack<Symbol> temporarySymbolManager = new Stack<>();


	public SynAn(LexAn lexan) {
		lex = lexan;
	}

	public void close() {
	}

	public AST parser() {
		return parse_prg();
	}

	private AST parse_prg() {
		ArrayList<AstDecl> astDecls = new ArrayList<>();
		switch (read()) {
			case TYP:
			case VAR:
			case FUN:
				//System.out.println("prg -> decl prg\'");
				astDecls.add(parse_decl());
				return parse_prg_(astDecls);
			default:
				error("Expected declaration, got", s);
		}
		return null;
	}

	private ASTs parse_prg_(ArrayList astsDecls) {
		switch (read()) {
			case TYP:
			case VAR:
			case FUN:
				//System.out.println("prg\' -> decl prg\'");
				astsDecls.add(parse_decl());
				return parse_prg_(astsDecls);
			case EOF:
				//System.out.println("prg\' -> .");
				return new ASTs(s.location, astsDecls);
			default:
				error("Expected declaration, got", s);
		}
		return null;
	}

	private ASTs parse_prg2_(ArrayList astsDecls) {
		switch (read()) {
			case TYP:
			case VAR:
			case FUN:
				//System.out.println("prg\' -> decl prg\'");
				astsDecls.add(parse_decl());
				return parse_prg2_(astsDecls);
			case RB:
				return new ASTs(s.location, astsDecls);
			default:
				error("Expected declaration, got", s);
		}
		return null;
	}


	private AstDecl parse_decl(){
		switch (peek()){
			case TYP:
				//System.out.println("decl -> typ id = type ;");
				if (read()==Token.ID){
					String name = s.lexeme;
					temporarySymbolManager.push(s);
					if(read()==Token.ASSIGN){
						AstType t = parse_type();
					if (peek()==Token.SEMI_COLON){
						return new AstTypDecl(temporarySymbolManager.pop().location, name, t);
					}else {
						error("Expected Semi Colon, instead got", s);
					}
				}}
				error("Illegal Typ Declaration", s);
			case VAR:
				//System.out.println("decl -> var id : type ;");
				if (read()==Token.ID){
					String name = s.lexeme;
					temporarySymbolManager.push(s);
					if (read()==Token.COLON){
						AstType t = parse_type();
					if (peek()==Token.SEMI_COLON){
						return new AstVarDecl(temporarySymbolManager.pop().location, name, t);
					}else{
						error("Expected Semi Colon, instead got", s);
					}
				}}
				error("Illegal Var Declaration", s);
			case FUN:
				//System.out.println("decl -> fun id ( arg ) : type = expr ;");
				if (read()==Token.ID){
					temporarySymbolManager.push(s);
					Symbol name = s;
					if (read()==Token.LB){
						ArrayList<ASTs> astsParams = new ArrayList<>();
						ASTs parameters = parse_arg(astsParams);
					if (peek()==Token.RB && read()==Token.COLON) {
						AstType t = parse_type();
						if (peek() == Token.ASSIGN) {
							AstExpr e = parse_expr();
							if (peek() == Token.SEMI_COLON){
								if (!temporarySymbolManager.isEmpty())
									temporarySymbolManager.pop();
								return new AstFunDecl(name.location, name.lexeme, parameters, t, e);
							}else{
								error("Expected Semi Colon, instead got", s);
							}
						}
					}
				}}
				error("Illegal Fun Declaration", s);
			default: error("Unexpected Symbol", s);
		}return null;
	}

	private ASTs parse_arg(ArrayList astsParams){
		switch (read()){
			case ID:
				//System.out.println("arg -> id : type arg\'");
				temporarySymbolManager.push(s);
				if (read()==Token.COLON){
					AstType t = parse_type();
					astsParams.add( new AstParDecl(temporarySymbolManager.peek().location, temporarySymbolManager.pop().lexeme, t) );
					return parse_arg_(astsParams);
				}
				error("Illegal Argument Style", s);
			case RB:
				//System.out.println("arg -> .");
				return new ASTs(s.location, astsParams);
			default: error("Unexpected Symbol", s);
		}return null;
	}

	private ASTs parse_arg_(ArrayList astsParams){
		switch (peek()){
			case RB:
				//System.out.println("arg\' -> .");
				return new ASTs(s.location, astsParams);
			case COMMA:
				//System.out.println("arg\' -> , id : type arg\'");
				if (read()==Token.ID){
					temporarySymbolManager.push(s);
					if (read()==Token.COLON){
						AstType t = parse_type();
						astsParams.add( new AstParDecl(temporarySymbolManager.peek().location, temporarySymbolManager.pop().lexeme, t) );
						return parse_arg_(astsParams);
				}}
				error("Illegal Argument Style", s);
			default: error("Unexpected Symbol", s);
		}return null;
	}

	private AstType parse_type(){
		switch (read()){
			case ID:
				temporarySymbolManager.push(s);
				read();
				return new AstTypeName(temporarySymbolManager.peek().location, temporarySymbolManager.pop().lexeme);
			case LB:
				//System.out.println("type -> ( type )");
				AstType t = parse_type();
				if (peek()==Token.RB) {
					read();
					return t;
				}
				error("Missing Brackets, maybe? got", s);
			case VOID:
				//System.out.println("type -> void");
				read();
				return new AstAtomType(s.location, AstAtomType.Kind.VOID);
			case CHAR:
				//System.out.println("type -> char");
				read();
				return new AstAtomType(s.location, AstAtomType.Kind.CHAR);
			case INT:
				//System.out.println("type -> int");
				read();
				return new AstAtomType(s.location, AstAtomType.Kind.INT);
			case LSB:
				//System.out.println("type -> [ expr ] type");
				AstExpr e = parse_expr();
				AstType ta = parse_type();
				return new AstArrType(s.location, ta, e);
			case EXP:
				//System.out.println("type -> ^ type");
				return new AstPtrType(s.location, parse_type());
			default: error("Unexpected Symbol", s);
		}return null;
	}

	private AstExpr parse_expr_(){
		switch (peek()){
			case ID:
				//System.out.println("expr\' -> id funcall");
				AstNameExpr name = new AstNameExpr(s.location, s.lexeme);
				return parse_funcall(name);
			case LB:
				//System.out.println("expr\' -> ( expr typecast )");
				AstExpr e = parse_expr();
				AstExpr t = parse_typecast(e);
				if (peek()==Token.RB)
					return t;
			error("Unstructured Expression", s);
			case VOID_CONST:
				//System.out.println("expr\' -> void_const");
				return new AstConstExpr(s.location, AstConstExpr.Kind.VOID, s.lexeme);
			case INT_CONST:
				//System.out.println("expr\' -> int_const");
				return new AstConstExpr(s.location, AstConstExpr.Kind.INT, s.lexeme);
			case CHAR_CONST:
				//System.out.println("expr\' -> char_const");
				return new AstConstExpr(s.location, AstConstExpr.Kind.CHAR, s.lexeme);
			case POINT_CONST:
				//System.out.println("expr\' -> point_const");
				return new AstConstExpr(s.location, AstConstExpr.Kind.PTR, s.lexeme);
			case LCB:
				//System.out.println("expr\' -> { stmt compound }");
				ArrayList<AstStmt> stmts = new ArrayList<>();
				temporarySymbolManager.push(s);
				read();
				stmts.add( parse_stmt() );
				AstStmtExpr c = parse_compound(stmts);
				if (peek()==Token.RCB)
					return c;
				error("Illegal Expression Construction", s);
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstExpr parse_expr(){
		Token p;
		if (temp == null) {
			p = read();
		}else{
			p = temp.token;
			temp = null;
		}
		switch (p){
			case ID: case LB: case EXP: case VOID_CONST: case INT_CONST: case CHAR_CONST:
			case POINT_CONST: case LCB: case PLUS: case MINUS: case NOT:
				//System.out.println("expr -> C D\'");
				AstExpr left = parse_C();
				AstExpr result = parse_D_(left);
				return result;
			case NEW: case DEL:
				//System.out.println("expr -> newdel expr");
				return new AstPreExpr(s.location, parse_newdel(), parse_expr());
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstNameExpr parse_funcall(AstNameExpr name){
		switch (read()){
			case ASSIGN: case SEMI_COLON: case COLON: case RB: case COMMA:
			case LSB: case RSB: case EXP: case WHERE: case OR: case AND:
			case EQUAL: case NOT_EQUAL: case LESS: case GREATER: case LESSEQUAL:
			case GREATEREQUAL: case PLUS: case MINUS: case MUL: case DIV:
			case MOD: case THEN: case DO:
				//System.out.println("funcall -> .");
				temp = s;
				return name;
			case LB:
				//System.out.println("funcall -> ( funcallarg )");
				temporarySymbolManager.push(s);
				ArrayList<AstExpr> args = new ArrayList<>();
				ASTs arg = parse_funcallarg(args);
				if (peek()==Token.RB)
					return new AstCallExpr(temporarySymbolManager.pop().location, name.name, arg);
				error("Missing Brackets, maybe? Got", s);
			default: error("Unexpected Symbol", s);
		}return null;
	}

	private ASTs parse_funcallarg(ArrayList args){
		switch (read()){
			case ID: case LB: case EXP: case VOID_CONST: case INT_CONST:
			case CHAR_CONST: case POINT_CONST: case LCB: case NEW:
			case DEL: case PLUS: case MINUS: case NOT:
				//System.out.println("funcallarg -> expr funcallarg\'");
				temp = s;
				args.add( parse_expr() );
				return parse_funcallarg_(args);
			case RB:
				//System.out.println("funcallarg -> .");
				return new ASTs(s.location, args);
			default: error("Unexpected Symbol", s);
		}return null;
	}

	private ASTs parse_funcallarg_(ArrayList args){
		switch (peek()){
			case COMMA:
				//System.out.println("funcallarg\' -> , expr funcallarg\'");
				args.add( parse_expr() );
				return parse_funcallarg_(args);
			case RB:
				//System.out.println("funcallarg\' -> .");
				return new ASTs(s.location, args);
			default: error("Unexpected Symbol", s);
		}return null;
	}

	private AstPreExpr.Oper parse_newdel(){
		switch (peek()){
			case NEW:
				//System.out.println("newdel -> new");
				return AstPreExpr.Oper.NEW;
			case DEL:
				//System.out.println("newdel -> del");
				return AstPreExpr.Oper.DEL;
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstStmtExpr parse_compound(ArrayList stmts){
		switch (read()){
			case ID: case LB: case EXP: case VOID_CONST: case INT_CONST: case CHAR_CONST:
			case POINT_CONST: case LCB: case NEW: case DEL: case PLUS: case MINUS:
			case NOT: case IF: case WHILE:
				//System.out.println("compound -> stmt compound");
				stmts.add( parse_stmt() );
				return parse_compound(stmts);
			case RCB:
				//System.out.println("compound -> .");
				if (temporarySymbolManager.isEmpty())
					return new AstStmtExpr(s.location, new ASTs(s.location, stmts));
				return new AstStmtExpr(temporarySymbolManager.peek().location, new ASTs(temporarySymbolManager.pop().location, stmts));
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstExpr parse_typecast(AstExpr e){
		switch (peek()){
			case COLON:
				//System.out.println("typecast -> : type");
				return new AstCastExpr(s.location, e, parse_type());
			case RB:
				//System.out.println("typecast -> .");
				return e;
			case WHERE:
				//System.out.println("typecast -> where decl prg\'");
				ArrayList<AstDecl> decls = new ArrayList<>();
				temporarySymbolManager.push(s);
				read();
				decls.add( parse_decl() );
				return new AstWhereExpr(temporarySymbolManager.pop().location, parse_prg2_(decls), e);
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstExpr parse_D_(AstExpr left){
		switch (peek()){
			case ASSIGN: case SEMI_COLON: case COLON: case RB: case COMMA: case RSB:
			case WHERE: case THEN: case DO:
				//System.out.println("D\' -> .");
				return left;
			case OR:
				//System.out.println("D\' -> or C D\'");
				read();
				AstExpr right = parse_C();
				AstExpr dis = new AstBinExpr(s.location, AstBinExpr.Oper.OR, left, right);
				return parse_D_(dis);
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstExpr parse_C(){
		switch (peek()){
			case ID: case LB: case EXP: case VOID_CONST: case INT_CONST: case CHAR_CONST:
			case POINT_CONST: case LCB: case PLUS: case MINUS: case NOT:
				//System.out.println("C -> R C\'");
				AstExpr r = parse_R();
				return parse_C_(r);
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstExpr parse_C_(AstExpr left){
		switch (peek()){
			case ASSIGN: case SEMI_COLON: case COLON: case RB: case COMMA: case RSB:
			case WHERE: case THEN: case DO: case OR:
				//System.out.println("C\' -> .");
				return left;
			case AND:
				//System.out.println("C\' -> and R C\'");
				read();
				AstExpr right = parse_R();
				AstExpr con = new AstBinExpr(s.location, AstBinExpr.Oper.AND, left, right);
				return parse_C_(con);
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstExpr parse_R(){
		switch (peek()){
			case ID: case LB: case EXP: case VOID_CONST: case INT_CONST: case CHAR_CONST:
			case POINT_CONST: case LCB: case PLUS: case MINUS: case NOT:
				//System.out.println("R -> A R\'");
				AstExpr a = parse_A();
				return parse_R_(a);
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstExpr parse_R_(AstExpr left){
		switch (peek()){
			case ASSIGN: case SEMI_COLON: case COLON: case RB: case COMMA: case RSB:
			case WHERE: case THEN: case DO: case OR: case AND:
				//System.out.println("R\' -> .");
				return left;
			case EQUAL:
				//System.out.println("R\' -> == A R\'");
				read();
				AstExpr right1 =  parse_A();
				AstExpr equ1 = new AstBinExpr(s.location, AstBinExpr.Oper.EQU, left, right1);
				return parse_R_(equ1);
			case NOT_EQUAL:
				//System.out.println("R\' -> != A R\'");
				read();
				AstExpr right2 =  parse_A();
				AstExpr equ2 = new AstBinExpr(s.location, AstBinExpr.Oper.NEQ, left, right2);
				return parse_R_(equ2);
			case LESS:
				//System.out.println("R\' -> < A R\'");
				read();
				AstExpr right3 =  parse_A();
				AstExpr equ3 = new AstBinExpr(s.location, AstBinExpr.Oper.LTH, left, right3);
				return parse_R_(equ3);
			case GREATER:
				//System.out.println("R\' -> > A R\'");
				read();
				AstExpr right4 =  parse_A();
				AstExpr equ4 = new AstBinExpr(s.location, AstBinExpr.Oper.GTH, left, right4);
				return parse_R_(equ4);
			case LESSEQUAL:
				//System.out.println("R\' -> <= A R\'");
				read();
				AstExpr right5 =  parse_A();
				AstExpr equ5 = new AstBinExpr(s.location, AstBinExpr.Oper.LEQ, left, right5);
				return parse_R_(equ5);
			case GREATEREQUAL:
				//System.out.println("R\' -> >= A R\'");
				read();
				AstExpr right6 =  parse_A();
				AstExpr equ6 = new AstBinExpr(s.location, AstBinExpr.Oper.GEQ, left, right6);
				return parse_R_(equ6);
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstExpr parse_A(){
		switch (peek()){
			case ID: case LB: case EXP: case VOID_CONST: case INT_CONST: case CHAR_CONST:
			case POINT_CONST: case LCB: case PLUS: case MINUS: case NOT:
				//System.out.println("A -> M A\'");
				AstExpr m = parse_M();
				return parse_A_(m);
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstExpr parse_A_(AstExpr left){
		switch (peek()){
			case ASSIGN: case SEMI_COLON: case COLON: case RB: case COMMA: case RSB:
			case WHERE: case THEN: case DO: case OR: case AND: case EQUAL: case NOT_EQUAL:
			case LESS: case GREATER: case LESSEQUAL: case GREATEREQUAL:
				//System.out.println("A\' -> .");
				return left;
			case PLUS:
				//System.out.println("A\' -> + M A\'");
				read();
				AstExpr right1 = parse_M();
				AstExpr sum1 = new AstBinExpr(s.location, AstBinExpr.Oper.ADD, left, right1);
				return parse_A_(sum1);
			case MINUS:
				//System.out.println("A\' -> - M A\'");
				read();
				AstExpr right2 = parse_M();
				AstExpr sum2 = new AstBinExpr(s.location, AstBinExpr.Oper.SUB, left, right2);
				return parse_A_(sum2);
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstExpr parse_M(){
		switch (peek()){
			case ID: case LB: case EXP: case VOID_CONST: case INT_CONST: case CHAR_CONST:
			case POINT_CONST: case LCB: case PLUS: case MINUS: case NOT:
				//System.out.println("M -> Pr M\'");
				AstExpr pr = parse_Pr();
				return parse_M_(pr);
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstExpr parse_M_(AstExpr left){
		switch (peek()){
			case ASSIGN: case SEMI_COLON: case COLON: case RB: case COMMA: case RSB:
			case WHERE: case THEN: case DO: case OR: case AND: case EQUAL: case NOT_EQUAL:
			case LESS: case GREATER: case LESSEQUAL: case GREATEREQUAL: case PLUS:
			case MINUS:
				//System.out.println("M\' -> .");
				return left;
			case MUL:
				//System.out.println("M\' -> * Pr M\'");
				read();
				AstExpr right1 = parse_Pr();
				AstExpr mul1 = new AstBinExpr(s.location, AstBinExpr.Oper.MUL, left, right1);
				return parse_M_(mul1);
			case DIV:
				//System.out.println("M\' -> / Pr M\'");
				read();
				AstExpr right2 = parse_Pr();
				AstExpr mul2 = new AstBinExpr(s.location, AstBinExpr.Oper.DIV, left, right2);
				return parse_M_(mul2);
			case MOD:
				//System.out.println("M\' -> % Pr M\'");
				read();
				AstExpr right3 = parse_Pr();
				AstExpr mul3 = new AstBinExpr(s.location, AstBinExpr.Oper.MOD, left, right3);
				return parse_M_(mul3);
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstExpr parse_Pr(){
		switch (peek()){
			case ID: case LB: case VOID_CONST: case INT_CONST: case CHAR_CONST:
			case POINT_CONST: case LCB:
				//System.out.println("Pr -> Po");
				return parse_Po();
			case EXP:
				//System.out.println("Pr -> ^ Po");
				read();
				return new AstPreExpr(s.location, AstPreExpr.Oper.PTR, parse_Po());
			case PLUS:
				//System.out.println("Pr -> + Po");
				read();
				return new AstPreExpr(s.location, AstPreExpr.Oper.ADD, parse_Po());
			case MINUS:
				//System.out.println("Pr -> - Po");
				read();
				return new AstPreExpr(s.location, AstPreExpr.Oper.SUB, parse_Po());
			case NOT:
				//System.out.println("Pr -> ! Po");
				read();
				return new AstPreExpr(s.location, AstPreExpr.Oper.NOT, parse_Po());
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstExpr parse_Po(){
		switch (peek()){
			case ID: case LB: case VOID_CONST: case INT_CONST: case CHAR_CONST:
			case POINT_CONST: case LCB:
				//System.out.println("Po -> expr\' Po\'");
				AstExpr e = parse_expr_();
				AstExpr p = parse_Po_(e);
				return p;
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstExpr parse_Po_(AstExpr left){
		Token p;
		if (temp == null) {
			p = read();
		}else{
			p = temp.token;
			temp = null;
		}

		switch (p){
			case ASSIGN: case SEMI_COLON: case COLON: case RB: case COMMA: case RSB:
			case WHERE: case THEN: case DO: case OR: case AND: case EQUAL: case NOT_EQUAL:
			case LESS: case GREATER: case LESSEQUAL: case GREATEREQUAL: case PLUS:
			case MINUS: case MUL: case DIV: case MOD:
				//System.out.println("Po\' -> .");
				return left;
			case LSB:
				//System.out.println("Po\' -> [ expr ] Po\'");
				AstExpr e = parse_expr();
				AstExpr result = new AstBinExpr(s.location, AstBinExpr.Oper.ARR, left, e);
				if (peek()==Token.RSB) {
					// Working varianta, samo n-dimenzionalne arraye napačno prioritizira -> a([0][0])
					/*AstExpr po = parse_Po_(e);
					return new AstBinExpr(s.location, AstBinExpr.Oper.ARR, left, po);*/
					return parse_Po_(result);
				}
				error("Illegal Postfix Notation", s);
			case EXP:
				//System.out.println("Po\' -> ^ Po\'");
				return new AstPstExpr(s.location, AstPstExpr.Oper.PTR, parse_Po_(left));
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstStmt parse_stmt(){
		switch (peek()){
			case ID: case LB: case EXP: case VOID_CONST: case INT_CONST: case CHAR_CONST:
			case POINT_CONST: case LCB: case NEW: case DEL: case PLUS: case MINUS: case NOT:
				//System.out.println("stmt -> expr assigment ;");
				temp = s;
				AstExpr e = parse_expr();
				AstStmt a = parse_assigment(e);
				if (peek()==Token.SEMI_COLON)
					return a;
				error("Expected Semi Colon, got ", s);
			case IF:
				//System.out.println("stmt -> if expr then stmt condition elif end ;");
				temporarySymbolManager.push(s);
				Symbol name_if = s;
				AstExpr eif = parse_expr();
				if (peek()==Token.THEN){
					ArrayList<AstStmt> stmts = new ArrayList<>();
					read();
					temporarySymbolManager.push(s);
					stmts.add( parse_stmt() );
					AstStmt list = parse_condition(stmts);
					AstStmt elif = parse_elif();
					if (peek()==Token.END)
						if (read()==Token.SEMI_COLON){
							if (temporarySymbolManager.isEmpty())
								return new AstIfStmt(name_if.location, eif, list, elif);
							return new AstIfStmt(temporarySymbolManager.pop().location, eif, list, elif);
						}
				}
				error("Illegal If Declaration", s);
			case WHILE:
				//System.out.println("stmt -> while expr do stmt condition end ;");
				temporarySymbolManager.push(s);
				Symbol name_while = s;
				AstExpr ewhile = parse_expr();
				if (peek()==Token.DO){
					ArrayList<AstStmt> stmts = new ArrayList<>();
					read();
					temporarySymbolManager.push(s);
					stmts.add( parse_stmt() );
					AstStmt result = parse_condition(stmts);
					if (peek()==Token.END)
						if (read()==Token.SEMI_COLON){
							if (temporarySymbolManager.isEmpty())
								return new AstWhileStmt(name_while.location, ewhile, result);
							return new AstWhileStmt(temporarySymbolManager.pop().location, ewhile, result);
						}

				}
				error("Illegal While Declaration", s);
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstStmt parse_assigment(AstExpr e){
		switch (peek()){
			case ASSIGN:
				//System.out.println("assigment -> = expr");
				return new AstAssignStmt(s.location, e, parse_expr());
			case SEMI_COLON:
				//System.out.println("assigment -> .");
				return new AstExprStmt(s.location, e);
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstStmt parse_condition(ArrayList stmts){
		switch (read()){
			case ID: case LB: case EXP: case VOID_CONST: case INT_CONST: case CHAR_CONST:
			case POINT_CONST: case LCB: case NEW: case DEL: case PLUS: case MINUS:
			case NOT: case IF: case WHILE:
				//System.out.println("condition -> stmt condition");
				stmts.add( parse_stmt() );
				return parse_condition(stmts);
			case END: case ELSE:
				//System.out.println("condition -> .");
				if (temporarySymbolManager.isEmpty())
					return new AstExprStmt(s.location, new AstStmtExpr(s.location, new ASTs(s.location, stmts)));
				return new AstExprStmt(temporarySymbolManager.peek().location, new AstStmtExpr(temporarySymbolManager.pop().location, new ASTs(s.location, stmts)));
			default : error("Unexpected Symbol", s);
		}return null;
	}

	private AstStmt parse_elif(){
		switch (peek()){
			case ELSE:
				//System.out.println("elif -> else stmt condition");
				ArrayList<AST> stmts = new ArrayList<>();
				read();
				stmts.add(parse_stmt());
				return parse_condition(stmts);
			case END:
				//System.out.println("elif -> .");
				return null;
			default : error("Unexpected Symbol", s);
		}return null;
	}


	// SUPPORT FUNCTIONS
	private Token peek(){
		return s.token;
	}
	private Token read(){
		prev = s;
		s = lex.lexer();
		return s.token;
	}

	private void error(String message, Symbol sym) throws Report.Error{
		message = "SYNTAX ERROR: "+message;
		if (sym.token==Token.EOF && prev==null)
			throw new Report.Error("Empty File");
		if (sym.location==null)
			throw new Report.Error(prev.location, message);
		throw new Report.Error(sym.location, message+" "+sym.lexeme);
	}


}
