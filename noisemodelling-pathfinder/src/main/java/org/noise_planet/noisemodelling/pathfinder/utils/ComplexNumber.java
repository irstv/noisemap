/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.utils;


/**
 * ComplexNumber is a class which implements complex numbers in Java.
 * It includes basic operations that can be performed on complex numbers such as,
 * addition, subtraction, multiplication, conjugate, modulus and squaring.
 * The data type for Complex Numbers.
 * The features of this library include:
 * Arithmetic Operations (addition, subtraction, multiplication, division)
 * Complex Specific Operations - Conjugate, Inverse, Absolute/Magnitude, Argument/Phase
 * Trigonometric Operations - sin, cos, tan, cot, sec, cosec
 * Mathematical Functions - exp
 * Complex Parsing of type x+yi
 *
 * @author      Abdul Fatir
 * @version		1.2
 *
 */
public class ComplexNumber
{
    /**
     * Used in <code>format(int)</code> to format the complex number as x+yi
     */
    public static final int XY = 0;
    /**
     * Used in <code>format(int)</code> to format the complex number as R.cis(theta), where theta is arg(z)
     */
    public static final int RCIS = 1;
    /**
     * The real, Re(z), part of the <code>ComplexNumber</code>.
     */
    private double real;
    /**
     * The imaginary, Im(z), part of the <code>ComplexNumber</code>.
     */
    private double imaginary;
    /**
     * Constructs a new <code>ComplexNumber</code> object with both real and imaginary parts 0 (z = 0 + 0i).
     */
    public ComplexNumber()
    {
        real = 0.0;
        imaginary = 0.0;
    }

    /**
     * Constructs a new <code>ComplexNumber</code> object.
     * @param real the real part, Re(z), of the complex number
     * @param imaginary the imaginary part, Im(z), of the complex number
     */

    public ComplexNumber(double real, double imaginary)
    {
        this.real = real;
        this.imaginary = imaginary;
    }

    /**
     * Adds another <code>ComplexNumber</code> to the current complex number.
     * @param z the complex number to be added to the current complex number
     */

    public void add(ComplexNumber z)
    {
        set(add(this,z));
    }

    /**
     * Subtracts another <code>ComplexNumber</code> from the current complex number.
     * @param z the complex number to be subtracted from the current complex number
     */

    public void subtract(ComplexNumber z)
    {
        set(subtract(this,z));
    }

    /**
     * Multiplies another <code>ComplexNumber</code> to the current complex number.
     * @param z the complex number to be multiplied to the current complex number
     */
    public void multiply(ComplexNumber z)
    {
        set(multiply(this,z));
    }
    
    /**
     * Divides the current <code>ComplexNumber</code> by another <code>ComplexNumber</code>.
     * @param z the divisor
     */
    public void divide(ComplexNumber z)
    {
        set(divide(this,z));
    }
    
    /**
     * Sets the value of current complex number to the passed complex number.
     * @param z the complex number
     */
    public void set(ComplexNumber z)
    {
        this.real = z.real;
        this.imaginary = z.imaginary;
    }
    /**
     * Adds two <code>ComplexNumber</code>.
     * @param z1 the first <code>ComplexNumber</code>.
     * @param z2 the second <code>ComplexNumber</code>.
     * @return the resultant <code>ComplexNumber</code> (z1 + z2).
     */
    public static ComplexNumber add(ComplexNumber z1, ComplexNumber z2)
    {
        return new ComplexNumber(z1.real + z2.real, z1.imaginary + z2.imaginary);
    }

    /**
     * Subtracts one <code>ComplexNumber</code> from another.
     * @param z1 the first <code>ComplexNumber</code>.
     * @param z2 the second <code>ComplexNumber</code>.
     * @return the resultant <code>ComplexNumber</code> (z1 - z2).
     */
    public static ComplexNumber subtract(ComplexNumber z1, ComplexNumber z2)
    {
        return new ComplexNumber(z1.real - z2.real, z1.imaginary - z2.imaginary);
    }
    
    /**
     * Multiplies one <code>ComplexNumber</code> to another.
     * @param z1 the first <code>ComplexNumber</code>.
     * @param z2 the second <code>ComplexNumber</code>.
     * @return the resultant <code>ComplexNumber</code> (z1 * z2).
     */
    public static ComplexNumber multiply(ComplexNumber z1, ComplexNumber z2)
    {
        double _real = z1.real*z2.real - z1.imaginary*z2.imaginary;
        double _imaginary = z1.real*z2.imaginary + z1.imaginary*z2.real;
        return new ComplexNumber(_real,_imaginary);
    }
    /**
     * Divides one <code>ComplexNumber</code> by another.
     * @param z1 the first <code>ComplexNumber</code>.
     * @param z2 the second <code>ComplexNumber</code>.
     * @return the resultant <code>ComplexNumber</code> (z1 / z2).
     */
    public static ComplexNumber divide(ComplexNumber z1, ComplexNumber z2)
    {
        ComplexNumber output = multiply(z1,z2.conjugate());
        double div = Math.pow(z2.mod(),2);
        return new ComplexNumber(output.real/div,output.imaginary/div);
    }

    /**
     * The complex conjugate of the current complex number.
     * @return a <code>ComplexNumber</code> object which is the conjugate of the current complex number
     */

    public ComplexNumber conjugate()
    {
        return new ComplexNumber(this.real,-this.imaginary);
    }

    /**
     * The modulus, magnitude or the absolute value of current complex number.
     * @return the magnitude or modulus of current complex number
     */

    public double mod()
    {
        return Math.sqrt(Math.pow(this.real,2) + Math.pow(this.imaginary,2));
    }

    /**
     * The square of the current complex number.
     * @return a <code>ComplexNumber</code> which is the square of the current complex number.
     */
    public ComplexNumber square()
    {
        double _real = this.real*this.real - this.imaginary*this.imaginary;
        double _imaginary = 2*this.real*this.imaginary;
        return new ComplexNumber(_real,_imaginary);
    }
    
    /**
     * @return the complex number in x + yi format
     */
    @Override
    public String toString()
    {
        String re = this.real+"";
        String im = "";
        if(this.imaginary < 0)
            im = this.imaginary+"i";
        else
            im = "+"+this.imaginary+"i";
        return re+im;
    }
    /**
     * Calculates the exponential of the <code>ComplexNumber</code>
     * @param z The input complex number
     * @return a <code>ComplexNumber</code> which is e^(input z)
     */
    public static ComplexNumber exp(ComplexNumber z)
    {
        double a = z.real;
        double b = z.imaginary;
        double r = Math.exp(a);
        a = r*Math.cos(b);
        b = r*Math.sin(b);
        return new ComplexNumber(a,b);
    }
    /**
     * Calculates the <code>ComplexNumber</code> to the passed integer power.
     * @param z The input complex number
     * @param power The power.
     * @return a <code>ComplexNumber</code> which is (z)^power
     */
    public static ComplexNumber pow(ComplexNumber z, int power)
    {
        ComplexNumber output = new ComplexNumber(z.getRe(),z.getIm());
        for(int i = 1; i < power; i++)
        {
            double _real = output.real*z.real - output.imaginary*z.imaginary;
            double _imaginary = output.real*z.imaginary + output.imaginary*z.real;
            output = new ComplexNumber(_real,_imaginary);
        }
        return output;
    }
    /**
     * Calculates the sine of the <code>ComplexNumber</code>
     * @param z the input complex number
     * @return a <code>ComplexNumber</code> which is the sine of z.
     */
    public static ComplexNumber sin(ComplexNumber z)
    {
        double x = Math.exp(z.imaginary);
        double x_inv = 1/x;
        double r = Math.sin(z.real) * (x + x_inv)/2;
        double i = Math.cos(z.real) * (x - x_inv)/2;
        return new ComplexNumber(r,i);
    }
    /**
     * Calculates the cosine of the <code>ComplexNumber</code>
     * @param z the input complex number
     * @return a <code>ComplexNumber</code> which is the cosine of z.
     */
    public static ComplexNumber cos(ComplexNumber z)
    {
        double x = Math.exp(z.imaginary);
        double x_inv = 1/x;
        double r = Math.cos(z.real) * (x + x_inv)/2;
        double i = -Math.sin(z.real) * (x - x_inv)/2;
        return new ComplexNumber(r,i);
    }
    /**
     * Calculates the tangent of the <code>ComplexNumber</code>
     * @param z the input complex number
     * @return a <code>ComplexNumber</code> which is the tangent of z.
     */
    public static ComplexNumber tan(ComplexNumber z)
    {
        return divide(sin(z),cos(z));
    }
    /**
     * Calculates the co-tangent of the <code>ComplexNumber</code>
     * @param z the input complex number
     * @return a <code>ComplexNumber</code> which is the co-tangent of z.
     */
    public static ComplexNumber cot(ComplexNumber z)
    {
        return divide(new ComplexNumber(1,0),tan(z));
    }
    /**
     * Calculates the secant of the <code>ComplexNumber</code>
     * @param z the input complex number
     * @return a <code>ComplexNumber</code> which is the secant of z.
     */
    public static ComplexNumber sec(ComplexNumber z)
    {
        return divide(new ComplexNumber(1,0),cos(z));
    }
    /**
     * Calculates the co-secant of the <code>ComplexNumber</code>
     * @param z the input complex number
     * @return a <code>ComplexNumber</code> which is the co-secant of z.
     */
    public static ComplexNumber cosec(ComplexNumber z)
    {
        return divide(new ComplexNumber(1,0),sin(z));
    }
    /**
     * The real part of <code>ComplexNumber</code>
     * @return the real part of the complex number
     */
    public double getRe()
    {
        return this.real;
    }
    /**
     * The imaginary part of <code>ComplexNumber</code>
     * @return the imaginary part of the complex number
     */
    public double getIm()
    {
        return this.imaginary;
    }
    /**
     * The argument/phase of the current complex number.
     * @return arg(z) - the argument of current complex number
     */
    public double getArg()
    {
        return Math.atan2(imaginary,real);
    }
    /**
     * Parses the <code>String</code> as a <code>ComplexNumber</code> of type x+yi.
     * @param s the input complex number as string
     * @return a <code>ComplexNumber</code> which is represented by the string.
     */
    public static ComplexNumber parseComplex(String s)
    {
        s = s.replaceAll(" ","");
        ComplexNumber parsed = null;
        if(s.contains(String.valueOf("+")) || (s.contains(String.valueOf("-")) && s.lastIndexOf('-') > 0))
        {
            String re = "";
            String im = "";
            s = s.replaceAll("i","");
            s = s.replaceAll("I","");
            if(s.indexOf('+') > 0)
            {
                re = s.substring(0,s.indexOf('+'));
                im = s.substring(s.indexOf('+')+1,s.length());
                parsed = new ComplexNumber(Double.parseDouble(re),Double.parseDouble(im));
            }
            else if(s.lastIndexOf('-') > 0)
            {
                re = s.substring(0,s.lastIndexOf('-'));
                im = s.substring(s.lastIndexOf('-')+1,s.length());
                parsed = new ComplexNumber(Double.parseDouble(re),-Double.parseDouble(im));
            }
        }
        else
        {
            // Pure imaginary number
            if(s.endsWith("i") || s.endsWith("I"))
            {
                s = s.replaceAll("i","");
                s = s.replaceAll("I","");
                parsed = new ComplexNumber(0, Double.parseDouble(s));
            }
            // Pure real number
            else
            {
                parsed = new ComplexNumber(Double.parseDouble(s),0);
            }
        }
        return parsed;
    }
    /**
     * Checks if the passed <code>ComplexNumber</code> is equal to the current.
     * @param z the complex number to be checked
     * @return true if they are equal, false otherwise
     */
    @Override
    public final boolean equals(Object z)
    {
        if (!(z instanceof ComplexNumber))
            return false;
        ComplexNumber a = (ComplexNumber) z;
        return (real == a.real) && (imaginary == a.imaginary);
    }
    /**
     * The inverse/reciprocal of the complex number.
     * @return the reciprocal of current complex number.
     */
    public ComplexNumber inverse()
    {
        return divide(new ComplexNumber(1,0),this);
    }
    /**
     * Formats the Complex number as x+yi or r.cis(theta)
     * @param format_id the format ID <code>ComplexNumber.XY</code> or <code>ComplexNumber.RCIS</code>.
     * @return a string representation of the complex number
     * @throws IllegalArgumentException if the format_id does not match.
     */
    public String format(int format_id) throws IllegalArgumentException
    {
        String out = "";
        if(format_id == XY)
            out = toString();
        else if(format_id == RCIS)
        {
            out = mod()+" cis("+getArg()+")";
        }
        else
        {
            throw new IllegalArgumentException("Unknown Complex Number format.");
        }
        return out;
    }
}
