package com.xlythe.calculator.material;

public class Solver {
    public static boolean equal(String a, String b) {
        return clean(a).equals(clean(b));
    }

    public static String clean(String equation) {
        return equation
                .replace('-', Constants.MINUS)
                .replace('/', Constants.DIV)
                .replace('*', Constants.MUL)
                .replace(Constants.INFINITY, Constants.INFINITY_UNICODE);
    }

    public static boolean isOperator(char c) {
        return ("" +
                Constants.PLUS +
                Constants.MINUS +
                Constants.DIV +
                Constants.MUL +
                Constants.POWER).indexOf(c) != -1;
    }

    public static boolean isNegative(String number) {
        return number.startsWith(String.valueOf(Constants.MINUS)) || number.startsWith("-");
    }

    public static boolean isDigit(char number) {
        return Character.isDigit(number);
    }

    public static boolean isOperator(String c) {
        return isOperator(c.charAt(0));
    }
}
