package pins.phase.lexan;

import java.io.*;
import pins.common.report.*;
import pins.data.symbol.*;

import java.util.Locale;
import java.util.Stack;
import java.util.regex.*;

/**
 * Lexical analyzer.
 */
public class LexAn implements AutoCloseable {

	private String srcFileName;

	private FileReader srcFile;

	public LexAn(String srcFileName) {
		this.srcFileName = srcFileName;
		try {
			srcFile = new FileReader(new File(srcFileName));
		} catch (FileNotFoundException __) {
			throw new Report.Error("Cannot open source file '" + srcFileName + "'.");
		}
	}

	public void close() {
		try {
			srcFile.close();
		} catch (IOException __) {
			throw new Report.Error("Cannot close source file '" + srcFileName + "'.");
		}
	}

	private int line = 1;
	private int col = 0;
	private int current;
	private String currentlyBuilding;
	private int previouslyBuilt = -3;
	private int commentCounter = 0;
	private int HT = 8;


	public Symbol lexer() throws Report.Error{

		do {
			if (commentCounter>0)
				lookForComment();
			else if (commentCounter<0)
				throw new Report.Error(new Location(line, col), "LEXICAL ERROR: Closing comment crashed the program.");

			if (previouslyBuilt == -3)
				current = readNext();
			else{
				current = previouslyBuilt;
				previouslyBuilt = -3;
			}
			currentlyBuilding="";
			switch (current) {
				// Special characters
				case -1:
					if (commentCounter>0) Report.warning("LEXICAL WARNING: End of file reached, but you still have an opened comment. Better check.");
					return new Symbol(Token.EOF, "", null);
				case '\n':		// New Line
					newLine();
					continue;
				case 13:		// Carriage Return
					col = 0;
					continue;
				case 9:			// Tab - HT horizontal tab
					col += (HT - (col % HT));
					continue;
				// Regular one-letter symbols
				case (char) '(':
					return new Symbol(Token.LB, "(", new Location(line, col));
				case (char) ')':
					return new Symbol(Token.RB, ")", new Location(line, col));
				case (char) '{':
					return new Symbol(Token.LCB, "{", new Location(line, col));
				case (char) '}':
					Symbol s = closeComment(false);
					if (s != null) return s;
					break;
				case (char) '[':
					return new Symbol(Token.LSB, "[", new Location(line, col));
				case (char) ']':
					return new Symbol(Token.RSB, "]", new Location(line, col));
				case (char) ',':
					return new Symbol(Token.COMMA, ",", new Location(line, col));
				case (char) ':':
					return new Symbol(Token.COLON, ":", new Location(line, col));
				case (char) ';':
					return new Symbol(Token.SEMI_COLON, ";", new Location(line, col));
				case (char) '&':
					return new Symbol(Token.AND, "&", new Location(line, col));
				case (char) '|':
					return new Symbol(Token.OR, "|", new Location(line, col));
				case (char) '*':
					return new Symbol(Token.MUL, "*", new Location(line, col));
				case (char) '/':
					return new Symbol(Token.DIV, "/", new Location(line, col));
				case (char) '%':
					return new Symbol(Token.MOD, "%", new Location(line, col));
				case (char) '+':
					return new Symbol(Token.PLUS, "+", new Location(line, col));
				case (char) '-':
					return new Symbol(Token.MINUS, "-", new Location(line, col));
				case (char) '^':
					return new Symbol(Token.EXP, "^", new Location(line, col));

				// Special symbols, either 1 or 2 long { ! != < <= etc }
				case (char) '!':
				case (char) '=':
				case (char) '<':
				case (char) '>': return checkIfEqualSymbol(current);

				// Building a Character Constant
				case '\'': return buildCharConst(current);

				case '#': openComment();
					continue;

			}

			// Much easier Integer Constant building
			if ( Character.isDigit(current) ) return buildIntConst(current);
			// Word building
			if ( Character.isLetter(current) || (char)current == '_' ) return buildWord(current);

			if (current==32)continue;

			throwError(new Report.Error(new Location(line, col), "LEXICAL ERROR: Illegal character "+(char)current));
		} while (true);

	}



	private int readNext() {
		try {
			col++;
			return srcFile.read();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -2;
	}

	/**
	 * Executes when '!', '=', '<' or '>' is met
	 * These guys can only have an '=' after them, so they're either alone or together
	 *
	 * @param cur Current character we're holding
	 * @return
	 */

	private Symbol checkIfEqualSymbol(int cur) {
		int next = readNext();
		if ((char) next == '=') {
			switch (cur) {
				case (char) '!':
					return new Symbol(Token.NOT_EQUAL, "!=", new Location(line, col-1, line, col));
				case (char) '=':
					return new Symbol(Token.EQUAL, "==", new Location(line, col-1, line, col));
				case (char) '<':
					return new Symbol(Token.LESSEQUAL, "<=", new Location(line, col-1, line, col));
				case (char) '>':
					return new Symbol(Token.GREATEREQUAL, ">=", new Location(line, col-1, line, col));
			}
		} else {
			previouslyBuilt = next;
			switch (cur) {
				case (char) '!':
				return new Symbol(Token.NOT, "!", new Location(line, col-1));
				case (char) '=':
					return new Symbol(Token.ASSIGN, "=", new Location(line, col-1));
				case (char) '<':
					return new Symbol(Token.LESS, "<", new Location(line, col-1));
				case (char) '>':
					return new Symbol(Token.GREATER, ">", new Location(line, col-1));
			}
		}
		throwError(new Report.Error(new Location(line, col), "LEXICAL ERROR: What is that symbol?"));
		return null;
	}

	/**
	 * If we stumble upon a ' , then we build a character
	 * Can throw an error!
	 * @param cur The character we're currently holding, which is '
	 * @return
	 */

	private Symbol buildCharConst(int cur) throws Report.Error{
		int next = readNext();
		String char_const = "'";
		//if (next == (char)'\'') return new Symbol(Token.CHAR_CONST, "''", new Location(line, col));	// Empty character

		if (next == (char)'\\'){	// '\
			next = readNext();
			char_const+="\\";
			if (next == (char)'\'' || next == (char)'\\'){	// '\' or '\\
				char_const += ""+(char)next;
				next = readNext();
				if (next == (char)'\'') return new Symbol(Token.CHAR_CONST, char_const+="\'" , new Location(line, col-char_const.length()+1, line, col));
				else{
					previouslyBuilt=next;
					throwError(new Report.Error(new Location(line, col-1), "LEXICAL ERROR: Character Construction "));
				}
			}else{
				previouslyBuilt=next;
				throwError(new Report.Error(new Location(line, col), "LEXICAL ERROR: Illegal Escape Character. Only ' and \\ are allowed. For now."));

			}
		}

		if (next == 39)
			throwError( new Report.Error(new Location(line, col), "LEXICAL ERROR: Character can't be null"));
		if ( next < 32 || next > 126 )
			throwError( new Report.Error(new Location(line, col), "LEXICAL ERROR: Illegal character"));

		char_const = char_const.concat(""+(char)next);
		next = readNext();
		if ( next != (char)'\'' )
			throwError( new Report.Error(new Location(line, col-2), "LEXICAL ERROR: Character is singular. Either close it, or check the contents.") );
		char_const = char_const.concat("\'");

		return new Symbol(Token.CHAR_CONST, char_const, new Location(line, col-char_const.length()+1, line, col));
	}

	/**
	 * If we get a digit, we keep building until we make a whole number
	 * @param cur Digit we're starting with
	 * @return
	 */

	private Symbol buildIntConst(int cur){
		String int_const = ""+Character.toString(cur);
		int next;
		while(true){
			next = readNext();
			if ( Character.isDigit(next) )
				int_const = int_const.concat(""+Character.toString(next));
			else{
				previouslyBuilt = next;
				return new Symbol(Token.INT_CONST, int_const, new Location(line, col-int_const.length(), line, col-1));
			}

		}
	}

	/**
	 * The MAIN method, build either a Keyword, void constant, pointer constant, or finally an Identifier
	 * @param cur Currently holding a character, which is a letter or an _
	 * @return
	 */

	private Symbol buildWord(int cur){
		checkLegalLetter(cur);

		String word = ""+(char)cur;
		int next;
		while (true){
			next = readNext();
			if ( checkLegalLetter(next) || Character.isDigit(next) || (char)next == '_' )
				word = word.concat(""+(char)next);
			else{
				previouslyBuilt = next;
				break;
			}
		}
		switch (word){
			case "char":
				return new Symbol(Token.CHAR, word, new Location(line, col-word.length(), line, col-1));
			case "del":
				return new Symbol(Token.DEL, word, new Location(line, col-word.length(), line, col-1));
			case "do":
				return new Symbol(Token.DO, word, new Location(line, col-word.length(), line, col-1));
			case "else":
				return new Symbol(Token.ELSE, word, new Location(line, col-word.length(), line, col-1));
			case "end":
				return new Symbol(Token.END, word, new Location(line, col-word.length(), line, col-1));
			case "fun":
				return new Symbol(Token.FUN, word, new Location(line, col-word.length(), line, col-1));
			case "if":
				return new Symbol(Token.IF, word, new Location(line, col-word.length(), line, col-1));
			case "int":
				return new Symbol(Token.INT, word, new Location(line, col-word.length(), line, col-1));
			case "new":
				return new Symbol(Token.NEW, word, new Location(line, col-word.length(), line, col-1));
			case "then":
				return new Symbol(Token.THEN, word, new Location(line, col-word.length(), line, col-1));
			case "typ":
				return new Symbol(Token.TYP, word, new Location(line, col-word.length(), line, col-1));
			case "var":
				return new Symbol(Token.VAR, word, new Location(line, col-word.length(), line, col-1));
			case "void":
				return new Symbol(Token.VOID, word, new Location(line, col-word.length(), line, col-1));
			case "where":
				return new Symbol(Token.WHERE, word, new Location(line, col-word.length(), line, col-1));
			case "while":
				return new Symbol(Token.WHILE, word, new Location(line, col-word.length(), line, col-1));
			case "none":
				return new Symbol(Token.VOID_CONST, word, new Location(line, col-word.length(), line, col-1));
			case "nil":
				return new Symbol(Token.POINT_CONST, word, new Location(line, col-word.length(), line, col-1));
			default:
				return new Symbol(Token.ID, word, new Location(line, col-word.length(), line, col-1));
		}
	}

	private boolean checkLegalLetter(int c){
		if ( Character.isLetter(c) ){
			if ( ((char)c >= 'A' && (char)c <= 'Z') || ((char)c >= 'a' && (char)c <= 'z') )
				return true;
			else
				throwError(new Report.Error(new Location(line, col), "LEXICAL ERROR: Only letters from A-Z are allowed -> "+(char)c));
		}
		return false;
	}


	private void openComment(){
		char temp = (char)current;
		int next = readNext();
		if( (char)next == '{'){
			commentCounter++;
		}else if(commentCounter==0){
			throwError(new Report.Error(new Location(line, col-1), "LEXICAL ERROR: Unknown character "+temp ));
		}
	}

	private Symbol closeComment(boolean flag){
		if (commentCounter==0) return new Symbol(Token.RCB, "}", new Location(line, col));
		int next = readNext();
		if( (char)next == '#')
			commentCounter--;
		else{
			if (flag)return null;
			previouslyBuilt=next;
			return new Symbol(Token.RCB, "}", new Location(line, col-1));
		}
		return null;
	}


	private void lookForComment(){
		int next;
		while(commentCounter!=0){
			next = readNext();
			switch (next){
				case -1: return;
				case 10: newLine(); break;
				case '#': openComment(); break;
				case '}': closeComment(true); break;
			}

		}

	}


	private void newLine(){
		line++;
		col = 0;
	}


	private Report.Error throwError(Report.Error e){
		throw e;
	}


}
