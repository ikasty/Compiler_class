package MiniC.Scanner;

import MiniC.Scanner.SourceFile;
import MiniC.Scanner.Token;

public final class Scanner {

  private SourceFile sourceFile;

  private char currentChar;
  private boolean verbose;
  private StringBuffer currentLexeme;
  private boolean currentlyScanningToken;
  private int currentLineNr;
  private int currentColNr;
  private State currentState;

  // buffer values
  private int tempLine, tempCol;
  private char tempChar;
  private StringBuffer tempLexeme;
  private State tempState, origState;
  private StringBuffer buffer;

  private static final int NEEDS_CHECK = 100;// 37 is temporary. NEEDS CHANGE
  private static final int WHITESPACES = 200;
  private static final int ERRORS = 36;
  private static final int UNACCEPTABLE = 300;

  private enum State {
    init(),
    // id, integer, float
    ID(0), Integer(15), exp(), exp_pm(), Float_with_exp(16), frac(), Float_frac(16),
    // operators
    Arith_op(NEEDS_CHECK), Div(14), Assign(1),
    // compares
    Compare(5), Not(4), Not_eq(6), GL(NEEDS_CHECK), GLE(NEEDS_CHECK),
    and_temp(), And(3), or_temp(), Or(2),
    // strings and separator
    string_temp(), escape(), String(18), Separator(NEEDS_CHECK),
    // comments and whitespaces
    Whitespaces(WHITESPACES), comment_in(), Comment(WHITESPACES), comment_blk_in(ERRORS), comment_blk_end(ERRORS), Comment_blk(WHITESPACES),
    // error states
    Err_string(ERRORS), Err_escape(ERRORS), Err_token(ERRORS), Err_comment(ERRORS),

    // GL, GLE, Arith_op
    LESSEQ(7), LESS(8), GREATER(9), GREATEREQ(10), PLUS(11), MINUS(12), TIMES(13),
    // keywords
    BOOL(19), ELSE(20), FLOAT(21), FOR(22), IF(23), INT(24), RETURN(25), VOID(26), WHILE(27), BOOLLITERAL(17),
    // Separator
    LEFTBRACE(28), RIGHTBRACE(29), LEFTBRACKET(30), RIGHTBRACKET(31),
    LEFTPAREN(32), RIGHTPAREN(33), COMMA(34), SEMICOLON(35),
    // Errors
    ERROR(36),
    // EOF
    EOF(37);

    private int value;
    private State(int value) { this.value = value; }
    private State() { this.value = UNACCEPTABLE; }
    public int getValue() { return this.value; }

    public boolean isWhitespace() { return this.value == WHITESPACES; }
    public boolean isError() { return this.value == ERRORS; }
    public boolean isAccepted() { return this.value != UNACCEPTABLE; }
    public boolean isNeedCheck() { return this.value == NEEDS_CHECK; }
  }

///////////////////////////////////////////////////////////////////////////////
  // Public Methods

  public Scanner(SourceFile source) {
    sourceFile = source;
    verbose = false;

    currentLineNr = 1;
    currentColNr = 1;

    useCurrentChar = true;

    // temp lexeme
    tempLexeme = new StringBuffer("");
    buffer = new StringBuffer("");
    tempCol = -1;

    readChar();
  }

  public void enableDebugging() {
    verbose = true;
  }

  public Token scan() {
    Token currentToken;
    SourcePos pos;
 
    pos = new SourcePos();

    do {
      currentLexeme  = new StringBuffer("");

      pos.StartLine  = currentLineNr;
      pos.EndLine    = currentLineNr;
      pos.StartCol   = currentColNr;

      currentState = State.init;
      scanToken();

      pos.EndCol     = currentColNr - 1;

    // skip comments and whitespaces
    } while (currentState.isWhitespace());

    currentToken = new Token(currentState.getValue(), currentLexeme.toString(), pos);

    if (verbose) currentToken.print();
    return currentToken;
  }

//////////////////////////////////////
// character read

  private void setLine() {
    if (currentChar == '\n') {
      currentColNr = 0;
      currentLineNr++;
    }
    currentColNr++;
//System.out.format("col nr to %d\n", currentColNr);
  }

  private boolean useCurrentChar;
  private void takeIt() {
    acceptLexeme();
    setLine();
    if (useCurrentChar) currentLexeme.append(currentChar);
    useCurrentChar = true;
  }

  private void lookAt() {
    setStage();
    setLine();
    if (useCurrentChar) tempLexeme.append(currentChar);
    useCurrentChar = true;
  }

  private void acceptLexeme() {
    currentLexeme.append(tempLexeme.toString());
    tempLexeme.setLength(0);
    tempCol = -1;
  }

  private void rejectLexeme() {
    if (tempLexeme.length() > 0) {
      buffer.insert(0, currentChar);
      buffer.insert(0, tempLexeme.substring(1));
      tempLexeme.setLength(0);
    }
  }

  private void setStage() {
    if (tempCol != -1) return ;
    tempCol  = currentColNr;
    tempLine = currentLineNr;
    tempChar = currentChar;
    tempState = origState;
  }

  private void resetStage() {
    if (tempCol == -1) return ;
    currentColNr = tempCol;
    currentLineNr = tempLine;
    currentChar = tempChar;
    currentState = tempState;
    tempCol = -1;
  }

  private void readChar() {
    if (buffer.length() > 0) {
      currentChar = buffer.charAt(0);
      buffer.deleteCharAt(0);
    } else {
      currentChar = sourceFile.readChar();
    }
  }

//////////////////////////////////////////////
// currentChar groups

  private boolean isDigit() {
    return currentChar >= '0' && currentChar <= '9';
  }
  private boolean isLetter() {
    return (currentChar >= 'a' && currentChar <= 'z') ||
           (currentChar >= 'A' && currentChar <= 'Z') ||
            currentChar == '_';
  }
  private boolean isExp() {
    return currentChar == 'e' || currentChar == 'E';
  }
  private boolean isWhitespace() {
    return currentChar == ' '  ||
           currentChar == '\f' ||
           currentChar == '\r' ||
           currentChar == '\t' ||
           currentChar == '\n';
  }
  private boolean isSeparator() {
    switch (currentChar) {
    case '[': case ']': case '{': case '}': case '(': case ')': case ';': case ',':
      return true;
    default:
      return false;
    }
  }
  private boolean isUseNewline() {
    switch (currentState) {
    case init: case comment_in: case comment_blk_in:
    case comment_blk_end: case string_temp: case escape:
      return true;
    default:
      return false;
    }
  }
  private boolean isUseNull() {
    switch (currentState) {
    case init: case comment_blk_in: case comment_blk_end:
      return true;
    default:
      return false;
    }
  }

//////////////////////////////////////////////

  private void scanToken() {
    boolean onState = true;

    // FSM
    while (onState) {
      origState = currentState;

//      System.out.format("Current state: %s. currentChar = [%c]\n", currentState.name(), currentChar);

      if ( (currentChar == '\n' && !isUseNewline()) ||
           (currentChar == '\u0000' && !isUseNull()) ) {
        onState = false;
        break;
      }

      switch (currentState) {
      case init:
             if (isDigit())      { currentState = State.Integer;     }
        else if (isLetter())     { currentState = State.ID;          }
        else if (isWhitespace()) { currentState = State.Whitespaces; }
        else if (isSeparator())  { currentState = State.Separator;   }

        else {
          switch (currentChar) {
          case '\u0000':
                    currentState = State.EOF; currentChar = '$'; break;
          case '*': case '+': case '-':
                    currentState = State.Arith_op; break;
          case '/': currentState = State.Div; break;
          case '&': currentState = State.and_temp; break;
          case '|': currentState = State.or_temp; break;
          case '=': currentState = State.Assign; break;
          case '!': currentState = State.Not; break;
          case '"': currentState = State.string_temp; useCurrentChar = false; break;
          case '.': currentState = State.frac; break;
          case '<': case '>':
                    currentState = State.GL; break;

          default:  currentState = State.Err_token; break;
          }
        }
        break;

      case Whitespaces:
        if (!isWhitespace()) onState = false;
        break;

      case Div:
        if (currentChar == '/') currentState = State.comment_in;
        else if (currentChar == '*') currentState = State.comment_blk_in;
        break;

      case comment_in:
        if (currentChar == '\n') currentState = State.Comment;
        break;

      case comment_blk_in:
        if (currentChar == '*') currentState = State.comment_blk_end;
        else if (currentChar == '\u0000') currentState = State.Err_comment;
        break;

      case comment_blk_end:
        if (currentChar == '/') currentState = State.Comment_blk;
        else if (currentChar == '\u0000') currentState = State.Err_comment;
        else if (currentChar != '*') {
          // 1. accept temp lexeme
          acceptLexeme();
          // 2. discard temp state
          tempCol = -1;
          // 3. set state to comment_blk
          currentState = State.comment_blk_in;
        }
        break;

      case ID:
        if (!isLetter() && !isDigit()) onState = false;
        break;

      case Integer:
        if (isDigit()) break;
        else if (currentChar == '.') currentState = State.Float_frac;
        else if (isExp()) currentState = State.exp;
        else onState = false;
        break;

      case frac:
        if (isDigit()) currentState = State.Float_frac;
        else onState = false;
        break;

      case exp:
        if (currentChar == '+' || currentChar == '-') currentState = State.exp_pm;
        else if (isDigit()) currentState = State.Float_with_exp;
        else onState = false;
        break;

      case exp_pm:
        if (isDigit()) currentState = State.Float_with_exp;
        else onState = false;
        break;

      case Float_frac:
        if (isDigit()) break;
        else if (isExp()) currentState = State.exp;
        else onState = false;
        break;

      case Float_with_exp:
        if (!isDigit()) onState = false;
        break;

      case Not:
        if (currentChar == '=') currentState = State.Not_eq;
        else onState = false; break;

      case and_temp:
        if (currentChar == '&') currentState = State.And;
        else onState = false; break;

      case or_temp:
        if (currentChar == '|') currentState = State.Or;
        else onState = false; break;

      case Assign:
        if (currentChar == '=') currentState = State.Compare;
        else onState = false; break;

      case GL:
        if (currentChar == '=') currentState = State.GLE;
        else onState = false; break;

      case string_temp:
        if (currentChar == '"')     { currentState = State.String; useCurrentChar = false; }
        else if (currentChar == '\\') currentState = State.escape;
        else if (currentChar == '\n') currentState = State.Err_string;
        break;

      case escape:
        if (currentChar == 'n') currentState = State.string_temp;
        else                    currentState = State.Err_escape;
        break;

      case Separator: case Arith_op: case String:
      case Not_eq: case GLE: case Compare: case And: case Or:
      case EOF: case Comment: case Comment_blk:
        onState = false; break;

      default:
        // Unknown State!
        System.out.format("ERROR: unknown state %s\n", currentState.name());
        onState = false;
      } // end FSM

      // error control
      if (currentState.isError()) {
        switch (currentState) {
        case Err_escape:
          System.out.println("ERROR: illegal escape sequence");
          currentState = State.string_temp;
          break;
        case Err_string:
          System.out.println("ERROR: unterminated string literal");
          currentState = State.String;
          acceptLexeme();
          onState = false;
          break;
        case Err_comment:
          System.out.println("ERROR: unterminated multi-line comment.");
          currentState = State.Comment_blk;
          acceptLexeme();
          onState = false;
          break;
        case Err_token:
          takeIt();
          onState = false;
          break;
        }
      }

      if (!onState) {
        if (tempState != null && tempState.isAccepted()) {
          rejectLexeme();
          resetStage();
        }
        break;
      }

      if (currentState.isAccepted()) {
        takeIt();
      } else {
        lookAt();
      }

      readChar();
    } // while

    // post check
    if (!currentState.isAccepted()) {
      acceptLexeme();
      currentState = State.Err_token;
    }
    if (currentState == State.ID || currentState.isNeedCheck()) {
      switch (currentLexeme.toString()) {
      case "bool":   currentState = State.BOOL;    break;
      case "else":   currentState = State.ELSE;    break;
      case "float":  currentState = State.FLOAT;   break;
      case "for":    currentState = State.FOR;     break;
      case "if":     currentState = State.IF;      break;
      case "int":    currentState = State.INT;     break;
      case "return": currentState = State.RETURN;  break;
      case "void":   currentState = State.VOID;    break;
      case "while":  currentState = State.WHILE;   break;

      case "true":
      case "false":  currentState = State.BOOLLITERAL; break;

      case "<":      currentState = State.LESS;      break;
      case ">":      currentState = State.GREATER;   break;
      case "<=":     currentState = State.LESSEQ;    break;
      case ">=":     currentState = State.GREATEREQ; break;
      case "+":      currentState = State.PLUS;      break;
      case "-":      currentState = State.MINUS;     break;
      case "*":      currentState = State.TIMES;     break;

      case "{":      currentState = State.LEFTBRACE;    break;
      case "}":      currentState = State.RIGHTBRACE;   break;
      case "[":      currentState = State.LEFTBRACKET;  break;
      case "]":      currentState = State.RIGHTBRACKET; break;
      case "(":      currentState = State.LEFTPAREN;    break;
      case ")":      currentState = State.RIGHTPAREN;   break;
      case ",":      currentState = State.COMMA;        break;
      case ";":      currentState = State.SEMICOLON;    break;
      }
    }
    // post check end 
  }
} // end class
