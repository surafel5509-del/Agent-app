package com.nexa.ai.core.markdown

/** Token categories produced by [SyntaxHighlighter]. */
enum class TokenType {
    PLAIN,
    KEYWORD,
    STRING,
    NUMBER,
    COMMENT,
    FUNCTION,
    TYPE,
    ANNOTATION,
    PROPERTY,
    PUNCTUATION,
}

data class HighlightToken(val text: String, val type: TokenType)

/**
 * Regex-driven, dependency-free syntax tokenizer.
 *
 * It intentionally covers the languages an agent is most likely to emit
 * (Kotlin, Java, Python, JS/TS, Go, Rust, Swift, C/C++, SQL, shell, JSON, YAML, HTML)
 * and degrades gracefully to plain text for anything else.
 */
object SyntaxHighlighter {

    private val keywords: Map<String, Set<String>> = mapOf(
        "kotlin" to setOf(
            "as", "break", "by", "class", "continue", "do", "else", "false", "for", "fun",
            "if", "in", "interface", "internal", "is", "null", "object", "override", "package",
            "private", "protected", "public", "return", "super", "this", "throw", "true", "try",
            "val", "var", "when", "while", "data", "sealed", "suspend", "inline", "operator",
            "companion", "import", "typealias", "where", "get", "set", "it",
        ),
        "java" to setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null",
        ),
        "python" to setOf(
            "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
            "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in",
            "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while",
            "with", "yield", "True", "False", "None", "print",
        ),
        "javascript" to setOf(
            "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger",
            "default", "delete", "do", "else", "export", "extends", "finally", "for", "function",
            "if", "import", "in", "instanceof", "let", "new", "of", "return", "super", "switch",
            "this", "throw", "try", "typeof", "var", "void", "while", "with", "yield", "true",
            "false", "null", "undefined",
        ),
        "typescript" to setOf(
            "abstract", "any", "as", "async", "await", "break", "case", "catch", "class", "const",
            "continue", "declare", "default", "delete", "do", "else", "enum", "export", "extends",
            "finally", "for", "from", "function", "if", "implements", "import", "in", "interface",
            "instanceof", "is", "keyof", "let", "namespace", "new", "of", "private", "protected",
            "public", "readonly", "return", "satisfies", "static", "super", "switch", "this",
            "throw", "try", "type", "typeof", "unknown", "var", "while", "yield",
        ),
        "go" to setOf(
            "break", "case", "chan", "const", "continue", "default", "defer", "else",
            "fallthrough", "for", "func", "go", "goto", "if", "import", "interface", "map",
            "package", "range", "return", "select", "struct", "switch", "type", "var", "nil",
        ),
        "rust" to setOf(
            "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else", "enum",
            "extern", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod", "move", "mut",
            "pub", "ref", "return", "self", "Self", "static", "struct", "super", "trait", "type",
            "unsafe", "use", "where", "while", "true", "false",
        ),
        "swift" to setOf(
            "associatedtype", "async", "await", "break", "case", "catch", "class", "continue",
            "default", "defer", "do", "else", "enum", "extension", "fallthrough", "fileprivate",
            "for", "func", "guard", "if", "import", "in", "init", "inout", "internal", "is", "let",
            "open", "operator", "private", "protocol", "public", "repeat", "rethrows", "return",
            "self", "Self", "static", "struct", "switch", "throw", "throws", "try", "var", "where",
            "while", "true", "false", "nil",
        ),
        "c" to setOf(
            "auto", "break", "case", "char", "const", "continue", "default", "do", "double",
            "else", "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long",
            "register", "return", "short", "signed", "sizeof", "static", "struct", "switch",
            "typedef", "union", "unsigned", "void", "volatile", "while", "NULL",
        ),
        "cpp" to setOf(
            "alignas", "auto", "break", "case", "catch", "class", "const", "constexpr", "continue",
            "decltype", "default", "delete", "do", "else", "enum", "explicit", "extern", "for",
            "friend", "if", "inline", "namespace", "new", "noexcept", "nullptr", "operator",
            "override", "private", "protected", "public", "return", "sizeof", "static", "struct",
            "switch", "template", "this", "throw", "try", "typedef", "typename", "union",
            "using", "virtual", "void", "while", "true", "false",
        ),
        "sql" to setOf(
            "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
            "create", "table", "alter", "drop", "index", "view", "join", "inner", "left", "right",
            "outer", "full", "on", "group", "by", "order", "having", "limit", "offset", "as",
            "and", "or", "not", "null", "primary", "key", "foreign", "references", "unique",
            "default", "constraint", "union", "all", "distinct", "case", "when", "then", "else",
            "end", "exists", "between", "like", "in", "is", "asc", "desc",
        ),
        "bash" to setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
            "function", "in", "select", "until", "return", "exit", "export", "local", "readonly",
            "set", "unset", "source", "echo", "cd", "sudo", "curl", "wget",
        ),
        "json" to setOf("true", "false", "null"),
        "yaml" to setOf("true", "false", "null", "yes", "no", "on", "off"),
    )

    private val aliases: Map<String, String> = mapOf(
        "kt" to "kotlin",
        "kts" to "kotlin",
        "py" to "python",
        "js" to "javascript",
        "jsx" to "javascript",
        "mjs" to "javascript",
        "cjs" to "javascript",
        "ts" to "typescript",
        "tsx" to "typescript",
        "rs" to "rust",
        "sh" to "bash",
        "shell" to "bash",
        "zsh" to "bash",
        "console" to "bash",
        "yml" to "yaml",
        "md" to "markdown",
        "html" to "xml",
        "xml" to "xml",
        "scss" to "css",
        "css" to "css",
    )

    fun tokenize(code: String, language: String?): List<HighlightToken> {
        if (code.isEmpty()) return emptyList()
        val normalized = language?.trim()?.lowercase()?.ifBlank { null }
        val resolved = normalized?.let { aliases[it] ?: it }

        return when (resolved) {
            "json" -> tokenizeJson(code)
            "xml" -> tokenizeXml(code)
            "css" -> tokenizeCss(code)
            else -> tokenizeGeneric(code, resolved)
        }
    }

    private fun tokenizeGeneric(code: String, language: String?): List<HighlightToken> {
        val words = language?.let { keywords[it] } ?: keywords["kotlin"].orEmpty()
        val lineComment = when (language) {
            "python", "bash", "yaml", "sh" -> "#[^\\n]*"
            "sql" -> "--[^\\n]*"
            else -> "//[^\\n]*"
        }
        val pattern = buildString {
            append("(?<comment>")
            append(lineComment)
            append("|/\\*[\\s\\S]*?\\*/")
            append("|<!--[^\\n]*?-->")
            append(")")
            append("|(?<string>\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'|`(?:\\\\.|[^`\\\\])*`)")
            append("|(?<annotation>@[A-Za-z_][\\w.]*|#\\w+)")
            append(if (language == "sql") "|(?<keyword>\\b(?i:(?:" else "|(?<keyword>\\b(?:(?:")
            append(words.joinToString("|") { Regex.escape(it) })
            append("))\\b)")
            append("|(?<number>\\b0[xX][0-9a-fA-F]+\\b|\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFlLuU]*\\b)")
            append("|(?<function>\\b[A-Za-z_][\\w$]*(?=\\s*\\())")
            append("|(?<type>\\b[A-Z][\\w$]*\\b)")
            append("|(?<punctuation>[{}()\\[\\];,.<>=+\\-*/%!&|?:~^])")
        }

        val regex = try {
            Regex(pattern)
        } catch (exception: Exception) {
            return listOf(HighlightToken(code, TokenType.PLAIN))
        }

        val tokens = ArrayList<HighlightToken>()
        var cursor = 0
        for (match in regex.findAll(code)) {
            val range = match.range
            if (range.first > cursor) {
                tokens += HighlightToken(code.substring(cursor, range.first), TokenType.PLAIN)
            }
            val type = when {
                match.groups["comment"] != null -> TokenType.COMMENT
                match.groups["string"] != null -> TokenType.STRING
                match.groups["annotation"] != null -> TokenType.ANNOTATION
                match.groups["keyword"] != null -> TokenType.KEYWORD
                match.groups["number"] != null -> TokenType.NUMBER
                match.groups["function"] != null -> TokenType.FUNCTION
                match.groups["type"] != null -> TokenType.TYPE
                match.groups["punctuation"] != null -> TokenType.PUNCTUATION
                else -> TokenType.PLAIN
            }
            tokens += HighlightToken(match.value, type)
            cursor = range.last + 1
        }
        if (cursor < code.length) {
            tokens += HighlightToken(code.substring(cursor), TokenType.PLAIN)
        }
        return tokens
    }

    private fun tokenizeJson(code: String): List<HighlightToken> {
        val regex = Regex(
            "(?<comment>//[^\\n]*|/\\*[\\s\\S]*?\\*/)" +
                "|(?<property>\"(?:\\\\.|[^\"\\\\])*\"\\s*(?=:))" +
                "|(?<string>\"(?:\\\\.|[^\"\\\\])*\")" +
                "|(?<keyword>\\b(?:true|false|null)\\b)" +
                "|(?<number>-?\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b)" +
                "|(?<punctuation>[{}\\[\\],:])",
        )
        return simpleTokens(code, regex)
    }

    private fun tokenizeXml(code: String): List<HighlightToken> {
        val regex = Regex(
            "(?<comment><!--[\\s\\S]*?-->)" +
                "|(?<string>\"[^\"\\n]*\"|'[^'\\n]*')" +
                "|(?<keyword></?[A-Za-z][\\w:.-]*|/?>)" +
                "|(?<property>\\b[A-Za-z_:][\\w:.-]*(?=\\s*=))" +
                "|(?<annotation><!DOCTYPE[^>]*>)" +
                "|(?<punctuation>[<>=/])",
        )
        return simpleTokens(code, regex)
    }

    private fun tokenizeCss(code: String): List<HighlightToken> {
        val regex = Regex(
            "(?<comment>/\\*[\\s\\S]*?\\*/)" +
                "|(?<string>\"[^\"\\n]*\"|'[^'\\n]*')" +
                "|(?<annotation>@[A-Za-z-]+)" +
                "|(?<property>\\b[a-zA-Z-]+(?=\\s*:))" +
                "|(?<number>-?\\b\\d+(?:\\.\\d+)?(?:px|em|rem|%|vh|vw|s|ms)?\\b)" +
                "|(?<function>\\b[a-zA-Z-]+(?=\\s*\\())" +
                "|(?<punctuation>[{}();,:])",
        )
        return simpleTokens(code, regex)
    }

    private fun simpleTokens(code: String, regex: Regex): List<HighlightToken> {
        val tokens = ArrayList<HighlightToken>()
        var cursor = 0
        for (match in regex.findAll(code)) {
            val range = match.range
            if (range.first > cursor) {
                tokens += HighlightToken(code.substring(cursor, range.first), TokenType.PLAIN)
            }
            val type = when {
                match.groups["comment"] != null -> TokenType.COMMENT
                match.groups["string"] != null -> TokenType.STRING
                match.groups["annotation"] != null -> TokenType.ANNOTATION
                match.groups["property"] != null -> TokenType.PROPERTY
                match.groups["keyword"] != null -> TokenType.KEYWORD
                match.groups["number"] != null -> TokenType.NUMBER
                match.groups["function"] != null -> TokenType.FUNCTION
                match.groups["type"] != null -> TokenType.TYPE
                match.groups["punctuation"] != null -> TokenType.PUNCTUATION
                else -> TokenType.PLAIN
            }
            tokens += HighlightToken(match.value, type)
            cursor = range.last + 1
        }
        if (cursor < code.length) {
            tokens += HighlightToken(code.substring(cursor), TokenType.PLAIN)
        }
        return tokens
    }
}
