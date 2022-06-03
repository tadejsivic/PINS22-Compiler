package pins.phase.seman;

import java.util.Vector;

import pins.common.report.Report;
import pins.data.ast.*;
import pins.data.ast.AstBinExpr.Oper;
import pins.data.ast.visitor.AstFullVisitor;
import pins.data.typ.*;


public class TypeChecker<Result, Arg> extends AstFullVisitor<Result, Arg> {

    boolean repeated_cycle = false;

    public TypeChecker(){}

    // Methods

    @Override
    public Result visit(ASTs<? extends AST> trees, Arg arg) {
        for (int i=0; i<2; i++) {
            for (AST t : trees.asts())
                if (t != null)
                    t.accept(this, arg);
        }
        return null;
    }

    // DECLARATIONS

    public Result visit(AstFunDecl funDecl, Arg arg) {
        if (funDecl.pars != null)
            funDecl.pars.accept(this, arg);

        SemType fun_type = (SemType) funDecl.type.accept(this, arg);
        if (fun_type==null) return null;
        SemAn.describesType.put( funDecl.type, fun_type.actualType() );

        SemType stmt_type = (SemType) funDecl.expr.accept(this, arg);

        if (stmt_type == null) return null;

        if (!fun_type.actualType().getClass().equals(stmt_type.actualType().getClass()))
            throwError(funDecl.expr, "Function type does not match that of its statements");

        return null;
    }

    public Result visit(AstParDecl parDecl, Arg arg) {

        if (SemAn.describesType.get(parDecl.type)!=null)
            return null;
        SemType subtype = (SemType) parDecl.type.accept(this, arg);
        if (subtype==null) return null;
        if (subtype.actualType() instanceof SemInt || subtype.actualType() instanceof SemChar
                || subtype.actualType() instanceof SemPtr) {
            SemAn.describesType.put(parDecl.type, subtype.actualType());
        }
        else
            throwError(parDecl, "Parameters can only be of types int, char or pointer");
        return null;
    }

    public Result visit(AstTypDecl typDecl, Arg arg) {
        SemName s = new SemName(typDecl.name);
        if (SemAn.declaresType.get(typDecl)!=null)
            return null;
        SemType subtype = (SemType) typDecl.type.accept(this, arg) ;
        if (subtype==null) return null;
        s.define(subtype.actualType());
        if (subtype != null) {
            SemAn.declaresType.put(typDecl, s);
        }

        return null;
    }

    public Result visit(AstVarDecl varDecl, Arg arg) {
        if (SemAn.describesType.get(varDecl)!=null)
            return null;

        SemType subtype = (SemType) varDecl.type.accept(this, arg) ;

        if (subtype != null)
            SemAn.describesType.put(varDecl.type, subtype.actualType());
        return null;
    }

    // TYPES

    public Result visit(AstAtomType atomType, Arg arg){
        switch (atomType.kind){
            case INT:
                SemType i = new SemInt();
                SemAn.describesType.put(atomType, i);
                return (Result) i;
            case VOID:
                SemType v = new SemVoid();
                SemAn.describesType.put(atomType, v);
                return (Result) v;
            case CHAR:
                SemType c = new SemChar();
                SemAn.describesType.put(atomType, c);
                return (Result) c;
        }
        return null;
    }

    public Result visit(AstTypeName typeName, Arg arg){
        SemType d = SemAn.declaresType.get( SemAn.declaredAt.get(typeName) );
        SemType r = null;
        if (d==null) {
            r = (SemType) SemAn.declaredAt.get(typeName).accept(this, arg);
            if (r==null)
                return null;
            SemAn.describesType.put(typeName, r.actualType());
            return (Result) r.actualType();
        }
        SemAn.describesType.put(typeName, d.actualType());
        return (Result) d.actualType();
    }

    public Result visit(AstPtrType ptrType, Arg arg) {
        SemType subtype = (SemType) ptrType.subType.accept(this, arg);
        if (subtype != null) {
            SemType p = new SemPtr(subtype);
            SemAn.describesType.put(ptrType, p.actualType());
            return (Result) p.actualType();
        }
        else
            return null;
    }

    public Result visit(AstArrType arrType, Arg arg) {
        SemType subtype = (SemType) arrType.elemType.accept(this, arg);
        SemType sizeExpr = (SemType) arrType.size.accept(this, arg);
        if ( sizeExpr instanceof SemInt ){
            if (subtype instanceof SemVoid)
                throwError(arrType.elemType, "Cannot make an array of void");
            long size=-1;
            try {
                size = Long.parseLong(((AstConstExpr) arrType.size).name);
            }catch (Exception e){
                throwError(arrType.size, "Only single integer sizes allowed");
            }
            if ( size > 0){
                SemType arr = new SemArr( subtype, size );
                if (arr==null) return null;
                SemAn.describesType.put(arrType, arr.actualType());
                super.visit(arrType, arg);
                return (Result) arr;
            }
        }
        throwError(arrType.size, "Only positive integer sizes allowed");
        return null;
    }

    // EXPRESSIONS

    public Result visit(AstConstExpr constExpr, Arg arg) {
        switch (constExpr.kind){
            case INT:
                SemInt s = new SemInt();
                SemAn.exprOfType.put(constExpr, s);
                return (Result) s;
            case CHAR:
                SemChar c = new SemChar();
                SemAn.exprOfType.put(constExpr, c);
                return (Result) c;
            case VOID:
                SemVoid v = new SemVoid();
                SemAn.exprOfType.put(constExpr, v);
                return (Result) v;
            case PTR:
                SemPtr p = new SemPtr( new SemVoid());
                SemAn.exprOfType.put(constExpr, p);
                return (Result) p;
        }
        return null;
    }

    public Result visit(AstNameExpr nameExpr, Arg arg) {
        SemType tip = (SemType) (SemAn.declaredAt.get(nameExpr).type).accept(this, arg);
        if (tip==null) return null;
        SemAn.exprOfType.put(nameExpr, tip.actualType());
        return (Result) tip.actualType();
    }

    public Result visit(AstPreExpr preExpr, Arg arg) {
        SemType expr = (SemType) preExpr.subExpr.accept(this, arg);
        if (expr==null) return null;
        if (preExpr.oper == AstPreExpr.Oper.ADD || preExpr.oper == AstPreExpr.Oper.SUB ||
            preExpr.oper == AstPreExpr.Oper.NOT ) {
            if ( expr.actualType() instanceof SemInt ) {
               SemAn.exprOfType.put(preExpr, new SemInt());
                return (Result) new SemInt();
            }else{
                throwError(preExpr, "Unary operands must be of type int");
            }
        }else if (preExpr.oper == AstPreExpr.Oper.NEW){
            if(!(expr.actualType() instanceof SemInt))
                throwError(preExpr.subExpr, "Expression should be of type int");            
            SemAn.exprOfType.put(preExpr, new SemPtr(new SemVoid()));
            return (Result) new SemPtr(new SemVoid());
        }else if (preExpr.oper == AstPreExpr.Oper.DEL){
            if(!(expr.actualType() instanceof SemPtr))
                throwError(preExpr.subExpr, "Expression should be of type pointer");            
            SemAn.exprOfType.put(preExpr, new SemVoid());
            return (Result) new SemVoid();
        }else if (preExpr.oper == AstPreExpr.Oper.PTR){
            SemType subtype = (SemType) preExpr.subExpr.accept(this, arg);
            SemAn.exprOfType.put(preExpr, new SemPtr(subtype));
            return (Result) new SemPtr(subtype);
        }

        return null;
    }

    public Result visit(AstPstExpr pstExpr, Arg arg) {
        SemType subtype = (SemType) pstExpr.subExpr.accept(this, arg);
        if (subtype==null) return null;
        if (!(subtype.actualType() instanceof SemPtr))
            throwError(pstExpr, "This operator requires a pointer type");
        if (((SemPtr)subtype).baseType==null) return null;
        SemAn.exprOfType.put(pstExpr, ((SemPtr)subtype).baseType.actualType());
        return (Result) ((SemPtr)subtype).baseType.actualType();
    }

    public Result visit(AstBinExpr binExpr, Arg arg) {
        SemType t1 = (SemType) binExpr.fstSubExpr.accept(this, arg);
        SemType t2 = (SemType) binExpr.sndSubExpr.accept(this, arg);
        if (SemAn.exprOfType.get(binExpr)!=null) return null;
        if (t1 == null || t2 == null) return null;

        if (binExpr.oper == AstBinExpr.Oper.ADD || binExpr.oper == AstBinExpr.Oper.SUB ||
        binExpr.oper == AstBinExpr.Oper.MUL || binExpr.oper == AstBinExpr.Oper.DIV ||
        binExpr.oper == AstBinExpr.Oper.MOD){
            if (!(t1.actualType() instanceof SemInt))
                throwError(binExpr.fstSubExpr, "Operand should be of type int");
            if (!(t2.actualType() instanceof SemInt))
                throwError(binExpr.sndSubExpr, "Operand should be of type int");
            SemAn.exprOfType.put(binExpr, new SemInt());
            return (Result) new SemInt();

        } else if (binExpr.oper == AstBinExpr.Oper.EQU || binExpr.oper == AstBinExpr.Oper.NEQ ||
        binExpr.oper == AstBinExpr.Oper.LTH || binExpr.oper == AstBinExpr.Oper.GTH || 
        binExpr.oper == AstBinExpr.Oper.LEQ || binExpr.oper == AstBinExpr.Oper.GEQ){
            if(t1.getClass().equals(t2.getClass())){            
                if (t1.actualType() instanceof SemInt || t1.actualType() instanceof SemChar ||
                        t1.actualType() instanceof SemPtr){
                    if (t1.actualType() instanceof SemPtr){
                        if (! (((SemPtr)t1).baseType.getClass().equals( ((SemPtr)t2).baseType.getClass() ) )){
                            throwError(binExpr.fstSubExpr, "Pointers do not match in subtypes");
                        }   
                    }
                    SemAn.exprOfType.put(binExpr, new SemInt());
                    return (Result) new SemInt();
                }
                else 
                    throwError(binExpr.fstSubExpr, "Only types int, char and ptr(x) are allowed here");
            }               
            else
                throwError(binExpr.fstSubExpr, "Operands should be of the same type");

        } else if (binExpr.oper == AstBinExpr.Oper.ARR){
            if (!(t1 instanceof SemArr))
                throwError(binExpr.fstSubExpr, "Accessing an array requires operand to be of type array");
            if (!(t2.actualType() instanceof SemInt))
                throwError(binExpr.sndSubExpr, "Accessing an array requires index to be of type int");
            SemAn.exprOfType.put(binExpr, ((SemArr)t1).elemType);
            return (Result) ((SemArr)t1).elemType;

        } else if (binExpr.oper == AstBinExpr.Oper.OR || binExpr.oper == AstBinExpr.Oper.AND){
            SemAn.exprOfType.put(binExpr, new SemInt());
            return (Result) new SemInt();
        }

        return null;
    }

    public Result visit(AstCallExpr callExpr, Arg arg) {
        if (callExpr.args != null)
            callExpr.args.accept(this, arg);

        AstDecl decl =  SemAn.declaredAt.get(callExpr);
        decl.accept(this, arg);
        if (decl == null) return null;
        if(SemAn.describesType.get(decl.type) == null) return null;
        if (decl instanceof AstFunDecl){
            Vector pars = ((AstFunDecl) decl).pars.asts();
            if (pars.size()!=0)
                if (SemAn.describesType.get( ((AstParDecl)(pars.get(0))).type ) == null) return null;
            Vector args = callExpr.args.asts();
            if (pars.size() != args.size())
                throwError(callExpr, "The number of arguments differs from the number of parameters");
            boolean matches = true;
            int arg_error = -1;
            for (int i=0; i < args.size(); i++){
                SemType arg_type = SemAn.exprOfType.get(args.get(i));
                if (arg_type==null) return null;
                if (!(arg_type.actualType() instanceof SemInt || arg_type.actualType() instanceof SemChar
                        || arg_type.actualType() instanceof SemPtr))
                    throwError((AST)args.get(i), "Only types of int, char and pointer are allowed here");
                SemType par_type =  SemAn.describesType.get( ((AstParDecl)(pars.get(i))).type ) ;
                if (par_type==null) return null;
                if (!( arg_type.actualType().getClass().equals( par_type.actualType().getClass() ))){
                    matches = false;
                    arg_error = i;
                    break;
                }
            }
            if (matches){
                SemAn.exprOfType.put(callExpr, SemAn.describesType.get(decl.type).actualType());
                return (Result) SemAn.describesType.get(decl.type).actualType();
            }else{
                throwError((AST)args.get(arg_error), "Argument type differs from parameter type");
            }
        }else
            throwError(callExpr, "Unstructured call. Check if it is a function you are calling");
        
		return null;
	}

    public Result visit(AstCastExpr castExpr, Arg arg) {
        SemType expr_type = (SemType) castExpr.subExpr.accept(this, arg);
        SemType type_type = (SemType) castExpr.type.accept(this, arg);
        if(type_type == null || expr_type == null)
            return null;
        if (!( expr_type.actualType() instanceof SemInt || expr_type.actualType() instanceof SemChar || expr_type.actualType() instanceof SemPtr))
            throwError(castExpr.subExpr, "Only expressions of types int, char and pointer are allowed");
        if (!(type_type.actualType() instanceof SemInt || type_type.actualType() instanceof SemChar || type_type.actualType() instanceof SemPtr))
            throwError(castExpr.type, "Only types of int, char and pointer are allowed");
        SemAn.exprOfType.put(castExpr, type_type.actualType());
        return (Result) type_type.actualType();
    }

    public Result visit(AstWhereExpr whereExpr, Arg arg) {
        if (SemAn.exprOfType.get(whereExpr)!=null)
            return (Result) SemAn.exprOfType.get(whereExpr);
        SemType expr = (SemType) whereExpr.subExpr.accept(this, arg);
        SemType decls = (SemType) whereExpr.decls.accept(this, arg);
        SemAn.exprOfType.put(whereExpr, expr);
        return (Result)expr;
    }


    public Result visit(AstStmtExpr stmtExpr, Arg arg) {
        if (stmtExpr.stmts != null)
            stmtExpr.stmts.accept(this, arg);
        Vector statements = stmtExpr.stmts.asts();
        SemType last = SemAn.stmtOfType.get(statements.get(statements.size()-1));
        if (last==null) return null;
        SemAn.exprOfType.put(stmtExpr, last.actualType());
        return (Result) last.actualType();
    }

    public Result visit(AstExprStmt exprStmt, Arg arg) {
        SemType expr_type = (SemType) exprStmt.expr.accept(this, arg);
        if (expr_type==null) return null;
        SemAn.stmtOfType.put(exprStmt, expr_type.actualType());
        return (Result) expr_type.actualType();
    }

    public Result visit(AstAssignStmt assignStmt, Arg arg) {
        SemType fst = (SemType) assignStmt.fstSubExpr.accept(this, arg);
        SemType snd = (SemType) assignStmt.sndSubExpr.accept(this, arg);
        if (fst==null||snd==null) return null;
        if (!( fst.actualType() instanceof SemInt || fst.actualType() instanceof SemChar || fst.actualType() instanceof SemPtr))
            throwError(assignStmt.fstSubExpr, "Only types of int, char and pointer are allowed");
        if (!(snd.actualType() instanceof SemInt || snd.actualType() instanceof SemChar || snd.actualType() instanceof SemPtr))
            throwError(assignStmt.sndSubExpr, "Only types of int, char and pointer are allowed");
        if (!( fst.actualType().getClass().equals(snd.actualType().getClass()) ))
            throwError(assignStmt.sndSubExpr, "Both variables should be of the same type");
        SemAn.stmtOfType.put(assignStmt, new SemVoid());
        return (Result) new SemVoid();
    }

    public Result visit(AstIfStmt ifStmt, Arg arg) {
        SemType cond = (SemType) ifStmt.condExpr.accept(this, arg);
        if (cond==null) return null;
        if (!(cond.actualType() instanceof SemInt))
            throwError(ifStmt.condExpr, "Condition should be of type int");
        SemType then_stmt = null, else_stmt = null;
        if (ifStmt.thenBodyStmt != null){
            then_stmt = (SemType) ifStmt.thenBodyStmt.accept(this, arg);
            SemAn.stmtOfType.put(ifStmt.thenBodyStmt, then_stmt);
        }
        if (ifStmt.elseBodyStmt != null){
            else_stmt = (SemType) ifStmt.elseBodyStmt.accept(this, arg);
            SemAn.stmtOfType.put(ifStmt.elseBodyStmt, else_stmt);
        }
        if (then_stmt==null) return null;
        if (!(then_stmt.actualType() instanceof SemVoid))
            throwError(ifStmt.thenBodyStmt, "Statements should be of type void");
        if (else_stmt==null) return null;
        if (else_stmt != null)
            if (!(else_stmt.actualType() instanceof SemVoid))
                throwError(ifStmt.elseBodyStmt, "Statements should be of type void");
        SemAn.stmtOfType.put(ifStmt, new SemVoid());
        return (Result) new SemVoid();
    }

    public Result visit(AstWhileStmt whileStmt, Arg arg) {
        SemType cond = (SemType) whileStmt.condExpr.accept(this, arg);
        if (cond==null) return null;
        if (!(cond.actualType() instanceof SemInt))
            throwError(whileStmt.condExpr, "Condition should be of type int");
        SemType body = (SemType) whileStmt.bodyStmt.accept(this, arg);
        if (body==null) return null;
        if (!(body.actualType() instanceof SemVoid))
            throwError(whileStmt.bodyStmt, "Statement should be of type void");
        SemAn.stmtOfType.put(whileStmt, new SemVoid());
        return (Result) new SemVoid();
    }


    // Random Support Methods
    void throwError(AST s, String message) throws Report.Error{
        if (s==null)
            return;
        throw new Report.Error(s.location, "SEMANTIC ERROR: "+message);
    }




}
