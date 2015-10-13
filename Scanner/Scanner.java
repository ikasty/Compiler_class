package MiniC.Scanner;

import MiniC.Scanner.SourceFile;
import MiniC.Scanner.Token;

public final class Scanner {

  private SourceFile sourceFile;

  private final class State {
    private char posChar;
    private StringBuffer lexeme;
    private int lineNr;
    private int colNr;
    private Status status;

    public State() {
      this.lineNr = 1;
      this.colNr = 1;
    }

    // copy constructor
    public State(State other) {
      this.posChar = other.posChar;
      this.lexeme = new StringBuffer(other.lexeme.toString());
      this.lineNr = other.lineNr;
      this.colNr = other.colNr;
      this.status = other.status;
    }

    public boolean addCurrentChar = true;
    public void takeIt() {
      if (this.addCurrentChar) this.lexeme.append(this.posChar);
      this.addCurrentChar = true;
    }

    public void setLine() {
      if (this.posChar == '\n') {
        this.lineNr++;
        this.colNr = 0;
      }
      this.colNr++;
    }
  }

  private State currentState, origState;

  private boolean verbose;
  // buffer values
  private StringBuffer buffer;

  private static final int NEEDS_CHECK = 100;
  private static final int WHITESPACES = 200;
  private static final int ERRORS = 36;
  private static final int UNACCEPTABLE = 300;

  private enum Status {
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
    private Status(int value) { this.value = value; }
    private Status() { this.value = UNACCEPTABLE; }
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
    buffer = new StringBuffer();
    currentState = new State();

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
      currentState.lexeme = new StringBuffer("");

      pos.StartLine  = currentState.lineNr;
      pos.EndLine    = currentState.lineNr;
      pos.StartCol   = currentState.colNr;

      currentState.status = Status.init;
      scanToken();

      pos.EndCol     = currentState.colNr - 1;

    // skip comments and whitespaces
    } while (currentState.status.isWhitespace());

    currentToken = new Token(currentState.status.getValue(), currentState.lexeme.toString(), pos);

    if (verbose) currentToken.print();
    return currentToken;
  }

//////////////////////////////////////
// character read

  private void takeIt() {
    acceptLexeme();
    currentState.setLine();
    currentState.takeIt();
  }

  private void lookAt() {
    if (origState == null) origState = new State(currentState);

    currentState.setLine();
    currentState.takeIt();
  }

  private void acceptLexeme() {
    if (origState != null) origState = null;
  }

  private void rejectLexeme() {
    if (origState == null) return ;
    buffer.insert(0, currentState.posChar);
    if (origState.lexeme.length() < currentState.lexeme.length()) {
      buffer.insert(0, currentState.lexeme.substring( origState.lexeme.length() + 1 ));
    }
    currentState = new State(origState);
    origState = null;
  }

  private void readChar() {
    if (buffer.length() > 0) {
      currentState.posChar = buffer.charAt(0);
      buffer.deleteCharAt(0);
    } else {
      currentState.posChar = sourceFile.readChar();
    }
  }

//////////////////////////////////////////////
// currentChar groups

  private boolean isDigit() {
    char pChar = currentState.posChar;
    return pChar >= '0' && pChar <= '9';
  }
  private boolean isLetter() {
    char pChar = currentState.posChar;
    return (pChar >= 'a' && pChar <= 'z') ||
           (pChar >= 'A' && pChar <= 'Z') ||
            pChar == '_';
  }
  private boolean isExp() {
    char pChar = currentState.posChar;
    return pChar == 'e' || pChar == 'E';
  }
  private boolean isWhitespace() {
    char pChar = currentState.posChar;
    return pChar == ' '  ||
           pChar == '\f' ||
           pChar == '\r' ||
           pChar == '\t' ||
           pChar == '\n';
  }
  private boolean isSeparator() {
    char pChar = currentState.posChar;
    switch (pChar) {
    case '[': case ']': case '{': case '}': case '(': case ')': case ';': case ',':
      return true;
    default:
      return false;
    }
  }
  private boolean isUseNewline() {
    Status status = currentState.status;
    switch (status) {
    case init: case comment_in: case comment_blk_in:
    case comment_blk_end: case string_temp: case escape:
      return true;
    default:
      return false;
    }
  }
  private boolean isUseNull() {
    Status status = currentState.status;
    switch (status) {
    case init: case comment_blk_in: case comment_blk_end:
      return true;
    default:
      return false;
    }
  }

//////////////////////////////////////////////

  private void scanToken() {
    boolean onStatus = true;
    Status nextStatus = currentState.status;

    // FSM
    while (onStatus) {
      char currentChar = currentState.posChar;

//      System.out.format("Current state: %s. currentChar = [%c]\n", currentState.status.name(), currentChar);

      if ( (currentChar == '\n' && !isUseNewline()) ||
           (currentChar == '\u0000' && !isUseNull()) ) {
        onStatus = false;
        break;
      }

      switch (currentState.status) {
      case init:
             if (isDigit())      { nextStatus = Status.Integer;     }
        else if (isLetter())     { nextStatus = Status.ID;          }
        else if (isWhitespace()) { nextStatus = Status.Whitespaces; }
        else if (isSeparator())  { nextStatus = Status.Separator;   }

        else {
          switch (currentChar) {
          case '\u0000':
                    nextStatus = Status.EOF; currentState.posChar = '$'; break;
          case '*': case '+': case '-':
                    nextStatus = Status.Arith_op; break;
          case '/': nextStatus = Status.Div; break;
          case '&': nextStatus = Status.and_temp; break;
          case '|': nextStatus = Status.or_temp; break;
          case '=': nextStatus = Status.Assign; break;
          case '!': nextStatus = Status.Not; break;
          case '"': nextStatus = Status.string_temp; currentState.addCurrentChar = false; break;
          case '.': nextStatus = Status.frac; break;
          case '<': case '>':
                    nextStatus = Status.GL; break;

          default:  nextStatus = Status.Err_token; break;
          }
        }
        break;

      case Whitespaces:
        if (!isWhitespace()) onStatus = false;
        break;

      case Div:
        if (currentChar == '/') nextStatus = Status.comment_in;
        else if (currentChar == '*') nextStatus = Status.comment_blk_in;
        break;

      case comment_in:
        if (currentChar == '\n') nextStatus = Status.Comment;
        break;

      case comment_blk_in:
        if (currentChar == '*') nextStatus = Status.comment_blk_end;
        else if (currentChar == '\u0000') nextStatus = Status.Err_comment;
        break;

      case comment_blk_end:
        if (currentChar == '/') nextStatus = Status.Comment_blk;
        else if (currentChar == '\u0000') nextStatus = Status.Err_comment;
        else if (currentChar != '*') {
          // 1. accept current lexeme
          acceptLexeme();
          // 2. set state to comment_blk_in
          nextStatus = Status.comment_blk_in;
        }
        break;

      case ID:
        if (!isLetter() && !isDigit()) onStatus = false;
        break;

      case Integer:
        if (isDigit()) break;
        else if (currentChar == '.') nextStatus = Status.Float_frac;
        else if (isExp()) nextStatus = Status.exp;
        else onStatus = false;
        break;

      case frac:
        if (isDigit()) nextStatus = Status.Float_frac;
        else onStatus = false;
        break;

      case exp:
        if (currentChar == '+' || currentChar == '-') nextStatus = Status.exp_pm;
        else if (isDigit()) nextStatus = Status.Float_with_exp;
        else onStatus = false;
        break;

      case exp_pm:
        if (isDigit()) nextStatus = Status.Float_with_exp;
        else onStatus = false;
        break;

      case Float_frac:
        if (isDigit()) break;
        else if (isExp()) nextStatus = Status.exp;
        else onStatus = false;
        break;

      case Float_with_exp:
        if (!isDigit()) onStatus = false;
        break;

      case Not:
        if (currentChar == '=') nextStatus = Status.Not_eq;
        else onStatus = false; break;

      case and_temp:
        if (currentChar == '&') nextStatus = Status.And;
        else onStatus = false; break;

      case or_temp:
        if (currentChar == '|') nextStatus = Status.Or;
        else onStatus = false; break;

      case Assign:
        if (currentChar == '=') nextStatus = Status.Compare;
        else onStatus = false; break;

      case GL:
        if (currentChar == '=') nextStatus = Status.GLE;
        else onStatus = false; break;

      case string_temp:
        if (currentChar == '"')     { nextStatus = Status.String; currentState.addCurrentChar = false; }
        else if (currentChar == '\\') nextStatus = Status.escape;
        else if (currentChar == '\n') nextStatus = Status.Err_string;
        break;

      case escape:
        if (currentChar == 'n') nextStatus = Status.string_temp;
        else                    nextStatus = Status.Err_escape;
        break;

      case Separator: case Arith_op: case String:
      case Not_eq: case GLE: case Compare: case And: case Or:
      case EOF: case Comment: case Comment_blk:
        onStatus = false; break;

      default:
        // Unknown Status!
        System.out.format("ERROR: unknown state %s\n", nextStatus.name());
        onStatus = false;
      } // end FSM

      // error control
      if (nextStatus.isError()) {
        switch (nextStatus) {
        case Err_escape:
          System.out.println("ERROR: illegal escape sequence");
          nextStatus = Status.string_temp;
          break;
        case Err_string:
          System.out.println("ERROR: unterminated string literal");
          nextStatus = Status.String;
          acceptLexeme();
          onStatus = false;
          break;
        case Err_comment:
          System.out.println("ERROR: unterminated multi-line comment.");
          nextStatus = Status.Comment_blk;
          acceptLexeme();
          onStatus = false;
          break;
        case Err_token:
          takeIt();
          onStatus = false;
          break;
        }
      }

      // exit check
      if (!onStatus) {
        currentState.status = nextStatus;
        if (origState != null && origState.status.isAccepted()) {
          rejectLexeme(); 
        }
        break;
      }

      // take it or look at
      if (nextStatus.isAccepted()) {
        takeIt();
      } else {
        lookAt();
      }

      currentState.status = nextStatus;
      readChar();
    } // while

    // post check
    if (!currentState.status.isAccepted()) {
      acceptLexeme();
      currentState.status = Status.Err_token;
    }
    if (currentState.status == Status.ID || currentState.status.isNeedCheck()) {
      switch (currentState.lexeme.toString()) {
      case "bool":   currentState.status = Status.BOOL;    break;
      case "else":   currentState.status = Status.ELSE;    break;
      case "float":  currentState.status = Status.FLOAT;   break;
      case "for":    currentState.status = Status.FOR;     break;
      case "if":     currentState.status = Status.IF;      break;
      case "int":    currentState.status = Status.INT;     break;
      case "return": currentState.status = Status.RETURN;  break;
      case "void":   currentState.status = Status.VOID;    break;
      case "while":  currentState.status = Status.WHILE;   break;

      case "true":
      case "false":  currentState.status = Status.BOOLLITERAL; break;

      case "<":      currentState.status = Status.LESS;      break;
      case ">":      currentState.status = Status.GREATER;   break;
      case "<=":     currentState.status = Status.LESSEQ;    break;
      case ">=":     currentState.status = Status.GREATEREQ; break;
      case "+":      currentState.status = Status.PLUS;      break;
      case "-":      currentState.status = Status.MINUS;     break;
      case "*":      currentState.status = Status.TIMES;     break;

      case "{":      currentState.status = Status.LEFTBRACE;    break;
      case "}":      currentState.status = Status.RIGHTBRACE;   break;
      case "[":      currentState.status = Status.LEFTBRACKET;  break;
      case "]":      currentState.status = Status.RIGHTBRACKET; break;
      case "(":      currentState.status = Status.LEFTPAREN;    break;
      case ")":      currentState.status = Status.RIGHTPAREN;   break;
      case ",":      currentState.status = Status.COMMA;        break;
      case ";":      currentState.status = Status.SEMICOLON;    break;
      }
    } // post check end
  } // end function
} // end class
