package MiniC.Parser;


import MiniC.Scanner.Token;
import MiniC.Scanner.SourcePos;
import MiniC.Parser.SyntaxError;
import MiniC.Scanner.Scanner;
import MiniC.ErrorReporter;

public class Parser {

    private Scanner scanner;
    private ErrorReporter errorReporter;
    private Token currentToken;

    public Parser(Scanner lexer, ErrorReporter reporter) {
	scanner = lexer;
        errorReporter = reporter;
    }

// for debug
//void //debug(String message) { System.out.printf("DEBUG] %s\n", message); }

    // accept() checks whether the current token matches tokenExpected.
    // If so, it fetches the next token.
    // If not, it reports a syntax error.
    void accept (int tokenExpected) throws SyntaxError {
	if (currentToken.kind == tokenExpected) {
	    currentToken = scanner.scan();
	} else {
	    syntaxError("\"%\" expected here", Token.spell(tokenExpected));
	}
    }

    // acceptIt() unconditionally accepts the current token
    // and fetches the next token from the scanner.
    void acceptIt() {
	currentToken = scanner.scan();
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

    public void parse() {
	currentToken = scanner.scan(); // get first token from scanner...

	try {
	    parseProgram();
	    if (currentToken.kind != Token.EOF) {
		syntaxError("\"%\" not expected after end of program",
			       currentToken.GetLexeme());
	    }
	}
	catch (SyntaxError s) {return; /* to be refined in Assignment 3...*/ }
	return;
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // typespecifier ::= "void" | "int" | "bool" | "float"
    //
    ///////////////////////////////////////////////////////////////////////////////

    public void parseTypespecifier() throws SyntaxError {//debug("typespecifier");
        if (isTypeSpecifier()) acceptIt();
        else syntaxError("Typespecifier expected", Token.spell(0));
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
    // program ::= ( typespecifier ID ( function-def | variable-def ) )*
    //
    ///////////////////////////////////////////////////////////////////////////////

    public void parseProgram() throws SyntaxError {//debug("program");
	while (isTypeSpecifier()) {
            acceptIt();
	    accept(Token.ID);
	    if(currentToken.kind == Token.LEFTPAREN) {
		parseFunction_def();
	    } else {
		parseVariable_def();
	    }
	}
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // Function-def ::= ( "(" Params-list? ")" Compound-stmt )
    //
    ///////////////////////////////////////////////////////////////////////////////

    public void parseFunction_def() throws SyntaxError {//debug("function-def");
        accept(Token.LEFTPAREN);
        if (isTypeSpecifier()) {
	    parseParams_list();
	}
	accept(Token.RIGHTPAREN);
	parseCompound_stmt();
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // Params-list ::= Params-decl ( "," Params-decl )*
    //
    ///////////////////////////////////////////////////////////////////////////////

    public void parseParams_list() throws SyntaxError {//debug("params-list");
	parseParams_decl();
        while (currentToken.kind == Token.COMMA) {
            acceptIt();
            parseParams_decl();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // Params-decl ::= typespecifier declarator
    //
    ///////////////////////////////////////////////////////////////////////////////

    public void parseParams_decl() throws SyntaxError {//debug("params-decl");
        parseTypespecifier();
        parseDeclarator();
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // Compound-stmt ::= "{" (typespecifier ID Variable-def)* Stmt* "}"
    //
    ///////////////////////////////////////////////////////////////////////////////

    public void parseCompound_stmt() throws SyntaxError {//debug("compound-stmt");
        accept(Token.LEFTBRACE);
        while (isTypeSpecifier()) {
            acceptIt();
            accept(Token.ID);
            parseVariable_def();
        }
        while (isStmt()) {
            parseStmt();
        }
        accept(Token.RIGHTBRACE);
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // Stmt ::= compound-stmt | if-stmt | while-stmt | for-stmt | return-stmt | calc-stmt
    //
    ///////////////////////////////////////////////////////////////////////////////

    public void parseStmt() throws SyntaxError {//debug("stmt");
        switch (currentToken.kind) {
            case Token.LEFTBRACE:   parseCompound_stmt();   break;
            case Token.IF:          parseIf_stmt();         break;
            case Token.WHILE:       parseWhile_stmt();      break;
            case Token.FOR:         parseFor_stmt();        break;
            case Token.RETURN:      parseReturn_stmt();     break;
            case Token.ID:          parseCalc_stmt();       break;
            default:                syntaxError("", "ID");
        }
    }

    boolean isStmt() {
        switch (currentToken.kind) {
        case Token.LEFTBRACE:
        case Token.IF:
        case Token.WHILE:
        case Token.FOR:
        case Token.RETURN:
        case Token.ID:
            return true;
        default:
            return false;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // if-stmt ::= IF "(" expr ")" stmt (ELSE stmt)?
    //
    ///////////////////////////////////////////////////////////////////////////////

    public void parseIf_stmt() throws SyntaxError {//debug("if-stmt");
        accept(Token.IF);
        accept(Token.LEFTPAREN);
        parseExpr();
        accept(Token.RIGHTPAREN);
        parseStmt();
        if (currentToken.kind == Token.ELSE) {
            acceptIt();
            parseStmt();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // while-stmt ::= WHILE "(" expr ")" stmt
    //
    ///////////////////////////////////////////////////////////////////////////////

    public void parseWhile_stmt() throws SyntaxError {//debug("while-stmt");
        accept(Token.WHILE);
        accept(Token.LEFTPAREN);
        parseExpr();
        accept(Token.RIGHTPAREN);
        parseStmt();
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // for-stmt ::= FOR "(" asgnexpr? ";" expr? ";" asgnexpr? ")" stmt
    //
    ///////////////////////////////////////////////////////////////////////////////

    public void parseFor_stmt() throws SyntaxError {//debug("for-stmt");
        accept(Token.FOR);
        accept(Token.LEFTPAREN);
        if (isAsgnexpr()) parseAsgnexpr();
        accept(Token.SEMICOLON);
        if (isExpr()) parseExpr();
        accept(Token.SEMICOLON);
        if (isAsgnexpr()) parseAsgnexpr();
        accept(Token.RIGHTPAREN);
        parseStmt();
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // asgnexpr ::= ID "=" expr
    //
    ///////////////////////////////////////////////////////////////////////////////

    public void parseAsgnexpr() throws SyntaxError {//debug("asgnexpr");
        accept(Token.ID);
        accept(Token.ASSIGN);
        parseExpr();
    }

    boolean isAsgnexpr() { return currentToken.kind == Token.ID; }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // return-stmt ::= RETURN expr? ";"
    //
    ///////////////////////////////////////////////////////////////////////////////

    public void parseReturn_stmt() throws SyntaxError {//debug("return-=stmt");
        accept(Token.RETURN);
        if (isExpr()) parseExpr();
        accept(Token.SEMICOLON);
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // calc-stmt :: ID ("=" expr | "[" expr "]" "=" expr | arglist) ";"
    //
    ///////////////////////////////////////////////////////////////////////////////

    public void parseCalc_stmt() throws SyntaxError {//debug("calc-stmt");
        accept(Token.ID);
        if (currentToken.kind == Token.ASSIGN) {
            acceptIt();
            parseExpr();
        } else
        if (currentToken.kind == Token.LEFTBRACKET) {
            acceptIt();
            parseExpr();
            accept(Token.RIGHTBRACKET);
            accept(Token.ASSIGN);
            parseExpr();
        } else
        if (isArglist()) {
            parseArglist();
        }
        accept(Token.SEMICOLON);
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // Variable-def ::= ( "[" INTLITERAL "]" )?  ( "=" initializer ) ? ( "," declarator ("=" initializer)? )* ";"
    //
    ///////////////////////////////////////////////////////////////////////////////

    public void parseVariable_def() throws SyntaxError {//debug("variable-def");
        if (currentToken.kind == Token.LEFTBRACKET) {
            acceptIt();
            accept(Token.INTLITERAL);
            accept(Token.RIGHTBRACKET);
        }
        if (currentToken.kind == Token.ASSIGN) {
            acceptIt();
            parseInitializer();
        }
        while (currentToken.kind == Token.COMMA) {
            acceptIt();
            parseDeclarator();
            if (currentToken.kind == Token.ASSIGN) {
                acceptIt();
                parseInitializer();
            }
        }
        accept(Token.SEMICOLON);
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    // declarator ::= ID ("[" INTLITERAL "]")?
    //
    //////////////////////////////////////////////////////////////////////////////

    public void parseDeclarator() throws SyntaxError {//debug("declarator");
        accept(Token.ID);
        if (currentToken.kind == Token.LEFTBRACKET) {
            acceptIt();
            accept(Token.INTLITERAL);
            accept(Token.RIGHTBRACKET);
        }
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    // initializer ::= expr | "{" expr ("," expr)* "}"
    //
    //////////////////////////////////////////////////////////////////////////////

    public void parseInitializer() throws SyntaxError {//debug("initializer");
        if (currentToken.kind == Token.LEFTBRACE) {
            acceptIt();
            parseExpr();
            while (currentToken.kind == Token.COMMA) {
                acceptIt();
                parseExpr();
            }
            accept(Token.RIGHTBRACE);
        } else {
            parseExpr();
        }
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    // expr ::= or-expr
    //
    //////////////////////////////////////////////////////////////////////////////

    public void parseExpr() throws SyntaxError {//debug("expr");
         parseOr_expr();
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

    //////////////////////////////////////////////////////////////////////////////
    //
    // or-expr ::= and-expr ("||" and-expr)*
    //
    //////////////////////////////////////////////////////////////////////////////

    public void parseOr_expr() throws SyntaxError {//debug("or-expr");
        parseAnd_expr();
        while (currentToken.kind == Token.OR) {
            acceptIt();
            parseAnd_expr();
        }
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    // and-expr ::= rel-expr ("&&" rel-expr)*
    //
    //////////////////////////////////////////////////////////////////////////////

    public void parseAnd_expr() throws SyntaxError {//debug("and-expr");
        parseRel_expr();
        while (currentToken.kind == Token.AND) {
            acceptIt();
            parseRel_expr();
        }
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    // rel-expr ::= add-expr (("==" | "!=" | "<" | "<=" | ">" | ">=") add-expr)?
    //
    //////////////////////////////////////////////////////////////////////////////

    public void parseRel_expr() throws SyntaxError {//debug("rel-expr");
        parseAdd_expr();
        switch (currentToken.kind) {
            case Token.EQ: case Token.NOTEQ:
            case Token.LESS: case Token.LESSEQ:
            case Token.GREATER: case Token.GREATEREQ:
                acceptIt();
                parseAdd_expr();
            default:
        }
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    // add-expr ::= mult-expr (("+" | "-") mult-expr)*
    //
    //////////////////////////////////////////////////////////////////////////////

    public void parseAdd_expr() throws SyntaxError {//debug("add-expr");
        parseMult_expr();
        while (currentToken.kind == Token.PLUS ||
               currentToken.kind == Token.MINUS)
        {
            acceptIt();
            parseMult_expr();
        }
    }         

    //////////////////////////////////////////////////////////////////////////////
    //
    // mult-expr ::= unary-expr (("*" | "/") unary-expr)*
    //
    //////////////////////////////////////////////////////////////////////////////

    public void parseMult_expr() throws SyntaxError {//debug("mult-expr");
        parseUnary_expr();
        while (currentToken.kind == Token.TIMES ||
               currentToken.kind == Token.DIV)
        {
            acceptIt();
            parseUnary_expr();
        }
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    // unary-expr ::= ("+" | "-" | "!")* prim-expr
    //
    //////////////////////////////////////////////////////////////////////////////

    public void parseUnary_expr() throws SyntaxError {//debug("unary-expr");
        while (currentToken.kind == Token.PLUS ||
               currentToken.kind == Token.MINUS ||
               currentToken.kind == Token.NOT)
        { acceptIt(); }
        parsePrim_expr();
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    // prim-expr ::= ID (arglist? | "[" expr "]") | "(" expr ")" | INTLITERAL | BOOLLITERAL | FLOATLITERAL | STRINGLITERAL
    //
    //////////////////////////////////////////////////////////////////////////////

    public void parsePrim_expr() throws SyntaxError {//debug("prim-expr");
        switch (currentToken.kind) {
        case Token.ID:
            acceptIt();
            if (currentToken.kind == Token.LEFTBRACKET) {
                acceptIt();
                parseExpr();
                accept(Token.RIGHTBRACKET);
            } else if (isArglist()) {
                parseArglist();
            }
            break;
        case Token.LEFTPAREN:
            acceptIt();
            parseExpr();
            accept(Token.RIGHTPAREN);
            break;
        case Token.INTLITERAL:
        case Token.BOOLLITERAL:
        case Token.FLOATLITERAL:
        case Token.STRINGLITERAL:
            acceptIt();
            break;
        default:
            syntaxError("", "ID");
        }
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    // arglist ::= "(" (arg ("," arg)*)? ")"
    //
    //////////////////////////////////////////////////////////////////////////////

    public void parseArglist() throws SyntaxError {//debug("arglist");
        accept(Token.LEFTPAREN);
        if (isArg()) {
            parseArg();
            while (currentToken.kind == Token.COMMA) {
                acceptIt();
                parseArg();
            }
        }
        accept(Token.RIGHTPAREN);
    }
    boolean isArglist() { return currentToken.kind == Token.LEFTPAREN; }

    //////////////////////////////////////////////////////////////////////////////
    //
    // arg ::= expr
    //
    //////////////////////////////////////////////////////////////////////////////

    public void parseArg() throws SyntaxError {//debug("arg");
         parseExpr();
    }
    boolean isArg() { return isExpr(); }
}
