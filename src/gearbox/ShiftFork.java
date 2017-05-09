package gearbox;

import javax.media.j3d.*;
import javax.vecmath.*;

// create shift fork object
public class ShiftFork extends TransformGroup {
    public ShiftFork(double gearRadius, double shaftRadius, Vector3d offsetVector, double thickness,
                     Appearance look, double pinAngle, double PinOffsetSign) {
        super();
        this.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);

        // create gear interface
        this.addChild(new Shaft(gearRadius, thickness, 20, look, look, false, 2 * Math.PI, 0.0,
                false));

        // create base shaft
        Shaft s = new Shaft(shaftRadius, thickness * 1.5, 20, look, look, false, 2 * Math.PI, 0.0,
                false);
        Transform3D tempTransform = new Transform3D();
        s.getTransform(tempTransform);
        tempTransform.setTranslation(offsetVector);
        s.setTransform(tempTransform);
        this.addChild(s);

        // create pin to shift cam
        double pinLength = 0.5;
        s = new Shaft(thickness * 0.5, pinLength, 20, look, look, false, 2 * Math.PI, 0.0, false);
        tempTransform = new Transform3D();
        s.getTransform(tempTransform);

        // move down to shift shaft
        tempTransform.setTranslation(offsetVector);

        // rotate 90 degrees around Y axis
        Transform3D tempTransformOther = new Transform3D();
        tempTransformOther.rotY(Math.toRadians(90));
        tempTransform.mul(tempTransformOther);

        // rotate to be normal to shift cam
        tempTransformOther = new Transform3D();
        tempTransformOther.rotX(pinAngle * PinOffsetSign);
        tempTransform.mul(tempTransformOther);

        // offset toward shift cam
        tempTransformOther = new Transform3D();
        tempTransformOther.setTranslation(new Vector3d(0.0, 0.0, pinLength * PinOffsetSign / 2.0));
        tempTransform.mul(tempTransformOther);

        s.setTransform(tempTransform);
        this.addChild(s);

        // compute coordinates and surface normals
        double frontZ = thickness / 2.0;
        double rearZ = -thickness / 2.0;
        int vertices = 4;
        Vector3d tempVector1 = new Vector3d(0.0, 0.0, 0.0);
        Vector3d tempVector2 = new Vector3d(0.0, 0.0, 0.0);
        Vector3f frontNormal = new Vector3f(0.0F, 0.0F, -1.0F);
        Vector3f rearNormal = new Vector3f(0.0F, 0.0F, 1.0F);
        Vector3d topNormal = new Vector3d(0.0, 0.0, 0.0);
        Vector3d bottomNormal = new Vector3d(0.0, 0.0, 0.0);

        // draw front/rear faces
        QuadArray frontFace = new QuadArray(vertices, GeometryArray.COORDINATES |
                                                      GeometryArray.NORMALS);
        QuadArray rearFace = new QuadArray(vertices, GeometryArray.COORDINATES |
                                                     GeometryArray.NORMALS);
        int index1 = 0;
        int index2 = 3;
        double[][] coords = {{gearRadius, 0.0}, {-gearRadius, 0.0}, {offsetVector.x - shaftRadius,
                                                                     offsetVector.y},
                             {offsetVector.x + shaftRadius, offsetVector.y}};
        for (int i = 0; i < coords.length; i++) {
            // draw front face
            frontFace.setCoordinate(index1, new Point3d(coords[i][0], coords[i][1], frontZ));
            frontFace.setNormal(index1, rearNormal);

            // draw back face
            rearFace.setCoordinate(index2, new Point3d(coords[i][0], coords[i][1], rearZ));
            rearFace.setNormal(index2, frontNormal);

            index1++;
            index2--;
        }
        // draw top/bottom faces
        Point3d[] topCoords = {new Point3d(-gearRadius, 0.0, frontZ),
                               new Point3d(-gearRadius, 0.0, rearZ),
                               new Point3d(offsetVector.x - shaftRadius, offsetVector.y, rearZ),
                               new Point3d(offsetVector.x - shaftRadius, offsetVector.y, frontZ)};
        Point3d[] bottomCoords = {new Point3d(gearRadius, 0.0, rearZ),
                                  new Point3d(gearRadius, 0.0, frontZ),
                                  new Point3d(offsetVector.x + shaftRadius, offsetVector.y, frontZ),
                                  new Point3d(offsetVector.x + shaftRadius, offsetVector.y, rearZ)};

        // compute normal for top side
        tempVector1.sub(topCoords[0], topCoords[1]);
        tempVector2.sub(topCoords[1], topCoords[2]);
        topNormal.cross(tempVector1, tempVector2);
        topNormal.normalize();

        // compute normal for bottom side
        tempVector1.sub(bottomCoords[0], bottomCoords[1]);
        tempVector2.sub(bottomCoords[1], bottomCoords[2]);
        bottomNormal.cross(tempVector1, tempVector2);
        bottomNormal.normalize();

        QuadArray topFace = new QuadArray(vertices, GeometryArray.COORDINATES |
                                                    GeometryArray.NORMALS);
        QuadArray bottomFace = new QuadArray(vertices, GeometryArray.COORDINATES |
                                                       GeometryArray.NORMALS);
        for (int i = 0; i < vertices; i++) {
            // draw top face
            topFace.setCoordinate(i, topCoords[i]);
            topFace.setNormal(i, new Vector3f(topNormal));

            // draw bottom face
            bottomFace.setCoordinate(i, bottomCoords[i]);
            bottomFace.setNormal(i, new Vector3f(bottomNormal));
        }
        this.addChild(new Shape3D(frontFace, look));
        this.addChild(new Shape3D(rearFace, look));
        this.addChild(new Shape3D(topFace, look));
        this.addChild(new Shape3D(bottomFace, look));
    }
}
