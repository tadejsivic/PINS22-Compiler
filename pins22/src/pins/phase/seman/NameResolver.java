package pins.phase.seman;

import pins.common.report.Report;
import pins.data.ast.*;
import pins.data.ast.visitor.AstFullVisitor;

public class NameResolver<Result, Arg> extends AstFullVisitor<Result, Arg> {

    SymbTable st;
    boolean repeated_cycle = false;

    public NameResolver(){
        st = new SymbTable();
    }

    // GENERAL

    @Override
    public Result visit(ASTs<? extends AST> trees, Arg arg) {
        for (int i=0; i<2; i++) {
            for (AST t : trees.asts()) {
                if (t != null)
                    t.accept(this, (Arg) Integer.valueOf(i));
            }
            if (trees.location == null)
                repeated_cycle = true;
        }
        return null;
    }

    // DECLARATIONS

    @Override
    public Result visit(AstFunDecl funDecl, Arg arg) {
        try {
            st.ins(funDecl.name, funDecl);
        }catch (SymbTable.CannotInsNameException e){
            if ( (int)arg==0 && !repeated_cycle )
                nameError(funDecl);
        }

        st.newScope();
        if (funDecl.pars != null)
            funDecl.pars.accept(this, arg);
        funDecl.type.accept(this, arg);
        funDecl.expr.accept(this, arg);
        st.oldScope();

        return null;
    }

    @Override
    public Result visit(AstParDecl parDecl, Arg arg) {
        //System.out.println("pars");
        if (parDecl.type != null)
            parDecl.type.accept(this, arg);
        try {
            st.ins(parDecl.name, parDecl);
        }catch (SymbTable.CannotInsNameException e){
           if ( (int)arg==0 && !repeated_cycle)
               nameError(parDecl);
        }
        return null;
    }

    @Override
    public Result visit(AstTypDecl typDecl, Arg arg) {
        //System.out.println("typ");
        if (typDecl.type != null)
            typDecl.type.accept(this, arg);
        try {
            st.ins(typDecl.name, typDecl);
        }catch (SymbTable.CannotInsNameException e) {
            if ( (int)arg==0 && !repeated_cycle )
                nameError(typDecl);
        }
        return null;
    }

    @Override
    public Result visit(AstVarDecl varDecl, Arg arg) {
        if (varDecl.type != null)
            varDecl.type.accept(this, arg);
        try {
            st.ins(varDecl.name, varDecl);
        }catch (SymbTable.CannotInsNameException e){
            if ( (int)arg==0 && !repeated_cycle )
                nameError(varDecl);
        }
        return null;
    }

    @Override
    public Result visit(AstWhereExpr whereExpr, Arg arg) {
        //System.out.println("where");
        st.newScope();
        whereExpr.decls.accept(this, arg);
        //whereExpr.decls.log("");
        whereExpr.subExpr.accept(this, arg);
        st.oldScope();
        return null;
    }


    // NAME USAGES

    @Override
    public Result visit(AstNameExpr nameExpr, Arg arg) {
        if (SemAn.declaredAt.get(nameExpr)!=null) return null;
        if (nameExpr != null){
            AstDecl n = null;
            try{
                n = st.fnd(nameExpr.name);
            }catch (SymbTable.CannotFndNameException e){
                if ( (int)arg==1 && repeated_cycle)
                    findError(nameExpr);
            }
            if (n==null)return null;
            SemAn.declaredAt.put(nameExpr, n);
        }
        return null;
    }

    @Override
    public Result visit(AstTypeName typeName, Arg arg) {
        if (SemAn.declaredAt.get(typeName)!=null) return null;
        if (typeName != null){
            AstDecl n = null;
            try{
                n = st.fnd(typeName.name);
            }catch (SymbTable.CannotFndNameException e){
                if ( (int)arg==1 && repeated_cycle)
                    findError(typeName);
            }
            if (n==null)return null;
            SemAn.declaredAt.put(typeName, n);
        }
        return null;
    }

    @Override
    public Result visit(AstCallExpr callExpr, Arg arg) {
        if (SemAn.declaredAt.get(callExpr)!=null) return null;
        AstDecl n = null;
        try{
            n = st.fnd(callExpr.name);
        }catch (SymbTable.CannotFndNameException e){
            if ((int)arg==1 && repeated_cycle)
                findError(callExpr);
        }
        if (n==null)return null;
        SemAn.declaredAt.put(callExpr, n);

        if (callExpr.args != null)
            callExpr.args.accept(this, arg);

        return null;
    }




    // Random Support Methods
    void nameError(AstDecl s) throws Report.Error{
        if (s==null)
            return;
        throw new Report.Error(s.location, "SEMANTIC ERROR: The name "+s.name+" already exists in this scope.");
    }
    void findError(AstNameExpr s) throws Report.Error{
        if (s==null)
            return;
        throw new Report.Error(s.location, "SEMANTIC ERROR: Variable/function "+s.name+" was never declared");
    }
    void findError(AstTypeName s) throws Report.Error{
        if (s==null)
            return;
        throw new Report.Error(s.location, "SEMANTIC ERROR: Variable/function "+s.name+" was never declared");
    }


}
