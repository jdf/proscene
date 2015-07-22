/*********************************************************************************
 * dandelion_tree
 * Copyright (c) 2014 National University of Colombia, https://github.com/remixlab
 * @author Jean Pierre Charalambos, http://otrolado.info/
 *
 * All rights reserved. Library that eases the creation of interactive
 * scenes, released under the terms of the GNU Public License v3.0
 * which is available at http://www.gnu.org/licenses/gpl.html
 *********************************************************************************/

package remixlab.dandelion.agent;

import remixlab.bias.event.*;
import remixlab.dandelion.core.*;
import remixlab.dandelion.core.Constants.*;

/**
 * A {@code MotionAgent<DOF1Action>}, such as a trackball, supporting
 * {@link remixlab.dandelion.core.Constants.ClickAction}s, and 
 * {@link remixlab.dandelion.core.Constants.DOF1Action}s actions.
 * <p>
 * @see remixlab.dandelion.core.Constants.ClickAction
 * @see remixlab.dandelion.core.Constants.DOF1Action
 */
public abstract class TrackballAgent extends MotionAgent<DOF1Action> {
	protected float	xSens	= 1f;

	public TrackballAgent(AbstractScene scn, String n) {
		super(scn, n);
	}

	@Override
	protected DOF1Event handleFeed() {
		return null;
	}
	
	@Override
	protected DOF1Event updateTrackedGrabberFeed() {
		return null;
	}

	@Override
	public float[] sensitivities(MotionEvent event) {
		if (event instanceof DOF3Event)
			return new float[] { xSens, 1f, 1f, 1f, 1f, 1f };
		else
			return super.sensitivities(event);
	}

	/**
	 * Defines the {@link #scrollSensitivity()}.
	 */
	public void setScrollSensitivity(float sensitivity) {
		xSens = sensitivity;
	}

	/**
	 * Returns the x sensitivity.
	 * <p>
	 * Default value is 1. A higher value will make the event more efficient (usually meaning a faster motion). Use a
	 * negative value to invert the along x-Axis motion direction.
	 * 
	 * @see #setWheelSensitivity(float)
	 */
	public float scrollSensitivity() {
		return xSens;
	}
}
