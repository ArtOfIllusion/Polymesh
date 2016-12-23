package artofillusion.polymesh;

import artofillusion.ViewerCanvas;
import artofillusion.math.Mat4;
import artofillusion.math.Vec3;

/**
 * This is the base class manipulator responsible for moving, resizing and rotating mesh selections.
 * SSMR = Select Scale Move Rotate
 * Variants are 2D manipulator and 3D manipulator
 */
public abstract class SSMRManipulator extends Manipulator
{
    public final static short SCALE = 0;
    public final static short ROTATE = 1;
    public final static short MOVE = 2;
    public final static short ABORT = 3;
    public final static short XAXIS = 0;
    public final static short YAXIS = 1;
    public final static short ZAXIS = 2;

    public final static short ANCHOR_LEFT = 0;
    public final static short ANCHOR_RIGHT = 1;
    public final static short ANCHOR_TOP = 2;
    public final static short ANCHOR_BOTTOM = 3;
    public final static short ANCHOR_CENTER = 4;

    public SSMRManipulator(AdvancedEditingTool tool, ViewerCanvas view, PolyMeshValueWidget valueWidget)
    {
        super(tool, view, valueWidget);
    }

    public static class ManipulatorScalingEvent extends ManipulatorEvent
    {
        private Mat4 scaleMatrix;

        public ManipulatorScalingEvent(Manipulator manipulator, Mat4 matrix, ViewerCanvas view)
        {
            super(manipulator, SCALE, view);
            scaleMatrix = matrix;
        }

        public Mat4 getScaleMatrix()
        {
            return scaleMatrix;
        }
    }

    public static class ManipulatorRotatingEvent extends ManipulatorEvent
    {
        private Mat4 mat;

        public ManipulatorRotatingEvent(Manipulator manipulator, Mat4 mat, ViewerCanvas view)
        {
            super(manipulator, view);
            this.mat = mat;
        }

        public Mat4 getMatrix()
        {
            return mat;
        }

    }

    public static class ManipulatorMovingEvent extends ManipulatorEvent
    {
        Vec3 drag;

        public ManipulatorMovingEvent(Manipulator manipulator, Vec3 dr, ViewerCanvas view)
        {
            super(manipulator, view);
            drag = dr;
        }

        public Vec3 getDrag()
        {
            return drag;
        }
    }
}
