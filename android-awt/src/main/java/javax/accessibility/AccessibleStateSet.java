package javax.accessibility;

import java.util.Vector;

/**
 * The set of states of an accessible object.
 */
public class AccessibleStateSet {

    /** Each entry in the Vector represents an AccessibleState. */
    protected Vector<AccessibleState> states = null;

    public AccessibleStateSet() {
        states = null;
    }

    public AccessibleStateSet(AccessibleState[] states) {
        if (states.length != 0) {
            this.states = new Vector<AccessibleState>(states.length);
            for (int i = 0; i < states.length; i++) {
                if (!this.states.contains(states[i])) {
                    this.states.addElement(states[i]);
                }
            }
        }
    }

    /** Adds a state if it is not already present; returns true if the set changed. */
    public boolean add(AccessibleState state) {
        if (states == null) {
            states = new Vector<AccessibleState>();
        }
        if (!states.contains(state)) {
            states.addElement(state);
            return true;
        } else {
            return false;
        }
    }

    public void addAll(AccessibleState[] states) {
        if (states.length != 0) {
            if (this.states == null) {
                this.states = new Vector<AccessibleState>(states.length);
            }
            for (int i = 0; i < states.length; i++) {
                if (!this.states.contains(states[i])) {
                    this.states.addElement(states[i]);
                }
            }
        }
    }

    public boolean remove(AccessibleState state) {
        if (states == null) {
            return false;
        } else {
            return states.removeElement(state);
        }
    }

    public void clear() {
        if (states != null) {
            states.removeAllElements();
        }
    }

    public boolean contains(AccessibleState state) {
        if (states == null) {
            return false;
        } else {
            return states.contains(state);
        }
    }

    public AccessibleState[] toArray() {
        if (states == null) {
            return new AccessibleState[0];
        } else {
            AccessibleState[] stateArray = new AccessibleState[states.size()];
            for (int i = 0; i < stateArray.length; i++) {
                stateArray[i] = (AccessibleState) states.elementAt(i);
            }
            return stateArray;
        }
    }

    /** The localized states, comma separated. */
    public String toString() {
        String ret = null;
        if ((states != null) && (states.size() > 0)) {
            ret = ((AccessibleState) (states.elementAt(0))).toDisplayString();
            for (int i = 1; i < states.size(); i++) {
                ret = ret + ","
                        + ((AccessibleState) (states.elementAt(i))).toDisplayString();
            }
        }
        return ret;
    }
}
