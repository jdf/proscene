/*********************************************************************************
 * dandelion_tree
 * Copyright (c) 2014 National University of Colombia, https://github.com/remixlab
 * @author Jean Pierre Charalambos, http://otrolado.info/
 *
 * All rights refserved. Library that eases the creation of interactive
 * scenes, released under the terms of the GNU Public License v3.0
 * which is available at http://www.gnu.org/licenses/gpl.html
 *********************************************************************************/

package remixlab.dandelion.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import remixlab.dandelion.constraint.*;
import remixlab.dandelion.geom.Mat;
import remixlab.dandelion.geom.Rotation;
import remixlab.dandelion.geom.Quat;
import remixlab.dandelion.geom.Rot;
import remixlab.dandelion.geom.Vec;
import remixlab.fpstiming.TimingHandler;
import remixlab.util.Copyable;
import remixlab.util.EqualsBuilder;
import remixlab.util.HashCodeBuilder;
import remixlab.util.Util;

/**
 * A Frame is a 2D/3D coordinate system, represented by a {@link #position()}, an {@link #orientation()} and
 * {@link #magnitude()}. The order of these transformations is important: the Frame is first translated, then rotated
 * around the new translated origin and then scaled.
 * <p>
 * In rare situations a frame can be {@link #linkTo(Frame)}, meaning that it will share its {@link #translation()},
 * {@link #rotation()}, {@link #scaling()}, {@link #referenceFrame()}, and {@link #constraint()} with the other frame,
 * which can be useful for some off-screen scenes.
 */
public class Frame implements Copyable, Constants {
	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37).
				append(krnl).
				append(linkedFramesList).
				append(srcFrame).
				toHashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (obj == this)
			return true;
		if (obj.getClass() != getClass())
			return false;

		Frame other = (Frame) obj;
		return new EqualsBuilder()
				.append(krnl, other.krnl)
				.append(linkedFramesList, other.linkedFramesList)
				.append(srcFrame, other.srcFrame)
				.isEquals();
	}

	/**
	 * Internal abstract class that holds the main frame attributes. This class is useful to link frames together (i.e.,
	 * to share these attributes) and its the base class for 2D and 3D Frame kernels.
	 */
	protected abstract class FrameKernel implements Copyable {
		@Override
		public int hashCode() {
			return new HashCodeBuilder(17, 37).
					append(trans).
					append(rot).
					append(scl).
					append(refFrame).
					append(constr).
					append(lastUpdate).
					toHashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!super.equals(obj))
				return false;
			if (getClass() != obj.getClass())
				return false;
			FrameKernel3D other = (FrameKernel3D) obj;
			return new EqualsBuilder()
					.appendSuper(super.equals(obj))
					.append(trans, other.trans)
					.append(scl, other.scl)
					.append(rot, other.rot)
					.append(refFrame, other.refFrame)
					.append(constr, other.constr)
					.isEquals();
		}

		protected Vec					trans;
		protected float				scl;
		protected Rotation		rot;
		protected Frame				refFrame;
		protected Constraint	constr;
		protected long				lastUpdate;

		public FrameKernel() {
			trans = new Vec(0, 0, 0);
			scl = 1;
			rot = null;
			refFrame = null;
			constr = null;
			lastUpdate = 0;
		}

		public FrameKernel(Vec p, Rotation r, float s) {
			trans = new Vec(p.x(), p.y(), p.z());
			setScaling(s);
			rot = r.get();
			refFrame = null;
			constr = null;
			lastUpdate = 0;
		}

		public FrameKernel(Vec p, Rotation r) {
			trans = new Vec(p.x(), p.y(), p.z());
			rot = r.get();
			scl = 1;
			refFrame = null;
			constr = null;
			lastUpdate = 0;
		}

		protected FrameKernel(FrameKernel other) {
			trans = new Vec(other.translation().vec[0], other.translation().vec[1], other.translation().vec[2]);
			rot = other.rotation().get();
			scl = other.scaling();
			refFrame = other.referenceFrame();
			constr = other.constraint();
			lastUpdate = other.lastUpdate();
		}

		public final Vec translation() {
			return trans;
		}

		public final void setTranslation(Vec t) {
			trans = t;
			modified();
		}

		public final float scaling() {
			return scl;
		}

		public final void setScaling(float s) {
			if (Util.positive(s)) {
				scl = s;
				modified();
			}
			else
				System.out.println("Warning. Scaling should be positive. Nothing done");
		}

		public final Rotation rotation() {
			return rot;
		}

		public final void setRotation(Rotation r) {
			rot = r;
			modified();
		}

		public Constraint constraint() {
			return constr;
		}

		public void setConstraint(Constraint c) {
			constr = c;
		}

		public final Frame referenceFrame() {
			return refFrame;
		}

		public void translate(Vec t) {
			translation().add(t);
			modified();
		}

		public void rotate(Rotation r) {
			rotation().compose(r);
			if (this instanceof FrameKernel3D)
				((Quat) rotation()).normalize(); // Prevents numerical drift
			modified();
		}

		public void scale(float s) {
			setScaling(scaling() * s);
		}

		protected void modified() {
			lastUpdate = TimingHandler.frameCount;
		}

		public long lastUpdate() {
			return lastUpdate;
		}

		public final void setReferenceFrame(Frame rFrame) {
			if (settingAsReferenceFrameWillCreateALoop(rFrame))
				System.out.println("Frame.setReferenceFrame would create a loop in Frame hierarchy");
			else {
				boolean identical = (referenceFrame() == rFrame);
				refFrame = rFrame;
				if (!identical)
					modified();
			}
		}

		public final boolean settingAsReferenceFrameWillCreateALoop(Frame frame) {
			Frame f = frame;
			while (f != null) {
				if (f == Frame.this)
					return true;
				f = f.referenceFrame();
			}
			return false;
		}
	}

	/**
	 * Internal class. 3D version of AbstractFrameKernel.
	 */
	protected class FrameKernel3D extends FrameKernel {
		public FrameKernel3D() {
			rot = new Quat();
		}

		public FrameKernel3D(Vec p, Quat r, float s) {
			super(p, r, s);
		}

		public FrameKernel3D(Vec p, Quat r) {
			super(p, r);
		}

		protected FrameKernel3D(FrameKernel3D other) {
			super(other);
		}

		@Override
		public FrameKernel3D get() {
			return new FrameKernel3D(this);
		}
	}

	/**
	 * Internal class. 2D version of AbstractFrameKernel.
	 */
	protected class FrameKernel2D extends FrameKernel {
		public FrameKernel2D() {
			rot = new Rot();
		}

		public FrameKernel2D(Vec p, Rot r, float s) {
			super(p, r, s);
		}

		public FrameKernel2D(Vec p, Rot r) {
			super(p, r);
		}

		protected FrameKernel2D(FrameKernel2D other) {
			super(other);
		}

		@Override
		public FrameKernel2D get() {
			return new FrameKernel2D(this);
		}
	}

	protected FrameKernel	krnl;
	protected List<Frame>	linkedFramesList;
	protected Frame				srcFrame;

	/**
	 * Convenience constructor that simply calls {@code this(true)}.
	 * 
	 * @see #Frame(boolean)
	 */
	public Frame() {
		this(true);
	}

	/**
	 * If {@code three_d} is true, creates a 3D frame; otherwise a 2D frame is created.
	 * <p>
	 * Its {@link #position()} is set to 0, its {@link #orientation()} is set to the identity rotation and its
	 * {@link #scaling()} is set to 1. The {@link #referenceFrame()} and the {@link #constraint()} are {@code null}.
	 */
	public Frame(boolean three_d) {
		if (three_d)
			krnl = new FrameKernel3D();
		else
			krnl = new FrameKernel2D();
		linkedFramesList = new ArrayList<Frame>();
		srcFrame = null;
	}

	/**
	 * Creates a Frame from {@code p}, {@code r}, and {@code s} which define its {@link #position()},
	 * {@link #orientation()} and {@link #magnitude()}.
	 * <p>
	 * See the {@link remixlab.dandelion.geom.Vec} and {@link remixlab.dandelion.geom.Rotation} documentations for
	 * convenient constructors and methods.
	 * <p>
	 * The Frame is defined in the world coordinate system (its {@link #referenceFrame()} is {@code null}). It has a
	 * {@code null} associated {@link #constraint()}.
	 */
	public Frame(Vec p, Rotation r, float s) {
		if (r instanceof Quat)
			krnl = new FrameKernel3D(p, (Quat) r, s);
		else if (r instanceof Rot)
			krnl = new FrameKernel2D(p, (Rot) r, s);

		linkedFramesList = new ArrayList<Frame>();
		srcFrame = null;
	}

	/**
	 * Creates a Frame from {@code p} and {@code r} which define its {@link #position()} and {@link #orientation()} (its
	 * {@link #scaling()} is set to 1).
	 * <p>
	 * See the {@link remixlab.dandelion.geom.Vec} and {@link remixlab.dandelion.geom.Rotation} documentations for
	 * convenient constructors and methods.
	 * <p>
	 * The Frame is defined in the world coordinate system (its {@link #referenceFrame()} is {@code null}). It has a
	 * {@code null} associated {@link #constraint()}.
	 */
	public Frame(Rotation r, Vec p) {
		if (r instanceof Quat)
			krnl = new FrameKernel3D(p, (Quat) r);
		else if (r instanceof Rot)
			krnl = new FrameKernel2D(p, (Rot) r);

		linkedFramesList = new ArrayList<Frame>();
		srcFrame = null;
	}

	protected Frame(Frame other) {
		if (other.is3D())
			krnl = new FrameKernel3D((FrameKernel3D) other.kernel());
		else
			krnl = new FrameKernel2D((FrameKernel2D) other.kernel());
		linkedFramesList = new ArrayList<Frame>();
		Iterator<Frame> iterator = other.linkedFramesList.iterator();
		while (iterator.hasNext())
			linkedFramesList.add(iterator.next());
		srcFrame = other.srcFrame;
	}

	@Override
	public Frame get() {
		return new Frame(this);
	}

	/**
	 * @return Frame kernel.
	 */
	public FrameKernel kernel() {
		return krnl;
	}

	protected void setKernel(FrameKernel k) {
		krnl = k;
	}

	/**
	 * @return true if frame is 2D.
	 */
	public boolean is2D() {
		return !is3D();
	}

	/**
	 * @return true if frame is 3D.
	 */
	public boolean is3D() {
		return rotation() instanceof Quat;
	}

	/**
	 * Returns the Frame scaling, defined with respect to the {@link #referenceFrame()}.
	 * <p>
	 * Use {@link #magnitude()} to get the result in world coordinates. These two values are identical when the
	 * {@link #referenceFrame()} is {@code null} (default).
	 * 
	 * @see #setScaling(float)
	 */
	public final float scaling() {
		return kernel().scaling();
	}

	/**
	 * @return the last frame the Frame was updated.
	 */
	public long lastUpdate() {
		return kernel().lastUpdate();
	}

	/**
	 * Returns the Frame translation, defined with respect to the {@link #referenceFrame()}.
	 * <p>
	 * Use {@link #position()} to get the result in world coordinates. These two values are identical when the
	 * {@link #referenceFrame()} is {@code null} (default).
	 * 
	 * @see #setTranslation(Vec)
	 * @see #setTranslationWithConstraint(Vec)
	 */
	public final Vec translation() {
		return kernel().translation();
	}

	/**
	 * Returns the Frame rotation, defined with respect to the {@link #referenceFrame()} (i.e, the current Rotation
	 * orientation).
	 * <p>
	 * Use {@link #orientation()} to get the result in world coordinates. These two values are identical when the
	 * {@link #referenceFrame()} is {@code null} (default).
	 * 
	 * @see #setRotation(Rotation)
	 * @see #setRotationWithConstraint(Rotation)
	 */
	public final Rotation rotation() {
		return kernel().rotation();
	}

	/**
	 * Returns the reference Frame, in which coordinates system the Frame is defined.
	 * <p>
	 * The Frame {@link #translation()}, {@link #rotation()} and {@link #scaling()} are defined with respect to the
	 * {@link #referenceFrame()} coordinate system. A {@code null} reference Frame (default value) means that the Frame is
	 * defined in the world coordinate system.
	 * <p>
	 * Use {@link #position()}, {@link #orientation()} and {@link #magnitude()} to recursively convert values along the
	 * reference Frame chain and to get values expressed in the world coordinate system. The values match when the
	 * reference Frame is {@code null}.
	 * <p>
	 * Use {@link #setReferenceFrame(Frame)} to set this value and create a Frame hierarchy. Convenient functions allow
	 * you to convert coordinates from one Frame to another: see {@link #coordinatesOf(Vec)},
	 * {@link #localCoordinatesOf(Vec)} , {@link #coordinatesOfIn(Vec, Frame)} and their inverse functions.
	 * <p>
	 * Vectors can also be converted using {@link #transformOf(Vec)}, {@link #transformOfIn(Vec, Frame)},
	 * {@link #localTransformOf(Vec)} and their inverse functions.
	 */
	public final Frame referenceFrame() {
		return kernel().referenceFrame();
	}

	/**
	 * Returns the current {@link remixlab.dandelion.constraint.Constraint} applied to the Frame.
	 * <p>
	 * A {@code null} value (default) means that no Constraint is used to filter the Frame translation and rotation.
	 * <p>
	 * See the Constraint class documentation for details.
	 */
	public Constraint constraint() {
		return kernel().constraint();
	}

	/**
	 * Links this frame (referred to as the requested frame) to {@code sourceFrame}, meaning that this frame will take
	 * (and share by reference) the {@link #translation()}, {@link #rotation()}, {@link #scaling()},
	 * {@link #referenceFrame()}, and {@link #constraint()} from the {@code sourceFrame}. This can be useful for some
	 * off-screen scenes, e.g., to link a frame defined in one scene to the Eye frame defined in other scene (see the
	 * CameraCrane example).
	 * <p>
	 * <b>Note:</b> Linking frames has the following properties:
	 * <ol>
	 * <li>A frame can be linked only to another frame (referred to as the source frame).</li>
	 * <li>A source frame can be linked by from many (requested) frames.</li>
	 * <li>A source frame can't be linked to another (source) frame, i.e., it can only receive links form other frames.</li>
	 * </ol>
	 * 
	 * @param sourceFrame
	 *          the frame to link this frame with.
	 * @return true if this frame can successfully being linked to the frame. False otherwise.
	 * 
	 * @see #linkFrom(Frame)
	 * @see #unlink()
	 * @see #unlinkFrom(Frame)
	 * @see #isLinked()
	 * @see #areLinkedTogether(Frame)
	 */
	public boolean linkTo(Frame sourceFrame) {
		// avoid loops
		if ((!linkedFramesList.isEmpty()) || sourceFrame.linkedFramesList.contains(this) || (sourceFrame == this))
			return false;

		if (sourceFrame.linkedFramesList.add(this)) {
			srcFrame = sourceFrame;
			setKernel(srcFrame.kernel());
			return true;
		}

		return false;
	}

	/**
	 * Attempts to link the {@code requestedFrame} to this frame.
	 * <p>
	 * See {@link #linkTo(Frame)} for the rules and terminology applying to the linking process.
	 * 
	 * @param requestedFrame
	 *          the frame that is requesting a link to this frame.
	 * @return true if the requested frame can successfully being linked to this frame. False otherwise.
	 * 
	 * @see #linkTo(Frame)
	 * @see #unlink()
	 * @see #unlinkFrom(Frame)
	 * @see #isLinked()
	 * @see #areLinkedTogether(Frame)
	 */
	public boolean linkFrom(Frame requestedFrame) {
		// avoid loops
		if ((!requestedFrame.linkedFramesList.isEmpty()) || linkedFramesList.contains(this) || (requestedFrame == this))
			return false;

		if (linkedFramesList.add(requestedFrame)) {
			requestedFrame.srcFrame = this;
			requestedFrame.setKernel(kernel());
			return true;
		}

		return false;
	}

	/**
	 * Unlinks this frame from its source frame. Does nothing if this frame is not linked to another frame.
	 * <p>
	 * Sets a new {@link #kernel()} for the frame with the same {@link #position()}, {@link #orientation()} and
	 * {@link #magnitude()} as the source frame. Sets {@link #referenceFrame()} to null and discards any
	 * {@link #constraint()}.
	 * <p>
	 * See {@link #linkTo(Frame)} for the rules and terminology applying to the linking process.
	 * 
	 * @return true if succeeded otherwise returns false.
	 * 
	 * @see #linkTo(Frame)
	 * @see #linkFrom(Frame)
	 * @see #unlinkFrom(Frame)
	 * @see #isLinked()
	 * @see #areLinkedTogether(Frame)
	 */
	public boolean unlink() {
		boolean result = false;
		if (srcFrame != null) {
			result = srcFrame.linkedFramesList.remove(this);
			if (result) {
				setKernel(is2D() ? new FrameKernel2D(srcFrame.position(), (Rot) srcFrame.orientation(), srcFrame.magnitude())
						: new FrameKernel3D(srcFrame.position(), (Quat) srcFrame.orientation(), srcFrame.magnitude()));
				setReferenceFrame(null);
				srcFrame = null;
			}
		}
		return result;
	}

	/**
	 * Unlinks the requested frame from this frame. Does nothing if the frames are not linked together (
	 * {@link #areLinkedTogether(Frame)}).
	 * <p>
	 * Sets a new {@link #kernel()} for the requested frame with the same {@link #position()}, {@link #orientation()} and
	 * {@link #magnitude()} as this Frame, but {@code null} {@link #referenceFrame()} and no {@link #constraint()}.
	 * <p>
	 * See {@link #linkTo(Frame)} for the rules and terminology applying to the linking process.
	 * 
	 * @return true if succeeded otherwise returns false.
	 * 
	 * @see #linkTo(Frame)
	 * @see #linkFrom(Frame)
	 * @see #unlink()
	 * @see #isLinked()
	 * @see #areLinkedTogether(Frame)
	 */
	public boolean unlinkFrom(Frame requestedFrame) {
		boolean result = false;
		if ((srcFrame == null) && (requestedFrame != this)) {
			result = linkedFramesList.remove(requestedFrame);
			if (result) {
				requestedFrame.setKernel(is2D() ? new FrameKernel2D(position(), (Rot) orientation(), magnitude())
						: new FrameKernel3D(position(), (Quat) orientation(), magnitude()));
				requestedFrame.setReferenceFrame(null);
				requestedFrame.srcFrame = null;
			}
		}
		return result;
	}

	/**
	 * Returns true if this frame is linked to a source frame or if this frame acts as the source frame of other frames.
	 * Otherwise returns false.
	 * <p>
	 * See {@link #linkTo(Frame)} for the rules and terminology applying to the linking process.
	 * 
	 * @see #linkTo(Frame)
	 * @see #linkFrom(Frame)
	 * @see #unlink()
	 * @see #unlinkFrom(Frame)
	 * @see #areLinkedTogether(Frame)
	 */
	public boolean isLinked() {
		if ((srcFrame != null) || (!linkedFramesList.isEmpty()))
			return true;
		return false;
	}

	/**
	 * Returns true if this frame is linked with {@code sourceFrame}. Otherwise returns false.
	 * <p>
	 * See {@link #linkTo(Frame)} for the rules and terminology applying to the linking process.
	 * 
	 * @see #linkTo(Frame)
	 * @see #linkFrom(Frame)
	 * @see #unlink()
	 * @see #unlinkFrom(Frame)
	 * @see #isLinked()
	 */
	public boolean areLinkedTogether(Frame sourceFrame) {
		if (sourceFrame == srcFrame)
			return true;
		if (linkedFramesList.contains(sourceFrame))
			return true;
		return false;
	}

	/**
	 * Sets the {@link #translation()} of the frame, locally defined with respect to the {@link #referenceFrame()}.
	 * <p>
	 * Use {@link #setPosition(Vec)} to define the world coordinates {@link #position()}. Use
	 * {@link #setTranslationWithConstraint(Vec)} to take into account the potential {@link #constraint()} of the Frame.
	 */
	public final void setTranslation(Vec t) {
		kernel().setTranslation(t);
	}

	/**
	 * Same as {@link #setTranslation(Vec)}, but with {@code float} parameters.
	 */
	public final void setTranslation(float x, float y) {
		setTranslation(new Vec(x, y));
	}

	/**
	 * Same as {@link #setTranslation(Vec)}, but with {@code float} parameters.
	 */
	public final void setTranslation(float x, float y, float z) {
		setTranslation(new Vec(x, y, z));
	}

	/**
	 * Sets the {@link #scaling()} of the frame, locally defined with respect to the {@link #referenceFrame()}.
	 * <p>
	 * Use {@link #setMagnitude(float)} to define the world coordinates {@link #magnitude()}.
	 */
	public final void setScaling(float s) {
		kernel().setScaling(s);
	}

	/**
	 * Same as {@link #setTranslation(Vec)}, but if there's a {@link #constraint()} it is satisfied.
	 * 
	 * @see #setRotationWithConstraint(Rotation)
	 * @see #setPositionWithConstraint(Vec)
	 * @see #setScaling(float)
	 */
	public final void setTranslationWithConstraint(Vec translation) {
		Vec deltaT = Vec.subtract(translation, this.translation());
		if (constraint() != null)
			deltaT = constraint().constrainTranslation(deltaT, this);

		kernel().translate(deltaT);
	}

	/**
	 * Set the current rotation. See the different {@link remixlab.dandelion.geom.Rotation} constructors.
	 * <p>
	 * Sets the {@link #rotation()} of the Frame, locally defined with respect to the {@link #referenceFrame()}.
	 * <p>
	 * Use {@link #setOrientation(Rotation)} to define the world coordinates {@link #orientation()}. The potential
	 * {@link #constraint()} of the Frame is not taken into account, use {@link #setRotationWithConstraint(Rotation)}
	 * instead.
	 * 
	 * @see #setRotationWithConstraint(Rotation)
	 * @see #rotation()
	 * @see #setTranslation(Vec)
	 */
	public final void setRotation(Rotation r) {
		kernel().setRotation(r);
	}

	/**
	 * Same as {@link #setRotation(Rotation)} but with {@code float} Rotation parameters.
	 */
	public final void setRotation(float x, float y, float z, float w) {
		setRotation(new Quat(x, y, z, w));
	}

	/**
	 * Defines a 2D {@link remixlab.dandelion.geom.Rotation}.
	 * 
	 * @param a
	 *          angle
	 */
	public final void setRotation(float a) {
		if (is3D())
			throw new RuntimeException("Scene should be in 2d for this method to work");
		setRotation(new Rot(a));
	}

	/**
	 * Same as {@link #setRotation(Rotation)}, but if there's a {@link #constraint()} it's satisfied.
	 * 
	 * @see #setTranslationWithConstraint(Vec)
	 * @see #setOrientationWithConstraint(Rotation)
	 * @see #setScaling(float)
	 */
	public final void setRotationWithConstraint(Rotation rotation) {
		Rotation deltaQ;

		if (is3D())
			deltaQ = Quat.compose(rotation().inverse(), rotation);
		else
			deltaQ = Rot.compose(rotation().inverse(), rotation);

		if (constraint() != null)
			deltaQ = constraint().constrainRotation(deltaQ, this);

		deltaQ.normalize(); // Prevent numerical drift

		kernel().rotate(deltaQ);
	}

	/**
	 * Sets the {@link #referenceFrame()} of the Frame.
	 * <p>
	 * The Frame {@link #translation()}, {@link #rotation()} and {@link #scaling()} are then defined in the
	 * {@link #referenceFrame()} coordinate system.
	 * <p>
	 * Use {@link #position()}, {@link #orientation()} and {@link #magnitude()} to express these in the world coordinate
	 * system.
	 * <p>
	 * Using this method, you can create a hierarchy of Frames. This hierarchy needs to be a tree, which root is the world
	 * coordinate system (i.e., {@code null} {@link #referenceFrame()}). No action is performed if setting
	 * {@code refFrame} as the {@link #referenceFrame()} would create a loop in the Frame hierarchy.
	 */
	public final void setReferenceFrame(Frame rFrame) {
		kernel().setReferenceFrame(rFrame);
	}

	/**
	 * Sets the {@link #constraint()} attached to the Frame.
	 * <p>
	 * A {@code null} value means no constraint.
	 */
	public void setConstraint(Constraint c) {
		kernel().setConstraint(c);
	}

	/**
	 * Returns the orientation of the Frame, defined in the world coordinate system.
	 * 
	 * @see #position()
	 * @see #magnitude()
	 * @see #setOrientation(Rotation)
	 * @see #rotation()
	 */
	public final Rotation orientation() {
		Rotation res = rotation().get();
		Frame fr = referenceFrame();
		while (fr != null) {
			if (is3D())
				res = Quat.compose(fr.rotation(), res);
			else
				res = Rot.compose(fr.rotation(), res);
			fr = fr.referenceFrame();
		}
		return res;
	}

	/**
	 * Sets the {@link #position()} of the Frame, defined in the world coordinate system.
	 * <p>
	 * Use {@link #setTranslation(Vec)} to define the local Frame translation (with respect to the
	 * {@link #referenceFrame()}). The potential {@link #constraint()} of the Frame is not taken into account, use
	 * {@link #setPositionWithConstraint(Vec)} instead.
	 */
	public final void setPosition(Vec p) {
		if (referenceFrame() != null)
			setTranslation(referenceFrame().coordinatesOf(p));
		else
			setTranslation(p);
	}

	/**
	 * Same as {@link #setPosition(Vec)}, but with {@code float} parameters.
	 */
	public final void setPosition(float x, float y) {
		setPosition(new Vec(x, y));
	}

	/**
	 * Same as {@link #setPosition(Vec)}, but with {@code float} parameters.
	 */
	public final void setPosition(float x, float y, float z) {
		setPosition(new Vec(x, y, z));
	}

	/**
	 * Sets the {@link #magnitude()} of the Frame, defined in the world coordinate system.
	 * <p>
	 * Use {@link #setScaling(float)} to define the local Frame scaling (with respect to the {@link #referenceFrame()}).
	 */
	public final void setMagnitude(float m) {
		Frame refFrame = referenceFrame();
		if (refFrame != null)
			setScaling(m / refFrame.magnitude());
		else
			setScaling(m);
	}

	/**
	 * Returns the magnitude of the Frame, defined in the world coordinate system.
	 * 
	 * @see #orientation()
	 * @see #position()
	 * @see #setPosition(Vec)
	 * @see #translation()
	 */
	public float magnitude() {
		if (referenceFrame() != null)
			return referenceFrame().magnitude() * scaling();
		else
			return scaling();
	}

	/**
	 * Same as {@link #setPosition(Vec)}, but if there's a {@link #constraint()} it is satisfied (without modifying
	 * {@code position}).
	 * 
	 * @see #setOrientationWithConstraint(Rotation)
	 * @see #setTranslationWithConstraint(Vec)
	 */
	public final void setPositionWithConstraint(Vec position) {
		if (referenceFrame() != null)
			position = referenceFrame().coordinatesOf(position);

		setTranslationWithConstraint(position);
	}

	/**
	 * Sets the {@link #orientation()} of the Frame, defined in the world coordinate system.
	 * <p>
	 * Use {@link #setRotation(Rotation)} to define the local frame rotation (with respect to the
	 * {@link #referenceFrame()}). The potential {@link #constraint()} of the Frame is not taken into account, use
	 * {@link #setOrientationWithConstraint(Rotation)} instead.
	 */
	public final void setOrientation(Rotation q) {
		if (referenceFrame() != null) {
			if (is3D())
				setRotation(Quat.compose(referenceFrame().orientation().inverse(), q));
			else
				setRotation(Rot.compose(referenceFrame().orientation().inverse(), q));
		}
		else
			setRotation(q);
	}

	/**
	 * Same as {@link #setOrientation(Rotation)}, but with {@code float} parameters.
	 */
	public final void setOrientation(float x, float y, float z, float w) {
		setOrientation(new Quat(x, y, z, w));
	}

	/**
	 * Same as {@link #setOrientation(Rotation)}, but if there's a {@link #constraint()} it is satisfied (without
	 * modifying {@code orientation}).
	 * 
	 * @see #setPositionWithConstraint(Vec)
	 * @see #setRotationWithConstraint(Rotation)
	 */
	public final void setOrientationWithConstraint(Rotation orientation) {
		if (referenceFrame() != null) {
			if (is3D())
				orientation = Quat.compose(referenceFrame().orientation().inverse(), orientation);
			else
				orientation = Rot.compose(referenceFrame().orientation().inverse(), orientation);
		}

		setRotationWithConstraint(orientation);
	}

	/**
	 * Returns the position of the Frame, defined in the world coordinate system.
	 * 
	 * @see #orientation()
	 * @see #magnitude()
	 * @see #setPosition(Vec)
	 * @see #translation()
	 */
	public final Vec position() {
		return inverseCoordinatesOf(new Vec(0, 0, 0));
	}

	/**
	 * Translates the Frame according to {@code t}, locally defined with respect to the {@link #referenceFrame()}.
	 * <p>
	 * If there's a {@link #constraint()} it is satisfied. Hence the translation actually applied to the Frame may differ
	 * from {@code t} (since it can be filtered by the {@link #constraint()}). Use {@link #setTranslation(Vec)} to
	 * directly translate the Frame without taking the {@link #constraint()} into account.
	 * 
	 * @see #rotate(Rotation)
	 * @see #scale(float)
	 */
	public final void translate(Vec t) {
		if (constraint() != null)
			kernel().translate(constraint().constrainTranslation(t, this));
		else
			kernel().translate(t);
	}

	/**
	 * Same as {@link #translate(Vec)} but with {@code float} parameters.
	 */
	public final void translate(float x, float y, float z) {
		translate(new Vec(x, y, z));
	}

	/**
	 * Same as {@link #translate(Vec)} but with {@code float} parameters.
	 */
	public final void translate(float x, float y) {
		translate(new Vec(x, y));
	}

	/**
	 * Scales the Frame according to {@code s}, locally defined with respect to the {@link #referenceFrame()}.
	 * 
	 * @see #rotate(Rotation)
	 * @see #translate(Vec)
	 */
	public void scale(float s) {
		kernel().scale(s);
	}

	/**
	 * Rotates the Frame by {@code r} (defined in the Frame coordinate system): {@code rotation().compose(r)}.
	 * <p>
	 * If there's a {@link #constraint()} it is satisfied. Hence the rotation actually applied to the Frame may differ
	 * from {@code q} (since it can be filtered by the {@link #constraint()}). Use {@link #setRotation(Rotation)} to
	 * directly rotate the Frame without taking the {@link #constraint()} into account.
	 * 
	 * @see #translate(Vec)
	 */
	public final void rotate(Rotation r) {
		if (constraint() != null)
			kernel().rotate(constraint().constrainRotation(r, this));
		else
			kernel().rotate(r);
	}

	/**
	 * Same as {@link #rotate(Rotation)} but with {@code float} rotation parameters.
	 */
	public final void rotate(float x, float y, float z, float w) {
		rotate(new Quat(x, y, z, w));
	}

	/**
	 * Makes the Frame {@link #rotate(Rotation)} by {@code rotation} around {@code point}.
	 * <p>
	 * {@code point} is defined in the world coordinate system, while the {@code rotation} axis is defined in the Frame
	 * coordinate system.
	 * <p>
	 * If the Frame has a {@link #constraint()}, {@code rotation} is first constrained using
	 * {@link remixlab.dandelion.constraint.Constraint#constrainRotation(Rotation, Frame)}. Hence the rotation actually
	 * applied to the Frame may differ from {@code rotation} (since it can be filtered by the {@link #constraint()}).
	 * <p>
	 * The translation which results from the filtered rotation around {@code point} is then computed and filtered using
	 * {@link remixlab.dandelion.constraint.Constraint#constrainTranslation(Vec, Frame)}.
	 */
	public void rotateAroundPoint(Rotation rotation, Vec point) {
		if (constraint() != null)
			rotation = constraint().constrainRotation(rotation, this);

		this.rotation().compose(rotation);
		if (is3D())
			this.rotation().normalize(); // Prevents numerical drift

		Rotation q;
		if (is3D())
			// q = new Quat(inverseTransformOf(((Quat) rotation).axis()), rotation.angle());//orig
			q = new Quat(orientation().rotate(((Quat) rotation).axis()), rotation.angle());
		else
			q = new Rot(rotation.angle());
		Vec t = Vec.add(point, q.rotate(Vec.subtract(position(), point)));
		t.subtract(translation());
		if (constraint() != null)
			kernel().translate(constraint().constrainTranslation(t, this));
		else
			kernel().translate(t);
	}

	/**
	 * Convenience function that simply calls {@code alignWithFrame(frame, false, 0.85f)}
	 */
	public final void alignWithFrame(Frame frame) {
		alignWithFrame(frame, false, 0.85f);
	}

	/**
	 * Convenience function that simply calls {@code alignWithFrame(frame, move, 0.85f)}
	 */
	public final void alignWithFrame(Frame frame, boolean move) {
		alignWithFrame(frame, move, 0.85f);
	}

	/**
	 * Convenience function that simply calls {@code alignWithFrame(frame, false, threshold)}
	 */
	public final void alignWithFrame(Frame frame, float threshold) {
		alignWithFrame(frame, false, threshold);
	}

	/**
	 * Aligns the Frame with {@code frame}, so that two of their axis are parallel.
	 * <p>
	 * If one of the X, Y and Z axis of the Frame is almost parallel to any of the X, Y, or Z axis of {@code frame}, the
	 * Frame is rotated so that these two axis actually become parallel.
	 * <p>
	 * If, after this first rotation, two other axis are also almost parallel, a second alignment is performed. The two
	 * frames then have identical orientations, up to 90 degrees rotations.
	 * <p>
	 * {@code threshold} measures how close two axis must be to be considered parallel. It is compared with the absolute
	 * values of the dot product of the normalized axis.
	 * <p>
	 * When {@code move} is set to {@code true}, the Frame {@link #position()} is also affected by the alignment. The new
	 * Frame {@link #position()} is such that the {@code frame} frame position (computed with {@link #coordinatesOf(Vec)},
	 * in the Frame coordinates system) does not change.
	 * <p>
	 * {@code frame} may be {@code null} and then represents the world coordinate system (same convention than for the
	 * {@link #referenceFrame()}).
	 */
	public final void alignWithFrame(Frame frame, boolean move, float threshold) {
		if (is3D()) {
			Vec[][] directions = new Vec[2][3];

			for (int d = 0; d < 3; ++d) {
				Vec dir = new Vec((d == 0) ? 1.0f : 0.0f, (d == 1) ? 1.0f : 0.0f, (d == 2) ? 1.0f : 0.0f);
				if (frame != null)
					directions[0][d] = frame.orientation().rotate(dir);
				else
					directions[0][d] = dir;
				directions[1][d] = orientation().rotate(dir);
			}

			float maxProj = 0.0f;
			float proj;
			short[] index = new short[2];
			index[0] = index[1] = 0;

			Vec vec = new Vec(0.0f, 0.0f, 0.0f);
			for (int i = 0; i < 3; ++i) {
				for (int j = 0; j < 3; ++j) {
					vec.set(directions[0][i]);
					proj = Math.abs(vec.dot(directions[1][j]));
					if ((proj) >= maxProj) {
						index[0] = (short) i;
						index[1] = (short) j;
						maxProj = proj;
					}
				}
			}
			Frame old = new Frame(this); // correct line
			// VFrame old = this.get();// this call the get overloaded method and hence add the frame to the mouse grabber

			vec.set(directions[0][index[0]]);
			float coef = vec.dot(directions[1][index[1]]);

			if (Math.abs(coef) >= threshold) {
				vec.set(directions[0][index[0]]);
				Vec axis = vec.cross(directions[1][index[1]]);
				float angle = (float) Math.asin(axis.magnitude());
				if (coef >= 0.0)
					angle = -angle;
				// setOrientation(Quaternion(axis, angle) * orientation());
				Quat q = new Quat(axis, angle);
				q = Quat.multiply(((Quat) rotation()).inverse(), q);
				q = Quat.multiply(q, (Quat) orientation());
				rotate(q);

				// Try to align an other axis direction
				short d = (short) ((index[1] + 1) % 3);
				Vec dir = new Vec((d == 0) ? 1.0f : 0.0f, (d == 1) ? 1.0f : 0.0f, (d == 2) ? 1.0f : 0.0f);
				dir = orientation().rotate(dir);

				float max = 0.0f;
				for (int i = 0; i < 3; ++i) {
					vec.set(directions[0][i]);
					proj = Math.abs(vec.dot(dir));
					if (proj > max) {
						index[0] = (short) i;
						max = proj;
					}
				}

				if (max >= threshold) {
					vec.set(directions[0][index[0]]);
					axis = vec.cross(dir);
					angle = (float) Math.asin(axis.magnitude());
					vec.set(directions[0][index[0]]);
					if (vec.dot(dir) >= 0.0)
						angle = -angle;
					// setOrientation(Quaternion(axis, angle) * orientation());
					q.fromAxisAngle(axis, angle);
					q = Quat.multiply(((Quat) rotation()).inverse(), q);
					q = Quat.multiply(q, (Quat) orientation());
					rotate(q);
				}
			}
			if (move) {
				Vec center = new Vec(0.0f, 0.0f, 0.0f);
				if (frame != null)
					center = frame.position();

				vec = Vec.subtract(center, inverseTransformOf(old.coordinatesOf(center)));
				vec.subtract(translation());
				translate(vec);
			}
		}
		else {
			Rot o;
			if (frame != null)
				o = (Rot) frame.orientation();
			else
				o = new Rot();
			o.normalize(true);
			((Rot) orientation()).normalize(true);

			float angle = 0; // if( (-QUARTER_PI <= delta) && (delta < QUARTER_PI) )
			float delta = Math.abs(o.angle() - orientation().angle());

			if (((float)Math.PI / 4 <= delta) && (delta < ((float)Math.PI * 3 / 4)))
				angle = (float)Math.PI / 2;
			else if ((((float)Math.PI * 3 / 4) <= delta) && (delta < ((float)Math.PI * 5 / 4)))
				angle = (float)Math.PI;
			else if ((((float)Math.PI * 5 / 4) <= delta) && (delta < ((float)Math.PI * 7 / 4)))
				angle = (float)Math.PI * 3 / 2;

			angle += o.angle();
			Rot other = new Rot(angle);
			other.normalize();
			setOrientation(other);
		}
	}

	/**
	 * Translates the Frame so that its {@link #position()} lies on the line defined by {@code origin} and
	 * {@code direction} (defined in the world coordinate system).
	 * <p>
	 * Simply uses an orthogonal projection. {@code direction} does not need to be normalized.
	 */
	public final void projectOnLine(Vec origin, Vec direction) {
		Vec shift = Vec.subtract(origin, position());
		Vec proj = shift;
		proj = Vec.projectVectorOnAxis(proj, direction);
		translate(Vec.subtract(shift, proj));
	}

	/**
	 * Rotates the frame so that its {@link #xAxis()} becomes {@code axis} defined in the world coordinate system.
	 * <p>
	 * <b>Attention:</b> this rotation is not uniquely defined. See {@link remixlab.dandelion.geom.Quat#fromTo(Vec, Vec)}.
	 * 
	 * @see #xAxis()
	 * @see #setYAxis(Vec)
	 * @see #setZAxis(Vec)
	 */
	public void setXAxis(Vec axis) {
		if (is3D())
			rotate(new Quat(new Vec(1.0f, 0.0f, 0.0f), transformOf(axis)));
		else
			rotate(new Rot(new Vec(1.0f, 0.0f, 0.0f), transformOf(axis)));
	}

	/**
	 * Rotates the frame so that its {@link #yAxis()} becomes {@code axis} defined in the world coordinate system.
	 * <p>
	 * <b>Attention:</b> this rotation is not uniquely defined. See {@link remixlab.dandelion.geom.Quat#fromTo(Vec, Vec)}.
	 * 
	 * @see #yAxis()
	 * @see #setYAxis(Vec)
	 * @see #setZAxis(Vec)
	 */
	public void setYAxis(Vec axis) {
		if (is3D())
			rotate(new Quat(new Vec(0.0f, 1.0f, 0.0f), transformOf(axis)));
		else
			rotate(new Rot(new Vec(0.0f, 1.0f, 0.0f), transformOf(axis)));
	}

	/**
	 * Rotates the frame so that its {@link #zAxis()} becomes {@code axis} defined in the world coordinate system.
	 * <p>
	 * <b>Attention:</b> this rotation is not uniquely defined. See {@link remixlab.dandelion.geom.Quat#fromTo(Vec, Vec)}.
	 * 
	 * @see #zAxis()
	 * @see #setYAxis(Vec)
	 * @see #setZAxis(Vec)
	 */
	public void setZAxis(Vec axis) {
		if (is3D())
			rotate(new Quat(new Vec(0.0f, 0.0f, 1.0f), transformOf(axis)));
		else
			System.out.println("There's no point in setting the Z axis in 2D");
	}

	public Vec xAxis() {
		return xAxis(true);
	}

	/**
	 * Returns the x-axis of the frame, represented as a normalized vector defined in the world coordinate system.
	 * 
	 * @see #setXAxis(Vec)
	 * @see #yAxis()
	 * @see #zAxis()
	 */
	public Vec xAxis(boolean positive) {
		Vec res;
		if (is3D()) {
			res = inverseTransformOf(new Vec(positive ? 1.0f : -1.0f, 0.0f, 0.0f));
			if (Util.diff(magnitude(), 1))
				res.normalize();
		}
		else {
			res = inverseTransformOf(new Vec(positive ? 1.0f : -1.0f, 0.0f));
			if (Util.diff(magnitude(), 1))
				res.normalize();
		}
		return res;
	}

	public Vec yAxis() {
		return yAxis(true);
	}

	/**
	 * Returns the y-axis of the frame, represented as a normalized vector defined in the world coordinate system.
	 * 
	 * @see #setYAxis(Vec)
	 * @see #xAxis()
	 * @see #zAxis()
	 */
	public Vec yAxis(boolean positive) {
		Vec res;
		if (is3D()) {
			res = inverseTransformOf(new Vec(0.0f, positive ? 1.0f : -1.0f, 0.0f));
			if (Util.diff(magnitude(), 1))
				res.normalize();
		}
		else {
			res = inverseTransformOf(new Vec(0.0f, positive ? 1.0f : -1.0f));
			if (Util.diff(magnitude(), 1))
				res.normalize();
		}
		return res;
	}

	public Vec zAxis() {
		return zAxis(true);
	}

	/**
	 * Returns the z-axis of the frame, represented as a normalized vector defined in the world coordinate system.
	 * 
	 * @see #setZAxis(Vec)
	 * @see #xAxis()
	 * @see #yAxis()
	 */
	public Vec zAxis(boolean positive) {
		Vec res = new Vec();
		if (is3D()) {
			res = inverseTransformOf(new Vec(0.0f, 0.0f, positive ? 1.0f : -1.0f));
			if (Util.diff(magnitude(), 1))
				res.normalize();
		}
		else
			System.out.println("There's no point in setting the Z axis in 2D");
		return res;
	}

	/**
	 * Returns the {@link remixlab.dandelion.geom.Mat} associated with this Frame.
	 * <p>
	 * This method could be used in conjunction with {@code applyMatrix()} to modify the
	 * {@link remixlab.dandelion.core.AbstractScene#modelView()} matrix from a Frame hierarchy. For example, with this
	 * Frame hierarchy:
	 * <p>
	 * {@code Frame body = new Frame();} <br>
	 * {@code Frame leftArm = new Frame();} <br>
	 * {@code Frame rightArm = new Frame();} <br>
	 * {@code leftArm.setReferenceFrame(body);} <br>
	 * {@code rightArm.setReferenceFrame(body);} <br>
	 * <p>
	 * The associated drawing code should look like:
	 * <p>
	 * {@code scene.pushModelView();}<br>
	 * {@code scene.applyMatrix(body.matrix());} <br>
	 * {@code drawBody();} <br>
	 * {@code scene.pushModelView();} <br>
	 * {@code scene.applyMatrix(leftArm.matrix());} <br>
	 * {@code drawArm();} <br>
	 * {@code scene.popModelView();} <br>
	 * {@code scene.pushModelView();} <br>
	 * {@code scene.applyMatrix(rightArm.matrix());} <br>
	 * {@code drawArm();} <br>
	 * {@code scene.popModelView();} <br>
	 * {@code scene.popModelView();} <br>
	 * <p>
	 * Note the use of nested {@code pushModelView()} and {@code popModelView()} blocks to represent the frame hierarchy:
	 * {@code leftArm} and {@code rightArm} are both correctly drawn with respect to the {@code body} coordinate system.
	 * <p>
	 * <b>Attention:</b> In Processing this technique is inefficient because {@code papplet.applyMatrix} will try to
	 * calculate the inverse of the transform. Avoid it whenever possible and instead use
	 * {@link #applyTransformation(AbstractScene)} which always is very efficient.
	 * <p>
	 * This matrix only represents the local Frame transformation (i.e., with respect to the {@link #referenceFrame()}).
	 * Use {@link #worldMatrix()} to get the full Frame transformation matrix (i.e., from the world to the Frame
	 * coordinate system). These two match when the {@link #referenceFrame()} is {@code null}.
	 * <p>
	 * The result is only valid until the next call to {@code matrix()} or {@link #worldMatrix()}. Use it immediately (as
	 * above).
	 * 
	 * @see #applyTransformation(AbstractScene)
	 */
	public final Mat matrix() {
		Mat pM = new Mat();

		pM = rotation().matrix();

		pM.mat[12] = translation().vec[0];
		pM.mat[13] = translation().vec[1];
		pM.mat[14] = translation().vec[2];

		if (scaling() != 1) {
			pM.setM00(pM.m00() * scaling());
			pM.setM10(pM.m10() * scaling());
			pM.setM20(pM.m20() * scaling());

			pM.setM01(pM.m01() * scaling());
			pM.setM11(pM.m11() * scaling());
			pM.setM21(pM.m21() * scaling());

			pM.setM02(pM.m02() * scaling());
			pM.setM12(pM.m12() * scaling());
			pM.setM22(pM.m22() * scaling());
		}

		return pM;
	}

	/**
	 * Convenience function that simply calls {@code scn.applyTransformation(this)}.
	 * 
	 * @see #matrix()
	 * @see remixlab.dandelion.core.AbstractScene#applyTransformation(Frame)
	 */
	public void applyTransformation(AbstractScene scn) {
		scn.applyTransformation(this);
	}

	/**
	 * Convenience function that simply calls {@code scn.applyWorldTransformation(this)}.
	 * 
	 * @see #worldMatrix()
	 * @see remixlab.dandelion.core.AbstractScene#applyWorldTransformation(Frame)
	 */
	public void applyWorldTransformation(AbstractScene scn) {
		scn.applyWorldTransformation(this);
	}

	/**
	 * Returns the transformation matrix represented by the Frame.
	 * <p>
	 * This method should be used in conjunction with {@code applyMatrix()} to modify the
	 * {@link remixlab.dandelion.core.AbstractScene#modelView()} matrix from a Frame:
	 * <p>
	 * {@code // Here the modelview matrix corresponds to the world coordinate system.} <br>
	 * {@code Frame fr = new Frame(pos, Rotation(from, to));} <br>
	 * {@code scene.pushModelView();} <br>
	 * {@code scene.applyMatrix(worldMatrix());} <br>
	 * {@code // draw object in the fr coordinate system.} <br>
	 * {@code scene.popModelView();} <br>
	 * <p>
	 * This matrix represents the global Frame transformation: the entire {@link #referenceFrame()} hierarchy is taken
	 * into account to define the Frame transformation from the world coordinate system. Use {@link #matrix()} to get the
	 * local Frame transformation matrix (i.e. defined with respect to the {@link #referenceFrame()}). These two match
	 * when the {@link #referenceFrame()} is {@code null}.
	 * <p>
	 * <b>Attention:</b> The result is only valid until the next call to {@link #matrix()} or {@code worldMatrix()}. Use
	 * it immediately (as above).
	 */
	public final Mat worldMatrix() {
		if (referenceFrame() != null) {
			final Frame fr = new Frame();
			fr.setTranslation(position());
			fr.setRotation(orientation());
			fr.setScaling(scaling());
			return fr.matrix();
		} else
			return matrix();
	}

	/**
	 * Convenience function that simply calls {@code fromMatrix(pM, 1))}.
	 * 
	 * @see #fromMatrix(Mat, float)
	 */
	public final void fromMatrix(Mat pM) {
		fromMatrix(pM, 1);
	}

	/**
	 * Sets the Frame from a Mat representation: rotation in the upper left 3x3 matrix and translation on the last column.
	 * Scaling is defined separately in {@code scl}.
	 * <p>
	 * Hence, if a code fragment looks like:
	 * <p>
	 * {@code float [] m = new float [16]; m[0]=...;} <br>
	 * {@code gl.glMultMatrixf(m);} <br>
	 * <p>
	 * It is equivalent to write:
	 * <p>
	 * {@code Frame fr = new Frame();} <br>
	 * {@code fr.fromMatrix(m);} <br>
	 * {@code applyMatrix(fr.matrix());} <br>
	 * <p>
	 * Using this conversion, you can benefit from the powerful Frame transformation methods to translate points and
	 * vectors to and from the Frame coordinate system to any other Frame coordinate system (including the world
	 * coordinate system). See {@link #coordinatesOf(Vec)} and {@link #transformOf(Vec)}.
	 */
	public final void fromMatrix(Mat pM, float scl) {
		if (Util.zero(pM.mat[15])) {
			System.out.println("Doing nothing: pM.mat[15] should be non-zero!");
			return;
		}

		translation().vec[0] = pM.mat[12] / pM.mat[15];
		translation().vec[1] = pM.mat[13] / pM.mat[15];
		translation().vec[2] = pM.mat[14] / pM.mat[15];

		float[][] r = new float[3][3];

		r[0][0] = pM.mat[0] / pM.mat[15];
		r[0][1] = pM.mat[4] / pM.mat[15];
		r[0][2] = pM.mat[8] / pM.mat[15];
		r[1][0] = pM.mat[1] / pM.mat[15];
		r[1][1] = pM.mat[5] / pM.mat[15];
		r[1][2] = pM.mat[9] / pM.mat[15];
		r[2][0] = pM.mat[2] / pM.mat[15];
		r[2][1] = pM.mat[6] / pM.mat[15];
		r[2][2] = pM.mat[10] / pM.mat[15];

		setScaling(scl);// calls kernel().modified() :P

		if (scaling() != 1) {
			r[0][0] = r[0][0] / scaling();
			r[1][0] = r[1][0] / scaling();
			r[2][0] = r[2][0] / scaling();

			r[0][1] = r[0][1] / scaling();
			r[1][1] = r[1][1] / scaling();
			r[2][1] = r[2][1] / scaling();

			if (this.is3D()) {
				r[0][2] = r[0][2] / scaling();
				r[1][2] = r[1][2] / scaling();
				r[2][2] = r[2][2] / scaling();
			}
		}

		Vec x = new Vec(r[0][0], r[1][0], r[2][0]);
		Vec y = new Vec(r[0][1], r[1][1], r[2][1]);
		Vec z = new Vec(r[0][2], r[1][2], r[2][2]);

		rotation().fromRotatedBasis(x, y, z);
	}

	/**
	 * Returns a Frame representing the inverse of the Frame space transformation.
	 * <p>
	 * The the new Frame {@link #rotation()} is the {@link remixlab.dandelion.geom.Rotation#inverse()} of the original
	 * rotation. Its {@link #translation()} is the negated inverse rotated image of the original translation. Its
	 * {@link #scaling()} is 1 / original scaling.
	 * <p>
	 * If a Frame is considered as a space rigid transformation, i.e., translation and rotation, but no scaling
	 * (scaling=1), the inverse() Frame performs the inverse transformation.
	 * <p>
	 * Only the local Frame transformation (i.e., defined with respect to the {@link #referenceFrame()}) is inverted. Use
	 * {@link #worldInverse()} for a global inverse.
	 * <p>
	 * The resulting Frame has the same {@link #referenceFrame()} as the Frame and a {@code null} {@link #constraint()}.
	 */
	public final Frame inverse() {
		Frame fr = new Frame(Vec.multiply(rotation().inverseRotate(translation()), -1), rotation().inverse(), 1 / scaling());
		fr.setReferenceFrame(referenceFrame());
		return fr;
	}

	/**
	 * 
	 * Returns the {@link #inverse()} of the Frame world transformation.
	 * <p>
	 * The {@link #orientation()} of the new Frame is the {@link remixlab.dandelion.geom.Quat#inverse()} of the original
	 * orientation. Its {@link #position()} is the negated and inverse rotated image of the original position. The
	 * {@link #magnitude()} is the the original magnitude multiplicative inverse.
	 * <p>
	 * The result Frame has a {@code null} {@link #referenceFrame()} and a {@code null} {@link #constraint()}.
	 * <p>
	 * Use {@link #inverse()} for a local (i.e., with respect to {@link #referenceFrame()}) transformation inverse.
	 */
	public final Frame worldInverse() {
		return (new Frame(Vec.multiply(orientation().inverseRotate(position()), -1), orientation().inverse(),
				1 / magnitude()));
	}

	/**
	 * Returns the Frame coordinates of the point whose position in the {@code from} coordinate system is {@code src}
	 * (converts from {@code from} to Frame).
	 * <p>
	 * {@link #coordinatesOfIn(Vec, Frame)} performs the inverse transformation.
	 */
	public final Vec coordinatesOfFrom(Vec src, Frame from) {
		if (this == from)
			return src;
		else if (referenceFrame() != null)
			return localCoordinatesOf(referenceFrame().coordinatesOfFrom(src, from));
		else
			return localCoordinatesOf(from.inverseCoordinatesOf(src));
	}

	/**
	 * Returns the {@code in} coordinates of the point whose position in the Frame coordinate system is {@code src}
	 * (converts from Frame to {@code in}).
	 * <p>
	 * {@link #coordinatesOfFrom(Vec, Frame)} performs the inverse transformation.
	 */
	public final Vec coordinatesOfIn(Vec src, Frame in) {
		Frame fr = this;
		Vec res = src;
		while ((fr != null) && (fr != in)) {
			res = fr.localInverseCoordinatesOf(res);
			fr = fr.referenceFrame();
		}

		if (fr != in)
			// in was not found in the branch of this, res is now expressed in the world
			// coordinate system. Simply convert to in coordinate system.
			res = in.coordinatesOf(res);

		return res;
	}

	/**
	 * Returns the Frame transform of the vector whose coordinates in the {@code from} coordinate system is {@code src}
	 * (converts vectors from {@code from} to Frame).
	 * <p>
	 * {@link #transformOfIn(Vec, Frame)} performs the inverse transformation.
	 */
	public final Vec transformOfFrom(Vec src, Frame from) {
		if (this == from)
			return src;
		else if (referenceFrame() != null)
			return localTransformOf(referenceFrame().transformOfFrom(src, from));
		else
			return localTransformOf(from.inverseTransformOf(src));
	}

	/**
	 * Returns the {@code in} transform of the vector whose coordinates in the Frame coordinate system is {@code src}
	 * (converts vectors from Frame to {@code in}).
	 * <p>
	 * {@link #transformOfFrom(Vec, Frame)} performs the inverse transformation.
	 */
	public final Vec transformOfIn(Vec src, Frame in) {
		Frame fr = this;
		Vec res = src;
		while ((fr != null) && (fr != in)) {
			res = fr.localInverseTransformOf(res);
			fr = fr.referenceFrame();
		}

		if (fr != in)
			// in was not found in the branch of this, res is now expressed in
			// the world
			// coordinate system. Simply convert to in coordinate system.
			res = in.transformOf(res);

		return res;
	}

	/**
	 * Returns the Frame coordinates of a point {@code src} defined in the {@link #referenceFrame()} coordinate system
	 * (converts from {@link #referenceFrame()} to Frame).
	 * <p>
	 * {@link #localInverseCoordinatesOf(Vec)} performs the inverse conversion.
	 * 
	 * @see #localTransformOf(Vec)
	 */
	public final Vec localCoordinatesOf(Vec src) {
		return Vec.divide(rotation().inverseRotate(Vec.subtract(src, translation())), scaling());
	}

	/**
	 * Returns the Frame coordinates of a point {@code src} defined in the world coordinate system (converts from world to
	 * Frame).
	 * <p>
	 * {@link #inverseCoordinatesOf(Vec)} performs the inverse conversion. {@link #transformOf(Vec)} converts vectors
	 * instead of coordinates.
	 */
	public final Vec coordinatesOf(Vec src) {
		if (referenceFrame() != null)
			return localCoordinatesOf(referenceFrame().coordinatesOf(src));
		else
			return localCoordinatesOf(src);
	}

	/**
	 * Returns the {@link #referenceFrame()} coordinates of a point {@code src} defined in the Frame coordinate system
	 * (converts from Frame to {@link #referenceFrame()}).
	 * <p>
	 * {@link #localCoordinatesOf(Vec)} performs the inverse conversion.
	 * 
	 * @see #localInverseTransformOf(Vec)
	 */
	public final Vec localInverseCoordinatesOf(Vec src) {
		return Vec.add(rotation().rotate(Vec.multiply(src, scaling())), translation());
	}

	/**
	 * Returns the world coordinates of the point whose position in the Frame coordinate system is {@code src} (converts
	 * from Frame to world).
	 * <p>
	 * {@link #coordinatesOf(Vec)} performs the inverse conversion. Use {@link #inverseTransformOf(Vec)} to transform
	 * vectors instead of coordinates.
	 */
	public final Vec inverseCoordinatesOf(Vec src) {
		Frame fr = this;
		Vec res = src;
		while (fr != null) {
			res = fr.localInverseCoordinatesOf(res);
			fr = fr.referenceFrame();
		}
		return res;
	}

	/**
	 * Returns the Frame transform of a vector {@code src} defined in the world coordinate system (converts vectors from
	 * world to Frame).
	 * <p>
	 * {@link #inverseTransformOf(Vec)} performs the inverse transformation. {@link #coordinatesOf(Vec)} converts
	 * coordinates instead of vectors (here only the rotational part of the transformation is taken into account).
	 */
	public final Vec transformOf(Vec src) {
		if (referenceFrame() != null)
			return localTransformOf(referenceFrame().transformOf(src));
		else
			return localTransformOf(src);
	}

	/**
	 * Returns the world transform of the vector whose coordinates in the Frame coordinate system is {@code src} (converts
	 * vectors from Frame to world).
	 * <p>
	 * {@link #transformOf(Vec)} performs the inverse transformation. Use {@link #inverseCoordinatesOf(Vec)} to transform
	 * coordinates instead of vectors.
	 */
	public final Vec inverseTransformOf(Vec src) {
		Frame fr = this;
		Vec res = src;
		while (fr != null) {
			res = fr.localInverseTransformOf(res);
			fr = fr.referenceFrame();
		}
		return res;
	}

	/**
	 * Returns the Frame transform of a vector {@code src} defined in the {@link #referenceFrame()} coordinate system
	 * (converts vectors from {@link #referenceFrame()} to Frame).
	 * <p>
	 * {@link #localInverseTransformOf(Vec)} performs the inverse transformation.
	 * 
	 * @see #localCoordinatesOf(Vec)
	 */
	public final Vec localTransformOf(Vec src) {
		return Vec.divide(rotation().inverseRotate(src), scaling());
	}

	/**
	 * Returns the {@link #referenceFrame()} transform of a vector {@code src} defined in the Frame coordinate system
	 * (converts vectors from Frame to {@link #referenceFrame()}).
	 * <p>
	 * {@link #localTransformOf(Vec)} performs the inverse transformation.
	 * 
	 * @see #localInverseCoordinatesOf(Vec)
	 */
	public final Vec localInverseTransformOf(Vec src) {
		return rotation().rotate(Vec.multiply(src, scaling()));
	}
}
