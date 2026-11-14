/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package javax.swing;

import java.awt.Component;

/**
 * An instance of the <code>Spring</code> class holds three properties that
 * characterize its behavior: the <em>minimum</em>, <em>preferred</em>, and
 * <em>maximum</em> values. Each of these properties may be involved in
 * defining its fourth, <em>value</em>, property based on a series of rules.
 * <p>
 * A <code>Spring</code> stretched or compressed away from its preferred
 * value distributes the change according to its <em>strain</em>: the
 * difference between value and preferred value as a fraction of the room
 * between the preferred value and the maximum (when stretched) or minimum
 * (when compressed) value. Compound springs built by {@link #sum},
 * {@link #max}, {@link #minus} and {@link #scale} propagate a value set on
 * them to their operands so that the operands share the strain.
 *
 * @see SpringLayout
 * @see SpringLayout.Constraints
 *
 * @author      Philip Milne
 * @since       1.4
 */
public abstract class Spring {

    /**
     * An integer value signifying that a property value has not yet been calculated.
     */
    public static final int UNSET = Integer.MIN_VALUE;

    /**
     * Used by factory methods to create a <code>Spring</code>.
     */
    protected Spring() {}

    /**
     * Returns the <em>minimum</em> value of this <code>Spring</code>.
     *
     * @return the <code>minimumValue</code> property of this <code>Spring</code>
     */
    public abstract int getMinimumValue();

    /**
     * Returns the <em>preferred</em> value of this <code>Spring</code>.
     *
     * @return the <code>preferredValue</code> of this <code>Spring</code>
     */
    public abstract int getPreferredValue();

    /**
     * Returns the <em>maximum</em> value of this <code>Spring</code>.
     *
     * @return the <code>maximumValue</code> property of this <code>Spring</code>
     */
    public abstract int getMaximumValue();

    /**
     * Returns the current <em>value</em> of this <code>Spring</code>.
     *
     * @return  the <code>value</code> property of this <code>Spring</code>
     */
    public abstract int getValue();

    /**
     * Sets the current <em>value</em> of this <code>Spring</code> to <code>value</code>.
     *
     * @param   value the new setting of the <code>value</code> property
     */
    public abstract void setValue(int value);

    private double range(boolean contract) {
        return contract ? (getPreferredValue() - getMinimumValue()) :
                          (getMaximumValue() - getPreferredValue());
    }

    /*pp*/ double getStrain() {
        double delta = (getValue() - getPreferredValue());
        return delta/range(getValue() < getPreferredValue());
    }

    /*pp*/ void setStrain(double strain) {
        setValue(getPreferredValue() + (int) (strain * range(strain < 0)));
    }

    /*pp*/ boolean isCyclic(SpringLayout l) {
        return false;
    }

    /*pp*/ static abstract class AbstractSpring extends Spring {
        protected int size = UNSET;

        public int getValue() {
            return size != UNSET ? size : getPreferredValue();
        }

        public final void setValue(int size) {
            if (this.size == size) {
                return;
            }
            if (size == UNSET) {
                clear();
            } else {
                setNonClearValue(size);
            }
        }

        protected void clear() {
            size = UNSET;
        }

        protected void setNonClearValue(int size) {
            this.size = size;
        }
    }

    private static class StaticSpring extends AbstractSpring {
        protected int min;
        protected int pref;
        protected int max;

        public StaticSpring(int pref) {
            this(pref, pref, pref);
        }

        public StaticSpring(int min, int pref, int max) {
            this.min = min;
            this.pref = pref;
            this.max = max;
        }

        public String toString() {
            return "StaticSpring [" + min + ", " + pref + ", " + max + "]";
        }

        public int getMinimumValue() {
            return min;
        }

        public int getPreferredValue() {
            return pref;
        }

        public int getMaximumValue() {
            return max;
        }
    }

    private static class NegativeSpring extends Spring {
        private Spring s;

        public NegativeSpring(Spring s) {
            this.s = s;
        }

        // Note the use of max value rather than minimum value here.
        // See the opening preamble on arithmetic with springs.

        public int getMinimumValue() {
            return -s.getMaximumValue();
        }

        public int getPreferredValue() {
            return -s.getPreferredValue();
        }

        public int getMaximumValue() {
            return -s.getMinimumValue();
        }

        public int getValue() {
            return -s.getValue();
        }

        public void setValue(int size) {
            // No need to check for UNSET as
            // Integer.MIN_VALUE == -Integer.MIN_VALUE.
            s.setValue(-size);
        }

        /*pp*/ boolean isCyclic(SpringLayout l) {
            return s.isCyclic(l);
        }
    }

    private static class ScaleSpring extends Spring {
        private Spring s;
        private float factor;

        private ScaleSpring(Spring s, float factor) {
            this.s = s;
            this.factor = factor;
        }

        public int getMinimumValue() {
            return Math.round((factor < 0 ? s.getMaximumValue() : s.getMinimumValue()) * factor);
        }

        public int getPreferredValue() {
            return Math.round(s.getPreferredValue() * factor);
        }

        public int getMaximumValue() {
            return Math.round((factor < 0 ? s.getMinimumValue() : s.getMaximumValue()) * factor);
        }

        public int getValue() {
            return Math.round(s.getValue() * factor);
        }

        public void setValue(int value) {
            if (value == UNSET) {
                s.setValue(UNSET);
            } else {
                s.setValue(Math.round(value / factor));
            }
        }

        /*pp*/ boolean isCyclic(SpringLayout l) {
            return s.isCyclic(l);
        }
    }

    /*pp*/ static class WidthSpring extends AbstractSpring {
        /*pp*/ Component c;

        public WidthSpring(Component c) {
            this.c = c;
        }

        public int getMinimumValue() {
            return c.getMinimumSize().width;
        }

        public int getPreferredValue() {
            return c.getPreferredSize().width;
        }

        public int getMaximumValue() {
            // We will be doing arithmetic with the results of this call,
            // so if a returned value is Integer.MAX_VALUE we will get
            // arithmetic overflow. Truncate such values.
            return Math.min(Short.MAX_VALUE, c.getMaximumSize().width);
        }
    }

    /*pp*/ static class HeightSpring extends AbstractSpring {
        /*pp*/ Component c;

        public HeightSpring(Component c) {
            this.c = c;
        }

        public int getMinimumValue() {
            return c.getMinimumSize().height;
        }

        public int getPreferredValue() {
            return c.getPreferredSize().height;
        }

        public int getMaximumValue() {
            return Math.min(Short.MAX_VALUE, c.getMaximumSize().height);
        }
    }

    /*pp*/ static abstract class SpringMap extends Spring {
        private Spring s;

        public SpringMap(Spring s) {
            this.s = s;
        }

        protected abstract int map(int i);

        protected abstract int inv(int i);

        public int getMinimumValue() {
            return map(s.getMinimumValue());
        }

        public int getPreferredValue() {
            return map(s.getPreferredValue());
        }

        public int getMaximumValue() {
            return Math.min(Short.MAX_VALUE, map(s.getMaximumValue()));
        }

        public int getValue() {
            return map(s.getValue());
        }

        public void setValue(int value) {
            if (value == UNSET) {
                s.setValue(UNSET);
            } else {
                s.setValue(inv(value));
            }
        }

        /*pp*/ boolean isCyclic(SpringLayout l) {
            return s.isCyclic(l);
        }
    }

    // Use the instance variables of the StaticSpring superclass to
    // cache values that have already been calculated.
    /*pp*/ static abstract class CompoundSpring extends StaticSpring {
        protected Spring s1;
        protected Spring s2;

        public CompoundSpring(Spring s1, Spring s2) {
            super(UNSET);
            this.s1 = s1;
            this.s2 = s2;
        }

        public String toString() {
            return "CompoundSpring of " + s1 + " and " + s2;
        }

        protected void clear() {
            super.clear();
            min = pref = max = UNSET;
            s1.setValue(UNSET);
            s2.setValue(UNSET);
        }

        protected abstract int op(int x, int y);

        public int getMinimumValue() {
            if (min == UNSET) {
                min = op(s1.getMinimumValue(), s2.getMinimumValue());
            }
            return min;
        }

        public int getPreferredValue() {
            if (pref == UNSET) {
                pref = op(s1.getPreferredValue(), s2.getPreferredValue());
            }
            return pref;
        }

        public int getMaximumValue() {
            if (max == UNSET) {
                max = op(s1.getMaximumValue(), s2.getMaximumValue());
            }
            return max;
        }

        public int getValue() {
            if (size == UNSET) {
                size = op(s1.getValue(), s2.getValue());
            }
            return size;
        }

        /*pp*/ boolean isCyclic(SpringLayout l) {
            return l.isCyclic(s1) || l.isCyclic(s2);
        }
    }

    private static class SumSpring extends CompoundSpring {
        public SumSpring(Spring s1, Spring s2) {
            super(s1, s2);
        }

        protected int op(int x, int y) {
            return x + y;
        }

        protected void setNonClearValue(int size) {
            super.setNonClearValue(size);
            s1.setStrain(this.getStrain());
            s2.setValue(size - s1.getValue());
        }
    }

    private static class MaxSpring extends CompoundSpring {

        public MaxSpring(Spring s1, Spring s2) {
            super(s1, s2);
        }

        protected int op(int x, int y) {
            return Math.max(x, y);
        }

        protected void setNonClearValue(int size) {
            super.setNonClearValue(size);
            s1.setValue(size);
            s2.setValue(size);
        }
    }

    /**
     * Returns a strut -- a spring whose <em>minimum</em>, <em>preferred</em>, and
     * <em>maximum</em> values each have the value <code>pref</code>.
     *
     * @param  pref the <em>minimum</em>, <em>preferred</em>, and
     *         <em>maximum</em> values of the new spring
     * @return a spring whose <em>minimum</em>, <em>preferred</em>, and
     *         <em>maximum</em> values each have the value <code>pref</code>
     *
     * @see Spring
     */
    public static Spring constant(int pref) {
        return constant(pref, pref, pref);
    }

    /**
     * Returns a spring whose <em>minimum</em>, <em>preferred</em>, and
     * <em>maximum</em> values have the values: <code>min</code>, <code>pref</code>,
     * and <code>max</code> respectively.
     *
     * @param  min the <em>minimum</em> value of the new spring
     * @param  pref the <em>preferred</em> value of the new spring
     * @param  max the <em>maximum</em> value of the new spring
     * @return a spring whose <em>minimum</em>, <em>preferred</em>, and
     *         <em>maximum</em> values have the values: <code>min</code>, <code>pref</code>,
     *         and <code>max</code> respectively
     *
     * @see Spring
     */
    public static Spring constant(int min, int pref, int max) {
        return new StaticSpring(min, pref, max);
    }

    /**
     * Returns <code>-s</code>: a spring running in the opposite direction to <code>s</code>.
     *
     * @param s a {@code Spring} object
     * @return <code>-s</code>: a spring running in the opposite direction to <code>s</code>
     *
     * @see Spring
     */
    public static Spring minus(Spring s) {
        return new NegativeSpring(s);
    }

    /**
     * Returns <code>s1+s2</code>: a spring representing <code>s1</code> and <code>s2</code>
     * in series. In a sum, <code>s3</code>, of two springs, <code>s1</code> and <code>s2</code>,
     * the <em>strains</em> of <code>s1</code>, <code>s2</code>, and <code>s3</code> are maintained
     * at the same level (to within the precision implied by their integer <em>value</em>s).
     *
     * @param s1 a {@code Spring} object
     * @param s2 a {@code Spring} object
     * @return <code>s1+s2</code>: a spring representing <code>s1</code> and <code>s2</code> in series
     *
     * @see Spring
     */
    public static Spring sum(Spring s1, Spring s2) {
        return new SumSpring(s1, s2);
    }

    /**
     * Returns <code>max(s1, s2)</code>: a spring whose value is always greater than (or equal to)
     * the values of both <code>s1</code> and <code>s2</code>.
     *
     * @param s1 a {@code Spring} object
     * @param s2 a {@code Spring} object
     * @return <code>max(s1, s2)</code>: a spring whose value is always greater than (or equal to)
     *         the values of both <code>s1</code> and <code>s2</code>
     * @see Spring
     */
    public static Spring max(Spring s1, Spring s2) {
        return new MaxSpring(s1, s2);
    }

    // Remove these, they're not used often and can be created using minus -
    // as per these implementations.

    /*pp*/ static Spring difference(Spring s1, Spring s2) {
        return sum(s1, minus(s2));
    }

    /**
     * Returns a spring whose <em>minimum</em>, <em>preferred</em>, <em>maximum</em>
     * and <em>value</em> properties are each multiples of the properties of the
     * argument spring, <code>s</code>. Minimum and maximum properties are
     * swapped when <code>factor</code> is negative (in accordance with the
     * rules of interval arithmetic).
     * <p>
     * When factor is, for example, 0.5f the result represents 'the mid-point'
     * of its input - an operation that is useful for centering components in
     * a container.
     *
     * @param s the spring to scale
     * @param factor amount to scale by.
     * @return  a spring whose properties are those of the input spring <code>s</code>
     * multiplied by <code>factor</code>
     * @throws NullPointerException if <code>s</code> is null
     * @since 1.5
     */
    public static Spring scale(Spring s, float factor) {
        checkArg(s);
        return new ScaleSpring(s, factor);
    }

    /**
     * Returns a spring whose <em>minimum</em>, <em>preferred</em>, <em>maximum</em>
     * and <em>value</em> properties are defined by the widths of the <em>minimumSize</em>,
     * <em>preferredSize</em>, <em>maximumSize</em> and <em>size</em> properties
     * of the supplied component. The returned spring is a 'wrapper' implementation
     * whose methods call the appropriate size methods of the supplied component.
     * The minimum, preferred, maximum and value properties of the returned spring
     * therefore report the current state of the appropriate properties in the
     * component and track them as they change.
     *
     * @param c Component used for calculating size
     * @return  a spring whose properties are defined by the horizontal component
     * of the component's size methods.
     * @throws NullPointerException if <code>c</code> is null
     * @since 1.5
     */
    public static Spring width(Component c) {
        checkArg(c);
        return new WidthSpring(c);
    }

    /**
     * Returns a spring whose <em>minimum</em>, <em>preferred</em>, <em>maximum</em>
     * and <em>value</em> properties are defined by the heights of the <em>minimumSize</em>,
     * <em>preferredSize</em>, <em>maximumSize</em> and <em>size</em> properties
     * of the supplied component.
     *
     * @param c Component used for calculating size
     * @return  a spring whose properties are defined by the vertical component
     * of the component's size methods.
     * @throws NullPointerException if <code>c</code> is null
     * @since 1.5
     */
    public static Spring height(Component c) {
        checkArg(c);
        return new HeightSpring(c);
    }

    /**
     * If <code>s</code> is null, this throws an NullPointerException.
     */
    private static void checkArg(Object s) {
        if (s == null) {
            throw new NullPointerException("Argument must not be null");
        }
    }
}
