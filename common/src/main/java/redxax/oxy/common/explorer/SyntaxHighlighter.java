package redxax.oxy.common.explorer;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMaker;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import javax.swing.text.Segment;

public class SyntaxHighlighter {

    public static Text highlight(String line, String fileName) {
        String syntaxStyle = getSyntaxStyle(fileName);
        TokenMakerFactory factory = TokenMakerFactory.getDefaultInstance();
        TokenMaker tokenMaker = factory.getTokenMaker(syntaxStyle);
        Segment segment = new Segment(line.toCharArray(), 0, line.length());
        Token token = tokenMaker.getTokenList(segment, 0, line.length());
        MutableText mutableText = Text.literal("");
        while (token != null && token.isPaintable()) {
            String tokenText = token.getLexeme();
            int tokenType = token.getType();
            int color;
            if (tokenType == Token.COMMENT_EOL || tokenType == Token.COMMENT_MULTILINE || tokenType == Token.COMMENT_DOCUMENTATION) {
                color = 0x6A9955;
            } else if (tokenType == Token.LITERAL_STRING_DOUBLE_QUOTE) {
                color = 0xCE9178;
            } else if (tokenType == Token.DATA_TYPE || tokenType == Token.RESERVED_WORD) {
                color = 0xC586C0;
            } else if (tokenType == Token.LITERAL_NUMBER_DECIMAL_INT || tokenType == Token.LITERAL_NUMBER_FLOAT) {
                color = 0xB5CEA8;
            } else if (tokenType == Token.FUNCTION || tokenType == Token.VARIABLE) {
                color = 0x9CDCFE;
            } else {
                color = 0xFFFFFF;
            }
            mutableText.append(Text.literal(tokenText).styled(s -> s.withColor(color)));
            token = token.getNextToken();
        }
        return mutableText;
    }

    private static String getSyntaxStyle(String fileName) {
        String ext = getExtension(fileName).toLowerCase();
        return switch (ext) {
            case "java" -> SyntaxConstants.SYNTAX_STYLE_JAVA;
            case "js" -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
            case "ts" -> SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT;
            case "cpp", "c" -> SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS;
            case "cs" -> SyntaxConstants.SYNTAX_STYLE_CSHARP;
            case "py" -> SyntaxConstants.SYNTAX_STYLE_PYTHON;
            case "json" -> SyntaxConstants.SYNTAX_STYLE_JSON;
            case "yaml", "yml", "toml" -> SyntaxConstants.SYNTAX_STYLE_YAML;
            case "properties" -> SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE;
            case "xml" -> SyntaxConstants.SYNTAX_STYLE_XML;
            case "html", "htm" -> SyntaxConstants.SYNTAX_STYLE_HTML;
            case "css" -> SyntaxConstants.SYNTAX_STYLE_CSS;
            case "sql" -> SyntaxConstants.SYNTAX_STYLE_SQL;
            case "bat", "cmd" -> SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH;
            case "sh", "bash" -> SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL;
            case "php" -> SyntaxConstants.SYNTAX_STYLE_PHP;
            case "go" -> SyntaxConstants.SYNTAX_STYLE_GO;
            case "kotlin" -> SyntaxConstants.SYNTAX_STYLE_KOTLIN;
            case "rust" -> SyntaxConstants.SYNTAX_STYLE_RUST;
            case "markdown" -> SyntaxConstants.SYNTAX_STYLE_MARKDOWN;
            case "dockerfile" -> SyntaxConstants.SYNTAX_STYLE_DOCKERFILE;
            case "ini" -> SyntaxConstants.SYNTAX_STYLE_INI;
            case "csv" -> SyntaxConstants.SYNTAX_STYLE_CSV;
            case "lua" -> SyntaxConstants.SYNTAX_STYLE_LUA;
            case "scala" -> SyntaxConstants.SYNTAX_STYLE_SCALA;
            case "vhdl" -> SyntaxConstants.SYNTAX_STYLE_VHDL;
            case "groovy" -> SyntaxConstants.SYNTAX_STYLE_GROOVY;
            case "clojure" -> SyntaxConstants.SYNTAX_STYLE_CLOJURE;
            case "dart" -> SyntaxConstants.SYNTAX_STYLE_DART;
            default -> SyntaxConstants.SYNTAX_STYLE_NONE;
        };
    }

    private static String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1);
    }
}