package MiniC.SemanticAnalysis;

import MiniC.ErrorReporter;
import MiniC.StdEnvironment;
import MiniC.Scanner.SourcePos;
import MiniC.AstGen.*;

public class SemanticAnalysis implements Visitor {

    private ErrorReporter reporter;
    private ScopeStack scopeStack;
    private boolean IsFunctionBlock;
    private Type currentFunctionReturnType;

    // for debug
    void debug(String message) {
        System.out.printf("DEBUG] %s\n", message);
    }

    public SemanticAnalysis(ErrorReporter reporter) {
        this.reporter = reporter;
        this.scopeStack = new ScopeStack ();
        // Here we enter the entities from the StdEnvironment into the scope stack:
        // The scope stack is on level 1 now (initial setting).
        scopeStack.enter ("int", StdEnvironment.intTypeDecl);
        scopeStack.enter ("bool", StdEnvironment.boolTypeDecl);
        scopeStack.enter ("float", StdEnvironment.floatTypeDecl);
        scopeStack.enter ("void", StdEnvironment.voidTypeDecl);
        scopeStack.enter ("getInt", StdEnvironment.getInt);
        scopeStack.enter ("putInt", StdEnvironment.putInt);
        scopeStack.enter ("getBool", StdEnvironment.getBool);
        scopeStack.enter ("putBool", StdEnvironment.putBool);
        scopeStack.enter ("getFloat", StdEnvironment.getFloat);
        scopeStack.enter ("putFloat", StdEnvironment.putFloat);
        scopeStack.enter ("getString", StdEnvironment.getString);
        scopeStack.enter ("putString", StdEnvironment.putString);
        scopeStack.enter ("putLn", StdEnvironment.putLn);
    }

    //
    // Prints the name of a class,
    // usefull for debugging...
    //
    private void PrintClassName(AST t) {
        System.out.println("The class of " + t +
                           " is " + t.getClass().getName());
    }

    private String idOfDecl(AST d) {
        String id;
        if (d == null) return "";
        assert ((d instanceof FunDecl) || (d instanceof VarDecl)
                || (d instanceof FormalParamDecl));
        if (d instanceof FunDecl) {
            id = ((FunDecl) d).idAST.Lexeme;
        } else if (d instanceof VarDecl) {
            id = ((VarDecl) d).idAST.Lexeme;
        } else {
            id = ((FormalParamDecl) d).astIdent.Lexeme;
        }
        return id;
    }

    // For FunDecl, VarDecl and FormalParamDecl, this function returns
    // the type of the declaration.
    // 1) for functions declarations, this is the return type of the function
    // 2) for variable declarations, this is the type of the variable
    private Type typeOfDecl(AST d) {
        Type T;
        if (d == null) {
            return StdEnvironment.errorType;
        }
        assert ((d instanceof FunDecl) || (d instanceof VarDecl)
                || (d instanceof FormalParamDecl));
        if (d instanceof FunDecl) {
            T = ((FunDecl) d).tAST;
        } else if (d instanceof VarDecl) {
            T = ((VarDecl) d).tAST;
        } else {
            T = ((FormalParamDecl) d).astType;
        }
        return T;
    }

    // This function returns the element type of an ArrayType AST node. 
    private Type typeOfArrayType(AST d) {
        assert (d != null);
        assert (d instanceof ArrayType);
        ArrayType T = (ArrayType)d;
        return T.astType;
    }

    // This function returns true, if an operator accepts integer or
    // floating point arguments.
    //  <int> x <int> -> <sometype>
    //  <float> x <float> -> <sometype>
    private boolean HasIntOrFloatArgs (Operator op) {
        return (op.Lexeme.equals("+") ||
                op.Lexeme.equals("-") ||
                op.Lexeme.equals("*") ||
                op.Lexeme.equals("/") ||
                op.Lexeme.equals("<") ||
                op.Lexeme.equals("<=") ||
                op.Lexeme.equals(">") ||
                op.Lexeme.equals(">=") ||
                op.Lexeme.equals("==") ||
                op.Lexeme.equals("!="));
    }

    // This function returns true, if an operator accepts bool arguments.
    //  <bool> x <bool> -> <sometype>
    private boolean HasBoolArgs (Operator op) {
        return (op.Lexeme.equals("&&") ||
                op.Lexeme.equals("||") ||
                op.Lexeme.equals("!") ||
                op.Lexeme.equals("=="));
    }

    // This function returns true, if an operator returns a bool value.
    //  <sometype> x <sometype> -> bool
    private boolean HasBoolReturnType (Operator op) {
        return (op.Lexeme.equals("&&") ||
                op.Lexeme.equals("||") ||
                op.Lexeme.equals("!")  ||
                op.Lexeme.equals("!=") ||
                op.Lexeme.equals("==") ||
                op.Lexeme.equals("<")  ||
                op.Lexeme.equals("<=") ||
                op.Lexeme.equals(">")  ||
                op.Lexeme.equals(">="));
    }

    // This function performs coercion of an integer-valued expression e.
    // It creates an i2f operator and a unary expression.
    // Expression e becomes the expression-AST of this unary expression.
    //
    //            Expr AST for e <int>
    //
    // =>
    //
    //            UnaryExpr <float>
    //              |     \
    //              |      \
    //              |       \
    //           i2f<int>   Expr AST for e <int>
    //
    private Expr i2f (Expr e) {
        Operator op = new Operator ("i2f", new SourcePos());
        op.type = StdEnvironment.intType;
        UnaryExpr eAST = new UnaryExpr (op, e, new SourcePos());
        eAST.type = StdEnvironment.floatType;
        return eAST;
    }

    // Given a function declaration FunDecl, this method returns the number
    // of formal parameters. E.g., for the following function
    //
    //    void foo (int a, bool b){}
    //
    // the return value will be 2.
    // Note: this function assumes the AST tree layout from Assignment 3.
    private int GetNrOfFormalParams(FunDecl f) {
        int NrArgs = 0;
        Decl D = f.paramsAST;
        assert ((D instanceof EmptyFormalParamDecl) ||
                (D instanceof FormalParamDeclSequence));
        if(D instanceof EmptyFormalParamDecl)
            return 0;
        while (D instanceof FormalParamDeclSequence) {
            NrArgs++;
            D = ((FormalParamDeclSequence) D).rAST;
            assert ((D instanceof EmptyFormalParamDecl) ||
                    (D instanceof FormalParamDeclSequence));
        }
        return NrArgs;
    }

    // Given a function declaration FunDecl, this method returns the AST for 
    // the formal parameter nr (nr is the number of the parameter).
    // E.g., for the following function and nr=2,
    //
    //    void foo (int a, bool b){}
    //
    // the AST returned will be "bool b".
    // Note: this function assumes the AST tree layout from Assignment 3.
    private FormalParamDecl GetFormalParam (FunDecl f, int nr) {
        int fArgs = GetNrOfFormalParams(f);
        assert (fArgs >= 0);
        assert (nr <= fArgs);
        FormalParamDeclSequence S = (FormalParamDeclSequence) f.paramsAST;
        for (int i = 1; i < nr; i++) {
            assert(S.rAST instanceof FormalParamDeclSequence);
            S = (FormalParamDeclSequence) S.rAST;
        }
        assert (S.lAST instanceof FormalParamDecl);
        return (FormalParamDecl) S.lAST;
    }

    // Get the number of actual parameters of a function call expression:
    // Similar to GetNrOfFormalParams above.
    // Note: this function assumes the AST tree layout from Assignment 3.
    private int GetNrOfActualParams(CallExpr f) {
        int NrArgs = 0;
        Expr P = f.paramAST;
        assert ((P instanceof EmptyActualParam) ||
                (P instanceof ActualParamSequence));
        if(P instanceof EmptyActualParam)
            return 0;
        while (P instanceof ActualParamSequence) {
            NrArgs++;
            P = ((ActualParamSequence) P).rAST;
            assert ((P instanceof EmptyActualParam) ||
                    (P instanceof ActualParamSequence));
        }
        return NrArgs;
    }

    // Given a function call expression, get the actual parameter nr
    // (nr is the number of the parameter).
    // Similar to GetFormalParam above.
    // Note: this function assumes the AST tree layout from Assignment 3.
    private ActualParam GetActualParam (CallExpr f, int nr) {
        int aArgs = GetNrOfActualParams(f);
        Expr P = f.paramAST;
        assert (aArgs >= 0);
        assert (nr <= aArgs);
        for (int i = 1; i < nr; i++) {
            assert (P instanceof ActualParamSequence);
            P = ((ActualParamSequence) P).rAST;
        }
        assert (((ActualParamSequence) P).lAST instanceof ActualParam);
        return (ActualParam) ((ActualParamSequence) P).lAST;
    }

    // Given a type t, this function can be used to print the type.
    // Useful for debuggging, a similar mechanism is used in the
    // TreeDrawer Visitor.
    private String TypeTag (Type t) {
        String l = new String("");
        if (t == null) {
            l = new String("<?>");
        } else if (t.Tequal(StdEnvironment.intType)) {
            l = new String ("<int>");
        } else if (t.Tequal(StdEnvironment.boolType)) {
            l = new String ("<bool>");
        } else if (t.Tequal(StdEnvironment.floatType)) {
            l = new String ("<float>");
        } else if (t.Tequal(StdEnvironment.stringType)) {
            l = new String ("<string>");
        } else if (t.Tequal(StdEnvironment.voidType)) {
            l = new String ("<void>");
        } else if (t instanceof ErrorType) {
            l = new String ("<error>");
        } else {
            assert(false);
        }
        return l;
    }

    // This array of strings contains the error messages that we generate
    // for errors detected during semantic analysis. These messages are
    // output using the ErrorReporter.
    // Example: reporter.reportError(errMsg[0], "", new SourcePos());
    //          will print "ERROR #0: main function is missing".
    private String errMsg[] = {
        "#0: main function missing",
        "#1: return type of main must be int",

        //defining occurrences of identifiers,
        //for local, global variables and for formal parameters:
        "#2: identifier redeclared",
        "#3: identifier declared void",
        "#4: identifier declared void[]",

        //applied occurrences of identifiers:
        "#5: undeclared identifier",

        //assignment statements:
        "#6: incompatible types for =",
        "#7: invalid lvalue in assignment",

        //expression types:
        "#8: incompatible type for return statement",
        "#9: incompatible types for binary operator",
        "#10: incompatible type for unary operator",

        //scalars:
        "#11: attempt to use a function as a scalar",

        //arrays:
        "#12: attempt to use scalar/function as an array",
        "#13: wrong type for element in array initializer",
        "#14: invalid initializer: array initializer for scalar",
        "#15: invalid initializer: scalar initializer for array",
        "#16: too many elements in array initializer",
        "#17: array subscript is not an integer",
        "#18: array size missing",

        //functions:
        "#19: attempt to reference a scalar/array as a function",

        //conditional expressions:
        "#20: \"if\" conditional is not of type boolean",
        "#21: \"for\" conditional is not of type boolean",
        "#22: \"while\" conditional is not of type boolean",

        //parameters:
        "#23: too many actual parameters",
        "#24: too few actual parameters",
        "#25: wrong type for actual parameter"
    };

    // Checks whether the source program, represented by its AST, satisfies the
    // language's scope rules and type rules.
    // Decorates the AST as follows:
    //  (a) Each applied occurrence of an identifier or operator is linked to
    //      the corresponding declaration of that identifier or operator.
    //  (b) Each expression and value-or-variable-name is decorated by its type.
    // MAIN ENTRY:
    public void check(Program progAST) {
        visit(progAST);
        // STEP 3:
        // Check Error 0
        // 
        // Retrieve "main" from the scope stack. If it is not there (null is
        // returned), then the program does not contain a main function.
        if (scopeStack.retrieve("main") == null) {
            reporter.reportError(errMsg[0], "", progAST.pos);
        }
    }

    public void visit(Program x) {
        x.D.accept(this);
    }

    public void visit(EmptyDecl x) {
    }

    public void visit(FunDecl x) {
        currentFunctionReturnType = x.tAST;
        // STEP 1:
        // Enter this function in the scope stack. Return Error 2 if this
        // name is already present in this scope.
        if ( !scopeStack.enter(idOfDecl(x), x) ) {
            // Error 2
            reporter.reportError(errMsg[2], idOfDecl(x) + " duplicated.", x.pos);
        }

        // STEP 3:
        // Check Error 1: 
        // If this function is the "main" function, then ensure that
        // x.tAST is of type int.
        if (idOfDecl(x).equals("main") &&
            !(currentFunctionReturnType.Tequal(StdEnvironment.intType))) {
            reporter.reportError(errMsg[1], "", x.idAST.pos);
        }

        // STEP 1:
        // Open a new scope in the scope stack. This will be the scope for the
        // function's formal parameters and the function's body.
        // We will close this scope in the visit procedure of this
        // function's compound_stmt.
        scopeStack.openScope();

        // The following flag is needed when we visit compound statements {...},
        // to avoid opening a fresh scope for function bodies (because we have
        // already opened one, for the formal parameters).
        IsFunctionBlock = true; // needed in {...}, to avoid opening a fresh scope.

        x.paramsAST.accept(this);
        x.stmtAST.accept(this);
    }

    public void visit(TypeDecl x) {
        assert (false); // TypeDecl nodes occur only in the StdEnvironment AST.
    }

    public void visit(FormalParamDecl x) {
        if (x.astType instanceof ArrayType) {
            ((ArrayType)x.astType).astExpr.accept(this);
        }
        // STEP 1:
        // Here we visit the declaration of a formal parameter. You should enter
        // the lexeme x.astIdent.Lexeme together with its declaration x into
        // the scope stack. If this name is already present in the current scope,
        // the scope stack enter method will return false. You should report
        // Error 2 in that case.
        if ( !scopeStack.enter(idOfDecl(x), x) ) {
            // Error 2
            reporter.reportError(errMsg[2], idOfDecl(x) + " duplicated.", x.astIdent.pos);
        }

        // STEP 3:
        // Check that the formal parameter is not of type void or void[]. 
        // Report error messages 3 and 4 respectively:
        if (x.astType.Tequal(StdEnvironment.voidType)) {
            reporter.reportError(errMsg[3], "", x.astType.pos);
        }

        if ((x.astType instanceof ArrayType) &&
            ((ArrayType)x.astType).astType.Tequal(StdEnvironment.voidType)) {
            reporter.reportError(errMsg[4], "", x.pos);
        }
    }

    public void visit(FormalParamDeclSequence x) {
        x.lAST.accept(this);
        x.rAST.accept(this);
    }

    public void visit(EmptyFormalParamDecl x) {
    }

    public void visit(StmtSequence x) {
        x.s1AST.accept(this);
        x.s2AST.accept(this);
    }

    public void visit(AssignStmt x) {
        x.lAST.accept(this);
        x.rAST.accept(this);

        //STEP 2:
        // Here we type-check assignment statements
        // Two conditions must be ensured:
        // 1) The type of the right-hand side of the assignment statement
        //    (x.rAST.type) must be assignment-compatible
        //    to the left-hand side of the assignment statement.
        //    You can use x.rAST.type.AssignableTo to test assignment-compatibility
        //    of the type of the left-hand side (x.lAST.type).
        // 2) If 2 types are assignment-compatible, then we need to check
        //    whether a coercion from int to float is needed. You can use
        //    x.lAST.type.Tequal(StdEnvironment.floatType) to check whether
        //    the left-hand side is of type float. Check the right-hand side
        //    for type int and use i2f if a coercion is needed. Hint: the return
        //    statement uses a similar mechanism....
        // If conditions (1) or (2) are violated, then you should report Error 6.
        if (x.rAST.type.AssignableTo(x.lAST.type)) {
            if (x.lAST.type.Tequal(StdEnvironment.floatType) &&
                x.rAST.type.Tequal(StdEnvironment.intType)) {
                x.rAST = i2f(x.rAST);
            }
        } else {
            reporter.reportError(errMsg[6], "Cannot assigned", x.rAST.pos);
        }

        if(!(x.lAST instanceof VarExpr) && !(x.lAST instanceof ArrayExpr)) {
            reporter.reportError(errMsg[7], "", x.lAST.pos);
        }
    }

    public void visit(IfStmt x) {
        x.eAST.accept(this);

        //STEP 2:
        // Here we are visiting an if statement. If the condition x.eAST.type
        // is not of type bool, we have to issue Error 20. You can have a
        // look at "for" loops, which use a similar check for the loop condition.
        if (!x.eAST.type.Tequal(StdEnvironment.boolType)) {
            reporter.reportError(errMsg[20], "if conditions need boolean type", x.eAST.pos);
        }

        x.thenAST.accept(this);
        if(x.elseAST != null) {
            x.elseAST.accept(this);
        }
    }

    public void visit(WhileStmt x) {
        x.eAST.accept(this);
        
        //STEP 2:
        // Here we are visiting a while statement. If the loop condition
        // is not of type bool, we have to issue Error 22. You can have a
        // look at "for" loops which use a similar check.
        if (!x.eAST.type.Tequal(StdEnvironment.boolType)) {
            reporter.reportError(errMsg[22], "while conditions need boolean type", x.eAST.pos);
        }

        x.stmtAST.accept(this);
    }

    public void visit(ForStmt x) {
        x.e1AST.accept(this);
        if(!(x.e2AST instanceof EmptyExpr)) {
            x.e2AST.accept(this);
            if(!x.e2AST.type.Tequal(StdEnvironment.boolType)) {
                reporter.reportError(errMsg[21], "", x.e2AST.pos);
            }
        }
        if(!(x.e3AST instanceof EmptyExpr)) {
            x.e3AST.accept(this);
        }
        x.stmtAST.accept(this);
    }

    public void visit(ReturnStmt x) {
        if (!(x.eAST instanceof EmptyExpr)) {
            x.eAST.accept(this);
            // STEP 2:
            // The following code checks assignment-compatibility of the return
            // statement's expression with the return type of the function.
            // Uncomment this code
            // as soon as you have finished type-checking of expressions.
            if(x.eAST.type.AssignableTo(currentFunctionReturnType)) {
                // Check for type coercion: if the function returns float, but
                // the expression of the return statement is of type int, we
                // need to convert this expression to float.
                if(currentFunctionReturnType.Tequal(StdEnvironment.floatType) &&
                   x.eAST.type.Tequal(StdEnvironment.intType)) {
                    //coercion of operand to float:
                    x.eAST = i2f(x.eAST);
                }
            } else {
                reporter.reportError(errMsg[8], "", x.eAST.pos);
            }
        }
    }

    public void visit(CompoundStmt x) {
        /*
         * If this CompoundStmt is the CompoundStmt of a Function, then
         * we already opened the scope before visiting the formal parameters.
         * No need to open a scope in that case. Otherwise set IsFunctionBlock
         * to false, to remember for nested {...}.
         *
         */
        if (IsFunctionBlock) {
            IsFunctionBlock = false; // nested {...} need to open their own scope.
        } else {
            // STEP 1:
            // Open a new scope for the compound statement (nested block within
            // a function body.
            scopeStack.openScope();
        }
        // STEP 1:
        // Invoke the semantic analysis visitor for the declarations and the
        // statements of this CompoundStmt. Hint: look up the file AstGen/CompoundStmt.java
        // to learn about the AST children of this node.
        x.astDecl.accept(this);
        x.astStmt.accept(this);

        // STEP 1:
        // Visiting of this {...} compound statement is done. Close the scope
        // for this compound statement (even if it represents a function body).
        scopeStack.closeScope();
    }

    public void visit(EmptyStmt x) {
    }

    public void visit(EmptyCompoundStmt x) {
    }

    public void visit(CallStmt x) {
        x.eAST.accept(this);
    }

    public void visit(VarDecl x) {
        if (x.tAST instanceof ArrayType) {
            ((ArrayType)x.tAST).astExpr.accept(this);
        }
        if (!(x.eAST instanceof EmptyExpr)) {
            x.eAST.accept(this);
            if (x.tAST instanceof ArrayType) {
                //STEP 4:
                //
                // Array declarations. -> ExprSequence
                // Check for error messages 15, 16, 13.
                // Perform i2f coercion if necessary.

                // 일단 ExprSequence가 아니면 Error 15
                if (!(x.eAST instanceof ExprSequence)) {
                    reporter.reportError(errMsg[15], "cannot init scalar to array", x.pos);
                } else {
                    int array_count = ((ArrayType)x.tAST).GetRange();
                    Expr expSequence = (ExprSequence)x.eAST;
                    Type array_type = ((ArrayType)x.tAST).astType;

                    while ((array_count--) > 0 && !(expSequence instanceof EmptyExpr)) {
                        Type init_type = ((ExprSequence)expSequence).lAST.type;

                        if (init_type.AssignableTo(array_type)) {
                            // check i2f coercion
                            if (array_type.Tequal(StdEnvironment.floatType) &&
                                init_type.Tequal(StdEnvironment.intType)) {
                                ((ExprSequence)expSequence).lAST = i2f(((ExprSequence)expSequence).lAST);
                            }
                        } else {
                            // Error 13
                            reporter.reportError(errMsg[13], "cannot assign this expression", ((ExprSequence)expSequence).rAST.pos);
                        }

                        expSequence = ((ExprSequence)expSequence).rAST;
                    }

                    // rAST가 EmptyExpr가 아니다 = 개수가 더 많다 = Error 16
                    if (!(expSequence instanceof EmptyExpr)) {
                        reporter.reportError(errMsg[16], "too many initializer", x.pos);
                    }
                }
            } else {
                //STEP 4:
                //
                // Non-array declarations, i.e., scalar variables.
                // Check for error messages 14, 6.
                // Perform i2f coercion if necessary.
                if (x.eAST instanceof ExprSequence) {
                    reporter.reportError(errMsg[14], "cannot init array to scalar", x.pos);
                } else {
                    if (x.eAST.type.AssignableTo(x.tAST)) {
                        // check i2f coercion
                        if (x.tAST.Tequal(StdEnvironment.floatType) &&
                            x.eAST.type.Tequal(StdEnvironment.intType)) {
                            x.eAST = i2f(x.eAST);
                        }
                    } else {
                        // Error 6
                        reporter.reportError(errMsg[6], "cannot assign this value", x.eAST.pos);
                    }
                }
            }
        }
        //STEP 1:
        // Here we are visiting a variable declaration x.
        // Enter this variable into the scope stack. Like with formal parameters,
        // if an identifier of the same name is already present, then you should
        // report Error 2.
        if ( !scopeStack.enter(idOfDecl(x), x) ) {
            // Error 2
            reporter.reportError(errMsg[2], idOfDecl(x) + " duplicated.", x.pos);
        }

        // STEP 3:
        // Check that the variable is not of type void or void[]. 
        // Report error messages 3 and 4 respectively:
        if (x.tAST.Tequal(StdEnvironment.voidType)) {
            reporter.reportError(errMsg[3], "", x.idAST.pos);
        }

        if ((x.tAST instanceof ArrayType) &&
            ((ArrayType)x.tAST).astType.Tequal(StdEnvironment.voidType)) {
            reporter.reportError(errMsg[4], "", x.pos);
        }
    }

    public void visit(DeclSequence x){
        x.D1.accept(this);
        x.D2.accept(this);
    }

    public void visit(VarExpr x) {
        x.Ident.accept(this);

        //STEP 2:
        // Here we are visiting a variable expression.
        // Its type is synthesized from the type of the applied occurrence
        // of its identifier. Use "instanceof" to find out whether x.Ident.declAST
        // is a function declaration (FunDecl). In that case you should report
        // Error 11 and set x.type to the error type from StdEnvironment.
        x.type = typeOfDecl (x.Ident.declAST);

        if (x.Ident.declAST instanceof FunDecl) {
            reporter.reportError(errMsg[11], "cannot use function declarator as scalar", x.pos);
        }
    }

    public void visit(AssignExpr x) {
        x.lAST.accept(this);
        x.rAST.accept(this);
        if(x.rAST.type.AssignableTo(x.lAST.type)) {
            //check for type coercion:
            if(x.lAST.type.Tequal(StdEnvironment.floatType) &&
               x.rAST.type.Tequal(StdEnvironment.intType)) {
                //coercion of right operand to int:
                x.rAST = i2f(x.rAST);
            }
        } else {
            reporter.reportError(errMsg[6], "", x.rAST.pos);
        }
        if(!(x.lAST instanceof VarExpr) && !(x.lAST instanceof ArrayExpr)) {
            reporter.reportError(errMsg[7], "", x.lAST.pos);
        }
    }

    public void visit(IntExpr x) {
        //STEP 2:
        // Here we are visiting an integer literal. Set x.type of this
        // AST node to the int type from the standard environment
        // (StdEnvironment.intType).
        x.type = StdEnvironment.intType;
    }

    public void visit(FloatExpr x) {
        //STEP 2:
        // Here we are visiting a float literal. Set x.type of this
        // AST node to the float type from the standard environment
        // (StdEnvironment.floatType).
        x.type = StdEnvironment.floatType;
    }

    public void visit(BoolExpr x) {
        //STEP 2:
        // Here we are visiting a bool literal. Set x.type of this
        // AST node to the bool type from the standard environment
        // (StdEnvironment.boolType).
        x.type = StdEnvironment.boolType;
    }

    public void visit(StringExpr x) {
        //STEP 2:
        // Here we are visiting a string literal. Set x.type of this
        // AST node to the string type from the standard environment
        // (StdEnvironment.stringType).
        x.type = StdEnvironment.stringType;
    }

    public void visit(ArrayExpr x) {
        x.idAST.accept(this);
        x.indexAST.accept(this);
        if(!x.indexAST.type.Tequal(StdEnvironment.intType)) {
            reporter.reportError(errMsg[17], "", x.indexAST.pos);
        }
        VarExpr VE = (VarExpr)x.idAST;
        if(!(typeOfDecl(VE.Ident.declAST) instanceof ArrayType)) {
            reporter.reportError(errMsg[12], "", x.pos);
           x.type = StdEnvironment.errorType; 
        } else {
           x.type = typeOfArrayType(x.idAST.type);
        }
    }

    public void visit(BinaryExpr x) {
        x.lAST.accept(this);
        x.oAST.accept(this);
        x.rAST.accept(this);
        if(HasIntOrFloatArgs(x.oAST)) {
            if(x.lAST.type.Tequal(StdEnvironment.intType) &&
               x.rAST.type.Tequal(StdEnvironment.intType)) {
                x.oAST.type = StdEnvironment.intType;
                if(HasBoolReturnType(x.oAST)) {
                    x.type = StdEnvironment.boolType;
                } else {
                    x.type = StdEnvironment.intType;
                }
                return;
            } else if(x.lAST.type.Tequal(StdEnvironment.floatType) &&
                      x.rAST.type.Tequal(StdEnvironment.floatType)) {
                x.oAST.type = StdEnvironment.floatType;
                if(HasBoolReturnType(x.oAST)) {
                    x.type = StdEnvironment.boolType;
                } else {
                    x.type = StdEnvironment.floatType;
                }
                return;
            } else if (x.lAST.type.Tequal(StdEnvironment.intType) &&
                       x.rAST.type.Tequal(StdEnvironment.floatType)) {
                //coercion of left operand to float:
                x.lAST = i2f(x.lAST);
                x.oAST.type = StdEnvironment.floatType;
                if(HasBoolReturnType(x.oAST)) {
                    x.type = StdEnvironment.boolType;
                } else {
                    x.type = StdEnvironment.floatType;
                }
                return;
            } else if (x.lAST.type.Tequal(StdEnvironment.floatType) &&
                       x.rAST.type.Tequal(StdEnvironment.intType)) {
                // STEP 2:
                // This code is part of the type checking for binary
                // expressions. In this case,
                // the left-hand operand is float, the right-hand operand is int.
                // We have to type-cast the right operand to float.
                // This is the dual case to "int x float" above.
                x.rAST = i2f(x.rAST);
                x.oAST.type = StdEnvironment.floatType;
                if (HasBoolReturnType(x.oAST)) {
                    x.type = StdEnvironment.boolType;
                } else {
                    x.type = StdEnvironment.floatType;
                }
                return ;
            }
        } else if (HasBoolArgs(x.oAST)) {
            if (x.lAST.type.Tequal(StdEnvironment.boolType) &&
               x.rAST.type.Tequal(StdEnvironment.boolType)) {
                x.oAST.type = StdEnvironment.intType; //!!!!!!!!!!!!!!!!!!!!!
                x.type = StdEnvironment.boolType;
                return;
            }
        }
        x.oAST.type = StdEnvironment.errorType;
        x.type = StdEnvironment.errorType;
        if (!((x.lAST.type instanceof ErrorType) || (x.rAST.type instanceof ErrorType)))
        {
           // Error not spurious, because AST children are ok.
           reporter.reportError(errMsg[9], "", x.pos);
        }
    }

    public void visit(UnaryExpr x) {
        x.oAST.accept(this);
        x.eAST.accept(this);
        
        //STEP 2:
        // Here we synthesize the type attribute for a unary operator.
        // x.eAST.type contains the type of the subexpression of this
        // unary operator.
        //
        // If x.eAST is of type int or float, and if oAST is an operator
        // that supports these types, then x.oAST.type and x.type
        // have to be set to x.eAST.type.
        //
        // If x.eAST is of type bool, and if x.oAST is an operator that
        // supports bool, then x.type is bool, but  x.oAST.type is of type
        // int (because of the JVM convention to represent true and false
        // as ints.
        //
        // In all other cases, x.oAST.type and x.type have to be set to
        // errorType, and Error 10 must be reported.
        //
        // You can have a look at visit(BinaryExpr) for a similar, yet
        // slightly more complicated case.
        if (HasIntOrFloatArgs(x.oAST)) {
            if ((x.eAST.type.Tequal(StdEnvironment.intType)) ||
                (x.eAST.type.Tequal(StdEnvironment.floatType))) {
                x.oAST.type = x.type = x.eAST.type;
            } else {
                x.oAST.type = x.type = StdEnvironment.errorType;
            }
        } else if (HasBoolArgs(x.oAST)) {
            if (x.eAST.type.Tequal(StdEnvironment.boolType)) {
                x.type = StdEnvironment.boolType;
                x.oAST.type = StdEnvironment.intType; //!!!!!!!!!!!!!!!!!
            } else {
                x.oAST.type = x.type = StdEnvironment.errorType;
            }
        } else {
            x.oAST.type = x.type = StdEnvironment.errorType;
        }

        if (x.type instanceof ErrorType) {
            reporter.reportError(errMsg[10], "", x.pos);
        }
    }

    public void visit(EmptyExpr x) {
    }

    public void visit(ActualParam x) {
        x.pAST.accept(this);
        x.type = x.pAST.type;
    }

    public void visit(EmptyActualParam x) {
    }

    public void visit(ActualParamSequence x) {
        x.lAST.accept(this);
        x.rAST.accept(this);
    }

    public void visit(CallExpr x) {
        //Here we perform semantic analysis of function calls:
        x.type = StdEnvironment.errorType;
        x.idAST.accept(this);
        x.paramAST.accept(this);
        //Retrieve the declaration of x from the scope stack:
        Decl D = scopeStack.retrieve(x.idAST.Lexeme);
        // STEP 3:
        // Use "instanceof" to find out if D is a FunDecl. If not, report
        // Error 19 and *return*.
        // This check detects cases like
        //  int f; f(22);
        // where f is not a function.
        if (!(D instanceof FunDecl)) {
            reporter.reportError(errMsg[19], idOfDecl(D) + " is not a function", x.pos);
            return ;
        }

        FunDecl F = (FunDecl ) D;
        // STEP 2:
        // Check that the number of formal args from F and the number of actual
        // parameters of the function call x match.
        // Use the functions GetNrOfFormalParams and
        // GetNrOfActualParams from the beginning of this file to retrieve
        // the number of formal and actual parameters.
        int NrFormalParams = GetNrOfFormalParams(F);

        if (NrFormalParams < GetNrOfActualParams(x)) {
            reporter.reportError(errMsg[23], "needs " + NrFormalParams + " parameter(s)", x.pos);
        } else if (NrFormalParams > GetNrOfActualParams(x)) {
            reporter.reportError(errMsg[24], "needs " + NrFormalParams + " parameter(s)", x.pos);
            // NrFormalParams = min(NrFormalParams, NrActalParams);
            NrFormalParams = GetNrOfActualParams(x);
        } else {
            // STEP 2:
            // Here we check that the types of the formal and actual parameters
            // match (Error 25). This is similar to type-checking the left-hand
            // and right-hand sides of assignment statements. Two steps need
            // to be carried out:
            //
            // (1)
            // Check that types of formal and actual args match: this means that
            // the actual parameter must be assignable to the formal parameter.
            // You can imagine passing an actual parameter to a formal parameter
            // like an assignment statement: formal_par = actual_par.
            //
            // (2)
            // Perform type coercion (int->float) of the *actual* parameter if necessary.
            for (int i = 1; i <= NrFormalParams; i++) {
                FormalParamDecl Form = GetFormalParam(F, i);
                ActualParam Act = GetActualParam(x, i);
                Type FormalT = Form.astType;
                Type ActualT = Act.pAST.type;

                if (ActualT.AssignableTo(FormalT)) {
                    // check i2f
                    if ((FormalT.Tequal(StdEnvironment.floatType)) &&
                        (ActualT.Tequal(StdEnvironment.intType))) {
                        Act.pAST = i2f(Act.pAST);
                    }
                } else {
                    reporter.reportError(errMsg[25] + ", %,", "parameter " + i, x.pos);
                }
            }
        }

        // set the return type of the call expression to the return type of
        // its function:
        x.type = typeOfDecl(F);
    }

    public void visit(ExprSequence x) {
        x.lAST.accept(this);
        x.rAST.accept(this);
    }

    public void visit(ID x) {
        // STEP 1:
        // Here we look up the declaration of an identifier
        // from the scope stack. If no declaration can be found on the
        // scope stack, you should report Error 5.
        Decl binding = scopeStack.retrieve(x.Lexeme);
        if (binding != null) {
            x.declAST = binding;
        } else {
            // Error 5
            reporter.reportError(errMsg[5], x.Lexeme + " used before declared.", x.pos);
        }
    }

    public void visit(Operator x) {

    } 

    public void visit(IntLiteral x) {

    } 

    public void visit(FloatLiteral x) {

    } 

    public void visit(BoolLiteral x) {

    } 

    public void visit(StringLiteral x) {

    } 

    public void visit(IntType x) {

    }

    public void visit(FloatType x) {

    }

    public void visit(BoolType x) {

    }

    public void visit(StringType x) {

    }

    public void visit(VoidType x) {

    }

    public void visit(ArrayType x) {

    }

    public void visit(ErrorType x) {

    }

}
