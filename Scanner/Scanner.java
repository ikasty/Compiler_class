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
  private State tempState;
  private StringBuffer buffer;

  private static final int NEEDS_CHECK = 100;// 37 is temporary. NEEDS CHANGE
  private static final int WHITESPACES = 200;
  private static final int ERRORS = 36;
  private static final int UNACCEPTABLE = 300;

  private enum State {
    init(), ID(0), Integer(15), exp(), exp_pm(), Float_with_exp(16), frac(), Float_frac(16),
    Arith_op(NEEDS_CHECK), Div(14), Assign(1), Separator(NEEDS_CHECK),
    Compare(5), Not(4), Not_eq(6), GL(NEEDS_CHECK), GLE(NEEDS_CHECK),
    and_temp(), And(3), or_temp(), Or(2), string_temp(), escape(), String(18), Braket(NEEDS_CHECK),
    Whitespaces(WHITESPACES), Comment(WHITESPACES), comment_blk_in(), comment_blk_end(), Comment_blk(WHITESPACES),
    Err_string(ERRORS), Err_escape(ERRORS), Err_token(ERRORS), Err_comment(ERRORS),
    EOF(37);

    private int value;
    private State(int value) { this.value = value; }
    private State() { this.value = UNACCEPTABLE; }
    public int getValue() { return this.value; }

    public boolean isWhitespace() { return this.value == WHITESPACES; }
    public boolean isError() { return this.value == ERRORS; }
    public boolean isAccepted() { return this.value != UNACCEPTABLE; }
  }

///////////////////////////////////////////////////////////////////////////////
  // Public Methods

  public Scanner(SourceFile source) {
    sourceFile = source;
    verbose = false;

    currentLineNr = 1;
    currentColNr= 1;

    // temp lexeme
    tempLexeme = new StringBuffer("");
    buffer = new StringBuffer("");
    tempCol = 0;
    tempLine = 0;

    readChar();
  }

  public void enableDebugging() {
    verbose = true;
  }

  public Token scan() {
    Token currentToken;
    SourcePos pos;

    currentlyScanningToken = false;
    currentLexeme = new StringBuffer("");

    pos = new SourcePos();

    do {
      pos.StartLine  = currentLineNr;
      pos.EndLine    = currentLineNr;
      pos.StartCol   = currentColNr;
System.out.println("try to scan...");
      currentState = State.init;
      scanToken();
System.out.println("Finished.");
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
  }

  private void takeIt() {
    currentLexeme.append(tempLexeme);
    tempLexeme.setLength(0);

    currentLexeme.append(currentChar);
    setLine();
  }

  private void lookAt() {
    if (tempCol == 0) {
      tempCol = currentColNr;
      tempLine = currentLineNr;
      tempChar = currentChar;
      tempState = currentState;
    } else {
      tempLexeme.append(currentChar);
    }
    setLine();
  }

  private void rejectLexeme() {
    buffer.insert(0, tempLexeme);
    tempLexeme.setLength(0);

    if (tempCol > 0) {
      currentColNr = tempCol;
      currentLineNr = tempLine;
      tempCol = 0; tempLine = 0;
      currentChar = tempChar;
      currentState = tempState;
    }
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
           (currentChar >= 'A' && currentChar <= 'Z');
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
  private boolean isBraket() {
    switch (currentChar) {
    case '[': case ']': case '{': case '}': case '(': case ')': case ';': case ',':
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
      System.out.format("Current state: %s. currentChar = %c\n", currentState.name(), currentChar);

      if ( currentChar == '\n' &&
           currentState != State.init &&
           currentState != State.comment_blk_in &&
           currentState != State.string_temp &&
           currentState != State.escape ) {
        onState = false;
        break;
      }

      switch (currentState) {
      case init:
             if (isDigit())      { currentState = State.Integer;     }
        else if (isLetter())     { currentState = State.ID;          }
        else if (isWhitespace()) { currentState = State.Whitespaces; }
        else if (isBraket())     { currentState = State.Braket;      }

        else {
          switch (currentChar) {
          case '\u0000':
                    currentState = State.EOF; break;
          case '*': case '+': case '-':
                    currentState = State.Arith_op; break;
          case '/': currentState = State.Div; break;
          case '&': currentState = State.and_temp; break;
          case '|': currentState = State.or_temp; break;
          case '=': currentState = State.Assign; break;
          case '!': currentState = State.Not; break;
          case '"': currentState = State.string_temp; break;
          case '.': currentState = State.frac; break;
          case '<': case '>':
                    currentState = State.GL; break;

          default:  currentState = State.Err_token; break;
          }
        }
System.out.format("init state to %s\n", currentState.name());
        break;

      case Whitespaces:
        if (!isWhitespace()) onState = false;
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
        if (currentChar == '"') currentState = State.String;
        else if (currentChar == '/') currentState = State.escape;
        else if (currentChar == '\n') {
          currentState = State.Err_string;
          onState = false;
        }
        break;

      case escape:
        if (currentChar == 'n') currentState = State.string_temp;
        else {
          currentState = State.Err_escape;
          onState = false;
        }
        break;

      case Braket: case Not_eq: case GLE: case Arith_op: case And: case Or: case Compare: case EOF:
        onState = false; break;

      default:
        // Unknown State!

      } // end FSM

      if (!onState) {
        rejectLexeme();
        break;
      }

      if (currentState.isAccepted()) {
        takeIt();
      } else {
        lookAt();
      }

      readChar();
    } // while
  }
} // end class
