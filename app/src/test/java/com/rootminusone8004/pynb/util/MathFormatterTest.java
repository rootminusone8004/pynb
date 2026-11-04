package com.rootminusone8004.pynb.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MathFormatterTest {

    // ---------------------------------------------------------------- inline math

    @Test
    public void testInlineSingleDollarMathKeptInline() {
        String input = "The formula is $E = mc^2$ and $c = 3 \\times 10^8$.";
        assertEquals(input, MathFormatter.formatMath(input));
    }

    @Test
    public void testGreekLettersInlineMath() {
        String input = "Mean is $\\mu$ and standard deviation is $\\sigma$, with $\\epsilon = 10^{-8}$.";
        assertEquals(input, MathFormatter.formatMath(input));
    }

    @Test
    public void testInlineMathWithUnderscoresAndStars() {
        String input = "Weights $w_i * x_i$ and $a_1 + a_2$ here";
        assertEquals(input, MathFormatter.formatMath(input));
    }

    @Test
    public void testLatexParenthesisDelimiters() {
        String input = "Inline \\( a^2 + b^2 = c^2 \\) formula.";
        assertEquals("Inline $a^2 + b^2 = c^2$ formula.", MathFormatter.formatMath(input));
    }

    @Test
    public void testInlineMathAcrossSoftLineBreakIsFlattened() {
        String input = "Let $a +\n- b$ hold";
        assertEquals("Let $a + - b$ hold", MathFormatter.formatMath(input));
    }

    // ---------------------------------------------------------------- currency / stray dollars

    @Test
    public void testCurrencyEscaped() {
        String input = "The price is $100, or maybe $5.50 or $1,000 or $20,000.";
        assertEquals("The price is \\$100, or maybe \\$5.50 or \\$1,000 or \\$20,000.",
                MathFormatter.formatMath(input));
    }

    @Test
    public void testCurrencyFollowedByMath() {
        String input = "It costs $5 and $x^2$ is math";
        assertEquals("It costs \\$5 and $x^2$ is math", MathFormatter.formatMath(input));
    }

    @Test
    public void testTwoPricesAreNotMath() {
        String input = "between $5 and $10 dollars";
        assertEquals("between \\$5 and \\$10 dollars", MathFormatter.formatMath(input));
    }

    @Test
    public void testEscapedDollarUntouched() {
        String input = "Costs \\$5 and \\$x\\$";
        assertEquals(input, MathFormatter.formatMath(input));
    }

    // ---------------------------------------------------------------- code is never touched

    @Test
    public void testInlineCodeNotConverted() {
        String input = "Use `$x` or `echo $PATH` in bash, but $x = 10$ is math.";
        assertEquals(input, MathFormatter.formatMath(input));
    }

    @Test
    public void testFencedCodeBlockNotConverted() {
        String input = "```python\nx = 1\n$y = 2$\n```\nHere is $f(x) = x^2$ and \\(y\\).";
        assertEquals("```python\nx = 1\n$y = 2$\n```\nHere is $f(x) = x^2$ and $y$.",
                MathFormatter.formatMath(input));
    }

    // ---------------------------------------------------------------- display math

    @Test
    public void testDisplayDoubleDollarOnOwnLineBecomesBlock() {
        String input = "$$\\sum_{i=1}^n x_i$$";
        assertEquals("$$\n\\sum_{i=1}^n x_i\n$$", MathFormatter.formatMath(input));
    }

    @Test
    public void testDisplayDoubleDollarInsideTextStaysInline() {
        String input = "so $$x = 1$$ holds";
        assertEquals(input, MathFormatter.formatMath(input));
    }

    @Test
    public void testDisplayInParagraphIsSplitOut() {
        String input = "We have\n$$ a = b $$\nwhich is true.";
        assertEquals("We have\n$$\na = b\n$$\nwhich is true.", MathFormatter.formatMath(input));
    }

    @Test
    public void testMultilineDisplayWithMarkdownLookalikesAndBlankLines() {
        String input = "$$\\begin{aligned}\na &= b \\\\\n\n- c &= d\n\\end{aligned}$$";
        assertEquals("$$\n\\begin{aligned}\na &= b \\\\\n- c &= d\n\\end{aligned}\n$$",
                MathFormatter.formatMath(input));
    }

    @Test
    public void testLatexBracketDelimiters() {
        String input = "Display \\[ x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a} \\]";
        assertEquals("Display $$x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}$$",
                MathFormatter.formatMath(input));
    }

    @Test
    public void testLatexBracketDelimitersOnOwnLines() {
        String input = "Text\n\\[\nx^2\n\\]\nMore";
        assertEquals("Text\n$$\nx^2\n$$\nMore", MathFormatter.formatMath(input));
    }

    @Test
    public void testLatexEnvironment() {
        String input = "\\begin{equation}\nx + y = z\n\\end{equation}";
        assertEquals("$$\n\\begin{equation}\nx + y = z\n\\end{equation}\n$$", MathFormatter.formatMath(input));
    }

    @Test
    public void testEnvironmentInsideDollarsNotDoubleWrapped() {
        String input = "$$\n\\begin{align}\nx &= 1\n\\end{align}\n$$";
        assertEquals("$$\n\\begin{align}\nx &= 1\n\\end{align}\n$$", MathFormatter.formatMath(input));
    }

    @Test
    public void testLatexAlignedEnvironment() {
        String input = "\\begin{aligned}\na &= b + c \\\\\nd &= e + f\n\\end{aligned}";
        assertEquals("$$\n\\begin{aligned}\na &= b + c \\\\\nd &= e + f\n\\end{aligned}\n$$",
                MathFormatter.formatMath(input));
    }

    @Test
    public void testDisplayInsideListItemKeepsIndentation() {
        String input = "- Loss:\n  $$\n  L = \\sum_i (y_i - \\hat y_i)^2\n  $$\n- Next";
        assertEquals("- Loss:\n  $$\n  L = \\sum_i (y_i - \\hat y_i)^2\n  $$\n- Next",
                MathFormatter.formatMath(input));
    }

    @Test
    public void testDisplayAfterListMarker() {
        String input = "1. $$x^2$$\n2. two";
        // single-line display after a list marker stays as $$...$$ (rendered centred by the plugin)
        assertEquals(input, MathFormatter.formatMath(input));
    }

    @Test
    public void testUnclosedDoubleDollarEscaped() {
        assertEquals("only \\$\\$ here", MathFormatter.formatMath("only $$ here"));
    }

    // ---------------------------------------------------------------- LaTeX normalization

    @Test
    public void testNormalizeStripsEquationAndStars() {
        assertEquals("x = 1", MathFormatter.normalizeLatex("\\begin{equation*} x = 1 \\end{equation*}"));
        assertEquals("\\begin{align}a&=b\\end{align}",
                MathFormatter.normalizeLatex("\\begin{align*}a&=b\\end{align*}"));
    }

    @Test
    public void testNormalizeLabelsTagsAndMacros() {
        assertEquals("x = \\vert y\\vert", MathFormatter.normalizeLatex("x = \\lvert y\\rvert \\label{eq:1}"));
        assertEquals("E \\qquad\\text{(1)}", MathFormatter.normalizeLatex("E \\tag{1}"));
        assertEquals("\\boldsymbol{x} \\colonequals 1", MathFormatter.normalizeLatex("\\bm{x} \\coloneqq 1"));
        assertEquals("a \\\\ b", MathFormatter.normalizeLatex("a \\\\ b \\\\"));
        assertEquals("\\operatorname{argmax}_x", MathFormatter.normalizeLatex("\\operatorname*{argmax}_x"));
    }

    @Test
    public void testNormalizeLeavesKnownCommandsAlone() {
        String latex = "\\vert x \\vert + \\boldsymbol{y} + \\begin{aligned}a\\end{aligned}";
        assertEquals(latex, MathFormatter.normalizeLatex(latex));
    }
}
