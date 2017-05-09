package gearbox;

import javax.media.j3d.*;
import javax.vecmath.*;

// hook into the RotationInterpolator used to animate the shift cam to also animate the index cam
// follower
public class CamInterpolator extends RotationInterpolator {
    double sin;
    double cos;
    double old_angle;
    double angle_offset;
    double radius;
    double x_off;
    double y_off;
    Transform3D tempTransform;
    Vector3d tempVector;
    TransformGroup followerTG;

    public CamInterpolator(Alpha alpha, TransformGroup target, Transform3D axisOfTransform,
                           double minimumAngle, double maximumAngle, TransformGroup followerTG,
                           double radius, double x_off, double y_off) {
        super(alpha, target, axisOfTransform, (float) minimumAngle, (float) maximumAngle);
        old_angle = 0;
        tempTransform = new Transform3D();
        tempVector = new Vector3d();
        double angle = Math.toRadians(195);
        angle_offset = Math.toRadians(105);
        sin = Math.sin(angle);
        cos = Math.cos(angle);
        this.followerTG = followerTG;
        this.radius = radius;
        this.x_off = x_off;
        this.y_off = y_off;
    }

    // move the follower as the shift cam rotates.
    public void processStimulus(java.util.Enumeration criteria) {
        super.processStimulus(criteria);
        // compute and update the new follower position
        double value = this.getAlpha().value();
        double angle = (this.getMaximumAngle() - this.getMinimumAngle()) * value + this
                       .getMinimumAngle();
        // Don't do work that we don't have to do
        if (angle == old_angle) {
            return;
        }
        old_angle = angle;
        double r = radius - StarCam.profile(angle + angle_offset);
        // fudge factor to take into account the fact that the follower has a non-zero diameter
        double x = Math.toDegrees(angle);
        if (x >= 45 && x <= 105) {
            // the "neutral" area
            ;
        } else {
            r += Math.abs(Math.sin(value * Math.PI * 2) * 0.025);
        }
        tempVector.set(r * sin, x_off + r * cos, y_off);
        tempTransform.setTranslation(tempVector);
        followerTG.setTransform(tempTransform);
    }
}
