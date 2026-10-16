package com.pynb.app.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MathFormatterTest {

    @Test
    public void testInlineSingleDollarMath() {
        String input = "The formula is $E = mc^2$ and $c = 3 \\times 10^8$.";
        String output = MathFormatter.formatMath(input);
        assertEquals("The formula is $$E = mc^2$$ and $$c = 3 \\times 10^8$$.", output);
    }

    @Test
    public void testGreekLettersInlineMath() {
        String input = "Mean is $\\mu$ and standard deviation is $\\sigma$, with $\\epsilon = 10^{-8}$.";
        String output = MathFormatter.formatMath(input);
        assertEquals("Mean is $$\\mu$$ and standard deviation is $$\\sigma$$, with $$\\epsilon = 10^{-8}$$.", output);
    }

    @Test
    public void testBlockDoubleDollarMathPreserved() {
        String input = "$$\\sum_{i=1}^n x_i$$";
        String output = MathFormatter.formatMath(input);
        assertEquals("$$\\sum_{i=1}^n x_i$$", output);
    }

    @Test
    public void testCurrencyNotConverted() {
        String input = "The price is $100, or maybe $5.50 or $1,000 or $20,000.";
        String output = MathFormatter.formatMath(input);
        assertEquals("The price is $100, or maybe $5.50 or $1,000 or $20,000.", output);
    }

    @Test
    public void testInlineCodeNotConverted() {
        String input = "Use `$x` or `echo $PATH` in bash, but $x = 10$ is math.";
        String output = MathFormatter.formatMath(input);
        assertEquals("Use `$x` or `echo $PATH` in bash, but $$x = 10$$ is math.", output);
    }

    @Test
    public void testFencedCodeBlockNotConverted() {
        String input = "```python\nx = 1\n$y = 2$\n```\nHere is $f(x) = x^2$.";
        String output = MathFormatter.formatMath(input);
        assertTrue(output.startsWith("```python\nx = 1\n$y = 2$\n```"));
        assertTrue(output.contains("$$f(x) = x^2$$"));
    }

    @Test
    public void testLatexParenthesisDelimiters() {
        String input = "Inline \\( a^2 + b^2 = c^2 \\) formula.";
        String output = MathFormatter.formatMath(input);
        assertEquals("Inline $$ a^2 + b^2 = c^2 $$ formula.", output);
    }

    @Test
    public void testLatexBracketDelimiters() {
        String input = "Display \\[ x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a} \\]";
        String output = MathFormatter.formatMath(input);
        assertTrue(output.contains("$$\n x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a} \n$$"));
    }

    @Test
    public void testLatexEnvironment() {
        String input = "\\begin{equation}\nx + y = z\n\\end{equation}";
        String output = MathFormatter.formatMath(input);
        assertTrue(output.contains("$$\n\\begin{equation}\nx + y = z\n\\end{equation}\n$$"));
    }

    @Test
    public void testLatexAlignedEnvironment() {
        String input = "\\begin{aligned}\na &= b + c \\\\\nd &= e + f\n\\end{aligned}";
        String output = MathFormatter.formatMath(input);
        assertTrue(output.contains("$$\n\\begin{aligned}\na &= b + c \\\\\nd &= e + f\n\\end{aligned}\n$$"));
    }
}
