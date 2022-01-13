package com.ath0.rpn;
import ch.obermuhlner.math.big.BigDecimalMath;

import android.util.Log;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Stack;

@FunctionalInterface
interface Function3<One, Two, Three> {
  public Three apply(One one, Two two);
}
@FunctionalInterface
interface Function4<One, Two, Three, Four> {
  public Four apply(One one, Two two, Three three);
}

/**
 * Model for RPN calculator. 
 * Implements a stack and a set of typical operations on it.
 * Some operations can cause arithmetic exceptions; in those cases, a String 
 * is used to return an error message, and null indicates no error. This
 * violation of good style allows the calling controller to handle operations 
 * uniformly with no knowledge of mathematics, and rely on the stack object's
 * operation method to supply the appropriate error message.
 */
public class CalculatorStack implements Serializable {

  public boolean bin = false;
  private static final char[] CH_ZEROS = new char[100];

  /**
   * Object version for serialization.
   */
  private static final long serialVersionUID = 1L;

  // Number of characters to preallocate when converting a stack value into a 
  // string or storing input from user.
  private static final int TYPICAL_LENGTH = 32;
  // 4x the above
  private static final int TYPICAL_LENGTH_X4 = 128;

  // How many digits of precision (decimal places) are used internally in 
  // calculations.
  private static final int INTERNAL_SCALE = 32;

  private final Stack<BigDecimal> stack;
  public BigDecimal LastX = null;

  // Initial scale is 2 decimal places, as that's the most useful for general 
  // everyday calculations.
  private int scale = 2;

  //
  private BigInteger intVal = null;
  private transient long smallValue;


  public CalculatorStack() {
    super();
    this.stack = new Stack<BigDecimal>();
    Arrays.fill(CH_ZEROS, '0');
  }

  /**
   * Pushes a value onto the stack.
   * @param number A valid decimal number, in a String. Usually taken from the 
   * InputBuffer.
   */
  public void push(final String number) {
    try {
      final BigDecimal newnum = new BigDecimal(number);

      if(bin) {
        this.stack.push(bitStringToBigDecimal(number));
      } else
      {
        this.stack.push(new BigDecimal(number));
      }
    } catch (RuntimeException e) {
      //result = e.getMessage();
    }
  }

  /**
   * Push last x onto stack
   */
  public void PushLastX()
  {
    if(LastX != null) {
      this.stack.push(LastX);
    }
  }

  /**
   * Returns whether the stack is empty.
   */
  public boolean isEmpty() {
    return this.stack.isEmpty();
  }

  /**
   * Gets the contents of the stack as a string.
   * @param levels the number of levels of stack to return
   * @return a text representation of the stack
   */
  public StringBuilder toString(final int levels) {
    final StringBuilder result = new StringBuilder(TYPICAL_LENGTH_X4);
    if (this.stack != null) {
      final int depth = this.stack.size();
      for (int i = 0; i < levels; i++) {
        if (i != 0) {
          result.append('\n');
        }
        final int idx = depth - levels + i;
        if (idx >= 0) {
          result.append(formatNumber(this.stack.get(idx)));
        }
      }
    }
    return result;
  }

  /**
   * Get value without thousands commas for unit tests, to avoid needing to
   * implement formatNumber there.
   */
  @Override
  public String toString() {
    return this.toString(1).toString().replaceAll(",", "");
  }

  /**
   * Formats a BigDecimal number to a fixed number of decimal places, and adds 
   * thousands commas.
   * @param number
   * @return
   */
  private String formatNumber(final BigDecimal number) {
    if(bin) {
      return binFormat(number);
    } else {
      //return decFormat(number);
      return engFormat(number);
    }
  }

  private String engFormat(BigDecimal number)
  {
    final StringBuilder result = new StringBuilder(TYPICAL_LENGTH);
    result.append(toEngineeringString(number.setScale(this.scale, RoundingMode.HALF_UP)));
    return result.toString();
  }

  private String decFormat(BigDecimal number) {
    final StringBuilder result = new StringBuilder(TYPICAL_LENGTH);
    result.append(number.setScale(this.scale, RoundingMode.HALF_UP).toPlainString());
    if (this.scale > 0) {
      if (result.indexOf(".") == -1) {
        result.append('.');
      }
      final int zerosAfterPoint = result.length() - result.indexOf(".") - 1;
      for (int i = zerosAfterPoint; i < this.scale; i++) {
        result.append('0');
      }
    }
    //Add commas
    int dot = result.indexOf(".");
    if (dot < 1) {
      dot = result.length();
    }
    int lowindex = 0;
    if (result.charAt(0) == '-') {
      lowindex = 1;
    }
    return result.toString();
  }

  private String binFormat(BigDecimal number) {
    final StringBuilder result = new StringBuilder(TYPICAL_LENGTH);
    result.append(number.toBigInteger().toString(2));
    return nibble(result);
  }

  private BigInteger getUnscaledValue(BigDecimal bigD) {
    BigInteger i = bigD.unscaledValue();
    return i;
  }

  public String toEngineeringString(BigDecimal bigD) {
    Arrays.fill(CH_ZEROS, '0');

    String intString = getUnscaledValue(bigD).toString();
    if(bigD.unscaledValue().intValue() == 0)
      return "0";

    Log.d("BigDecimal tostring", bigD.toString());
    Log.d("intstring:",intString);
    Log.d("scale", Integer.toString(bigD.scale()));
    int begin = (getUnscaledValue(bigD).signum() < 0) ? 2 : 1;
    int end = intString.length();
    int iii = bigD.scale();
    long exponent = -(long)(bigD.scale()) + end - begin;
    StringBuilder result = new StringBuilder(intString);

    if ((bigD.scale() > 0) && (exponent >= 0)) {
      if (exponent >= 0) {
        //result.insert(end - bigD.scale()-(int)exponent, '.');
        //end++;
      } else {
        result.insert(begin - 1, "0.");
        result.insert(begin + 1, CH_ZEROS, 0, -(int)exponent - 1);
      }
    } //else {
      int delta = end - begin;
      int rem = (int)(exponent % 3);

      if (rem != 0) {
        // adjust exponent so it is a multiple of three
        if (getUnscaledValue(bigD).signum() == 0) {
          // zero value
          rem = (rem < 0) ? -rem : 3 - rem;
          exponent += rem;
        } else {
          // nonzero value
          rem = (rem < 0) ? rem + 3 : rem;
          exponent -= rem;
          begin += rem;
        }
        if (delta < 3) {
          //for (int i = rem - delta; i > 0; i--) {
          //  result.insert(end++, '0');
          //
        }
      }
      if (end - begin >= 1) {
        result.insert(begin, '.');
        int l = result.length();
        int startDel = begin+bigD.scale();

        if(startDel < l) {
          result = result.delete(startDel, l - 1);
        }


        while(result.charAt(result.length()-1) == '0')
        {
          Log.d("", "0");
          result.deleteCharAt(result.length()-1);
        }
        if(result.charAt(result.length()-1) == '.')
          result.deleteCharAt(result.length()-1);

        end++;
      }

      if (exponent != 0) {
        result.insert(result.length(), 'E');
        if (exponent > 0) {
          result.insert(result.length(), '+');
        }
        result.insert(result.length(), Long.toString(exponent));
      }
    return result.toString();
  }

  private String nibble(StringBuilder result) {
    //Check how much padding is needed
    //If zero no padding needed
    int padding = 4 - (result.length() % 4);
    if(padding != 4)
    {
      for(int i = 0; i < padding; i++) {
        result.insert(0, "0");
      }
    }
    return result.toString().replaceAll(("[0-9A-F]{4}"), "$0 ").trim();
  }

  /**
   * Changes the sign of the top number on the stack.
   */
  public void chs() {
    if (!this.stack.isEmpty()) {
      final BigDecimal topnum = this.stack.pop();
      this.stack.push(topnum.negate());
    }
  }

  /**
   * Drops the top element from the stack.
   */
  public void drop() {
    if (!this.stack.isEmpty()) {
      this.stack.pop();
    }
  }

  /**
   * Duplicates the top element on the stack.
   */
  public void dup() {
    if (!this.stack.isEmpty()) {
      final BigDecimal topnum = this.stack.peek();
      this.stack.push(topnum);
    }
  }

  /**
   * Swaps the top two elements on the stack.
   */
  public void swap() {
    if (this.stack.size() > 1) {
      final BigDecimal x = this.stack.pop();
      final BigDecimal y = this.stack.pop();
      this.stack.push(x);
      this.stack.push(y);
    }
  }

  /**
   * Adds together the top two elements on the stack, and replaces them with
   * the result.
   */
  public void add() {
    if (this.stack.size() > 1) {
      final BigDecimal x = StoreLastX(this.stack.pop());
      final BigDecimal y = this.stack.pop();
      final BigDecimal r = y.add(x);
      this.stack.push(r);
    }
  }

  /**
   * Subtracts the top number on the stack from the number beneath it, and 
   * replaces them both with the result.
   */
  public void subtract() {
    if (this.stack.size() > 1) {
      BigDecimal x = StoreLastX(this.stack.pop());
      BigDecimal y = this.stack.pop();
      BigDecimal r = y.subtract(x);
      this.stack.push(r);
    }
  }

  private BigDecimal StoreLastX(BigDecimal x)
  {
    LastX = x;
    return x;
  }

  /**
   * Multiplies the top two numbers on the stack together, and replaces them 
   * with the result.
   */
  public void multiply() {
    if (this.stack.size() > 1) {
      BigDecimal x = StoreLastX(this.stack.pop());
      BigDecimal y = this.stack.pop();
      BigDecimal r = y.multiply(x);
      this.stack.push(r);
    }
  }

  public String percent()
  {
    String result = null;
    if (this.stack.size() > 1) {
      try
      {
        BigDecimal r;
        BigDecimal x = StoreLastX(this.stack.pop());
        BigDecimal y = this.stack.pop();

        r = y.divide(new BigDecimal(100)).multiply(x);
        this.stack.push(r);
      }
      catch (ArithmeticException ex)
      {
        result = ex.getMessage();
      }
      catch (RuntimeException ex)
      {
        result = ex.getMessage();
      }
    }
    return result;
  }

  public String sin() { return ExecuteFunction3((x, y) -> { return BigDecimalMath.sin(x,y); }); }
  public String asin() { return ExecuteFunction3((x, y) -> { return BigDecimalMath.asin(x,y); }); }
  public String cos()
  {
    return ExecuteFunction3((x, y) -> { return BigDecimalMath.cos(x,y); });
  }
  public String acos()
  {
    return ExecuteFunction3((x, y) -> { return BigDecimalMath.acos(x,y); });
  }
  public String tan()
  {
    return ExecuteFunction3((x, y) -> { return BigDecimalMath.tan(x,y); });
  }
  public String atan()
  {
    return ExecuteFunction3((x, y) -> { return BigDecimalMath.atan(x,y); });
  }

  public String ln()
  {
    return ExecuteFunction3((x, y) -> { return BigDecimalMath.log(x,y); });
  }
  public String log10()
  {
    return ExecuteFunction3((x, y) -> { return BigDecimalMath.log10(x,y); });
  }
  public String log2()
  {
    return ExecuteFunction3((x, y) -> { return BigDecimalMath.log2(x,y); });
  }
  public String pow()
  {
    return ExecuteFunction4((x,y,z) -> { return BigDecimalMath.pow(x,y,z); });
  }
  private String ExecuteFunction3(Function3<BigDecimal, MathContext,  BigDecimal> func3)
  {
    String result = null;
    if (this.stack.size() >= 1) {
      try
      {
        BigDecimal x = StoreLastX(this.stack.pop());

        MathContext mathContext = new MathContext(this.scale);

        BigDecimal r;
        r = func3.apply(x, mathContext);
        this.stack.push(r);
      }
      catch (ArithmeticException ex)
      {
        result = ex.getMessage();
      }
      catch (RuntimeException ex)
      {
        result = ex.getMessage();
      }
    }
    return result;
  }

  private String ExecuteFunction4(Function4<BigDecimal, BigDecimal, MathContext,  BigDecimal> func4)
  {
    String result = null;
    if (this.stack.size() > 1) {
      try
      {
        BigDecimal y = StoreLastX(this.stack.pop());
        BigDecimal x = this.stack.pop();


        MathContext mathContext = new MathContext(this.scale);

        BigDecimal r;
        r = func4.apply(x,y, mathContext);
        this.stack.push(r);
      }
      catch (ArithmeticException ex)
      {
        result = ex.getMessage();
      }
      catch (RuntimeException ex)
      {
        result = ex.getMessage();
      }
    }
    return result;
  }

  public String modulo()
  {
    String result = null;
    if (this.stack.size() > 1) {
      try
      {
        BigDecimal r;
        BigDecimal x = StoreLastX(this.stack.pop());
        BigDecimal y = this.stack.pop();

        r = y.remainder(x);
        this.stack.push(r);
      }
      catch (ArithmeticException ex)
      {
        result = ex.getMessage();
      }
      catch (RuntimeException ex)
      {
        result = ex.getMessage();
      }
    }
    return result;
  }
  
  /**
   * Takes the top item on the stack, and uses its integer value as the power
   * for raising the number beneath it.
   * e.g. before:  X Y  after: X^Y   before: 2 3  after: 8
   * @return an error message, or null if there is no error
   */
  // Returns error message, or null if no error.
  public String power() {
    String result = null;
    if (this.stack.size() > 1) {
      BigDecimal y = StoreLastX(this.stack.pop());
      BigDecimal x = this.stack.pop();

      try {
        BigDecimal r;
        try {
          // Try an exact approach first
          int yi = y.intValueExact();
          Log.d("power", "Computed power exactly");
          r = x.pow(yi);
        } catch (ArithmeticException ex) {
          // If we can't compute it exactly, compute an approximate value
          r = approxPow(x,y);
          Log.d("power", "Computed power approximately");
        }
        this.stack.push(r);
      } catch (RuntimeException e) {
        result = e.getMessage();
      }
    }
    return result;
  }

  /**
   * Uses the top number on the stack to divide the number beneath it.
   * Replaces both with the result of the division.
   * e.g. before: x y  after: x/y   before:  4 2  after: 2
   * @return an error message, or null if there is no error
   */
  public String divide() {
    String result = null;
    if (this.stack.size() > 1) {
      BigDecimal x = StoreLastX(this.stack.pop());
      BigDecimal y = this.stack.pop();
      // We use HALF_EVEN rounding because this statistically minimizes 
      // cumulative error during repeated calculations.
      try {
        BigDecimal r = y.divide(x, INTERNAL_SCALE,
            RoundingMode.HALF_EVEN);
        this.stack.push(r);
      } catch (ArithmeticException e) {
        result = e.getMessage();
      }
    }
    return result;
  }

  public String and() {
    String result = null;
    if (this.stack.size() > 1) {
      BigInteger x = StoreLastX(this.stack.pop()).toBigInteger();
      BigInteger y = this.stack.pop().toBigInteger();
      // We use HALF_EVEN rounding because this statistically minimizes
      // cumulative error during repeated calculations.
      try {
        BigInteger r = y.and(x);
        this.stack.push(new BigDecimal(r));
      } catch (ArithmeticException e) {
        result = e.getMessage();
      }
    }
    return result;
  }

  public String or() {
    String result = null;
    if (this.stack.size() > 1) {
      BigInteger x = StoreLastX(this.stack.pop()).toBigInteger();
      BigInteger y = this.stack.pop().toBigInteger();
      // We use HALF_EVEN rounding because this statistically minimizes
      // cumulative error during repeated calculations.
      try {
        BigInteger r = y.or(x);
        this.stack.push(new BigDecimal(r));
      } catch (ArithmeticException e) {
        result = e.getMessage();
      }
    }
    return result;
  }

  /**
   * Computes the reciprocal of the top element on the stack, and replaces it
   * with the result.
   * @return an error message, or null if there is no error
   */
  public String reciprocal() {
    String result = null;
    if (!this.stack.isEmpty()) {
      BigDecimal x = StoreLastX(this.stack.pop());
      try {
        BigDecimal y = BigDecimal.ONE.divide(x, INTERNAL_SCALE, 
            RoundingMode.HALF_EVEN);
        this.stack.push(y);
      } catch (ArithmeticException e) {
        result = e.getMessage();
      }
    }
    return result;
  }

  /**
   * Sets the display scale, in decimal places.
   * Computation is always performed to the INTERNAL_SCALE.
   * @param newscale new scale value
   */
  public void setScale(final int newscale) {
    this.scale = newscale;
  }

  /**
   * Sets the display scale to the integer value of the top element on the
   * stack, as long as that value is less than the INTERNAL_SCALE.
   */
  public void setScale() {
    if (!this.stack.isEmpty()) {
      BigDecimal x = StoreLastX(this.stack.pop());
      int sc = x.intValue();
      if (sc < INTERNAL_SCALE) {
        setScale(sc);
      }
    }
  }

  /**
   * Gets the current display scale.
   * @return
   */
  public int getScale() {
    return this.scale;
  }

  /**
   * Computes the square root of the value on the top of the stack, and
   * replaces that value with the result.
   */
  public String sqrt() {
    String result = null;

    if (!this.stack.isEmpty()) {
      try {
        BigDecimal x = sqrt(StoreLastX(this.stack.pop()), INTERNAL_SCALE);
        this.stack.push(x);
      } catch (RuntimeException e) {
        result = e.getMessage();
      }
    }
    return result;
  }

  /**
   * Computes the square of the value on the top of the stack, and
   * replaces that value with the result.
   */
  public String sqr() {
    String result = null;

    if (!this.stack.isEmpty()) {
      try {
        BigDecimal x = StoreLastX(this.stack.pop());
        this.stack.push(x.multiply(x));
      } catch (RuntimeException e) {
        result = e.getMessage();
      }
    }
    return result;
  }

  /**
   * Computes the square root of x to a given scale, x >= 0.
   * Use Newton's algorithm.
   * Taken from "Java Number Cruncher: The Java Programmer's Guide to 
   * Numerical Computing" (Ronald Mak, 2003) http://goo.gl/CXpi2
   * @param x the value of x
   * @param scale the desired scale of the result
   * @return the result value
   */
  private static BigDecimal sqrt(final BigDecimal x, final int scale)
  {
    // Check that x >= 0.
    if (x.signum() < 0) {
      throw new IllegalArgumentException("x < 0");
    }
    if (x.signum() == 0) {
      return BigDecimal.ZERO;
    }

    // n = x*(10^(2*scale))
    BigInteger n = x.movePointRight(scale << 1).toBigInteger();

    // The first approximation is the upper half of n.
    int bits = (n.bitLength() + 1) >> 1;
    BigInteger ix = n.shiftRight(bits);
    BigInteger ixPrev;

    // Loop until the approximations converge
    // (two successive approximations are equal after rounding).
    do {
      ixPrev = ix;

      // x = (x + n/x)/2
      ix = ix.add(n.divide(ix)).shiftRight(1);

      Thread.yield();
    } while (ix.compareTo(ixPrev) != 0);

    return new BigDecimal(ix, scale);
  }

  /**
   * Compute the power x^y to a the given scale, using doubles.
   * Loses some precision, but means y can have non integer values.
   */
  private static BigDecimal approxPow(final BigDecimal x, final BigDecimal y)
  {
    double d;

    // Check that |y| >= 1 for negative x.
    if (x.signum() < 0 && y.abs().doubleValue() < 1.0) {
      throw new IllegalArgumentException("|n| < 1");
    }
    // Check that y is positive or 0 for x = 0.
    else if (x.signum() == 0 && y.signum() < 0) {
      throw new IllegalArgumentException("n < 0");
    }

    d = Math.pow(x.doubleValue(), y.doubleValue());
    return new BigDecimal(d);
  }

  //Convert bitstring to decimal
  public BigDecimal bitStringToBigDecimal(String bitStr){
    BigDecimal sum = new BigDecimal("0");
    BigDecimal base = new BigDecimal(2);
    BigDecimal temp;

    for(int i=0 ; i<bitStr.length() ; i++){
      if(bitStr.charAt(i)== '1'){
        int exponent= bitStr.length()-1-i;
        temp=base.pow(exponent);
        sum=sum.add(temp);
      }
    }
    return sum;
  }
}
