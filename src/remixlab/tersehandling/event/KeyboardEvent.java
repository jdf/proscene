/*********************************************************************************
 * TerseHandling
 * Copyright (c) 2014 National University of Colombia, https://github.com/remixlab
 * @author Jean Pierre Charalambos, http://otrolado.info/
 *     
 * All rights reserved. Library that eases the creation of interactive
 * scenes, released under the terms of the GNU Public License v3.0
 * which is available at http://www.gnu.org/licenses/gpl.html
 *********************************************************************************/
package remixlab.tersehandling.event;

import remixlab.tersehandling.event.shortcut.KeyboardShortcut;
import remixlab.util.EqualsBuilder;
import remixlab.util.HashCodeBuilder;

/**
 * A keyboard event is a {@link remixlab.tersehandling.event.TerseEvent} specialization that encapsulates a
 * {@link remixlab.tersehandling.event.shortcut.KeyboardShortcut}. Keyboard shortcuts may be of one form out of two: 1.
 * A single Character; or, 2. A modifier mask (such as: (TH_ALT | TH_SHIFT)) plus a virtual.
 * <p>
 * <b>Note</b> that virtual key codes are used to report which keyboard key has been pressed, rather than a character
 * generated by the combination of one or more keystrokes (such as "A", which comes from shift and "a"). Their values
 * depend on the platform your running your code. In Java, for instance, have a look at <a
 * href="http://docs.oracle.com/javase/7/docs/api/java/awt/event/KeyEvent.html">KeyEvent</a> to get some VK_* values.
 * You may use these values in your code to freely choose them as a convention in your code. Have a look at the
 * CameraCustomization example.
 */
public class KeyboardEvent extends TerseEvent {
	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37).appendSuper(super.hashCode())
						.append(key)
						.append(vKey)
						.toHashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (obj == this)
			return true;
		if (obj.getClass() != getClass())
			return false;

		KeyboardEvent other = (KeyboardEvent) obj;
		return new EqualsBuilder().appendSuper(super.equals(obj))
						.append(key, other.key)
						.append(vKey, other.vKey)
						.isEquals();
	}

	protected Character key;
	protected Integer vKey;

	/**
	 * Constructs a keyboard event with the <b>modifiers</b> and <b>vk</b> defining its
	 * {@link remixlab.tersehandling.event.shortcut.KeyboardShortcut}.
	 */
	public KeyboardEvent(Integer modifiers, Integer vk) {
		super(modifiers);
		this.key = null;
		this.vKey = vk;
	}

	/**
	 * Constructs a keyboard event with <b>c</b> defining its
	 * {@link remixlab.tersehandling.event.shortcut.KeyboardShortcut}.
	 */
	public KeyboardEvent(Character c) {
		super();
		this.key = c;
		this.vKey = null;
	}

	/**
	 * @param other
	 */
	protected KeyboardEvent(KeyboardEvent other) {
		super(other);
		this.key = new Character(other.key);
		this.vKey = new Integer(other.vKey);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see remixlab.tersehandling.event.TerseEvent#get()
	 */
	@Override
	public KeyboardEvent get() {
		return new KeyboardEvent(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see remixlab.tersehandling.event.TerseEvent#shortcut()
	 */
	@Override
	public KeyboardShortcut shortcut() {
		if (key == null)
			return new KeyboardShortcut(modifiers(), keyCode());
		else
			return new KeyboardShortcut(key());
	}

	/**
	 * Returns the character key defining the events keyboard shortcut. It may be null meaning that the keyboard is of the
	 * shape: {@link #modifiers()} mask + {@link #keyCode()}
	 */
	public Character key() {
		return key;
	}

	/**
	 * Returns the key code defining the events keyboard shortcut. It may be null meaning that the keyboard is of the
	 * shape: {@link #key()}.
	 */
	public Integer keyCode() {
		return vKey;
	}
}
