package MiniC.Parser;

import MiniC.Scanner.Scanner;
import MiniC.Scanner.Token;
import MiniC.Scanner.SourcePos;
import MiniC.Parser.SyntaxError;
import MiniC.ErrorReporter;
import MiniC.AstGen.*;


public class Parser {

    private Scanner scanner;
    private ErrorReporter errorReporter;
    private Token currentToken;
    private SourcePos previousTokenPosition;

    public Parser(Scanner lexer, ErrorReporter reporter) {
        scanner = lexer;
        errorReporter = reporter;
    }

    // for debug
    void debug(String message) {
//        System.out.printf("DEBUG] %s\n", message);
    }
    void debug() {
//        currentToken.print();
    }

    // accept() checks whether the current token matches tokenExpected.
    // If so, it fetches the next token.
    // If not, it reports a syntax error.
    void accept (int tokenExpected) throws SyntaxError {
        if (currentToken.kind == tokenExpected) {debug();
            previousTokenPosition = currentToken.GetSourcePos();
            currentToken = scanner.scan();
        } else {
            syntaxError("\"%\" expected here", Token.spell(tokenExpected));
        }
    }

    // acceptIt() unconditionally accepts the current token
    // and fetches the next token from the scanner.
    void acceptIt() {debug();
        previousTokenPosition = currentToken.GetSourcePos();
        currentToken = scanner.scan();
    }

    // start records the position of the start of a phrase.
    // This is defined to be the position of the first
    // character of the first token of the phrase.
    void start(SourcePos position) {
        position.StartCol = currentToken.GetSourcePos().StartCol;
        position.StartLine = currentToken.GetSourcePos().StartLine;
    }

    // finish records the position of the end of a phrase.
    // This is defined to be the position of the last
    // character of the last token of the phrase.
    void finish(SourcePos position) {
        position.EndCol = previousTokenPosition.EndCol;
        position.EndLine = previousTokenPosition.EndLine;
    }

    // get new SourcePos with initiated by start function
    SourcePos getNewPos() {
        SourcePos pos = new SourcePos();
        start(pos);
        return pos;
    }

    void syntaxError(String messageTemplate, String tokenQuoted) throws SyntaxError {
        SourcePos pos = currentToken.GetSourcePos();
        errorReporter.reportError(messageTemplate, tokenQuoted, pos);
        throw(new SyntaxError());
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // toplevel parse() routine:
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Program parse() {

        Program ProgramAST = null;

        previousTokenPosition = new SourcePos();
        previousTokenPosition.StartLine = 0;
        previousTokenPosition.StartCol = 0;
        previousTokenPosition.EndLine = 0;
        previousTokenPosition.EndCol = 0;

        currentToken = scanner.scan(); // get first token from scanner...

        try {
            ProgramAST = parseProgram();
            if (currentToken.kind != Token.EOF) {
                syntaxError("\"%\" not expected after end of program",
                               currentToken.GetLexeme());
            }
        }
        catch (SyntaxError s) { return null; }
        return ProgramAST;
    }

   
    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseProgram():
    //
    // program ::= ( typespecifier ID ( function-def | variable-def ) )*
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Program parseProgram() throws SyntaxError {debug("program");
        SourcePos pos = new SourcePos();
        start(pos);
        Decl D = parseProgramHelper();
        finish(pos);
        Program P = new Program (D, pos);
        return P;
    }

    // parseProgramHelper: recursive helper function to facilitate AST construction.
    Decl parseProgramHelper () throws SyntaxError {
        if ( !isTypeSpecifier() ) {
           return new EmptyDecl(previousTokenPosition);
        }

        SourcePos pos = getNewPos();

        Type T = parseTypeSpecifier();
        ID Ident = parseID();

        if (currentToken.kind == Token.LEFTPAREN) {
           Decl newD = parseFunction_def(T, Ident, pos);
           return new DeclSequence(newD, parseProgramHelper(), previousTokenPosition);
        } else {
           DeclSequence Vars = parseVariable_def(T, Ident, pos);
           DeclSequence VarsTail = Vars.GetRightmostDeclSequenceNode();
           Decl RemainderDecls = parseProgramHelper();
           VarsTail.SetRightSubtree (RemainderDecls);
           return Vars;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseTypeSpecifier():
    //
    // typespecifier ::= "void" | "int" | "bool" | "float"
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Type parseTypeSpecifier() throws SyntaxError {
        Type T = null;
        switch (currentToken.kind) {
        case Token.INT:
            T = new IntType(currentToken.GetSourcePos());
            break;
        case Token.FLOAT:
            T = new FloatType(currentToken.GetSourcePos());
            break;
        case Token.BOOL:
            T = new BoolType(currentToken.GetSourcePos());
            break;
        case Token.VOID:
            T = new VoidType(currentToken.GetSourcePos());
            break;
        default:
            syntaxError("Type specifier expected", "");
        }
        acceptIt();
        return T;
    }

    boolean isTypeSpecifier() {
        int token = currentToken.kind;
        if (token == Token.VOID ||
            token == Token.INT  ||
            token == Token.BOOL ||
            token == Token.FLOAT) {
            return true;
        } else {
            return false;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseFunction_def():
    //
    // FunPart ::= ( "(" Params-list? ")" Compound-stmt )
    //
    ///////////////////////////////////////////////////////////////////////////////

    public FunDecl parseFunction_def(Type T, ID Ident, SourcePos pos) throws SyntaxError {debug("function_def");
        accept(Token.LEFTPAREN);
        Decl PDecl = parseParams_list(); // can also be empty...
        accept(Token.RIGHTPAREN);
        CompoundStmt CStmt = parseCompound_stmt();
        finish(pos);
        return new FunDecl (T, Ident, PDecl, CStmt, pos);
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseParams_list():
    //
    // ParamsList ::= Params-decl ( "," Params-decl )*
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Decl parseParams_list() throws SyntaxError {
        if ( !isTypeSpecifier() ) {
           return new EmptyFormalParamDecl(previousTokenPosition);
        }
        
        SourcePos pos = getNewPos();
        Decl PDecl = parseParams_decl();
        finish(pos);

        if (currentToken.kind == Token.COMMA) {
            acceptIt();
        }
        return new FormalParamDeclSequence (PDecl, parseParams_list(), pos);
    } 


    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseParams_decl():
    //
    // params-decl ::= (VOID|INT|BOOL|FLOAT) Declarator
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Decl parseParams_decl() throws SyntaxError {
        Type T = null;
        Decl D = null;

        SourcePos pos = getNewPos();
        if ( isTypeSpecifier() ) {
            T = parseTypeSpecifier();
        } else {
            syntaxError("Type specifier instead of % expected",
                        Token.spell(currentToken.kind));
        }
        D = parseDeclarator(T, pos);
        return D;
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseDeclarator():
    //
    // Declarator ::= ID ( "[" INTLITERAL "]" )?
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Decl parseDeclarator(Type T, SourcePos pos) throws SyntaxError {
        ID Ident = parseID();
        if (currentToken.kind == Token.LEFTBRACKET) {
            ArrayType ArrT = parseArrayIndexDecl(T);
            finish(pos);
            return new FormalParamDecl (ArrT, Ident, pos);
        }
        finish(pos);
        return new FormalParamDecl (T, Ident, pos);
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseArrayIndexDecl (Type T):
    //
    // Take [INTLITERAL] and generate an ArrayType
    //
    ///////////////////////////////////////////////////////////////////////////////

    public ArrayType parseArrayIndexDecl(Type T) throws SyntaxError {
        accept(Token.LEFTBRACKET);
        IntExpr IE = parseIntExpr();
        accept(Token.RIGHTBRACKET);
        return new ArrayType (T, IE, previousTokenPosition);
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseVariable_def():
    //
    // Variable-def ::= ( "[" INTLITERAL "]" )?  ( "=" initializer ) ? ( "," init-decl)* ";"
    //
    ///////////////////////////////////////////////////////////////////////////////

    public DeclSequence parseVariable_def(Type T, ID Ident, SourcePos pos) throws SyntaxError {debug("variable-def");
        Type theType = T;
        Decl D;
        DeclSequence Seq;
        Expr E;

        if (currentToken.kind == Token.LEFTBRACKET) {
            theType = parseArrayIndexDecl(T);
        }
        
        if (currentToken.kind == Token.ASSIGN) {
            acceptIt();
            E = parseInitializer();
        } else {
            E = new EmptyExpr(previousTokenPosition);
        }
        
        D = new VarDecl(theType, Ident, E, previousTokenPosition);
        Seq = parseVariableHelper(T, D, pos);

        accept(Token.SEMICOLON);

        return Seq;
    }

    DeclSequence parseVariableHelper(Type T, Decl D, SourcePos pos) throws SyntaxError {
        if (currentToken.kind == Token.COMMA) {
            finish(pos);
            acceptIt();
            return new DeclSequence(D, parseInit_decl(T), pos);
        } else {
            return new DeclSequence(D, new EmptyDecl(previousTokenPosition),
                                    previousTokenPosition);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseInit_decl():
    //
    // init-decl ::= ID ("[" INTLITERAL "]" )? ("=" initializer)
    //
    ///////////////////////////////////////////////////////////////////////////////

    public DeclSequence parseInit_decl(Type T) throws SyntaxError {
        Decl D;
        ID Ident;
        Type theType = T;
        Expr E;
        SourcePos pos = getNewPos();

        Ident = parseID();
        if (currentToken.kind == Token.LEFTBRACKET) {
            theType = parseArrayIndexDecl(T);
        }

        if (currentToken.kind == Token.ASSIGN) {
            acceptIt();
            E = parseInitializer();
        } else {
            E = new EmptyExpr(previousTokenPosition);
        }

        D = new VarDecl(theType, Ident, E, previousTokenPosition);

        return parseVariableHelper(T, D, pos);
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    // parseInitializer():
    //
    // initializer ::= expr | "{" expr ("," expr)* "}"
    //
    //////////////////////////////////////////////////////////////////////////////

    public Expr parseInitializer() throws SyntaxError {debug("initializer");
        if (currentToken.kind == Token.LEFTBRACE) {
            acceptIt();
            Expr E = new ExprSequence(parseExpr(), parseInitializerHelper(), previousTokenPosition);
            accept(Token.RIGHTBRACE);

            return E;
        } else {
            return parseExpr();
        }
    }

    Expr parseInitializerHelper() throws SyntaxError {
        if ( currentToken.kind == Token.RIGHTBRACE )
            return new EmptyExpr(previousTokenPosition);

        accept(Token.COMMA);
        return new ExprSequence(parseExpr(), parseInitializerHelper(), previousTokenPosition);
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseExpr():
    //
    // expr ::= or-expr
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Expr parseExpr() throws SyntaxError {debug("expr");
        return parseOr_expr();
    }

    boolean isExpr() {
        switch (currentToken.kind) {
           case Token.PLUS:
           case Token.MINUS:
           case Token.NOT:
           case Token.ID:
           case Token.LEFTPAREN:
           case Token.INTLITERAL:
           case Token.BOOLLITERAL:
           case Token.FLOATLITERAL:
           case Token.STRINGLITERAL:
               return true;
           default:
               return false;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseOr_expr():
    //
    // or-expr ::= and-expr ("||" and-expr)*
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Expr parseOr_expr() throws SyntaxError {debug("or-expr");
        SourcePos pos = getNewPos();
        Expr LE = parseAnd_expr();

        return parseOr_exprHelper(LE, pos);
    }

    Expr parseOr_exprHelper(Expr LE, SourcePos pos) throws SyntaxError {
        if (currentToken.kind == Token.OR) {
            Operator oper = parseOperator();
            Expr RE = parseAnd_expr();
            finish(pos);
            
            LE = new BinaryExpr(LE, oper, RE, pos);
            return parseOr_exprHelper(LE,  getNewPos());
        }
        return LE;
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseAnd_expr():
    //
    // and-expr ::= rel-expr ("&&" rel-expr)*
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Expr parseAnd_expr() throws SyntaxError {debug("and-expr");
        SourcePos pos = getNewPos();
        Expr LE = parseRel_expr();

        return parseAnd_exprHelper(LE, pos);
    }

    Expr parseAnd_exprHelper(Expr LE, SourcePos pos) throws SyntaxError {
        if (currentToken.kind == Token.AND) {
            Operator oper = parseOperator();
            Expr RE = parseRel_expr();
            finish(pos);
            
            LE = new BinaryExpr(LE, oper, RE, pos);
            return parseAnd_exprHelper(LE,  getNewPos());
        }
        return LE;
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseRel_expr():
    //
    // rel-expr ::= add-expr (("==" | "!=" | "<" | "<=" | ">" | ">=") add-expr)?
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Expr parseRel_expr() throws SyntaxError {debug("rel-expr");
        SourcePos pos = getNewPos();
        Expr LE = parseAdd_expr();

        if ( isRelOper() ) {
            Operator oper = parseOperator();
            Expr RE = parseAdd_expr();

            finish(pos);
            return new BinaryExpr(LE, oper, RE, pos);
        }

        return LE;
    }

    boolean isRelOper() {
        switch (currentToken.kind) {
            case Token.EQ:      case Token.NOTEQ:
            case Token.LESS:    case Token.LESSEQ:
            case Token.GREATER: case Token.GREATEREQ: return true;
            default: return false;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseAdd_expr():
    //
    // add-expr ::= mult-expr (("+" | "-") mult-expr)*
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Expr parseAdd_expr() throws SyntaxError {debug("add-expr");
        SourcePos pos = getNewPos();
        Expr LE = parseMult_expr();

        return parseAdd_exprHelper(LE, pos);
    }

    Expr parseAdd_exprHelper(Expr LE, SourcePos pos) throws SyntaxError {
        if (currentToken.kind == Token.PLUS ||
            currentToken.kind == Token.MINUS) {
            Operator oper = parseOperator();
            Expr RE = parseMult_expr();
            finish(pos);
            
            LE = new BinaryExpr(LE, oper, RE, pos);
            return parseAdd_exprHelper(LE,  getNewPos());
        }
        return LE;
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseMult_expr():
    //
    // mult-expr ::= unary-expr (("*" | "/") unary-expr)*
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Expr parseMult_expr() throws SyntaxError {debug("mult-expr");
        SourcePos pos = getNewPos();
        Expr LE = parseUnary_expr();

        return parseMult_exprHelper(LE, pos);
    }

    Expr parseMult_exprHelper(Expr LE, SourcePos pos) throws SyntaxError {
        if (currentToken.kind == Token.TIMES ||
            currentToken.kind == Token.DIV) {
            Operator oper = parseOperator();
            Expr RE = parseUnary_expr();
            finish(pos);
            
            LE = new BinaryExpr(LE, oper, RE, pos);
            return parseMult_exprHelper(LE,  getNewPos());
        }
        return LE;
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseUnary_expr():
    //
    // unary-expr ::= ("+" | "-" | "!")* prim-expr
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Expr parseUnary_expr() throws SyntaxError {debug("unary-expr");
        if (currentToken.kind == Token.PLUS  ||
            currentToken.kind == Token.MINUS ||
            currentToken.kind == Token.NOT) {
            SourcePos pos = getNewPos();
            Operator oper = parseOperator();
            Expr E = parseUnary_expr();

            finish(pos);
            return new UnaryExpr(oper, E, pos);
        } else {
            return parsePrim_expr();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // parsePrim_expr():
    //
    // prim-expr ::= ID (arglist? | "[" expr "]") | "(" expr ")" | INTLITERAL | BOOLLITERAL | FLOATLITERAL | STRINGLITERAL
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Expr parsePrim_expr() throws SyntaxError {debug("prim-expr");
        SourcePos pos = getNewPos();

        switch (currentToken.kind) {
        case Token.ID:
            ID Ident = parseID();

            if (currentToken.kind == Token.LEFTBRACKET) {
                // array
                VarExpr varexpr = parseVarExpr(Ident);
                acceptIt();
                Expr E = parseExpr();
                accept(Token.RIGHTBRACKET);

                finish(pos);
                return new ArrayExpr(varexpr, E, pos);
            } else if ( isArglist() ) {
                // function call
                Expr param = parseArglist();

                finish(pos);
                return new CallExpr(Ident, param, pos);
            } else {
                // normal variant
                return parseVarExpr(Ident);
            }

        case Token.LEFTPAREN:debug("prim-expr parentheses");
            acceptIt();
            Expr E = parseExpr();
            accept(Token.RIGHTPAREN);

            return E;

        case Token.INTLITERAL:    return parseIntExpr();
        case Token.BOOLLITERAL:   return parseBoolExpr();
        case Token.FLOATLITERAL:  return parseFloatExpr();
        case Token.STRINGLITERAL: return parseStringExpr();

        default: syntaxError("", "ID"); return null;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseCompound_stmt():
    //
    // CompoundStmt ::= "{" variable-def* Stmt* "}"
    //
    ///////////////////////////////////////////////////////////////////////////////

    public CompoundStmt parseCompound_stmt() throws SyntaxError {debug("compound-stmt");
        SourcePos pos = new SourcePos();
        start(pos);
        accept(Token.LEFTBRACE);
        Decl D = parseCompoundDeclsHelper();
        Stmt S = parseCompoundStmtsHelper();
        accept(Token.RIGHTBRACE);
        finish(pos);
        if ( (D.getClass() == EmptyDecl.class) &&
             (S.getClass() == EmptyStmt.class)) {
           return new EmptyCompoundStmt (previousTokenPosition);
        } else {
           return new CompoundStmt (D, S, pos);
        }
    }

    public Decl parseCompoundDeclsHelper () throws SyntaxError {
        if ( !isTypeSpecifier() ) {
           return new EmptyDecl (previousTokenPosition);
        }
        SourcePos pos = getNewPos();
        Type T = parseTypeSpecifier();
        ID Ident = parseID();
        DeclSequence Vars = parseVariable_def(T, Ident, pos);
        DeclSequence VarsTail = Vars.GetRightmostDeclSequenceNode();
        Decl RemainderDecls = parseCompoundDeclsHelper();
        VarsTail.SetRightSubtree(RemainderDecls);
        return Vars;       
    }

    public Stmt parseCompoundStmtsHelper () throws SyntaxError {
        if (! (currentToken.kind == Token.LEFTBRACE ||
               currentToken.kind == Token.IF ||
               currentToken.kind == Token.WHILE ||
               currentToken.kind == Token.FOR ||
               currentToken.kind == Token.RETURN ||
               currentToken.kind == Token.ID)
            ) {
            return new EmptyStmt (previousTokenPosition);
        }
        Stmt S = parseStmt();
        return new StmtSequence (S, parseCompoundStmtsHelper(), previousTokenPosition);
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseStmt():
    //
    // Stmt ::= compound-stmt | if-stmt | while-stmt | for-stmt | return-stmt | call-stmt
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Stmt parseStmt() throws SyntaxError {debug("stmt");
        switch (currentToken.kind) {
            case Token.LEFTBRACE:   return parseCompound_stmt();
            case Token.IF:          return parseIf_stmt();
            case Token.WHILE:       return parseWhile_stmt();
            case Token.FOR:         return parseFor_stmt();
            case Token.RETURN:      return parseReturn_stmt();
            case Token.ID:          return parseCalc_stmt();
            default:                syntaxError("Statement required", "ID"); return null;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // if-stmt ::= IF "(" expr ")" stmt (ELSE stmt)?
    //
    ///////////////////////////////////////////////////////////////////////////////

    public IfStmt parseIf_stmt() throws SyntaxError {
        SourcePos pos = new SourcePos();
        start(pos);

        accept(Token.IF);
        accept(Token.LEFTPAREN);
        Expr E = parseExpr();
        accept(Token.RIGHTPAREN);
        Stmt S = parseStmt();
        Stmt Else = null;
        
        if (currentToken.kind == Token.ELSE) {
            acceptIt();
            Else = parseStmt();
        }

        finish(pos);
        return new IfStmt (E, S, Else, pos);
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // while-stmt ::= WHILE "(" expr ")" stmt
    //
    ///////////////////////////////////////////////////////////////////////////////

    public WhileStmt parseWhile_stmt() throws SyntaxError {
        SourcePos pos = getNewPos();

        accept(Token.WHILE);
        accept(Token.LEFTPAREN);
        Expr E = parseExpr();
        accept(Token.RIGHTPAREN);
        Stmt S = parseStmt();

        finish(pos);
        return new WhileStmt (E, S, pos);
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // for-stmt ::= FOR "(" asgnexpr? ";" expr? ";" asgnexpr? ")" stmt
    //
    ///////////////////////////////////////////////////////////////////////////////

    public ForStmt parseFor_stmt() throws SyntaxError {
        SourcePos pos = getNewPos();

        Expr initExpr, condExpr, incrExpr;
        Stmt S;

        initExpr = new EmptyExpr (previousTokenPosition);
        condExpr = new EmptyExpr (previousTokenPosition);
        incrExpr = new EmptyExpr (previousTokenPosition);

        accept(Token.FOR);
        accept(Token.LEFTPAREN);
        if (isAsgnexpr()) initExpr = parseAsgnexpr();
        accept(Token.SEMICOLON);
        if (isExpr()) condExpr = parseExpr();
        accept(Token.SEMICOLON);
        if (isAsgnexpr()) incrExpr = parseAsgnexpr();
        accept(Token.RIGHTPAREN);
        S = parseStmt();

        finish(pos);
        return new ForStmt(initExpr, condExpr, incrExpr, S, pos);
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // asgnexpr ::= ID "=" expr
    //
    ///////////////////////////////////////////////////////////////////////////////

    public AssignExpr parseAsgnexpr() throws SyntaxError {
        SourcePos pos = getNewPos();

        Expr Var = parseVarExpr();
        accept(Token.ASSIGN);
        Expr E = parseExpr();

        finish(pos);
        return new AssignExpr(Var, E, pos);
    }

    boolean isAsgnexpr() { return currentToken.kind == Token.ID; }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // return-stmt ::= RETURN expr? ";"
    //
    ///////////////////////////////////////////////////////////////////////////////

    public ReturnStmt parseReturn_stmt() throws SyntaxError {
        SourcePos pos = getNewPos();

        Expr E = new EmptyExpr (previousTokenPosition);

        accept(Token.RETURN);
        if (isExpr()) E = parseExpr();
        accept(Token.SEMICOLON);

        finish(pos);
        return new ReturnStmt (E, pos);
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // calc-stmt :: ID ("=" expr | "[" expr "]" "=" expr | arglist) ";"
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Stmt parseCalc_stmt() throws SyntaxError {debug("calc-stmt");
        SourcePos pos = getNewPos();

        ID Ident = parseID();

        // function call
        if (isArglist()) {
            Expr param = parseArglist();
            accept(Token.SEMICOLON);

            finish(pos);
            CallExpr callexpr = new CallExpr(Ident, param, pos);
            return new CallStmt(callexpr, pos);
        }
debug("calc-stmt assign mode");
        // assign stmt        
        Expr asgnExpr = parseCalc_stmtHelper(Ident);
        accept(Token.ASSIGN);
        Expr E = parseExpr();
        finish(pos);

        accept(Token.SEMICOLON);
        return new AssignStmt(asgnExpr, E, pos);
    }

    public Expr parseCalc_stmtHelper(ID Ident) throws SyntaxError {
        SourcePos pos = getNewPos();
        VarExpr var = new VarExpr(Ident, previousTokenPosition);

        // array check
        if (currentToken.kind == Token.LEFTBRACKET) {
            acceptIt();
            Expr index = parseExpr();
            accept(Token.RIGHTBRACKET);

            finish(pos);
            return new ArrayExpr(var, index, pos);
        } else {
            return var;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseArglist():
    //
    // ArgList ::= "(" ( arg ( "," arg )* )? ")"
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Expr parseArglist() throws SyntaxError {
        accept(Token.LEFTPAREN);

        if (currentToken.kind == Token.RIGHTPAREN) {
            acceptIt();
            return new EmptyActualParam (previousTokenPosition);
        }

        Expr Params = new ActualParamSequence (parseArg(), parseArglistHelper(), previousTokenPosition);

        accept(Token.RIGHTPAREN);
        return Params;
    }

    public Expr parseArglistHelper() throws SyntaxError {
        if (currentToken.kind == Token.RIGHTPAREN) {
            return new EmptyActualParam (previousTokenPosition);
        }

        accept(Token.COMMA);
        return new ActualParamSequence (parseArg(), parseArglistHelper(), previousTokenPosition);
    }

    boolean isArglist() { return currentToken.kind == Token.LEFTPAREN; }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // parseArg():
    //
    // arg ::= expr
    //
    ///////////////////////////////////////////////////////////////////////////////

    public Expr parseArg() throws SyntaxError {
        return new ActualParam (parseExpr(), previousTokenPosition);
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // Terminals
    //
    ///////////////////////////////////////////////////////////////////////////////

    public ID parseID() throws SyntaxError {
        ID Ident = new ID(currentToken.GetLexeme(), currentToken.GetSourcePos());
        accept(Token.ID);
        return Ident;
    }

    public IntLiteral parseIntLiteral() throws SyntaxError {debug("intliteral");
        IntLiteral L = new IntLiteral
            (currentToken.GetLexeme(), currentToken.GetSourcePos());
        accept(Token.INTLITERAL);
        return L;
    }

    public BoolLiteral parseBoolLiteral() throws SyntaxError {
        BoolLiteral L = new BoolLiteral
            (currentToken.GetLexeme(), currentToken.GetSourcePos());
        accept(Token.BOOLLITERAL);
        return L;
    }

    public FloatLiteral parseFloatLiteral() throws SyntaxError {
        FloatLiteral L = new FloatLiteral
            (currentToken.GetLexeme(), currentToken.GetSourcePos());
        accept(Token.FLOATLITERAL);
        return L;
    }

    public StringLiteral parseStringLiteral() throws SyntaxError {
        StringLiteral L = new StringLiteral
            (currentToken.GetLexeme(), currentToken.GetSourcePos());
        accept(Token.STRINGLITERAL);
        return L;
    }

    public Operator parseOperator() throws SyntaxError {
        Operator opAST = new Operator (currentToken.GetLexeme(), previousTokenPosition);
        acceptIt();
        return opAST;
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // Terminal Expressions
    //
    ///////////////////////////////////////////////////////////////////////////////

    public VarExpr parseVarExpr() throws SyntaxError {
        return new VarExpr(parseID(), currentToken.GetSourcePos());
    }

    public VarExpr parseVarExpr(ID Ident) throws SyntaxError {
        return new VarExpr(Ident, currentToken.GetSourcePos());
    }

    public IntExpr parseIntExpr() throws SyntaxError {debug("intexpr");
        return new IntExpr (parseIntLiteral(), currentToken.GetSourcePos());
    }

    public BoolExpr parseBoolExpr() throws SyntaxError {
        return new BoolExpr (parseBoolLiteral(), currentToken.GetSourcePos());
    }

    public FloatExpr parseFloatExpr() throws SyntaxError {
        return new FloatExpr (parseFloatLiteral(), currentToken.GetSourcePos());
    }

    public StringExpr parseStringExpr() throws SyntaxError {
        return new StringExpr (parseStringLiteral(), currentToken.GetSourcePos());
    }
}
